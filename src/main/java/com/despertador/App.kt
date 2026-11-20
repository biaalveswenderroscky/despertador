package com.despertador

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

const val PREFS = "despertador"
const val CANAL = "alarme_despertador"
const val ACAO_TOCAR = "com.despertador.TOCAR"
const val ACERTOS_NECESSARIOS = 3

val FUNDO = Color.parseColor("#FFF0F5")      // fundo rosa clarinho
val TEXTO = Color.parseColor("#5B2A47")      // texto principal (ameixa)
val SUAVE = Color.parseColor("#A5527F")      // texto secundário
val ROSA = Color.parseColor("#D6336C")       // destaque: botão principal e títulos
val ROSA_CLARO = Color.parseColor("#FBCFE8") // botões secundários e bordas
val VERDE = Color.parseColor("#2E8B57")      // mensagem "Certo!"
val VERMELHO = Color.parseColor("#D62839")   // mensagem de erro

// ---------------------------------------------------------------
// Utilidades para montar a tela
// ---------------------------------------------------------------
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.texto(conteudo: String, tamanho: Float, cor: Int = TEXTO, negrito: Boolean = false): TextView {
    val t = TextView(this)
    t.text = conteudo
    t.textSize = tamanho
    t.setTextColor(cor)
    if (negrito) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

fun Context.botao(rotulo: String, destaque: Boolean): Button {
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

fun LinearLayout.adicionar(v: View, topo: Int = 12) {
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
