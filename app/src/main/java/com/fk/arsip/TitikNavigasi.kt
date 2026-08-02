package com.fk.arsip

data class TitikNavigasi(
    val tipe: Int, 
    val teks: String,
    val indeksTujuan: Int = -1,
    val warnaGenap: Boolean = false // Setel nilai default di sini
)


