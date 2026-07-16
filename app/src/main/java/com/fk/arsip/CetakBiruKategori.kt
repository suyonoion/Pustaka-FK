package com.fk.arsip
// Objek Singleton: Memori statis yang menyala konstan dan dapat diakses dari seluruh sirkuit aplikasi
object CetakBiruKategori {

    // ========================================================
    // BLOK MEMORI SENTRAL EKSTERNAL
    // Tambahkan atau modifikasi seluruh kategori Laci dan Kata Kunci di sini
    // ========================================================
    val MATRIKS_UTAMA = listOf(
        "Ijazah Khusus Murid" to listOf(
            "Dzikir Jahar" to listOf("dj", "dzikir jahar", "jaharan","dj jamaah"),
            "Dzikir Sirri" to listOf("dzikir sirri","dzikir sirr"),
            "Olah Roso" to listOf("olah roso")
        ),
        "Ijazah Umum" to listOf(
            "Adab Minta Izin Ijazah" to listOf("minta izin", "dimintakan izin", "adab ngelmu","ijazah",),
            "Pagar Gaib Fatwa Kehidupan (PGFK)" to listOf("pagar gaib", "pg", "pgfk","pagar","benteng"),
            "Wasilah (Kirim Al-Fatehah)" to listOf("wasilah", "al-fatehah", "alfatehah","fatehah","ijazah wasilah"),
            "Mengusir Jin" to listOf("yaa hayyu yaa matin","ya hayyu ya Matin"),
            "Sembelih Hewan" to listOf("bawang putih", "sembelih"),
            "Ain/Sawan" to listOf("penyakit ain","sawanen"),
            "Segala Hajat" to listOf("hajat apa saja"),
            "Gangguan Melihat Makhluk Halus" to listOf("mahluk halus"),
            "Melepas Energi Negatif" to listOf("gedruk bumi"),
            "Angin/Hujan Kencang" to listOf("jopo montro","angin ribut","angin lebat","angin kencang"),
            "Caroko Walik" to listOf("caroko walik","honocoroko","carokowalik","aksara jawa"),
            "Puter Giling Orang Hilang" to listOf("puter giling"),
            "Usir Tikus" to listOf("alkemi mistik","alkemi","hama tikus","dancok","rajah tikus"),
            "Sholawat Nariyah" to listOf("sholawat nariyah","ba'da subuh 12x","shalawat nariyah","nariyah"),
            "Dzikir Fida'" to listOf("dzikir fida'","7000x","70.000x","fida'","fida","fidda"),
            "Sakaratul Maut/Peluruh Ilmu" to listOf("sakaratul maut","peluruh ilmu","laqod","sekaratul")
            // (Catatan: Lanjutkan sisa sub-kategori ijazah umum Anda di sini)
        ),
        "Program Social" to listOf(
            "Program Sosial" to listOf("program social", "baksos", "sosial")
        ),
        "Acara Kopdar" to listOf(
            "Acara Kopdar" to listOf("kopdar", "kumpul","jamaah")
        ),
        "Produk FK" to listOf(
            "Produk FK" to listOf("produk fk", "madu fk", "kopi fk","minyak fk","oli fk","teh celup herbal fk","biofk","pupuk fk")
        ),
        "Ekonomi" to listOf(
            "Ekonomi" to listOf("uang", "ekonomi", "bisnis", "rupiah","jual beli")
        ),
        "Politik & Negara" to listOf(
            "Politik & Negara" to listOf("politik", "pemerintah", "negara", "pejabat","dpr","mbg","rakyat")
        ),
        "Pertanian" to listOf(
            "Pertanian" to listOf("tani", "sawah", "pupuk", "tikus","alkemi","padi","jagung","bajak","pengairan","buah","tanah","kebun","ladang","daun","batang","drip","tanam","tanaman")
        ),
        "Belum di Kategorikan" to listOf(
            "Belum di Kategorikan" to listOf("umum_fallback_signal")
        )

    )
    // ========================================================
}
