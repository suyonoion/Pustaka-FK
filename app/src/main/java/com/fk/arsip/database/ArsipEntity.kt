package com.fk.arsip.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabel_arsip")
data class ArsipEntity(
    // Menggunakan ID mutlak dari Facebook untuk mencegah duplikasi data
    @PrimaryKey val idPosting: String, 
    val namaPenulis: String,
    val urlProfilPic: String,
    val waktuRilis: Long,
    val tanggalBaca: String,
    val kontenPenuh: String,
    val tautanAsli: String,
    val daftarFoto: String,
    
    // KOMPARTEMEN BARU
    val kategori: String 
)
