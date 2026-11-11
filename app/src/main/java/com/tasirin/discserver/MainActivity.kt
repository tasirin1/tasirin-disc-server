package com.tasirin.discserver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvAlamat: TextView
    private lateinit var tvPenemuan: TextView
    private lateinit var tvGalat: TextView
    private lateinit var tvFolder: TextView
    private lateinit var tvGames: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val penyegar = object : Runnable {
        override fun run() {
            segarkan()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAlamat = findViewById(R.id.tvAlamat)
        tvPenemuan = findViewById(R.id.tvPenemuan)
        tvGalat = findViewById(R.id.tvGalat)
        tvFolder = findViewById(R.id.tvFolder)
        tvGames = findViewById(R.id.tvGames)

        findViewById<Button>(R.id.btnFolder).setOnClickListener { pilihFolder() }
        findViewById<Button>(R.id.btnMulai).setOnClickListener { mulaiServer() }
        findViewById<Button>(R.id.btnBerhenti).setOnClickListener {
            kirimKeLayanan(DiscServerService.ACTION_STOP)
        }

        muatDaftarGame()
        segarkan()
    }

    override fun onResume() {
        super.onResume()
        handler.post(penyegar)
    }

    override fun onPause() {
        handler.removeCallbacks(penyegar)
        super.onPause()
    }

    // --- Pilihan folder (SAF) ---

    private fun pilihFolder() {
        val niat = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(niat, RC_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_FOLDER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Sebagian perangkat menolak; akses sesi ini tetap dicoba.
            }
            Prefs.setFolderUri(this, uri)
            muatDaftarGame()
            if (ServerState.running) {
                // Mulai ulang layanan agar memakai folder baru.
                kirimKeLayanan(DiscServerService.ACTION_STOP)
                handler.postDelayed({ kirimKeLayanan(DiscServerService.ACTION_START) }, 400)
            }
        }
    }

    // --- Kendali layanan ---

    private fun mulaiServer() {
        if (Prefs.folderUri(this) == null) {
            Toast.makeText(this, "Pilih folder game dulu ya", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_NOTIF)
        }
        kirimKeLayanan(DiscServerService.ACTION_START)
    }

    private fun kirimKeLayanan(aksi: String) {
        val niat = Intent(this, DiscServerService::class.java).setAction(aksi)
        if (aksi == DiscServerService.ACTION_START &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            startForegroundService(niat)
        } else {
            startService(niat)
        }
    }

    // --- Tampilan ---

    private fun muatDaftarGame() {
        val uri = Prefs.folderUri(this)
        if (uri == null) {
            tvFolder.text = "Folder: belum dipilih"
            aturTeksGame("Belum ada game. Pilih folder dulu.")
            return
        }
        val nama = try {
            DocumentFile.fromTreeUri(this, uri)?.name
        } catch (e: Exception) {
            null
        }
        tvFolder.text = "Folder: ${nama ?: uri.lastPathSegment ?: uri.toString()}"
        Thread {
            val pustaka = GameLibrary.scan(this, uri)
            val daftar = pustaka?.gameNames() ?: emptyList()
            runOnUiThread {
                if (!ServerState.running) {
                    ServerState.games = daftar
                }
                tampilkanGame(daftar)
            }
        }.start()
    }

    private fun tampilkanGame(daftar: List<String>) {
        if (daftar.isEmpty()) {
            aturTeksGame("Tidak ada berkas ISO/CSO/CHD di folder ini.")
        } else {
            aturTeksGame(daftar.joinToString("\n"))
        }
    }

    private fun aturTeksGame(teks: String) {
        if (tvGames.text.toString() != teks) {
            tvGames.text = teks
        }
    }

    private fun segarkan() {
        tvStatus.text = when {
            ServerState.starting -> "Menyiapkan…"
            ServerState.running -> "Berjalan"
            else -> "Berhenti"
        }
        tvAlamat.text = if (ServerState.running) {
            "http://${ServerState.ip ?: "?"}:${ServerState.port}/"
        } else {
            "—"
        }
        tvPenemuan.text = "Penemuan otomatis: " + when {
            !ServerState.running -> "—"
            ServerState.registered -> "aktif (PPSSPP harusnya menemukan sendiri)"
            else -> "belum terdaftar (pakai cara manual bila perlu)"
        }
        val galat = ServerState.error
        if (galat != null && !ServerState.running) {
            tvGalat.text = galat
            tvGalat.visibility = View.VISIBLE
        } else {
            tvGalat.visibility = View.GONE
        }
        if (ServerState.running) {
            tampilkanGame(ServerState.games)
        }
    }

    companion object {
        private const val RC_FOLDER = 1001
        private const val RC_NOTIF = 1002
    }
}
