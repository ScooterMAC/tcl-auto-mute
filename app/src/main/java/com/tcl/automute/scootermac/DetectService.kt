package com.tcl.automute.scootermac

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.content.Context

class DetectService : Service() {
    companion object {
        const val ACTION_START = "com.tcl.automute.scootermac.START"
        const val ACTION_STOP = "com.tcl.automute.scootermac.STOP"
        const val CHANNEL_ID = "admuter_channel"
    }

    private val svcScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var running = false
    private var controller: TclController? = null

    private var sensitivity = 0.5f
    private var tvIp: String? = null
    private var mutingEnabled = true
    private var silentMode = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sensitivity = intent.getFloatExtra("sensitivity", 0.5f)
                tvIp = intent.getStringExtra("tv_ip")
                mutingEnabled = intent.getBooleanExtra("muting_enabled", true)
                silentMode = intent.getBooleanExtra("silent_mode", false)
                controller = RokuTclController(tvIp)
                startForeground(1, buildNotification("Listening for commercials"))
                startDetection()
            }
            ACTION_STOP -> {
                stopDetection()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "AdMuter", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AdMuter")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentIntent(pi)
            .build()
    }

    private fun startDetection() {
        if (running) return
        running = true
        svcScope.launch { audioLoop() }
    }

    private fun stopDetection() {
        running = false
        svcScope.cancel()
    }

    override fun onDestroy() {
        stopDetection()
        super.onDestroy()
    }

    // --- Audio capture + detection loop ---
    private suspend fun audioLoop() = withContext(Dispatchers.Default) {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val audioBuffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)

        record.startRecording()

        // detection state
        var commercialConfidence = 0.0
        var muted = false

        val fft = SimpleFFT( nextPow2(bufferSize) )

        // adaptive baseline
        var baseline = 0.0
        var adaptCount = 0

        while (running) {
            val read = record.read(audioBuffer, 0, audioBuffer.size)
            if (read <= 0) continue

            // convert to float
            for (i in 0 until read) floatBuffer[i] = audioBuffer[i].toFloat() / Short.MAX_VALUE

            // RMS loudness
            val rms = computeRMS(floatBuffer, read)
            val db = 20 * log10(rms + 1e-9)

            // update adaptive baseline for first N frames
            if (adaptCount < 50) {
                baseline = (baseline * adaptCount + db) / (adaptCount + 1)
                adaptCount++
            }

            val flux = computeSpectralFlux(floatBuffer, read, fft)

            val loudnessScore = (((db - baseline) + 10) / 30).coerceIn(0.0, 1.0)
            val fluxScore = (flux / 10.0).coerceIn(0.0, 1.0)

            val combined = (loudnessScore * 0.6) + (fluxScore * 0.4)

            val threshold = 0.5 - (sensitivity - 0.5)

            commercialConfidence = commercialConfidence * 0.85 + combined * 0.15

            if (commercialConfidence > threshold && !muted) {
                muted = true
                if (mutingEnabled) {
                    controller?.muteWithRetries(3)
                }
                showAlert("Commercial detected — Muted")
                startForeground(1, buildNotification("Muted (commercial)"))
                logEvent("Commercial detected — Muted")
            } else if (commercialConfidence < (threshold * 0.7) && muted) {
                muted = false
                if (mutingEnabled) {
                    controller?.unmuteWithRetries(3)
                }
                showAlert("Program resumed — Unmuted")
                startForeground(1, buildNotification("Unmuted (program)"))
                logEvent("Program resumed — Unmuted")
            }

            delay(300)
        }

        record.stop()
        record.release()
    }

    private fun computeRMS(buf: FloatArray, len: Int): Double {
        var sum = 0.0
        for (i in 0 until len) {
            val v = buf[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / len)
    }

    private var prevMag: DoubleArray? = null
    private fun computeSpectralFlux(buf: FloatArray, len: Int, fft: SimpleFFT): Double {
        val window = DoubleArray(fft.n)
        for (i in window.indices) window[i] = if (i < len) buf[i].toDouble() else 0.0
        val mag = fft.magnitudeSpectrum(window)
        val prev = prevMag
        var flux = 0.0
        if (prev != null) {
            val l = minOf(mag.size, prev.size)
            for (i in 0 until l) {
                val diff = mag[i] - prev[i]
                if (diff > 0) flux += diff
            }
        }
        prevMag = mag
        return flux
    }

    private fun nextPow2(x: Int): Int {
        var v = 1
        while (v < x) v = v shl 1
        return v
    }

    private fun showAlert(text: String) {
        if (silentMode) return
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun logEvent(text: String) {
        try {
            val fn = java.io.File(getFilesDir(), "events.log")
            val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())
            fn.appendText("$ts - $text\n")
        } catch (e: Exception) {
            // ignore
        }
    }
}
