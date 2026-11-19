package com.despertador

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import kotlin.random.Random

private const val PREFS = "despertador"
private const val CANAL = "alarme_despertador"
private const val ACAO_TOCAR = "com.despertador.TOCAR"
private const val ACERTOS_NECESSARIOS = 3

private val FUNDO = Color.parseColor("#0E1216")
private val TEXTO = Color.parseColor("#EEF1F4")
private val SUAVE = Color.parseColor("#9AA6B1")
private val LARANJA = Color.parseColor("#FF8A4C")
private val VERDE = Color.parseColor("#4ADE80")
private val VERMELHO = Color.parseColor("#F87171")

// ---------------------------------------------------------------
// Utilidades para montar a tela
// ---------------------------------------------------------------
private fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

private fun Context.texto(conteudo: String, tamanho: Float, cor: Int = TEXTO, negrito: Boolean = false): TextView {
    val t = TextView(this)
    t.text = conteudo
    t.textSize = tamanho
    t.setTextColor(cor)
    if (negrito) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

private fun Context.botao(rotulo: String, destaque: Boolean): Button {
    val b = Button(this)
    b.text = rotulo
    b.isAllCaps = false
    b.setTextColor(if (destaque) Color.parseColor("#1A0D05") else TEXTO)
    b.setBackgroundColor(if (destaque) LARANJA else Color.parseColor("#2A333B"))
    return b
}

private fun LinearLayout.adicionar(v: View, topo: Int = 12) {
    val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    p.topMargin = (topo * resources.displayMetrics.density).toInt()
    addView(v, p)
}

// ---------------------------------------------------------------
// Agendamento do alarme (usa o sistema de alarmes do Android)
// ---------------------------------------------------------------
private fun pendingTocar(ctx: Context): PendingIntent {
    val intent = Intent(ctx, AlarmReceiver::class.java)
    intent.action = ACAO_TOCAR
    return PendingIntent.getBroadcast(
        ctx, 1, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun agendar(ctx: Context, quando: Long): Boolean {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val abrir = PendingIntent.getActivity(
        ctx, 2, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return try {
        am.setAlarmClock(AlarmManager.AlarmClockInfo(quando, abrir), pendingTocar(ctx))
        true
    } catch (e: SecurityException) {
        false
    }
}

fun cancelarAlarme(ctx: Context) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.cancel(pendingTocar(ctx))
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("alvo").apply()
}

// ---------------------------------------------------------------
// Recebe o aviso do sistema na hora do alarme e liga o som
// ---------------------------------------------------------------
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("alvo").apply()
        try {
            context.startForegroundService(Intent(context, RingService::class.java))
        } catch (e: Exception) {
            // sem fazer nada: não há outra forma segura de tocar neste caso
        }
    }
}

// Depois de reiniciar o celular, o Android apaga os alarmes. Aqui eles voltam.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alvo = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("alvo", 0L)
        if (alvo > System.currentTimeMillis()) agendar(context, alvo)
    }
}

// ---------------------------------------------------------------
// Serviço que toca o som e vibra até as contas serem resolvidas
// ---------------------------------------------------------------
class RingService : Service() {
    companion object {
        @Volatile
        var tocando = false
    }

    private var player: MediaPlayer? = null
    private var vibrador: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tocando = true

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canal = NotificationChannel(CANAL, "Alarme", NotificationManager.IMPORTANCE_HIGH)
        canal.setSound(null, null) // o som vem do MediaPlayer, não da notificação
        nm.createNotificationChannel(canal)

        val abrir = Intent(this, AlarmActivity::class.java)
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 3, abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacao = Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Acorda!")
            .setContentText("Toque aqui e resolva as contas para desligar o alarme.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notificacao)
        }

        iniciarSom()

