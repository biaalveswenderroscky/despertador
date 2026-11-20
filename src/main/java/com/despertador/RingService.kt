package com.despertador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator

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
