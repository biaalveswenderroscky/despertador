package com.despertador

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

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

        val titulo = texto("🎀 Despertador da Bibica 🎀", 26f, ROSA, true)
        titulo.gravity = Gravity.CENTER
        raiz.adicionar(titulo, 0)

        val subtitulo = texto("Para desligar, acerte $ACERTOS_NECESSARIOS contas seguidas.", 16f, SUAVE)
        subtitulo.gravity = Gravity.CENTER
        raiz.adicionar(subtitulo, 8)

        raiz.adicionar(texto("Horário do alarme (toque para mudar)", 14f, SUAVE), 28)
        botaoHora = botao("", false)
        botaoHora.textSize = 40f
        botaoHora.setOnClickListener { escolherHora() }
        raiz.adicionar(botaoHora, 8)

        status = texto("", 16f, SUAVE)
        raiz.adicionar(status, 16)

        val botaoArmar = botao("Armar alarme 🎀", true)
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
