package com.tasirin.discserver

import android.content.Context
import android.net.Uri

// Keadaan server yang dibagikan: ditulis layanan, dibaca aktivitas (jajak pendapat 1 detik).
object ServerState {
    @Volatile var running: Boolean = false
    @Volatile var starting: Boolean = false
    @Volatile var ip: String? = null
    @Volatile var port: Int = -1
    @Volatile var registered: Boolean = false
    @Volatile var error: String? = null
    @Volatile var games: List<String> = emptyList()
}

// Pilihan tersimpan: URI folder game (SAF).
object Prefs {
    private const val FILE = "pengaturan"
    private const val KEY_FOLDER = "folder_uri"

    fun folderUri(context: Context): Uri? {
        val s = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER, null)
        return s?.let { Uri.parse(it) }
    }

    fun setFolderUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_FOLDER, uri?.toString())
            .apply()
    }
}
