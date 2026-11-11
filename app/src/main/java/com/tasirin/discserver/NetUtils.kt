package com.tasirin.discserver

import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtils {
    // Alamat IPv4 LAN perangkat ini; alamat privat (situs-lokal) didahulukan.
    fun localIpv4(): String? {
        var cadangan: String? = null
        try {
            val semua = NetworkInterface.getNetworkInterfaces()
            while (semua.hasMoreElements()) {
                val ni = semua.nextElement()
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                val alamat = ni.inetAddresses
                while (alamat.hasMoreElements()) {
                    val a = alamat.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        if (a.isSiteLocalAddress) return a.hostAddress
                        if (cadangan == null) cadangan = a.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // Abaikan: pemanggil menampilkan penanda "tidak ada jaringan".
        }
        return cadangan
    }
}
