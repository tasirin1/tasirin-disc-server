package com.tasirin.discserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

// Layanan latar depan yang memegang server HTTP + pendaftar penemuan,
// supaya server tetap hidup selama PPSSPP di perangkat lain bermain.
class DiscServerService : Service() {

    private var server: DiscHttpServer? = null
    private var registrar: ServerRegistrar? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                hentikan()
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
            ACTION_START -> {
                if (!ServerState.running && !ServerState.starting) {
                    mulai()
                }
            }
            else -> {
                if (!ServerState.running && !ServerState.starting) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun mulai() {
        ServerState.starting = true
        ServerState.error = null
        startForeground(ID_NOTIF, buatNotifikasi("Menyiapkan server…"))
        Thread {
            var sukses = false
            try {
                val uri = Prefs.folderUri(this)
                if (uri == null) {
                    ServerState.error = "Folder game belum dipilih."
                } else {
                    val pustaka = GameLibrary.scan(this, uri)
                    if (pustaka == null) {
                        ServerState.error = "Folder game tidak bisa dibaca. Pilih ulang foldernya."
                    } else {
                        val srv = DiscHttpServer(this, pustaka, PORT_BAWAAN)
                        if (srv.start()) {
                            server = srv
                            ServerState.port = srv.port
                            ServerState.ip = NetUtils.localIpv4()
                            ServerState.games = pustaka.gameNames()
                            ServerState.running = true
                            val reg = ServerRegistrar(
                                ipProvider = { NetUtils.localIpv4() },
                                portProvider = { ServerState.port }
                            )
                            registrar = reg
                            reg.start()
                            sukses = true
                        } else {
                            ServerState.error =
                                "Port $PORT_BAWAAN sampai ${PORT_BAWAAN + 19} sibuk semua."
                        }
                    }
                }
            } catch (e: Exception) {
                ServerState.error = "Gagal memulai: ${e.message}"
            }
            ServerState.starting = false
            if (sukses) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(ID_NOTIF, buatNotifikasi(teksAlamat()))
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
        }.start()
    }

    private fun hentikan() {
        try {
            registrar?.stop()
        } catch (e: Exception) {
            // Abaikan.
        }
        registrar = null
        try {
            server?.stop()
        } catch (e: Exception) {
            // Abaikan.
        }
        server = null
        ServerState.running = false
        ServerState.starting = false
        ServerState.registered = false
        ServerState.port = -1
        ServerState.ip = null
    }

    override fun onDestroy() {
        hentikan()
        super.onDestroy()
    }

    private fun teksAlamat(): String {
        val ip = ServerState.ip ?: "?"
        return "Berjalan di http://$ip:${ServerState.port}/"
    }

    private fun buatNotifikasi(teks: String): Notification {
        buatKanal()
        val niat = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, niat,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ID_KANAL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return b.setSmallIcon(R.drawable.ic_stat_disc)
            .setContentTitle("Tasirin Disc Server")
            .setContentText(teks)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun buatKanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(ID_KANAL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        ID_KANAL, "Server disc", NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    companion object {
        const val ACTION_START = "com.tasirin.discserver.MULAI"
        const val ACTION_STOP = "com.tasirin.discserver.BERHENTI"
        const val PORT_BAWAAN = 51234
        private const val ID_NOTIF = 1
        private const val ID_KANAL = "server_disc"
    }
}
