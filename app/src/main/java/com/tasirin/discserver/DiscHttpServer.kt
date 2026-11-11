package com.tasirin.discserver

import android.content.Context
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Server HTTP yang meniru protokol Remote Disc Streaming PPSSPP,
// mengacu implementasi Core/WebServer.cpp di PPSSPP (GPL, hanya perilakunya yang ditiru):
// - GET / atau /<folder>/  -> daftar isi teks polos: baris pertama "/", lalu nama per baris
//   (nama di-encode persen; folder diberi akhiran "/").
// - HEAD /<berkas>         -> 200 + Content-Length + "Accept-Ranges: bytes".
// - GET /<berkas>          -> wajib header Range "bytes=A-B" -> 206 + Content-Range;
//   tanpa Range -> 418; format Range rusak -> 400; di luar berkas -> 416.
class DiscHttpServer(
    context: Context,
    private val library: GameLibrary,
    private val startPort: Int
) {
    private val appContext = context.applicationContext
    private val aktif = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null

    @Volatile
    var port: Int = -1
        private set

    fun start(): Boolean {
        if (aktif.get()) return true
        var ss: ServerSocket? = null
        var terpilih = -1
        // Sama seperti PPSSPP: port utama penuh -> coba port berikutnya.
        for (calon in startPort until startPort + 20) {
            try {
                ss = ServerSocket(calon, 50, InetAddress.getByName("0.0.0.0"))
                terpilih = calon
                break
            } catch (e: Exception) {
                ss = null
            }
        }
        if (ss == null) return false
        serverSocket = ss
        port = terpilih
        pool = Executors.newCachedThreadPool()
        aktif.set(true)
        Thread { terimaLoop(ss) }.start()
        return true
    }

    fun stop() {
        aktif.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Abaikan.
        }
        serverSocket = null
        pool?.shutdownNow()
        pool = null
        port = -1
    }

    private fun terimaLoop(ss: ServerSocket) {
        while (aktif.get()) {
            val s = try {
                ss.accept()
            } catch (e: Exception) {
                break
            }
            try {
                pool?.execute { tangani(s) }
            } catch (e: Exception) {
                try {
                    s.close()
                } catch (e2: Exception) {
                    // Abaikan.
                }
            }
        }
    }

    private fun tangani(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 30000
                val masukan = BufferedReader(
                    InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1)
                )
                val barisPermintaan = masukan.readLine() ?: return
                val bagian = barisPermintaan.split(' ')
                if (bagian.size < 2) {
                    tulisTeks(s, 400, "Bad request.")
                    return
                }
                val metode = bagian[0].uppercase()
                var target = bagian[1]
                val tanya = target.indexOf('?')
                if (tanya >= 0) target = target.substring(0, tanya)

                val header = HashMap<String, String>()
                while (true) {
                    val baris = masukan.readLine() ?: break
                    if (baris.isEmpty()) break
                    val titikDua = baris.indexOf(':')
                    if (titikDua > 0) {
                        header[baris.substring(0, titikDua).trim().lowercase()] =
                            baris.substring(titikDua + 1).trim()
                    }
                }

                when {
                    target == "/" || target.endsWith("/") -> layaniDaftar(s, target)
                    metode == "HEAD" -> layaniKepala(s, target)
                    metode == "GET" -> layaniBerkas(s, target, header["range"])
                    else -> tulisTeks(s, 400, "Bad request.")
                }
            } catch (e: Exception) {
                // Klien pergi / waktu habis: diam saja.
            }
        }
    }

    // --- Daftar isi ---

    private fun layaniDaftar(s: Socket, target: String) {
        val node = library.resolve(segmenDari(target))
        if (node == null || !node.isDir) {
            tulisTeks(s, 404, "File not found.")
            return
        }
        val sb = StringBuilder()
        sb.append("/\n")
        for (nama in node.children.keys.sorted()) {
            val anak = node.children[nama]!!
            sb.append(encodeUri(nama))
            if (anak.isDir) sb.append('/')
            sb.append('\n')
        }
        val badan = sb.toString().toByteArray(StandardCharsets.UTF_8)
        val out = s.getOutputStream()
        tulisKepala(out, 200, "OK", badan.size.toLong(), "text/plain")
        out.write(badan)
        out.flush()
    }

    // --- Kepala berkas (HEAD) ---

    private fun layaniKepala(s: Socket, target: String) {
        val node = library.resolve(segmenDari(target))
        val berkas = node?.file
        if (node == null || node.isDir || berkas == null) {
            tulisTeks(s, 404, "File not found.")
            return
        }
        val ukuran = berkas.length()
        if (ukuran <= 0) {
            tulisTeks(s, 404, "File not found.")
            return
        }
        tulisKepala(
            s.getOutputStream(), 200, "OK", ukuran,
            "application/octet-stream", "Accept-Ranges: bytes\r\n"
        )
    }

    // --- Isi berkas (GET dengan Range) ---

    private fun layaniBerkas(s: Socket, target: String, range: String?) {
        val node = library.resolve(segmenDari(target))
        val berkas = node?.file
        if (node == null || node.isDir || berkas == null) {
            tulisTeks(s, 404, "File not found.")
            return
        }
        val ukuran = berkas.length()
        if (ukuran <= 0) {
            tulisTeks(s, 404, "File not found.")
            return
        }
        if (range == null) {
            tulisTeks(s, 418, "This server only supports range requests.")
            return
        }
        val cocok = Regex("^bytes=(\\d+)-(\\d+)").find(range.trim())
        if (cocok == null) {
            tulisTeks(s, 400, "Could not understand range request.")
            return
        }
        val awal = cocok.groupValues[1].toLongOrNull()
        val akhir = cocok.groupValues[2].toLongOrNull()
        if (awal == null || akhir == null || awal < 0 || awal > akhir || akhir >= ukuran) {
            tulisTeks(s, 416, "Range goes outside of file.")
            return
        }
        val pfd = try {
            appContext.contentResolver.openFileDescriptor(berkas.uri, "r")
        } catch (e: Exception) {
            null
        }
        if (pfd == null) {
            tulisTeks(s, 500, "File access failed.")
            return
        }
        pfd.use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                try {
                    fis.channel.position(awal)
                } catch (e: Exception) {
                    tulisTeks(s, 500, "File access failed.")
                    return
                }
                val out = s.getOutputStream()
                val panjang = akhir - awal + 1
                tulisKepala(
                    out, 206, "Partial Content", panjang, "application/octet-stream",
                    "Content-Range: bytes $awal-$akhir/$ukuran\r\n"
                )
                val buf = ByteArray(16 * 1024)
                var sisa = panjang
                while (sisa > 0) {
                    val baca = fis.read(buf, 0, minOf(buf.size.toLong(), sisa).toInt())
                    if (baca < 0) break
                    out.write(buf, 0, baca)
                    sisa -= baca
                }
                out.flush()
            }
        }
    }

    // --- Pembantu ---

    private fun tulisTeks(s: Socket, kode: Int, isi: String) {
        val badan = isi.toByteArray(StandardCharsets.UTF_8)
        val pesan = when (kode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            416 -> "Range Not Satisfiable"
            418 -> "I'm a teapot"
            500 -> "Internal Server Error"
            else -> "Error"
        }
        val out = s.getOutputStream()
        tulisKepala(out, kode, pesan, badan.size.toLong(), "text/plain")
        out.write(badan)
        out.flush()
    }

    private fun tulisKepala(
        out: OutputStream,
        kode: Int,
        pesan: String,
        panjang: Long,
        tipe: String,
        tambahan: String? = null
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.0 ").append(kode).append(' ').append(pesan).append("\r\n")
        if (panjang >= 0) sb.append("Content-Length: ").append(panjang).append("\r\n")
        sb.append("Content-Type: ").append(tipe).append("\r\n")
        if (tambahan != null) sb.append(tambahan)
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.ISO_8859_1))
        out.flush()
    }

    private fun segmenDari(target: String): List<String> {
        return target.split('/').filter { it.isNotEmpty() }.map { decodeUri(it) }
    }

    private fun decodeUri(s: String): String {
        return try {
            // URLDecoder mengubah '+' menjadi spasi; lindungi '+' asli dulu.
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            s
        }
    }

    private fun encodeUri(nama: String): String {
        val aman = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val sb = StringBuilder()
        for (b in nama.toByteArray(StandardCharsets.UTF_8)) {
            val v = b.toInt() and 0xFF
            val c = v.toChar()
            if (aman.indexOf(c) >= 0) {
                sb.append(c)
            } else {
                sb.append('%').append(String.format("%02X", v))
            }
        }
        return sb.toString()
    }
}