        try {
            startActivity(abrir)
        } catch (e: Exception) {
            // se o Android bloquear, a notificação abre a tela quando você tocar nela
        }
        return START_STICKY
    }

    private fun iniciarSom() {
        if (player != null) return

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            // sem som, a vibração e a notificação continuam
        }

        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
            vibrador = v
        } catch (e: Exception) {
            // aparelho sem vibração
        }
    }

    override fun onDestroy() {
        tocando = false
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        try {
            vibrador?.cancel()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}

// ---------------------------------------------------------------
// Tela do alarme tocando: só sai resolvendo as contas
// ---------------------------------------------------------------
class AlarmActivity : Activity() {
    private var acertos = 0
    private var resposta = 0
    private lateinit var conta: TextView
    private lateinit var progresso: TextView
    private lateinit var feedback: TextView
    private lateinit var campo: EditText

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se o alarme não está tocando (tela aberta por engano), fecha.
        if (!RingService.tocando) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.gravity = Gravity.CENTER_HORIZONTAL
        raiz.setBackgroundColor(FUNDO)
        raiz.setPadding(dp(24), dp(48), dp(24), dp(24))

        raiz.adicionar(texto("Acorda!", 34f, LARANJA, true), 0)
        progresso = texto("", 16f, SUAVE)
        raiz.adicionar(progresso, 8)

        conta = texto("", 56f, TEXTO, true)
        conta.gravity = Gravity.CENTER
        raiz.adicionar(conta, 32)

        campo = EditText(this)
        campo.inputType = InputType.TYPE_CLASS_NUMBER
        campo.textSize = 32f
        campo.gravity = Gravity.CENTER
        campo.setTextColor(TEXTO)
        campo.imeOptions = EditorInfo.IME_ACTION_DONE
        campo.setOnEditorActionListener { _, _, _ ->
            responder()
            true
        }
        raiz.adicionar(campo, 16)

        val botaoResponder = botao("Responder", true)
        botaoResponder.setOnClickListener { responder() }
        raiz.adicionar(botaoResponder, 16)

        feedback = texto("", 16f, SUAVE)
        feedback.gravity = Gravity.CENTER
        raiz.adicionar(feedback, 16)

        val rolagem = ScrollView(this)
        rolagem.setBackgroundColor(FUNDO)
        rolagem.isFillViewport = true
        rolagem.addView(raiz)
        setContentView(rolagem)

        novaConta()
    }

    private fun novaConta() {
        val a: Int
        val b: Int
        val simbolo: String
        when (Random.nextInt(3)) {
            0 -> {
                a = Random.nextInt(10, 100)
                b = Random.nextInt(10, 100)
                simbolo = "+"
                resposta = a + b
            }
            1 -> {
                a = Random.nextInt(30, 100)
                b = Random.nextInt(10, 30)
                simbolo = "-"
                resposta = a - b
            }
            else -> {
                a = Random.nextInt(3, 13)
                b = Random.nextInt(3, 13)
                simbolo = "×"
                resposta = a * b
            }
        }
        conta.text = "$a $simbolo $b"
        campo.setText("")
        progresso.text = "$acertos de $ACERTOS_NECESSARIOS acertos seguidos"
    }

    private fun responder() {
        val digitado = campo.text.toString().trim().toIntOrNull()
        if (digitado == null) {
            mostrar("Digite apenas números.", VERMELHO)
            return
        }
        if (digitado == resposta) {
            acertos++
            if (acertos >= ACERTOS_NECESSARIOS) {
                terminar()
                return
            }
            mostrar("Certo!", VERDE)
        } else {
            acertos = 0
            mostrar("Errado! O contador voltou para zero.", VERMELHO)
        }
        novaConta()
    }

    private fun mostrar(mensagem: String, cor: Int) {
        feedback.text = mensagem
        feedback.setTextColor(cor)
    }

    private fun terminar() {
        stopService(Intent(this, RingService::class.java))
        Toast.makeText(this, "Alarme desligado. Bom dia!", Toast.LENGTH_LONG).show()
        finishAndRemoveTask()
    }

    // O botão Voltar não escapa do despertador.
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
    }
}

// ---------------------------------------------------------------
// Tela principal: escolher o horário e armar o alarme
// ---------------------------------------------------------------
class MainActivity : Activity() {
    private var hora = 7
    private var minuto = 0
    private lateinit var botaoHora: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(FUNDO)
        raiz.setPadding(dp(20), dp(40), dp(20), dp(24))

        raiz.adicionar(texto("Despertador Matemático", 26f, TEXTO, true), 0)
        raiz.adicionar(
            texto("Para desligar, acerte $ACERTOS_NECESSARIOS contas seguidas.", 16f, SUAVE),
            8
        )

        raiz.adicionar(texto("Horário do alarme (toque para mudar)", 14f, SUAVE), 28)
        botaoHora = botao("", false)
        botaoHora.textSize = 40f
        botaoHora.setOnClickListener { escolherHora() }
        raiz.adicionar(botaoHora, 8)

        status = texto("", 16f, SUAVE)
        raiz.adicionar(status, 16)

        val botaoArmar = botao("Armar alarme", true)
        botaoArmar.setOnClickListener { armarAlarme() }
        raiz.adicionar(botaoArmar, 16)

        val botaoCancelar = botao("Cancelar alarme", false)
        botaoCancelar.setOnClickListener {
            cancelarAlarme(this)
            atualizarStatus()
        }
        raiz.adicionar(botaoCancelar, 12)

        val botaoTestar = botao("Testar agora", false)
        botaoTestar.setOnClickListener {
            startForegroundService(Intent(this, RingService::class.java))
        }
        raiz.adicionar(botaoTestar, 12)

        val rolagem = ScrollView(this)
        rolagem.setBackgroundColor(FUNDO)
        rolagem.isFillViewport = true
        rolagem.addView(raiz)
        setContentView(rolagem)

        mostrarHora()
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    private fun mostrarHora() {
        botaoHora.text = String.format("%02d:%02d", hora, minuto)
    }

    private fun escolherHora() {
        TimePickerDialog(this, { _, h, m ->
            hora = h
            minuto = m
            mostrarHora()
        }, hora, minuto, true).show()
    }

    private fun armarAlarme() {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hora)
        c.set(Calendar.MINUTE, minuto)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)

        if (agendar(this, c.timeInMillis)) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("alvo", c.timeInMillis).apply()
            Toast.makeText(this, "Alarme armado!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "O Android não permitiu agendar o alarme.", Toast.LENGTH_LONG).show()
        }
        atualizarStatus()

        // No Android 14+, a tela cheia pode precisar de permissão. Se faltar, abre a configuração.
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun atualizarStatus() {
        val alvo = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("alvo", 0L)
        if (alvo > System.currentTimeMillis()) {
            val c = Calendar.getInstance()
            c.timeInMillis = alvo
            status.text = String.format(
                "Alarme armado para %02d:%02d",
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE)
            )
        } else {
            status.text = "Nenhum alarme armado."
        }
    }
}
