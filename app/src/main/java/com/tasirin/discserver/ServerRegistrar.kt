package com.tasirin.discserver

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

// Mendaftarkan alamat LAN server ini ke server penemuan PPSSPP
// (report.ppsspp.org/match/update), persis seperti server bawaan PPSSPP,
// supaya "Browse games" di PPSSPP bisa menemukan server ini otomatis dalam
// satu jaringan. Ini usaha terbaik saja: gagal pun server tetap bisa
// dipakai lewat konfigurasi manual (IP + port) di PPSSPP.
class ServerRegistrar(
    private val ipProvider: () -> String?,
    private val portProvider: () -> Int
) {
    private val jalan = AtomicBoolean(false)
    private var benang: Thread? = null

    fun start() {
        if (jalan.getAndSet(true)) return
        benang = Thread {
            while (jalan.get()) {
                daftarSekali()
                var tidur = 0
                while (jalan.get() && tidur < 60) {
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        break
                    }
                    tidur++
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        jalan.set(false)
        benang?.interrupt()
        benang = null
    }

    private fun daftarSekali() {
        val ip = ipProvider() ?: return
        val port = portProvider()
        if (port <= 0) return
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://report.ppsspp.org/match/update?local=$ip&port=$port")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            // Server penemuan mengenali pendaftar dari agen pengguna protokol PPSSPP.
            conn.setRequestProperty("User-Agent", "PPSSPP/1.19.3")
            val kode = conn.responseCode
            ServerState.registered = kode in 200..299
            try {
                conn.inputStream?.close()
            } catch (e: Exception) {
                // Abaikan.
            }
        } catch (e: Exception) {
            ServerState.registered = false
        } finally {
            conn?.disconnect()
        }
    }
}
