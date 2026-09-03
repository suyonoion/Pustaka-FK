package com.fk.arsip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.bumptech.glide.Glide
import com.fk.arsip.curl.CurlPage
import com.fk.arsip.curl.BudayakanBaca
import com.fk.arsip.database.ArsipEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

/**
 * Merender tiap "halaman" menjadi Bitmap tekstur untuk BudayakanBaca (CurlView
 * OpenGL). Sejak update ini, 1 ARSIP BISA MENEMPATI LEBIH DARI 1 HALAMAN kalau
 * kontennya panjang -- lihat bagian "PAGINASI" di bawah untuk kenapa & caranya.
 *
 * ------------------------------------------------------------------
 * THREADING (tidak berubah dari perbaikan sebelumnya):
 * updatePage() dipanggil dari GL THREAD dan harus SELALU langsung return.
 * Kalau bitmap final belum ada di cache, pasang placeholder instan, lalu
 * kerjakan render sesungguhnya (fetch Glide + inflate/draw View) di thread
 * background terpisah. Begitu selesai, panggil refreshHalaman(index) yang
 * diteruskan ke BudayakanBaca.refreshPageTexture() supaya tekstur halaman
 * yang sedang tampil diperbarui tanpa mengganggu animasi curl.
 *
 * Bitmap dari cache SELALU disalin (bukan diberikan objek aslinya) sebelum
 * diserahkan ke CurlPage, karena CurlPage.setTexture()/reset() me-recycle()
 * bitmap lama begitu diganti -- kalau cache & CurlPage berbagi objek yang
 * sama, cache ikut rusak (lihat histori perbaikan crash "recycled bitmap").
 *
 * ------------------------------------------------------------------
 * PAGINASI -- kenapa & bagaimana:
 * Sebelumnya 1 arsip = 1 halaman selalu, dan konten ditaruh di dalam
 * ScrollView di dalam item_buku.xml. Itu masalahnya: begitu View itu
 * "difoto" jadi Bitmap statis, ScrollView cuma menggambar apa yang
 * kelihatan di layar -- teks yang ada di bawah area yang kelihatan itu
 * SIMPLY TIDAK IKUT TERGAMBAR (bukan disembunyikan, betul-betul hilang dari
 * bitmap), makin parah di landscape karena tinggi layar lebih pendek.
 *
 * Perbaikannya: teks panjang dipecah jadi beberapa halaman (ukuran font
 * tetap, seperti buku asli), lewat 2 mekanisme terpisah:
 *
 *  1) PERKIRAAN CEPAT (untuk `getPageCount()` & lompat-ke-halaman dari
 *     drawer, lihat MainActivity.indexHalamanUntukArsip): dihitung pakai
 *     rumus kasar (panjang teks / perkiraan lebar-tinggi baris), BUKAN
 *     StaticLayout, supaya tetap instan walau datanya puluhan ribu arsip.
 *     Sengaja dibuat SEDIKIT BERLEBIH (bukan pas-pasan) supaya arahnya
 *     aman -- kalaupun meleset, meleset ke arah "kelebihan slot halaman"
 *     (paling buruk ada halaman nyaris kosong), BUKAN "kekurangan slot"
 *     (yang berarti balik lagi ke bug teks terpotong).
 *
 *  2) PEMOTONGAN PERSIS (untuk render sesungguhnya, per halaman yang
 *     benar-benar dibuka): pakai StaticLayout mengukur baris demi baris
 *     dari TEKS ASLI pada lebar sebenarnya, dipotong per halaman begitu
 *     tingginya akan melebihi area yang tersedia. Ini yang menjamin TIDAK
 *     ADA baris yang terpotong di tengah pada halaman yang benar-benar
 *     dibuka pengguna.
 *
 * KETERBATASAN YANG DISENGAJA (supaya scope tetap terkendali): paginasi
 * hanya berlaku untuk konten Tanya-Jawab biasa (txtKontenUtama). Postingan
 * bertipe "Membagikan Status" (ada blok status yang dibagikan ulang)
 * TETAP 1 halaman seperti sebelumnya -- kasus ini jauh lebih jarang & lebih
 * rumit strukturnya (ada 2 blok teks + kotak bersarang), jadi belum
 * dipaginasi. Kalau ini ternyata sering kepotong juga, kabari saya lagi.
 */
