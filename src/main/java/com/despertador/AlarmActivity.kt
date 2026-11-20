package com.despertador

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

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

        raiz.adicionar(texto("Acorda, Bibica! 🎀", 30f, ROSA, true), 0)
        progresso = texto("", 16f, SUAVE)
        raiz.adicionar(progresso, 8)

        conta = texto("", 56f, TEXTO, true)
        conta.gravity = Gravity.CENTER
        val cartao = GradientDrawable()
        cartao.cornerRadius = dp(28).toFloat()
        cartao.setColor(Color.WHITE)
        cartao.setStroke(dp(2), ROSA_CLARO)
        conta.background = cartao
        conta.setPadding(dp(16), dp(24), dp(16), dp(24))
        raiz.adicionar(conta, 32)

        campo = EditText(this)
        campo.inputType = InputType.TYPE_CLASS_NUMBER
        campo.textSize = 32f
        campo.gravity = Gravity.CENTER
        campo.setTextColor(TEXTO)
        val fundoCampo = GradientDrawable()
        fundoCampo.cornerRadius = dp(24).toFloat()
        fundoCampo.setColor(Color.WHITE)
        fundoCampo.setStroke(dp(2), ROSA)
        campo.background = fundoCampo
        campo.setPadding(dp(16), dp(12), dp(16), dp(12))
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
        Toast.makeText(this, "Alarme desligado. Bom dia, Bibica! 🎀", Toast.LENGTH_LONG).show()
        finishAndRemoveTask()
    }

    // O botão Voltar não escapa do despertador.
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
    }
}
