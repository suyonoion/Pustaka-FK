package com.fk.arsip

// Objek Singleton: Memori statis dengan data yang tervalidasi 100% dari Kargo_Pecahan_1 & 2
object CetakBiruKategori {

    val MATRIKS_UTAMA = listOf(
        "Ijazah Khusus Murid" to listOf(
            "Dzikir Jahar" to listOf("dj", "dzikir jahar", "jaharan","dj jamaah"),
            "Dzikir Sirri" to listOf("dzikir sirri","dzikir sirr"),
            "Olah Roso" to listOf("olah roso")
        ),
        "Ijazah Umum" to listOf(
            "Adab Minta Izin Ijazah" to listOf("minta izin", "dimintakan izin", "adab ngelmu","ijazah"),
            "Pagar Gaib Fatwa Kehidupan (PGFK)" to listOf("pagar gaib", "pg", "pgfk","pagar"),
            "Wasilah (Kirim Al-Fatehah)" to listOf("wasilah", "al-fatehah", "alfatehah","fatehah","ijazah wasilah"),
            "Mengusir Jin" to listOf("yaa hayyu yaa matin","ya hayyu ya Matin"),
            "Sembelih Hewan" to listOf("bawang putih", "sembelih"),
            "Ain/Sawan" to listOf("penyakit ain","sawanen","sawan"),
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
        ),
        "Zuhri Formalism & Tasawuf" to listOf(
            "Zuhri Formalism" to listOf("zuhri formalism", "bit-massa", "bit-mass", "fraktal", "dimensi", "master protocol", "formal constraints"),
            "Gamma Locking" to listOf("gamma locking", "kunci gamma", "locking"),
            "Terminologi Tasawuf" to listOf("tasawuf", "makrifat", "tariqah", "syeikh", "adab", "madad", "fatehah", "dzikir", "olah roso")
        ),
        "Program Social" to listOf(
            "Program Sosial" to listOf("program social", "baksos", "sosial")
        ),
        "Acara Kopdar" to listOf(
            "Acara Kopdar" to listOf("kopdar", "kumpul","jamaah","kopdar akbar")
        ),
        "Produk FK" to listOf(
            "Produk FK" to listOf("produk fk", "madu fk", "kopi fk","minyak fk","oli fk","teh celup herbal fk","biofk","pupuk fk")
        ),
        "Ekonomi & Trading" to listOf(
            "Ekonomi Makro" to listOf("uang", "ekonomi", "bisnis", "rupiah","jual beli", "fiskal", "pajak"),
            "Trading" to listOf("trading", "forex", "market", "candlestick", "chart", "buy", "sell", "liquidity", "profit","bot","termux")
        ),
        "Politik & Negara" to listOf(
            "Politik & Pemerintahan" to listOf("politik", "pemerintah", "negara", "pejabat","dpr","mbg","rakyat"),
            "Hukum & Institusi" to listOf("korupsi", "jampidsus", "kasus bgn", "hukuman", "pangkat", "jabatan", "kolonel", "bintang", "polisi", "kejaksaan")
        ),
        "Pertanian" to listOf(
            "Teknis & Irigasi" to listOf("tani", "sawah", "irigasi", "springkler", "pipa", "galvanis", "nepel", "tekanan tinggi", "karet ban"),
            "Produksi & Tanaman" to listOf("pupuk", "tikus", "alkemi", "padi", "jagung", "bajak", "buah", "tanah", "kebun", "ladang", "daun", "batang", "drip", "tanam", "tanaman", "emisi karbon")
        ),
        "Media Sosial & Interaksi" to listOf(
            "Telemetri Publik" to listOf("likes", "komentar", "reaksi massa", "engagement", "metrik", "volume"),
            "Loyalitas Komunitas" to listOf("abah guru", "abah cinta", "syeikh", "ndherek nyimak", "salam ta'dim", "madad", "fk", "fatwa kehidupan")
        ),
        "Belum di Kategorikan" to listOf(
            "Residu" to listOf("umum_fallback_signal")
        )
    )
}