class BookPageProvider(
    private val context: Context,
    private val ambilData: () -> List<ArsipEntity>,
    private val refreshHalaman: (Int) -> Unit
) : BudayakanBaca.PageProvider {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val warnaKertas = 0xFFFFFDF7.toInt()
    private val warnaSampulBack = Color.rgb(160, 155, 140)
    private val densitas get() = context.resources.displayMetrics.density

    private val executor = Executors.newFixedThreadPool(2)
    @Volatile private var shutdown = false

    private val cacheMaks = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cacheBitmap = object : LruCache<String, Bitmap>(cacheMaks) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val sedangDiproses = ConcurrentHashMap.newKeySet<String>()

    // ------------------------------------------------------------------
    // PETA HALAMAN (estimasi cepat, lihat dokumentasi kelas di atas)
    // ------------------------------------------------------------------
    private var wKumulatif = -1
    private var hKumulatif = -1
    private var nKumulatif = -1
    private var kumulatif: IntArray = IntArray(0) // kumulatif[i] = total slot halaman utk arsip[0 until i]

    private fun pastikanKumulatif(w: Int, h: Int) {
        val data = ambilData()
        if (w == wKumulatif && h == hKumulatif && data.size == nKumulatif) return
        val lebarKontenPx = lebarKonten(w)
        val arr = IntArray(data.size + 1)
        for (i in data.indices) {
            val a = data[i]
            val tinggiBadanPx = tinggiBadan(h, adaMedia = a.daftarFoto.isNotBlank())
            arr[i + 1] = arr[i] + perkiraanJumlahHalaman(a.kontenPenuh, lebarKontenPx, tinggiBadanPx)
        }
        kumulatif = arr
        wKumulatif = w; hKumulatif = h; nKumulatif = data.size
    }

    private fun lebarKonten(w: Int) = (w - ((48 + 16) * densitas)).toInt().coerceAtLeast(1)

    private fun tinggiBadan(h: Int, adaMedia: Boolean): Int {
        val cadanganChrome = (190 * densitas).toInt() // header + garis + padding + footer dekoratif
        val cadanganMedia = if (adaMedia) 560 else 0 // ~tinggi blok foto (lihat wadahMultiFoto), sengaja konservatif
        return (h - cadanganChrome - cadanganMedia).coerceAtLeast((80 * densitas).toInt())
    }

    private fun perkiraanJumlahHalaman(teks: String, lebarKontenPx: Int, tinggiBadanPx: Int): Int {
        if (teks.isBlank() || teks.contains("--- Membagikan Status:")) return 1 // lihat batasan di dokumentasi kelas
        val ukuranFontPx = 14f * densitas
        // Perkiraan sengaja agak "sempit" (lebar rata-rata karakter dilebihkan,
        // tinggi baris dilebihkan) supaya jumlah halaman perkiraan cenderung
        // SAMA ATAU LEBIH BANYAK dari kebutuhan asli -- lihat catatan arah
        // yang aman di dokumentasi kelas.
        val karakterPerBaris = max(1f, lebarKontenPx / (ukuranFontPx * 0.55f))
        val tinggiBarisPx = ukuranFontPx * 1.35f
        val barisPerHalaman = max(1f, tinggiBadanPx / tinggiBarisPx)
        val jumlahBaris = ceil(teks.length / karakterPerBaris)
        return ceil(jumlahBaris / barisPerHalaman).toInt().coerceAtLeast(1)
    }

    /** Dipakai MainActivity untuk lompat langsung ke arsip tertentu (mis. dari drawer). */
    fun indexHalamanUntukArsip(posisiArsip: Int): Int {
        pastikanKumulatif(wKumulatif.coerceAtLeast(1), hKumulatif.coerceAtLeast(1))
        val data = ambilData()
        if (posisiArsip !in data.indices) return 0
        return kumulatif[posisiArsip] + 1 // +1 krn index 0 = sampul depan
    }

    /** Index arsip asli (abaikan sub-halaman) untuk index halaman ini, atau null kalau sampul. */
    fun indexArsipDari(indexHalaman: Int): Int? {
        val posisiKonten = indexHalaman - 1
        if (posisiKonten < 0 || kumulatif.isEmpty() || posisiKonten >= kumulatif.last()) return null
        return cariArsipIndex(posisiKonten)
    }

    private fun cariArsipIndex(posisiKonten: Int): Int {
        var lo = 0
        var hi = kumulatif.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (kumulatif[mid] <= posisiKonten) lo = mid else hi = mid - 1
        }
        return lo
    }

    override fun getPageCount(): Int {
        val w = if (wKumulatif > 0) wKumulatif else 1
        val h = if (hKumulatif > 0) hKumulatif else 1
        pastikanKumulatif(w, h)
        return (if (kumulatif.isEmpty()) 0 else kumulatif.last()) + 2 // + sampul depan + belakang
    }

    fun shutdown() {
        shutdown = true
        executor.shutdownNow()
    }

    // ------------------------------------------------------------------
    override fun updatePage(page: CurlPage, width: Int, height: Int, index: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        pastikanKumulatif(w, h)
        val totalHalamanKonten = if (kumulatif.isEmpty()) 0 else kumulatif.last()

        val cacheKey: String
        val tugas: (() -> Bitmap)?
        when {
            index == 0 -> {
                cacheKey = "sampul_depan:${w}x$h"
                tugas = { renderSampul(w, h, judul = "Pustaka FK", subjudul = "Arsip Fatwa & Kehidupan") }
            }
            index == totalHalamanKonten + 1 -> {
                cacheKey = "sampul_belakang:${w}x$h"
                tugas = { renderSampul(w, h, judul = "Tamat", subjudul = "Pustaka FK") }
            }
            else -> {
                val posisiKonten = index - 1
                if (posisiKonten < 0 || posisiKonten >= totalHalamanKonten) {
                    page.setTexture(renderKosong(w, h), CurlPage.SIDE_FRONT)
                    page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
                    return
                }
                val arsipIndex = cariArsipIndex(posisiKonten)
                val arsip = ambilData().getOrNull(arsipIndex)
                if (arsip == null) {
                    page.setTexture(renderKosong(w, h), CurlPage.SIDE_FRONT)
                    page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
                    return
                }
                val subIndex = posisiKonten - kumulatif[arsipIndex]
                val perkiraanTotalSub = kumulatif[arsipIndex + 1] - kumulatif[arsipIndex]
                cacheKey = "${arsip.idPosting}:${w}x$h:sub$subIndex"
                tugas = { renderHalamanArsip(w, h, arsip, index, totalHalamanKonten + 2, subIndex, perkiraanTotalSub) }
            }
        }

        val fromCache = cacheBitmap.get(cacheKey)
        if (fromCache != null) {
            page.setTexture(salinUntukTampil(fromCache, w, h), CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            prefetchTetangga(index, totalHalamanKonten)
            return
        }

        page.setTexture(renderPlaceholder(w, h), CurlPage.SIDE_FRONT)
        page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)

        if (sedangDiproses.add(cacheKey)) {
            executor.execute {
                try {
                    if (!shutdown) {
                        val bmp = tugas!!.invoke()
                        cacheBitmap.put(cacheKey, bmp)
                        refreshHalaman(index)
                    }
                } finally {
                    sedangDiproses.remove(cacheKey)
                }
            }
        }
    }

    /**
     * Render halaman kiri/kanan sekitar `index` di background lebih awal
     * (tanpa menunggu diminta), supaya waktu SWIPE terasa instan setelah
     * pengguna pernah singgah sebentar -- bukan cuma waktu dibuka persis.
     * Hanya jalan kalau belum ada di cache & belum sedang diproses.
     */
    private fun prefetchTetangga(index: Int, totalHalamanKonten: Int) {
        val w = wKumulatif.coerceAtLeast(1)
        val h = hKumulatif.coerceAtLeast(1)
        for (tetangga in intArrayOf(index - 1, index + 1, index + 2)) {
            if (tetangga < 0 || tetangga > totalHalamanKonten + 1) continue
            // Cek cache dulu SECARA SINKRON (murah) sebelum menjadwalkan apa
            // pun -- updatePage() ini dipanggil tiap frame utk halaman yg
            // sedang tampil, jadi kalau tidak dicek dulu, tetangga yg SUDAH
            // di-cache akan terus-menerus dijadwalkan ulang ke executor tiap
            // frame (kerja sia-sia, membanjiri thread pool tanpa manfaat).
            val key = cacheKeyUntuk(halaman = tetangga, w = w, h = h) ?: continue
            if (cacheBitmap.get(key) != null || sedangDiproses.contains(key)) continue
            executor.execute {
                if (shutdown) return@execute
                try {
                    updatePage(CurlPage(), w, h, tetangga)
                } catch (e: Exception) { /* prefetch best-effort, abaikan kegagalan */ }
            }
        }
    }

    /** Kunci cache untuk index halaman ini pada ukuran w/h saat ini, atau null kalau di luar jangkauan. */
    private fun cacheKeyUntuk(halaman: Int, w: Int, h: Int): String? {
        val totalHalamanKonten = if (kumulatif.isEmpty()) 0 else kumulatif.last()
        return when {
            halaman == 0 -> "sampul_depan:${w}x$h"
            halaman == totalHalamanKonten + 1 -> "sampul_belakang:${w}x$h"
            else -> {
                val posisiKonten = halaman - 1
                if (posisiKonten < 0 || posisiKonten >= totalHalamanKonten) return null
                val arsipIndex = cariArsipIndex(posisiKonten)
                val arsip = ambilData().getOrNull(arsipIndex) ?: return null
                val subIndex = posisiKonten - kumulatif[arsipIndex]
                "${arsip.idPosting}:${w}x$h:sub$subIndex"
            }
        }
    }

    // ------------------------------------------------------------------
    private fun renderPlaceholder(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(warnaKertas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E8C")
            textSize = height * 0.035f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Memuat\u2026", width / 2f, height / 2f, paint)
        return bmp
    }

    private fun renderKosong(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(warnaKertas)
        return bmp
    }

    private fun salinUntukTampil(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: renderKosong(width, height)
        } catch (e: OutOfMemoryError) {
            renderKosong(width, height)
        }
    }

    private fun renderSampul(width: Int, height: Int, judul: String, subjudul: String): Bitmap {
        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_sampul_depan, null, true)
            view.findViewById<TextView>(R.id.txtJudulSampul)?.text = judul
            view.findViewById<TextView>(R.id.txtSubjudulSampul)?.text = subjudul
            view
        }
    }

    // ------------------------------------------------------------------
    // PEMOTONGAN TEKS PERSIS (StaticLayout) -- dipanggil dari thread
    // background milik `executor`, aman melakukan kerja lumayan (bukan
    // GL thread / bukan UI thread).
    // ------------------------------------------------------------------
    private data class RencanaTeks(val potongan: List<IntRange>)
    private val rencanaCache = ConcurrentHashMap<String, RencanaTeks>()

    private fun ambilRencanaTeks(arsip: ArsipEntity, w: Int, h: Int): RencanaTeks {
        val key = "${arsip.idPosting}:${w}x$h"
        rencanaCache[key]?.let { return it }

        val teks = arsip.kontenPenuh.ifBlank { " " }
        if (teks.contains("--- Membagikan Status:")) {
            // Tidak dipaginasi (lihat batasan di dokumentasi kelas) -- 1 potongan = teks penuh.
            val hasil = RencanaTeks(listOf(0..teks.length))
            rencanaCache[key] = hasil
            return hasil
        }

        val lebarKontenPx = lebarKonten(w)
        val tinggiBadanPx = tinggiBadan(h, adaMedia = arsip.daftarFoto.isNotBlank())
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f * densitas }

        @Suppress("DEPRECATION")
        val layout = StaticLayout(teks, paint, lebarKontenPx, android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)

        val potongan = mutableListOf<IntRange>()
        var mulaiBaris = 0
        var tinggiTerpakai = 0
        for (baris in 0 until layout.lineCount) {
            val tinggiBaris = layout.getLineBottom(baris) - layout.getLineTop(baris)
            if (tinggiTerpakai + tinggiBaris > tinggiBadanPx && baris > mulaiBaris) {
                potongan.add(layout.getLineStart(mulaiBaris) until layout.getLineStart(baris))
                mulaiBaris = baris
                tinggiTerpakai = 0
            }
            tinggiTerpakai += tinggiBaris
        }
        potongan.add(layout.getLineStart(mulaiBaris) until teks.length)

        val hasil = RencanaTeks(potongan)
        rencanaCache[key] = hasil
        return hasil
    }

    // ------------------------------------------------------------------
    // HALAMAN ARSIP (dipanggil dari thread background milik `executor`)
    // ------------------------------------------------------------------
    private fun renderHalamanArsip(
        width: Int, height: Int, arsip: ArsipEntity, indexHalaman: Int, totalHalamanBuku: Int,
        subIndex: Int, perkiraanTotalSub: Int
    ): Bitmap {
        val rencana = ambilRencanaTeks(arsip, width, height)
        // Kalau perkiraan cepat "meleset kurang" (jarang, tapi bisa terjadi --
        // lihat dokumentasi kelas), subIndex bisa melebihi jumlah potongan
        // ASLI dari StaticLayout -- amankan dgn menampilkan potongan terakhir
        // yang tersedia, supaya tidak crash & tetap tidak ada teks yg hilang.
        val potonganIndex = subIndex.coerceAtMost(rencana.potongan.size - 1)
        val rentang = rencana.potongan[potonganIndex]
        val halamanTerakhirDariArsipIni = potonganIndex == rencana.potongan.size - 1
        val lanjutan = potonganIndex > 0

        var fotoRepresentatif: Bitmap? = null
        var isVideo = false
        var jumlahMediaLain = 0
        // Foto/video representatif HANYA ditempel di halaman terakhir arsip
        // ini (lihat dokumentasi kelas: kenapa media selalu dicadangkan di
        // halaman terakhir, bukan menyebar di tengah teks).
        if (halamanTerakhirDariArsipIni && arsip.daftarFoto.isNotBlank()) {
            val daftar = arsip.daftarFoto.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (daftar.isNotEmpty()) {
                val pertama = daftar[0]
                isVideo = pertama.startsWith("video:")
                val urlBersih = pertama.removePrefix("video:").removePrefix("image:")
                jumlahMediaLain = daftar.size - 1
                fotoRepresentatif = try {
                    Glide.with(context).asBitmap().load(urlBersih)
                        .submit(width, (height * 0.35f).toInt().coerceAtLeast(1))
                        .get(6, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    null
                }
            }
        }

        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_buku, null, true)
            view.background = KertasBergarisDrawable(density = context.resources.displayMetrics.density)

            val txtKontenUtama = view.findViewById<TextView>(R.id.txtKontenUtama)
            val txtKontenShared = view.findViewById<TextView>(R.id.txtKontenShared)
            val tinggiBarisPx = (KertasBergarisDrawable.TINGGI_BARIS_DP * context.resources.displayMetrics.density).toInt()
            TextViewCompat.setLineHeight(txtKontenUtama, tinggiBarisPx)
            TextViewCompat.setLineHeight(txtKontenShared, tinggiBarisPx)

            val kontenBersih = arsip.kontenPenuh
            val wadahDinamisKonten = view.findViewById<android.widget.LinearLayout>(R.id.wadahDinamisKonten)
            val wadahHeaderShared = view.findViewById<android.widget.LinearLayout>(R.id.wadahHeaderShared)
            val txtNamaPemilikShared = view.findViewById<TextView>(R.id.txtNamaPemilikShared)

            if (kontenBersih.contains("--- Membagikan Status:")) {
                // Kasus "shared status": belum dipaginasi, tetap seperti sebelumnya.
                val bagian = kontenBersih.split("\n\n--- Membagikan Status: ")
                val teksAsli = bagian[0].trim()
                if (teksAsli.isNotEmpty()) {
                    txtKontenUtama.text = teksAsli
                    txtKontenUtama.visibility = View.VISIBLE
                } else {
                    txtKontenUtama.visibility = View.GONE
                }
                val bantalanPx = (12 * context.resources.displayMetrics.density).toInt()
                wadahDinamisKonten.setBackgroundResource(R.drawable.bg_border_sharedpost)
                wadahDinamisKonten.setPadding(bantalanPx, bantalanPx, bantalanPx, bantalanPx)
                if (bagian.size > 1) {
                    val detail = bagian[1].split(" ---\n", limit = 2)
                    wadahHeaderShared.visibility = View.VISIBLE
                    txtKontenShared.visibility = View.VISIBLE
                    txtNamaPemilikShared.text = detail[0].trim()
                    if (detail.size > 1) {
                        txtKontenShared.text = detail[1].trim()
                    } else {
                        txtKontenShared.visibility = View.GONE
                    }
                }
            } else {
                // Kasus normal (Tanya-Jawab, dsb): potong sesuai `rentang` hasil
                // StaticLayout, dgn pewarnaan yg tetap konsisten dgn teks penuh.
                // `rentang` pakai konvensi `start until end` (end EKSKLUSIF) dari
                // StaticLayout, jadi argumen akhir ke subSequence() harus
                // `rentang.last + 1`, BUKAN `rentang.last` -- kalau tidak,
                // karakter terakhir tiap potongan akan hilang.
                val awal = rentang.first.coerceIn(0, kontenBersih.length)
                val akhir = (rentang.last + 1).coerceIn(awal, kontenBersih.length)
                val potonganBerwarna = warnaiKontenTanyaJawab(kontenBersih)
                    .let { SpannableStringBuilder(it) }
                    .subSequence(awal, akhir)
                txtKontenUtama.text = if (lanjutan) {
                    SpannableStringBuilder("\u21B3 (lanjutan halaman sebelumnya)\n\n").append(potonganBerwarna)
                } else potonganBerwarna
                txtKontenUtama.visibility = View.VISIBLE
                wadahDinamisKonten.setBackgroundResource(0)
                wadahDinamisKonten.setPadding(0, 0, 0, 0)
                wadahHeaderShared.visibility = View.GONE
                txtKontenShared.visibility = View.GONE
            }

            view.findViewById<TextView>(R.id.txtTanggal).text = arsip.tanggalBaca
            view.findViewById<TextView>(R.id.txtKategori).text = arsip.kategori
            view.findViewById<TextView>(R.id.txtNomorHalaman).text = "Halaman : $indexHalaman/$totalHalamanBuku"
            view.findViewById<ImageView>(R.id.imgProfilAbah)?.setImageResource(R.drawable.profil_abah)

            val wadahFoto = view.findViewById<android.widget.LinearLayout>(R.id.wadahMultiFoto)
            wadahFoto.removeAllViews()
            if (fotoRepresentatif != null) {
                wadahFoto.visibility = View.VISIBLE
                val bingkai = FrameLayout(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 500
                    ).apply { setMargins(0, 12, 0, 12) }
                    setBackgroundResource(R.drawable.bg_border_media)
                    setPadding(6, 6, 6, 6)
                }
                val img = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(fotoRepresentatif)
                }
                bingkai.addView(img)
                if (isVideo) {
                    val playIcon = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(100, 100).apply { gravity = android.view.Gravity.CENTER }
                        setImageResource(android.R.drawable.ic_media_play)
                        setColorFilter(Color.WHITE)
                    }
                    bingkai.addView(playIcon)
                }
                if (jumlahMediaLain > 0) {
                    val labelLebih = TextView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                            setMargins(0, 0, 12, 12)
                        }
                        text = "+$jumlahMediaLain lagi \u2192 tombol Lampiran"
                        setTextColor(Color.WHITE)
                        textSize = 10f
                        setBackgroundColor(0x80000000.toInt())
                        setPadding(10, 5, 10, 5)
                    }
                    bingkai.addView(labelLebih)
                }
                wadahFoto.addView(bingkai)
            } else {
                wadahFoto.visibility = View.GONE
            }

            view
        }
    }

    private fun warnaiKontenTanyaJawab(teksLengkap: String): Spannable {
        val spannable = SpannableString(teksLengkap)
        val batas = teksLengkap.indexOf("=====")
        if (batas != -1) {
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#004D40")), 0, batas, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#212121")), batas, teksLengkap.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#212121")), 0, teksLengkap.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    // ------------------------------------------------------------------
    private fun renderViewKeBitmapDiMainThread(width: Int, height: Int, buatView: () -> View): Bitmap {
        val latch = CountDownLatch(1)
        var hasil: Bitmap? = null
        mainHandler.post {
            try {
                val view = buatView()
                val w = width.coerceAtLeast(1)
                val h = height.coerceAtLeast(1)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, w, h)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bmp))
                hasil = bmp
            } catch (e: Exception) {
                hasil = null
            } finally {
                latch.countDown()
            }
        }
        val selesai = latch.await(4, TimeUnit.SECONDS)
        return if (selesai && hasil != null) hasil!! else renderKosong(width, height)
    }
}
