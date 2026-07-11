package com.fk.arsip.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArsipDao {
    // Katup Injeksi Massal: Memompa ratusan blok data sekaligus dalam satu putaran mesin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun injeksiMassal(arsip: List<ArsipEntity>)

    // Tuas Penyedot Total: Menarik data secara berurutan dari yang paling baru
    @Query("SELECT * FROM tabel_arsip ORDER BY waktuRilis DESC")
    fun tarikSemuaArsip(): List<ArsipEntity>

    // Katup Saringan Resolusi Tinggi (Search)
    @Query("SELECT * FROM tabel_arsip WHERE kontenPenuh LIKE '%' || :kataKunci || '%' ORDER BY waktuRilis DESC")
    fun saringArsip(kataKunci: String): List<ArsipEntity>
    
    // Sensor Kapasitas Tangki
    @Query("SELECT COUNT(*) FROM tabel_arsip")
    fun hitungTotalArsip(): Int
}
