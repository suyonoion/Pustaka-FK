package com.fk.arsip

object DataGaleri {

    // 1. SUB: GURU (36 FOTO - LOOPING)
    private val listFotoGuru: List<GaleriFoto> = (1..36).map { i ->
        val nomorPadded = String.format("%02d", i)
        val nomorIndex = String.format("%03d", i)
        GaleriFoto(
            nomorFoto = "#$nomorIndex",
            subKategori = "Guru",
            fotoPath = "galeri/pustakafk_foto_abah_$nomorPadded.jpg",
            deskripsi = "Foto Abah Syeikh Muhammad Zuhri."
        )
    }

    // 2. SUB: UMUM (DESKRIPSI DINAMIS)
    private val listFotoUmum: List<GaleriFoto> = listOf(
        GaleriFoto("#037", "Umum", "galeri/pustakafk_foto_umum_01.jpg", "Suasana SanFK Kendal Merayakan Ultah Guru."),
        GaleriFoto("#038", "Umum", "galeri/pustakafk_foto_umum_02.jpg", "Kopdar SEKAMPUNG. Lokasi Semarang, Jawa Tengah."),
        GaleriFoto("#039", "Umum", "galeri/pustakafk_foto_umum_03.jpg", "Dzikir Jama'ah Rutinan Submawil Kendal di Saung JAWA."),
        GaleriFoto("#040", "Umum", "galeri/pustakafk_foto_umum_04.jpg", "Foto Bersama Kopdar Akbar ke-9."),
        GaleriFoto("#041", "Umum", "galeri/pustakafk_foto_umum_05.jpg", "Acara Kopdar SEKAMPUNG. Lokasi Kendal, Jawa Tengah.")
    )

    val listFotoAbah: List<GaleriFoto> = listFotoGuru + listFotoUmum
}
