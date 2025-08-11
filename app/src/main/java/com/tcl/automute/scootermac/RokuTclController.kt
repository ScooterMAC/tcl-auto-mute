package com.tcl.automute.scootermac

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RokuTclController(private val tvIp: String?) : TclController {

    private val port = 8060
    private val mutePath = "/keypress/VolumeMute" // toggles mute

    override suspend fun mute() {
        if (tvIp.isNullOrBlank()) {
            Log.w(TAG, "No TV IP configured")
            return
        }
        sendPost(mutePath)
    }

    override suspend fun unmute() {
        if (tvIp.isNullOrBlank()) {
            Log.w(TAG, "No TV IP configured")
            return
        }
        sendPost(mutePath)
    }

    suspend fun muteWithRetries(retries: Int) {
        for (i in 0 until retries) {
            try {
                sendPost(mutePath)
                return
            } catch (e: Exception) {
                // retry
            }
        }
    }

    suspend fun unmuteWithRetries(retries: Int) {
        for (i in 0 until retries) {
            try {
                sendPost(mutePath)
                return
            } catch (e: Exception) {
                // retry
            }
        }
    }

    private suspend fun sendPost(path: String) = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$tvIp:$port$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2000
                readTimeout = 2000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain")
            }
            OutputStreamWriter(conn.outputStream).use { it.write("") }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Roku ECP returned HTTP $code")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "sendPost failed: ${'$'}{e.message}")
            throw e
        }
    }

    companion object { private const val TAG = "RokuTclController" }
}
