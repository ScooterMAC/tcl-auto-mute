package com.tcl.automute.scootermac

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.regex.Pattern

data class RokuDevice(val name: String, val ip: String)

object RokuDiscovery {
    private const val TAG = "RokuDiscovery"
    private const val SSDP_ADDR = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val MSEARCH = """M-SEARCH * HTTP/1.1
HOST: $SSDP_ADDR:$SSDP_PORT
MAN: "ssdp:discover"
ST: roku:ecp
MX: 3

""".trimIndent()

    suspend fun discoverAll(timeoutMs: Int = 5000): List<RokuDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<RokuDevice>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val addr = InetAddress.getByName(SSDP_ADDR)
                val sendData = MSEARCH.toByteArray()
                socket.send(DatagramPacket(sendData, sendData.size, addr, SSDP_PORT))

                val buf = ByteArray(4096)
                val packet = DatagramPacket(buf, buf.size)
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeoutMs) {
                    try {
                        socket.receive(packet)
                        val resp = String(packet.data, 0, packet.length)
                        if (resp.contains("roku:ecp", ignoreCase = true)) {
                            val ipMatch = Pattern.compile("(?im)^location:\s*http://([0-9.]+):").matcher(resp)
                            val nameMatch = Pattern.compile("(?im)^server:\s*(.*)").matcher(resp)
                            var ip: String? = null
                            var name: String? = null
                            if (ipMatch.find()) ip = ipMatch.group(1)
                            if (nameMatch.find()) name = nameMatch.group(1)
                            if (ip != null) {
                                devices.add(RokuDevice(name ?: "Roku", ip))
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // continue waiting until timeout end
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed: ${'$'}{e.message}")
        }
        devices.distinctBy { it.ip }
    }
}
