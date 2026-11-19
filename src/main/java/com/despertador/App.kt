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
import android.graphics.drawable.GradientDrawable
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

private val FUNDO = Color.parseColor("#FFF0F5")      // fundo rosa clarinho
private val TEXTO = Color.parseColor("#5B2A47")      // texto principal (ameixa)
private val SUAVE = Color.parseColor("#A5527F")      // texto secundário
private val ROSA = Color.parseColor("#D6336C")       // destaque: botão principal e títulos
private val ROSA_CLARO = Color.parseColor("#FBCFE8") // botões secundários e bordas
private val VERDE = Color.parseColor("#2E8B57")      // mensagem "Certo!"
private val VERMELHO = Color.parseColor("#D62839")   // mensagem de erro

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
    b.setTextColor(if (destaque) Color.WHITE else TEXTO)

    // Botão com cantos bem arredondados
    val fundo = GradientDrawable()
    fundo.shape = GradientDrawable.RECTANGLE
    fundo.cornerRadius = dp(28).toFloat()
    fundo.setColor(if (destaque) ROSA else ROSA_CLARO)
    if (!destaque) fundo.setStroke(dp(2), ROSA)
    b.background = fundo
    b.setPadding(dp(16), dp(14), dp(16), dp(14))
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
            .setContentTitle("Acorda, Bibica! 🎀")
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
