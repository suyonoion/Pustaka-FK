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

/**
 * Merender tiap "halaman" menjadi Bitmap tekstur untuk BudayakanBaca (CurlView
 * OpenGL).
 *
 * ------------------------------------------------------------------
 * PENYEDERHANAAN BESAR (menggantikan sistem paginasi lama):
 * Sebelumnya 1 arsip BISA menempati lebih dari 1 halaman kalau kontennya
 * panjang -- itu perlu larik akumulatif GLOBAL (`kumulatif[]`) yang memetakan
 * "halaman ke berapa" ke "arsip yang mana", dihitung dari PERKIRAAN jumlah
 * halaman per arsip (StaticLayout baru dipakai persis begitu arsip itu benar-
 * benar dibuka). Estimasi yang meleset di SATU arsip menggeser pemetaan
 * SEMUA arsip sesudahnya -- itu akar dari seluruh rentetan bug lompat-
 * meleset, ArrayIndexOutOfBounds, dan race kondisi lintas-thread yang
 * berulang kali muncul.
 *
 * Sekarang: **1 ARSIP = 1 HALAMAN, SELALU.** Nomor arsip (posisi di
 * `ambilData()`) = nomor halaman, langsung, tanpa hitungan apa pun.
 * Tidak ada lagi larik akumulatif, tidak ada lagi kunci/lock, tidak ada lagi
 * estimasi yang bisa meleset. Konten yang panjang untuk sementara ditampilkan
 * dengan ukuran font yang menyesuaikan (mengecil supaya tetap muat 1
 * halaman); dipotong dengan catatan ke Sumber Asli kalau tetap tidak muat
 * walau sudah di ukuran font minimum. Scroll-di-dalam-halaman direncanakan
 * sebagai tahap berikutnya, terpisah dari perubahan ini.
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

    // Batas aman tinggi bitmap render (kelipatan tinggi 1 layar) -- jaga2
    // konten yg ekstrem panjangnya (jarang) tidak mengalokasikan bitmap
    // nyaris tak terbatas. ~12 layar sudah lebih dari cukup utk status
    // terpanjang yg wajar; lebih dari itu, potong & arahkan ke Sumber Asli.
    private val BATAS_KALI_TINGGI_LAYAR = 12

    // ------------------------------------------------------------------
    // TAHAP 2: SCROLL DI DALAM HALAMAN
    // ------------------------------------------------------------------
    // Sejak Tahap 1, cacheBitmap TIDAK LAGI menyimpan bitmap seukuran layar
    // persis -- sekarang menyimpan bitmap SETINGGI KONTEN ASLINYA (bisa jauh
    // lebih tinggi dari 1 layar utk arsip yang panjang, lihat
    // renderViewKeBitmapTinggi()). Yang diserahkan ke CurlPage/GL SELALU
    // berupa POTONGAN seukuran-layar dari bitmap tinggi itu, pada posisi
    // scroll saat ini -- lihat potongUntukTampil().
    //
    // Hanya SATU halaman yang bisa digeser interaktif dalam satu waktu
    // (yang sedang tampil) -- `indexSedangDibaca`/`offsetGeserPx` cukup 2
    // variabel instance, tidak perlu Map per-halaman. Begitu pindah ke
    // index lain (curl selesai / lompat dari grid), offset otomatis balik
    // ke 0 (mulai dari atas lagi) -- lihat pengecekan `index == indexSedangDibaca`
    // di updatePage() & geserKontenHalaman().
    @Volatile private var indexSedangDibaca = -1
    @Volatile private var offsetGeserPx = 0

    private fun potongUntukTampil(bitmapTinggi: Bitmap, w: Int, h: Int, offsetY: Int): Bitmap {
        val maxOffset = (bitmapTinggi.height - h).coerceAtLeast(0)
        val offsetAman = offsetY.coerceIn(0, maxOffset)
        val tinggiPotongan = h.coerceAtMost(bitmapTinggi.height - offsetAman).coerceAtLeast(1)
        val lebarPotongan = w.coerceAtMost(bitmapTinggi.width).coerceAtLeast(1)
        return try {
            val potongan = Bitmap.createBitmap(bitmapTinggi, 0, offsetAman, lebarPotongan, tinggiPotongan)
            // PENTING -- BUG KRITIS KALAU DIABAIKAN: Bitmap.createBitmap(source,
            // x,y,w,h) mengembalikan OBJEK SUMBER ASLI APA ADANYA (bukan
            // salinan baru) kalau area yg diminta PERSIS sama dgn ukuran
            // sumbernya (x=0,y=0,w=source.width,h=source.height) -- dan itu
            // SELALU terjadi utk halaman yg kontennya muat 1 layar (offsetAman
            // selalu 0, tinggiPotongan selalu = tinggi penuh bitmap), yaitu
            // MAYORITAS arsip. Kalau dibiarkan, objek yg sama dgn yg masih
            // dipegang `cacheBitmap` diserahkan ke CurlPage.setTexture(), yang
            // akan me-recycle()-nya begitu diganti -- merusak cache & memicu
            // lagi kelas crash native "freePixels" yg berulang kali sudah
            // diperbaiki sebelumnya. WAJIB disalin ulang kalau ternyata objek
            // yg dikembalikan SAMA (bukan potongan baru).
            if (potongan === bitmapTinggi) {
                potongan.copy(potongan.config ?: Bitmap.Config.ARGB_8888, false) ?: renderKosong(w, h)
            } else {
                potongan
            }
        } catch (e: Exception) {
            renderKosong(w, h)
        } catch (e: OutOfMemoryError) {
            renderKosong(w, h)
        }
    }

    /**
     * Geser konten halaman `index` sejauh `deltaYPx` (positif = konten
     * bergerak ke atas, spt scroll biasa melihat lanjutan teks). Dipanggil
     * dari BudayakanBaca saat gestur sentuhan terdeteksi sbg scroll vertikal
     * (bukan balik halaman) -- lihat BudayakanBaca.onTouch().
     * @return true kalau posisi scroll benar-benar berubah (halaman ini
     * memang punya konten yg lebih panjang dari 1 layar & belum mentok).
     */
    @Volatile private var refreshTerakhirMs = 0L
    // ~30fps utk update TEKSTUR scroll -- cukup mulus utk mata, tapi jauh
    // lebih murah drpd memotong bitmap + upload tekstur GL PENUH tiap
    // event ACTION_MOVE (bisa >60x/detik di sebagian device -- itulah
    // penyebab "kaku/lag" yg dilaporkan). Posisi LOGIS (offsetGeserPx)
    // tetap ter-update SETIAP panggilan, cuma tekstur yg ditampilkan yg
    // dibatasi lajunya -- lihat selesaiGeserKontenHalaman() utk memastikan
    // posisi terakhir tetap tampil persis begitu jari diangkat (ACTION_UP),
    // walau update itu jatuh di tengah jendela throttle.
    private val JEDA_MINIMUM_REFRESH_MS = 32L

    // PERBAIKAN BUG "SCROLL BARU AKTIF SETELAH BUKA RECENT & BALIK LAGI":
    // sebelumnya geserKontenHalaman()/bisaDigeser() bergantung pada
    // `cacheKeyTerakhir[index]` -- sebuah Map yang HANYA diisi sbg EFEK
    // SAMPING oleh updatePage() (dan hanya di jalur cache-HIT-nya). Ada
    // celah waktu/urutan nyata di sana: kalau updatePage() BELUM SEMPAT
    // dipanggil ULANG utk index ini setelah render async-nya selesai (mis.
    // krn refreshPageTexture()'s pengecekan index==mLastRightIdx/dst belum
    // "kena" tepat pas render selesai), cacheKeyTerakhir[index] tetap
    // kosong SELAMANYA sampai ADA pemicu lain (spt onPause/onResume dari
    // buka recent, yang memaksa updatePages() jalan ulang & akhirnya
    // mengisi Map itu) -- padahal bitmap-nya SENDIRI sebenarnya SUDAH ADA
    // di cacheBitmap sejak lama, tinggal tidak "diketahui" via Map perantara
    // yang rapuh itu.
    //
    // Fix: geserKontenHalaman()/bisaDigeser() SEKARANG menghitung ulang
    // kunci cache-nya SENDIRI lewat resolusiHalaman() (murah -- cuma bikin
    // String key, TIDAK menjalankan render) & baca cacheBitmap LANGSUNG.
    // Tidak ada lagi Map perantara yang bisa "telat" terisi -- begitu bitmap
    // ADA di cache (dari jalur mana pun ia sampai ke sana), scroll langsung
    // bisa jalan, tanpa perlu event tambahan apa pun sbg pemicu.
    fun geserKontenHalaman(index: Int, deltaYPx: Int, w: Int, h: Int): Boolean {
        if (index != indexSedangDibaca) {
            indexSedangDibaca = index
            offsetGeserPx = 0
        }
        val resolusi = resolusiHalaman(index, w, h, ambilData()) ?: return false
        val bmp = cacheBitmap.get(resolusi.cacheKey) ?: return false
        val maxOffset = (bmp.height - h).coerceAtLeast(0)
        if (maxOffset <= 0) return false
        val baru = (offsetGeserPx + deltaYPx).coerceIn(0, maxOffset)
        if (baru == offsetGeserPx) return false
        offsetGeserPx = baru
        val sekarang = android.os.SystemClock.uptimeMillis()
        if (sekarang - refreshTerakhirMs < JEDA_MINIMUM_REFRESH_MS) {
            return true // posisi logis sudah benar; tekstur GL menyusul di tick berikutnya
        }
        refreshTerakhirMs = sekarang
        refreshHalaman(index)
        return true
    }

    /**
     * Dipanggil saat gestur scroll SELESAI (ACTION_UP/CANCEL) -- paksa satu
     * refresh tekstur TANPA throttle, supaya posisi yang tampil di layar
     * selalu persis sama dgn offsetGeserPx terakhir, walau update paling
     * akhir tadi kebetulan jatuh di tengah jendela throttle (lihat
     * geserKontenHalaman()).
     */
    fun selesaiGeserKontenHalaman(index: Int) {
        if (index != indexSedangDibaca) return
        refreshTerakhirMs = android.os.SystemClock.uptimeMillis()
        refreshHalaman(index)
    }

    /** Apakah halaman `index` punya konten yg lebih panjang dari 1 layar (butuh/bisa discroll). */
    fun bisaDigeser(index: Int, w: Int, h: Int): Boolean {
        val resolusi = resolusiHalaman(index, w, h, ambilData()) ?: return false
        val bmp = cacheBitmap.get(resolusi.cacheKey) ?: return false
        return bmp.height > h
    }

    /**
     * Dipakai utk indikator panah "masih ada lanjutan di bawah" (lihat
     * indikatorScrollBawah di activity_main.xml). Beda dari bisaDigeser():
     * ini juga memperhitungkan offsetGeserPx SAAT INI -- begitu user sudah
     * scroll sampai mentok bawah, harus balik false (indikator hilang),
     * bukan tetap true selama halaman itu panjang.
     */
    fun adaLanjutanDiBawah(index: Int, w: Int, h: Int): Boolean {
        val resolusi = resolusiHalaman(index, w, h, ambilData()) ?: return false
        val bmp = cacheBitmap.get(resolusi.cacheKey) ?: return false
        val maxOffset = (bmp.height - h).coerceAtLeast(0)
        if (maxOffset <= 0) return false
        val offsetSaatIni = if (index == indexSedangDibaca) offsetGeserPx else 0
        return offsetSaatIni < maxOffset
    }


    /**
     * Nomor arsip (posisi di ambilData(), 0-based) = nomor halaman - 1
     * (index 0 dicadangkan utk sampul depan). PEMETAAN LANGSUNG, tidak ada
     * hitungan/estimasi/lock apa pun -- lihat dokumentasi kelas di atas.
     */
    fun indexHalamanUntukArsip(posisiArsip: Int): Int = posisiArsip + 1

    /** Kebalikan dari indexHalamanUntukArsip -- null kalau sampul depan/belakang. */
    fun indexArsipDari(indexHalaman: Int): Int? {
        val posisiArsip = indexHalaman - 1
        val n = ambilData().size
        return if (posisiArsip in 0 until n) posisiArsip else null
    }

    override fun getPageCount(): Int = ambilData().size + 2 // + sampul depan + belakang

    fun shutdown() {
        shutdown = true
        executor.shutdownNow()
    }

    // ------------------------------------------------------------------
    /** Hasil resolusi index halaman -> cacheKey + tugas render latar belakangnya (tanpa efek samping). */
    private data class ResolusiHalaman(val cacheKey: String, val tugas: () -> Bitmap)

    private fun resolusiHalaman(index: Int, w: Int, h: Int, data: List<ArsipEntity>): ResolusiHalaman? {
        return when {
            index == 0 -> ResolusiHalaman("sampul_depan:${w}x$h") {
                renderSampul(w, h, judul = "Pustaka FK", subjudul = "Arsip Fatwa & Kehidupan")
            }
            index == data.size + 1 -> ResolusiHalaman("sampul_belakang:${w}x$h") {
                renderSampul(w, h, judul = "Tamat", subjudul = "Pustaka FK")
            }
            else -> {
                val arsipIndex = index - 1
                val arsip = data.getOrNull(arsipIndex) ?: return null
                ResolusiHalaman("${arsip.idPosting}:${w}x$h") {
                    renderHalamanArsip(w, h, arsip, arsipIndex + 1, data.size)
                }
            }
        }
    }

    /** Menjadwalkan render latar belakang utk `cacheKey` kalau belum sedang diproses; hasil masuk cache & memicu refreshHalaman(index). */
    private fun mintaRenderLatarBelakang(cacheKey: String, tugas: () -> Bitmap, index: Int) {
        if (sedangDiproses.add(cacheKey)) {
            executor.execute {
                try {
                    if (!shutdown) {
                        val bmp = tugas()
                        cacheBitmap.put(cacheKey, bmp)
                        refreshHalaman(index)
                    }
                } finally {
                    sedangDiproses.remove(cacheKey)
                }
            }
        }
    }

    override fun updatePage(page: CurlPage, width: Int, height: Int, index: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val data = ambilData()

        val resolusi = resolusiHalaman(index, w, h, data)
        if (resolusi == null) {
            page.setTexture(renderKosong(w, h), CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            return
        }

        val fromCache = cacheBitmap.get(resolusi.cacheKey)
        if (fromCache != null) {
            val offsetUntukHalamanIni = if (index == indexSedangDibaca) offsetGeserPx else 0
            page.setTexture(potongUntukTampil(fromCache, w, h, offsetUntukHalamanIni), CurlPage.SIDE_FRONT)
            page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
            prefetchTetangga(index, data.size, w, h, data)
            return
        }

        page.setTexture(renderPlaceholder(w, h), CurlPage.SIDE_FRONT)
        page.setColor(warnaSampulBack, CurlPage.SIDE_BACK)
        mintaRenderLatarBelakang(resolusi.cacheKey, resolusi.tugas, index)
    }

    /**
     * Render halaman kiri/kanan sekitar `index` di background lebih awal
     * (tanpa menunggu diminta), supaya waktu SWIPE terasa instan setelah
     * pengguna pernah singgah sebentar -- bukan cuma waktu dibuka persis.
     * Hanya jalan kalau belum ada di cache & belum sedang diproses.
     *
     * PERBAIKAN PENTING: versi lama membuat `CurlPage()` sekali-pakai dan
     * memanggil updatePage() dengannya dari THREAD BACKGROUND -- itu berarti
     * CurlPage.setTexture() (yang me-recycle() bitmap lama) ikut tersentuh
     * DI LUAR GL thread, melanggar kontrak GLSurfaceView (CurlPage/CurlMesh
     * cuma boleh disentuh dari GL thread). Sekarang prefetch CUMA mengisi
     * cache lewat resolusiHalaman()+mintaRenderLatarBelakang() -- TIDAK
     * PERNAH membuat atau menyentuh objek CurlPage sama sekali.
     */
    private fun prefetchTetangga(index: Int, totalArsip: Int, w: Int, h: Int, data: List<ArsipEntity>) {
        for (tetangga in intArrayOf(index - 1, index + 1, index + 2)) {
            if (tetangga < 0 || tetangga > totalArsip + 1) continue
            val resolusi = resolusiHalaman(tetangga, w, h, data) ?: continue
            // Cek cache dulu SECARA SINKRON (murah) sebelum menjadwalkan apa
            // pun -- updatePage() ini dipanggil tiap frame utk halaman yg
            // sedang tampil, jadi kalau tidak dicek dulu, tetangga yg SUDAH
            // di-cache akan terus-menerus dijadwalkan ulang ke executor tiap
            // frame (kerja sia-sia, membanjiri thread pool tanpa manfaat).
            if (cacheBitmap.get(resolusi.cacheKey) != null || sedangDiproses.contains(resolusi.cacheKey)) continue
            mintaRenderLatarBelakang(resolusi.cacheKey, resolusi.tugas, tetangga)
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

    private fun renderSampul(width: Int, height: Int, judul: String, subjudul: String): Bitmap {
        return renderViewKeBitmapDiMainThread(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_sampul_depan, null, true)
            view.findViewById<TextView>(R.id.txtJudulSampul)?.text = judul
            view.findViewById<TextView>(R.id.txtSubjudulSampul)?.text = subjudul
            view
        }
    }

    data class KontenBerbagi(val teksAsli: String, val namaPemilik: String, val kontenShared: String)

    /** Dipakai bersama oleh render & pengecekan panjang -- SATU tempat parsing, hindari duplikasi/inkonsistensi. */
    fun parseKontenBerbagi(kontenBersih: String): KontenBerbagi? {
        if (!kontenBersih.contains("--- Membagikan Status:")) return null
        val bagian = kontenBersih.split("\n\n--- Membagikan Status: ")
        val teksAsli = bagian[0].trim()
        if (bagian.size <= 1) return KontenBerbagi(teksAsli, "", "")
        val detail = bagian[1].split(" ---\n", limit = 2)
        val nama = detail[0].trim()
        val shared = if (detail.size > 1) detail[1].trim() else ""
        return KontenBerbagi(teksAsli, nama, shared)
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
    // HALAMAN ARSIP (dipanggil dari thread background milik `executor`)
    // ------------------------------------------------------------------
    private fun renderHalamanArsip(width: Int, height: Int, arsip: ArsipEntity, nomorArsip: Int, totalArsip: Int): Bitmap {
        val adaMedia = arsip.daftarFoto.isNotBlank()

        var fotoRepresentatif: Bitmap? = null
        var isVideo = false
        var jumlahMediaLain = 0
        if (adaMedia) {
            val daftar = arsip.daftarFoto.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (daftar.isNotEmpty()) {
                val pertama = daftar[0]
                isVideo = pertama.startsWith("video:")
                val urlBersih = pertama.removePrefix("video:").removePrefix("image:")
                jumlahMediaLain = daftar.size - 1
                fotoRepresentatif = try {
                    // PENTING: applicationContext (bukan context Activity) --
                    // lihat histori perbaikan crash "freePixels" terkait Glide
                    // di dokumentasi kelas.
                    // PERBAIKAN: diturunkan dari 6 detik ke 3 detik -- seluruh
                    // render halaman ini (termasuk teks & kesiapan utk scroll)
                    // menunggu foto ini SELESAI atau GAGAL dulu; 6 detik
                    // terlalu lama utk membuat halaman "belum siap discroll".
                    Glide.with(context.applicationContext).asBitmap().load(urlBersih)
                        .submit(width, (height * 0.35f).toInt().coerceAtLeast(1))
                        .get(3, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    null
                }
            }
        }

        val kontenBersih = arsip.kontenPenuh
        val kb = parseKontenBerbagi(kontenBersih)

        return renderViewKeBitmapTinggi(width, height) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_buku, null, true)
            view.background = KertasBergarisDrawable(density = context.resources.displayMetrics.density)

            val txtKontenUtama = view.findViewById<TextView>(R.id.txtKontenUtama)
            val txtKontenShared = view.findViewById<TextView>(R.id.txtKontenShared)
            // PENYEDERHANAAN TAHAP 2: tidak perlu lagi mengecilkan/memotong
            // teks -- ukuran font pakai default dari XML (14sp/13sp), dan
            // seluruh teks ditampilkan APA ADANYA. Kalau lebih tinggi dari 1
            // layar, view (dan bitmap-nya) memang dibuat lebih tinggi --
            // lihat renderViewKeBitmapTinggi() -- lalu digeser scroll saat
            // dibaca (lihat geserKontenHalaman()).
            val tinggiBarisPx = (KertasBergarisDrawable.TINGGI_BARIS_DP * context.resources.displayMetrics.density).toInt()
            TextViewCompat.setLineHeight(txtKontenUtama, tinggiBarisPx)
            TextViewCompat.setLineHeight(txtKontenShared, tinggiBarisPx)

            val wadahDinamisKonten = view.findViewById<android.widget.LinearLayout>(R.id.wadahDinamisKonten)
            val wadahHeaderShared = view.findViewById<android.widget.LinearLayout>(R.id.wadahHeaderShared)
            val txtNamaPemilikShared = view.findViewById<TextView>(R.id.txtNamaPemilikShared)

            if (kb == null) {
                txtKontenUtama.text = warnaiKontenTanyaJawab(kontenBersih)
                txtKontenUtama.visibility = View.VISIBLE
                wadahDinamisKonten.setBackgroundResource(0)
                wadahDinamisKonten.setPadding(0, 0, 0, 0)
                wadahHeaderShared.visibility = View.GONE
                txtKontenShared.visibility = View.GONE
            } else {
                if (kb.teksAsli.isNotBlank()) {
                    txtKontenUtama.text = warnaiKontenTanyaJawab(kb.teksAsli)
                    txtKontenUtama.visibility = View.VISIBLE
                } else {
                    txtKontenUtama.visibility = View.GONE
                }
                val bantalanPx = (12 * context.resources.displayMetrics.density).toInt()
                wadahDinamisKonten.setBackgroundResource(R.drawable.bg_border_sharedpost)
                wadahDinamisKonten.setPadding(bantalanPx, bantalanPx, bantalanPx, bantalanPx)
                wadahHeaderShared.visibility = if (kb.namaPemilik.isNotBlank()) View.VISIBLE else View.GONE
                if (kb.namaPemilik.isNotBlank()) txtNamaPemilikShared.text = kb.namaPemilik
                if (kb.kontenShared.isNotBlank()) {
                    txtKontenShared.text = kb.kontenShared
                    txtKontenShared.visibility = View.VISIBLE
                } else {
                    txtKontenShared.visibility = View.GONE
                }
            }

            view.findViewById<TextView>(R.id.txtTanggal).text = arsip.tanggalBaca
            view.findViewById<TextView>(R.id.txtKategori).text = arsip.kategori
            view.findViewById<TextView>(R.id.txtNomorHalaman).text = "Halaman : $nomorArsip/$totalArsip"
            view.findViewById<ImageView>(R.id.imgProfilAbah)?.setImageResource(R.drawable.profil_abah)
            view.findViewById<View>(R.id.wadahProfilPenulis).visibility = View.VISIBLE
            view.findViewById<View>(R.id.wadahFooterDekoratif).visibility = View.GONE

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

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    /**
     * TAHAP 2: sama seperti renderViewKeBitmapDiMainThread(), TAPI tinggi
     * bitmap-nya mengikuti tinggi ALAMI konten (wrap_content), bukan mepet
     * `tinggiMinimum` (tinggi 1 layar). Kalau kontennya pendek, hasilnya
     * sama seperti sebelumnya (persis `tinggiMinimum`, layout dgn
     * android:layout_weight="1" pada ScrollView di item_buku.xml akan
     * mengisi sisa ruang secara wajar). Kalau kontennya panjang, bitmap
     * yang dihasilkan LEBIH TINGGI dari 1 layar -- potongan yang benar2
     * ditampilkan ke pengguna (seukuran 1 layar, pada posisi scroll saat
     * itu) diambil belakangan oleh potongUntukTampil().
     */
    private fun renderViewKeBitmapTinggi(width: Int, tinggiMinimum: Int, buatView: () -> View): Bitmap {
        val latch = CountDownLatch(1)
        var hasil: Bitmap? = null
        mainHandler.post {
            try {
                val view = buatView()
                val w = width.coerceAtLeast(1)
                val hMin = tinggiMinimum.coerceAtLeast(1)
                // Ukur dulu tinggi alaminya (UNSPECIFIED) sebelum benar2
                // layout+gambar -- ini yang memungkinkan tahu berapa tinggi
                // total yang dibutuhkan tanpa memotong konten apa pun.
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val tinggiAlami = view.measuredHeight.coerceAtLeast(hMin).coerceAtMost(hMin * BATAS_KALI_TINGGI_LAYAR)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(tinggiAlami, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, w, tinggiAlami)
                val bmp = Bitmap.createBitmap(w, tinggiAlami, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bmp))
                hasil = bmp
            } catch (e: Exception) {
                hasil = null
            } catch (e: OutOfMemoryError) {
                // Konten ekstrem panjangnya (jarang) bisa gagal alokasi bitmap
                // -- daripada crash, tampilkan apa adanya di tinggi 1 layar
                // saja (masih bisa dibaca via tombol Sumber Asli).
                hasil = null
            } finally {
                latch.countDown()
            }
        }
        val selesai = latch.await(4, TimeUnit.SECONDS)
        return if (selesai && hasil != null) hasil!! else renderKosong(width, tinggiMinimum)
    }

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
