package com.fk.arsip

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * PAGING 3 — PagingSource generik pembungkus List<T> yang sudah utuh di memori.
 *
 * Dipakai khusus untuk mode PENCARIAN pada BukuAdapter/ViewPager2: hasil LIKE
 * query dari database masih disaring ulang di sisi client dengan regex batas-kata
 * (lihat eksekusiLogikaPencarian), sehingga urutan & jumlah hasil akhirnya hanya
 * diketahui setelah proses client-side itu selesai — tidak bisa diwakili oleh satu
 * query SQL PagingSource murni. Dengan membungkusnya di sini, BukuAdapter tetap
 * bisa memakai satu mekanisme PagingDataAdapter yang seragam untuk SEMUA mode
 * (browse biasa, kategori via query DB langsung, maupun pencarian via List ini),
 * lengkap dengan placeholder + DiffUtil, tanpa perlu subclass adapter terpisah.
 *
 * Untuk mode browse biasa & kategori (berpotensi puluhan ribu baris), gunakan
 * PagingSource asli dari ArsipDao (tarikSemuaArsipPaged, saringKategoriPaged, dst)
 * agar data benar-benar dimuat bertahap dari SQLite, bukan dari List ini.
 */
class ListPagingSource<T : Any>(private val sumberData: List<T>) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        val posisiAncor = state.anchorPosition ?: return null
        val ukuranHalaman = state.config.pageSize
        return maxOf(0, posisiAncor - ukuranHalaman / 2)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val posisiMulai = params.key ?: 0
        val ukuranHalaman = params.loadSize

        if (posisiMulai >= sumberData.size) {
            return LoadResult.Page(
                data = emptyList(),
                prevKey = if (posisiMulai == 0) null else maxOf(0, posisiMulai - ukuranHalaman),
                nextKey = null,
                itemsBefore = sumberData.size,
                itemsAfter = 0
            )
        }

        val posisiAkhir = minOf(sumberData.size, posisiMulai + ukuranHalaman)
        val potongan = sumberData.subList(posisiMulai, posisiAkhir)

        return LoadResult.Page(
            data = potongan,
            prevKey = if (posisiMulai == 0) null else maxOf(0, posisiMulai - ukuranHalaman),
            nextKey = if (posisiAkhir >= sumberData.size) null else posisiAkhir,
            itemsBefore = posisiMulai,
            itemsAfter = sumberData.size - posisiAkhir
        )
    }
}
