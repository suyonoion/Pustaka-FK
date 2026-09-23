package com.fk.arsip.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArsipDao {
    // Katup Injeksi Massal: Memompa ratusan blok data sekaligus dalam satu putaran mesin
    // PERBAIKAN: dijadikan `suspend fun` -- versi non-suspend sebelumnya BUKAN
    // titik cancel yang sah untuk coroutine Worker, sehingga saat
    // ExistingWorkPolicy.REPLACE membatalkan worker lama, insert yang sedang
    // berjalan (hingga 500 baris) tetap tuntas dulu sebelum pembatalan
    // benar-benar tereksekusi -- salah satu penyebab "2x injeksi" di Fase 5.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun injeksiMassal(arsip: List<ArsipEntity>)

    // Tuas Penyedot Total: Menarik data secara berurutan dari yang paling baru
    @Query("SELECT * FROM tabel_arsip ORDER BY waktuRilis DESC")
    fun tarikSemuaArsip(): List<ArsipEntity>

    // Katup Saringan Resolusi Tinggi (Search)
    @Query("SELECT * FROM tabel_arsip WHERE kontenPenuh LIKE '%' || :kataKunci || '%' ORDER BY waktuRilis DESC")
    fun saringArsip(kataKunci: String): List<ArsipEntity>
    
    // Sensor Kapasitas Tangki
    @Query("SELECT COUNT(*) FROM tabel_arsip")
    fun hitungTotalArsip(): Int
    
    // ========================================================
    // MODIFIKASI: Pipa Penyedot Stempel Jamak (Multi-Kategori)
    // Sensor LIKE '%' memungkinkan mesin mengekstrak status 
    // meskipun stempel target berada di antara stempel lainnya
    // ========================================================
    @Query("SELECT * FROM tabel_arsip WHERE kategori LIKE '%' || :namaKategori || '%' ORDER BY waktuRilis DESC")
    fun saringBerdasarkanKolomKategori(namaKategori: String): List<ArsipEntity>
    
    @Query("DELETE FROM tabel_arsip")
    suspend fun kurasTangkiKotor()
    
      @Query("SELECT * FROM tabel_arsip ORDER BY waktuRilis ASC")
    fun tarikSemuaArsipTerlama(): List<ArsipEntity>

    @Query("SELECT * FROM tabel_arsip WHERE kategori LIKE '%' || :parameterKategori || '%' ORDER BY waktuRilis ASC")
    fun saringBerdasarkanKategoriTerlama(parameterKategori: String): List<ArsipEntity>

    // ========================================================
    // PAGING 3: versi PagingSource dari query di atas, khusus untuk
    // mode buku (ViewPager2). Room otomatis memuat data per-halaman
    // langsung dari SQLite (bukan menarik semua baris ke memori) dan
    // otomatis invalidate/refresh saat tabel berubah (mis. setelah
    // injeksiMassal selesai).
    // ========================================================
    @Query("SELECT * FROM tabel_arsip ORDER BY waktuRilis DESC")
    fun tarikSemuaArsipPaged(): PagingSource<Int, ArsipEntity>

    @Query("SELECT * FROM tabel_arsip ORDER BY waktuRilis ASC")
    fun tarikSemuaArsipTerlamaPaged(): PagingSource<Int, ArsipEntity>

    @Query("SELECT * FROM tabel_arsip WHERE kategori LIKE '%' || :namaKategori || '%' ORDER BY waktuRilis DESC")
    fun saringKategoriPaged(namaKategori: String): PagingSource<Int, ArsipEntity>

    @Query("SELECT * FROM tabel_arsip WHERE kategori LIKE '%' || :namaKategori || '%' ORDER BY waktuRilis ASC")
    fun saringKategoriTerlamaPaged(namaKategori: String): PagingSource<Int, ArsipEntity>

    @Query("SELECT * FROM tabel_arsip WHERE kontenPenuh LIKE '%' || :kataKunci || '%' ORDER BY waktuRilis DESC")
    fun saringArsipPaged(kataKunci: String): PagingSource<Int, ArsipEntity>

    // ========================================================
    // FILTER SUMBER (FK/YW): query kombinasi kategori + sumber, dipakai
    // HANYA kalau sumber yang dipilih BUKAN "Semua Sumber" -- kalau kategori
    // juga "Semua Kategori", panggil dgn namaKategori="" (LIKE '%%' cocok
    // ke semua baris). Sengaja dipisah dari query lama di atas (bukan
    // menambah parameter opsional ke query yg sudah ada) supaya jalur lama
    // yang sudah stabil (Semua Sumber) tidak tersentuh sama sekali.
    // ========================================================
    @Query("SELECT * FROM tabel_arsip WHERE kategori LIKE '%' || :namaKategori || '%' AND sumberArsip = :sumber ORDER BY waktuRilis DESC")
    fun saringKombinasiSumber(namaKategori: String, sumber: String): List<ArsipEntity>

    @Query("SELECT * FROM tabel_arsip WHERE kategori LIKE '%' || :namaKategori || '%' AND sumberArsip = :sumber ORDER BY waktuRilis ASC")
    fun saringKombinasiSumberTerlama(namaKategori: String, sumber: String): List<ArsipEntity>

    // ========================================================
    // PENCARIAN + SUMBER: sama seperti saringKombinasiSumber() tapi untuk
    // kotak pencarian (kontenPenuh LIKE), bukan kategori -- dipakai supaya
    // hasil pencarian ikut mengikuti Tab FK/YW yang sedang aktif, persis
    // seperti filter kategori dari drawer.
    // ========================================================
    @Query("SELECT * FROM tabel_arsip WHERE kontenPenuh LIKE '%' || :kataKunci || '%' AND sumberArsip = :sumber ORDER BY waktuRilis DESC")
    fun saringArsipSumber(kataKunci: String, sumber: String): List<ArsipEntity>
}
