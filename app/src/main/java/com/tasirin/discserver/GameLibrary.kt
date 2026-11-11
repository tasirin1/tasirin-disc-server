package com.tasirin.discserver

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

// Peta folder game yang dipilih pengguna (SAF), siap disajikan server HTTP.
// Modelnya pohon simpul: tiap simpul adalah folder atau berkas game.
class GameLibrary private constructor(
    val rootName: String,
    private val root: Node
) {
    class Node(
        val name: String,
        val file: DocumentFile?,
        val isDir: Boolean,
        val children: MutableMap<String, Node> = mutableMapOf()
    )

    // Semua jalur game (relatif terhadap folder akar), terurut — untuk tampilan UI.
    fun gameNames(): List<String> {
        val hasil = ArrayList<String>()
        kumpulkan(root, "", hasil)
        return hasil.sorted()
    }

    private fun kumpulkan(node: Node, awalan: String, hasil: MutableList<String>) {
        for (anak in node.children.values) {
            val jalur = if (awalan.isEmpty()) anak.name else "$awalan/${anak.name}"
            if (anak.isDir) kumpulkan(anak, jalur, hasil) else hasil.add(jalur)
        }
    }

    // Simpul pada jalur relatif (segmen sudah di-decode). null = tidak ada.
    fun resolve(segments: List<String>): Node? {
        var kini = root
        for (seg in segments) {
            if (seg.isEmpty() || seg == ".") continue
            kini = kini.children[seg] ?: return null
        }
        return kini
    }

    companion object {
        // Sama seperti RemoteISOFileSupported di PPSSPP.
        private val EKSTENSI = setOf("iso", "cso", "chd", "pbp", "ppdmp")
        private const val KEDALAMAN_MAKS = 4

        fun didukung(nama: String): Boolean {
            val titik = nama.lastIndexOf('.')
            if (titik < 0 || titik == nama.length - 1) return false
            return nama.substring(titik + 1).lowercase() in EKSTENSI
        }

        // Pindai pohon SAF. null bila folder tidak terbaca.
        fun scan(context: Context, treeUri: Uri): GameLibrary? {
            val akar = try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (e: Exception) {
                null
            } ?: return null
            if (!akar.isDirectory) return null
            val root = Node(akar.name ?: "game", null, true)
            isi(root, akar, 0)
            return GameLibrary(akar.name ?: "Folder game", root)
        }

        private fun isi(node: Node, dir: DocumentFile, kedalaman: Int) {
            if (kedalaman > KEDALAMAN_MAKS) return
            val anak = try {
                dir.listFiles()
            } catch (e: Exception) {
                return
            }
            for (f in anak) {
                val nama = f.name ?: continue
                if (f.isDirectory) {
                    val sub = Node(nama, null, true)
                    node.children[nama] = sub
                    isi(sub, f, kedalaman + 1)
                } else if (f.isFile && didukung(nama)) {
                    node.children[nama] = Node(nama, f, false)
                }
            }
        }
    }
}
