package com.fk.arsip

data class GaleriFoto(
    val nomorFoto: String,
    val subKategori: String,
    val fotoPath: String,
    val deskripsi: String,
    val kanal: String = "[ SanFK / Saung / ZF ]"
)
