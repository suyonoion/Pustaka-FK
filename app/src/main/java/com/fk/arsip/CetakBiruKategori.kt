package com.fk.arsip
// Objek Singleton: Memori statis yang menyala konstan dan dapat diakses dari seluruh sirkuit aplikasi
object CetakBiruKategori {

    // ========================================================
    // BLOK MEMORI SENTRAL EKSTERNAL
    // Tambahkan atau modifikasi seluruh kategori Laci dan Kata Kunci di sini
    // ========================================================
    val MATRIKS_UTAMA = listOf(
        "Ijazah Khusus Murid" to listOf(
            "Dzikir Jahar" to listOf("DJ")
        ),
        "Ijazah Umum" to listOf(
            "Adab / Minta Izin Ijazah" to listOf("izin ijazah", "qobiltu", "adab"),
            "Pagar Gaib Fatwa Kehidupan (PGFK)" to listOf("pagar gaib", "pg", "pgfk"),
            "Wasilah (Kirim Al-Fatehah)" to listOf("wasilah", "al-fatehah", "alfatehah"),
            "Lempari Jin, yaa hayyu yaa matin" to listOf("yaa hayyu yaa matin", "jin"),
            "Sastra Balik Bala'" to listOf("balik bala", "tolak bala"),
            "Sembelih Hewan" to listOf("bawang putih", "sembelih")
            // (Catatan: Lanjutkan sisa sub-kategori ijazah umum Anda di sini)
        ),
        "Program Social" to listOf(
            "Program Social" to listOf("program social", "baksos", "sosial")
        ),
        "Acara Kopdar" to listOf(
            "Acara Kopdar" to listOf("kopdar", "kopi darat")
        ),
        "Produk FK" to listOf(
            "Produk FK" to listOf("produk fk", "madu", "kopi fk")
        ),
        "Ekonomi" to listOf(
            "Ekonomi" to listOf("uang", "ekonomi", "bisnis", "rupiah")
        ),
        "Politik & Negara" to listOf(
            "Politik & Negara" to listOf("politik", "pemerintah", "negara", "pejabat")
        ),
        "Agama" to listOf(
            "Agama" to listOf("agama", "syariat", "fiqih", "hadist")
        ),
        "Pertanian" to listOf(
            "Pertanian" to listOf("tani", "sawah", "pupuk", "kopi")
        ),
        "Umum" to listOf(
            "Umum" to listOf("umum_fallback_signal")
        )

    )
    // ========================================================
}
