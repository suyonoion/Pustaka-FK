package com.fk.arsip

// Objek Singleton: Memori statis dengan data yang tervalidasi 100% dari Kargo_Pecahan_1 & 2
object CetakBiruKategori {

    val MATRIKS_UTAMA = listOf(
        "Ilmu Ladunni" to listOf(
        "Ilmu Ladunni" to listOf("ladunni","laduni")
        ),
        "Ijazah Khusus Murid" to listOf(
            "Dzikir Jahar" to listOf("dj", "dzikir jahar", "jaharan","dj jamaah"),
            "Dzikir Sirri" to listOf("dzikir sirri","dzikir sirr"),
            "Olah Roso" to listOf("olah roso")
        ),
        "Ijazah Umum" to listOf(
            "Adab Minta Izin Ijazah" to listOf("minta izin", "dimintakan izin", "adab ngelmu"),
            "Pagar Gaib Fatwa Kehidupan (PGFK)" to listOf("pagar gaib", "pg", "pgfk"),
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
            "Zuhri Formalism" to listOf("zuhri formalism", "bit-massa", "bit-mass", "fraktal", "master protocol", "formal constraints","frekuensi","ZF","fisika","kuantum","Gemini","fisikawan","air jabung"),
            "Gamma Locking" to listOf("gamma locking", "kunci gamma", "locking")
        ),
        "Rule of Third" to listOf(
        "Rule of Third" to listOf("rule of third","intuisi")
        ),
        "Rahasia Waktu" to listOf(
        "Rahasia Waktu" to listOf("rahasia waktu")
        ),
        "Semboyan" to listOf(
        "Iqro' Kitabaka" to listOf("kitabaka"),
        "Ora Ono Opo Opo" to listOf("ora ono opo opo"),
        "Kebodohan" to listOf("belajar bodoh"),
        "Barisan Ningrat(Ning Akherat)" to listOf("ningrat")
        ),
        
        "Tingkat" to listOf(
          "Langit" to listOf("langit ke"),
          "Tingkatan" to listOf("tingkatan"),
          "Lapisan Cahaya" to listOf("lapisan cahaya")
        ),
       
        "GENUS (Jin)" to listOf(
        "GENUS (Jin)" to listOf("GENUS")
        ),
        
        "Tentang Sholat" to listOf(
           "Sholat" to listOf("TENTANG SHOLAT"),
           "Sholat Tasbih" to listOf("sholat tasbih")
        ),
        "Takdir/Rejeki/Maut" to listOf(
          "Takdir" to listOf("takdir"),
          "Rejeki" to listOf("rejeki","rizki"),
          "Maut" to listOf("maut","kematian","wafat","meninggal","ajal","alam kubur","alam kematian","barzah")
        ),
    "Tasawuf dan Laku Batin" to listOf(
        "Kholwat" to listOf("kholwat"),
        "Lelaku" to listOf("lelaku"),
        "Salik" to listOf("salik"),
        "Suluk" to listOf("suluk"),
        "Tirakat" to listOf("tirakat2","tirakat"),
        "Kultivasi" to listOf("kultivasi"),
        "Meditasi" to listOf("meditasi"),
        "Wening" to listOf("wening"),
        "Semedi" to listOf("semedi")
     ),
        "Program Social" to listOf(
            "Program Sosial" to listOf("program social", "baksos", "sosial"),
            "Baksos Palestina" to listOf("baksos fk gaza"),
            "Santunan" to listOf("santunan","yayasan","anak yatim")
        ),
        "Acara Kopdar" to listOf(
            "Acara Kopdar" to listOf("kopdar","kopdar akbar")
        ),
        "Produk FK" to listOf(
            "Produk FK" to listOf("produk fk", "madu fk", "kopi fk","minyak fk","oli fk","teh celup herbal fk","biofk","pupuk fk")
        ),
        "Ekonomi & Trading" to listOf(
            "Ekonomi Makro" to listOf("uang", "ekonomi", "bisnis", "rupiah","jual beli", "fiskal", "pajak"),
            "Trading" to listOf("trading", "forex", "market", "candlestick", "chart", "buy", "sell", "liquidity", "profit","bot","termux","script")
        ),
        "Politik & Negara" to listOf(
            "Politik & Pemerintahan" to listOf("politik", "pemerintah", "negara", "pejabat","dpr","mbg","rakyat"),
            "Hukum & Institusi" to listOf("korupsi", "jampidsus", "kasus bgn", "penjara","hukum negara","pangkat", "jabatan", "kolonel", "polisi", "kejaksaan")
        ),
        "Pertanian" to listOf(
            "Teknis & Irigasi" to listOf("tani", "sawah", "irigasi", "springkler", "pipa", "galvanis", "nepel", "tekanan tinggi", "karet ban"),
            "Produksi & Tanaman" to listOf("pupuk", "usir tikus", "alkemi", "padi", "jagung", "bajak", "tanam buah", "olah tanah", "kebun", "ladang", "daun", "batang", "drip", "tanam", "tanaman", "emisi karbon")
        ),
        "Belum di Kategorikan" to listOf(
            "Belum di Kategorikan" to listOf("umum_fallback_signal")
        )
    )
}
