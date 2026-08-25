package com.fk.arsip.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// PERBAIKAN EFISIENSI: tambah indeks pada kolom yang sering dipakai di
// WHERE/ORDER BY (waktuRilis, kategori) agar query sort & filter tidak
// full table scan setiap kali dipanggil.
@Entity(
    tableName = "tabel_arsip",
    indices = [Index(value = ["waktuRilis"]), Index(value = ["kategori"])]
)
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
