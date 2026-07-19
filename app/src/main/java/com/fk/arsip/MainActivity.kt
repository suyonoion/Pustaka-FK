package com.fk.arsip

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView
import androidx.viewpager2.widget.ViewPager2
import com.fk.arsip.database.ArsipDatabase
import com.fk.arsip.database.ArsipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import android.os.Handler
import android.os.Looper
import androidx.constraintlayout.widget.ConstraintLayout


// Cetak Biru Hibrida untuk Rel Samping
data class TitikNavigasi(
    val tipe: Int, // 0 = Tahun, 1 = Bulan
    val teks: String,
    val indeksTujuan: Int = -1,
    val warnaGenap: Boolean = false
)

// Ini bertindak sebagai parameter universal yang bisa dibaca oleh seluruh sistem.
enum class FaseInjeksi(val pesan: String, val idGambar: Int) {
    FASE_1("1. Memulai aplikasi pertama kali...", R.drawable.img_1),
    FASE_2("2. Menghubungkan ke server...", R.drawable.img_2),
    FASE_3("3. Mengunduh data status...", R.drawable.img_3),
    FASE_4("4. Menunggu jaringan stabil...", R.drawable.img_4), // Relay Darurat
    FASE_5("5. Mengelas blok data ke memori...", R.drawable.img_5),
    FASE_6("6. Injeksi baris data ke SQLite...", R.drawable.img_6),
    FASE_7("7. Proses selesai.", R.drawable.img_7)
}

class MainActivity : AppCompatActivity() {

    private val namaFile = "Master_Data_Arsip_FK_11_Juli_2026.json"
    private val urlKargo = "https://github.com/suyonoion/Pustaka-FK/releases/download/v1.0.0/Master_Data_Arsip_FK_11_Juli_2026.json"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navViewCustom: LinearLayout
    private lateinit var recyclerGridMode: RecyclerView
    private lateinit var wadahModeBuku: RelativeLayout
    private lateinit var proyektorBuku: ViewPager2
    private lateinit var edtPencarian: EditText
    private lateinit var panelStatusPencarian: CardView

    private lateinit var loadingPencarian: ProgressBar
    private lateinit var txtStatusPencarian: TextView
    
    private lateinit var gridAdapter: GridAdapter
    private lateinit var bukuAdapter: BukuAdapter
    
    // TAMBAHKAN 2 KOMPONEN INI UNTUK TELEMETRI HORIZONTAL
    private lateinit var pipaVolumeData: ProgressBar
    private lateinit var txtDetailSubProses: TextView
    // Tambahkan komponen ini di area deklarasi variabel
    private lateinit var recyclerTimeline: RecyclerView
    // Penampung Arus Data & Sakelar Status Informasi
    private var daftarArsipAktif: List<ArsipEntity> = listOf()
    private var isSearchMode = false 
    // Tuas Pengunci Interlock Mesin
    private var isMesinSibuk = false
    private var modeKategoriAktif = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navViewCustom = findViewById(R.id.navViewCustom)
                // PENGELASAN TUAS LACI (Membuka Rel Samping)
        findViewById<android.widget.ImageView>(R.id.btnMenuDrawer).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        recyclerGridMode = findViewById(R.id.recyclerGridMode)
        wadahModeBuku = findViewById(R.id.wadahModeBuku)
        proyektorBuku = findViewById(R.id.proyektorBuku)
        edtPencarian = findViewById(R.id.edtPencarian)
        // Tambahkan 3 baris ini:
        panelStatusPencarian = findViewById(R.id.panelStatusPencarian)
        loadingPencarian = findViewById(R.id.loadingPencarian)
        txtStatusPencarian = findViewById(R.id.txtStatusPencarian)

        // IKAT KOMPONEN BARU
        pipaVolumeData = findViewById(R.id.pipaVolumeData)
        txtDetailSubProses = findViewById(R.id.txtDetailSubProses)
        recyclerTimeline = findViewById(R.id.recyclerTimeline)

        recyclerGridMode.layoutManager = GridLayoutManager(this, 2)
        sesuaikanKompartemenGrid() // Pemicu kalkulasi saat mesin pertama kali menyala
        proyektorBuku.setPageTransformer(null)

        // Inisialisasi awal kompartemen transmisi (Adapter Kosong)
        gridAdapter = GridAdapter(emptyList()) { posisi -> bukaModeBuku(posisi) }
        
        // KALIBRASI GRID (Pembagian Lajur)
        val layoutManagerGrid = GridLayoutManager(this, 2)
        layoutManagerGrid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // Tipe Pembatas (Tahun|Bulan) makan 2 kolom, Tipe Konten makan 1 kolom
                return if (gridAdapter.getItemViewType(position) == GridAdapter.TIPE_PEMBATAS) 2 else 1
            }
        }
        recyclerGridMode.layoutManager = layoutManagerGrid
        recyclerGridMode.adapter = gridAdapter

                // ... (kode Anda yang ada di onCreate)
        bukuAdapter = BukuAdapter(daftarArsipAktif)
        proyektorBuku.adapter = bukuAdapter

        // INJEKSI TUAS PENGAMAN INI:
        // Memaksa mesin hanya memproses 1 halaman aktual tanpa merakit halaman sebelah
        proyektorBuku.offscreenPageLimit = 1
        
        // Mematikan efek pegas (overscroll) yang memakan siklus komputasi ekstra
        val mesinProyeksi = proyektorBuku.getChildAt(0) as? RecyclerView
        mesinProyeksi?.overScrollMode = View.OVER_SCROLL_NEVER

        // PENGELASAN UTAMA: Sirkuit Pengereman Berjenjang Mutlak (Hardware Back Button)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } 
                else if (wadahModeBuku.visibility == View.VISIBLE) {
                    wadahModeBuku.visibility = View.GONE
                    recyclerGridMode.visibility = View.VISIBLE
                } 
                // JARING PENANGKAP GANDA: Sensor Kolom Pencarian & Sensor Kategori Laci
                else if (isSearchMode || edtPencarian.text.toString().isNotEmpty() || modeKategoriAktif) {
                    
                    // 1. Matikan seluruh sakelar mode khusus
                    isSearchMode = false
                    modeKategoriAktif = false 
                    
                    // 2. Bersihkan indikator visual
                    edtPencarian.text.clear()
                    edtPencarian.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(edtPencarian.windowToken, 0)
                    
                    // 3. Tarik paksa seluruh muatan awal dari brankas
                    tampilkanIndikator("Memuat ulang semua status...", true)
                    lifecycleScope.launch(Dispatchers.IO) {
                        val database = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
                        val semuaData = database.tarikSemuaArsip()
                        
                        withContext(Dispatchers.Main) {
                            pompaDataKeLayar(semuaData)
                            panelStatusPencarian.visibility = View.GONE
                        }
                    }
                } 
                else {
                    tampilkanPanelKonfirmasiKeluar()
                }
            }
        })


        inisialisasiTuasFooterStatis()
        inisialisasiSirkuitAppDrawer()
        aktifkanSirkuitPencarian()
        eksekusiPabrikData()
    }
    
      // Fungsi ini adalah mesin pemutar roda gigi UI
    private fun perbaruiPanelTelemetri(fase: FaseInjeksi, persentase: Int, volumeSelesai: Int, volumeTotal: Int) {
        val panelUtama = findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama)
        val teksStatus = findViewById<TextView>(R.id.teksStatusFase)
        val indikatorVisual = findViewById<ImageView>(R.id.indikatorVisualMesin)
        val lingkarProgres = findViewById<ProgressBar>(R.id.lingkarPersentaseUtama)
        val teksPersen = findViewById<TextView>(R.id.teksPersentaseSentral)
        val teksTelemetri = findViewById<TextView>(R.id.teksTelemetriData)

        teksStatus.text = fase.pesan
        indikatorVisual.setImageResource(fase.idGambar)
        lingkarProgres.progress = persentase
        teksPersen.text = "$persentase%"

        if (fase != FaseInjeksi.FASE_7) {
            teksTelemetri.text = "Arsip Status Digital Fatwa Kehidupan: $volumeSelesai / $volumeTotal baris\nSistem sedang bekerja..."
        } else {
            teksTelemetri.text = "Seluruh blok data berhasil dilas ke dalam memori SQLite."
            Handler(Looper.getMainLooper()).postDelayed({
                panelUtama.visibility = View.GONE
            }, 1500)
        }
    }

    // Sensor Penangkap Guncangan Orientasi Layar
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Jalankan kalkulasi ulang pembagian kolom secara instan
        sesuaikanKompartemenGrid() 
    }    
    
        private fun inisialisasiSirkuitAppDrawer() {
        findViewById<TextView>(R.id.menuBiografi).setOnClickListener { eksekusiSaringanKategori("Biografi") }
        findViewById<TextView>(R.id.menuSosmed).setOnClickListener { eksekusiSaringanKategori("Sosial Media") }
        findViewById<TextView>(R.id.menuLetnan).setOnClickListener { eksekusiSaringanKategori("Letnan") }
        findViewById<TextView>(R.id.menuYayasan).setOnClickListener { eksekusiSaringanKategori("Yayasan") }
        
        inisialisasiKategoriDrawer()
    }

        private fun inisialisasiKategoriDrawer() {
        val wadah = findViewById<LinearLayout>(R.id.wadahKategoriDinamis)
        wadah.removeAllViews()

        for (kategori in CetakBiruKategori.MATRIKS_UTAMA) {
            val namaInduk = kategori.first
            val daftarCabang = kategori.second

            val barisInduk = TextView(this).apply {
                text = namaInduk
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#1C1E21"))
                setPadding(52, 36, 52, 36)
                setBackgroundResource(android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // Sensor Pemisah: Jika isi anak sama dengan nama induk, tampilkan sebagai tombol tunggal
            if (daftarCabang.size == 1 && daftarCabang[0].first == namaInduk) {
                barisInduk.setOnClickListener { eksekusiSaringanKategori(namaInduk) }
                wadah.addView(barisInduk)
            } else {
                // Sensor Pemisah: Jika isi anak berbeda/banyak, jadikan menu bersarang (Dropdown)
                barisInduk.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0)
                val wadahAnak = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }

                for (cabang in daftarCabang) {
                    val namaAnak = cabang.first
                    val barisAnak = TextView(this).apply {
                        text = "•  $namaAnak"
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#555555"))
                        setPadding(72, 24, 52, 24)
                        setBackgroundResource(android.R.drawable.list_selector_background)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { eksekusiSaringanKategori(namaAnak) }
                    }
                    wadahAnak.addView(barisAnak)
                }

                barisInduk.setOnClickListener {
                    if (wadahAnak.visibility == View.VISIBLE) {
                        wadahAnak.visibility = View.GONE
                        barisInduk.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0)
                    } else {
                        wadahAnak.visibility = View.VISIBLE
                        barisInduk.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_up_float, 0)
                    }
                }
                wadah.addView(barisInduk)
                wadah.addView(wadahAnak)
            }
        }
    }


    // Sirkuit Pintu Penyaringan Kategori Laci
  
    private fun eksekusiSaringanKategori(labelKategori: String) {
        if (isMesinSibuk) {
            Toast.makeText(this, "Sistem sedang merakit data. Harap tunggu.", Toast.LENGTH_SHORT).show()
            return
        }
        tampilkanIndikator("Menarik kargo: $labelKategori...", true)

        lifecycleScope.launch(Dispatchers.IO) {
            val database = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
            val hasilSaringanAkhir = database.saringBerdasarkanKolomKategori(labelKategori)

            withContext(Dispatchers.Main) {
                isSearchMode = false
                modeKategoriAktif = true
                edtPencarian.text.clear()
                
                // KALIBRASI OUTPUT: Merakit muatan teks menjadi format pelat judul buku
                val muatanTeks = "$labelKategori (${hasilSaringanAkhir.size} arsip)"
                
                // Pompa muatan teks baru ke mesin indikator
                tampilkanIndikator(muatanTeks, false)
                
                panelStatusPencarian.visibility = View.VISIBLE 
                wadahModeBuku.visibility = View.GONE
                recyclerGridMode.visibility = View.VISIBLE
                
                pompaDataKeLayar(hasilSaringanAkhir) 
                drawerLayout.closeDrawers()
            }

        }
    }



    // Sirkuit Pemicu Footer Statis
    private fun inisialisasiTuasFooterStatis() {
        val btnSanFK = findViewById<LinearLayout>(R.id.linkSanFK_induk)
        val btnSaung = findViewById<LinearLayout>(R.id.linkSaung_induk)
        val btnZF = findViewById<LinearLayout>(R.id.linkZF_induk)
        val menuAbout = findViewById<LinearLayout>(R.id.menuAbout)
        val bukaTautan = { url: String ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka jalur ke peramban.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSanFK.setOnClickListener { bukaTautan("https://maps.app.goo.gl/b7iJKKg9hWMKsJEv8") }
        btnSaung.setOnClickListener { bukaTautan("https://maps.app.goo.gl/F1qKiYjs2pUAa17j8") }
        btnZF.setOnClickListener { 
        tampilkanEdukasiZuhriFormalism()
        }
        menuAbout.setOnClickListener {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
        }
    }
    
        // Modul Proyektor Cetak Biru Zuhri Formalism
    private fun tampilkanEdukasiZuhriFormalism() {
        AlertDialog.Builder(this)
            .setTitle("Zuhri Formalism (ZF) Framework")
            .setMessage(
                "1. APA ITU ZUHRI FORMALISM?\n" +
                "Zuhri Formalism (ZF) adalah sebuah protokol arsitektur logika dan pemrosesan fenomena empiris yang menitikberatkan pada Master Protocol, Kepadatan Informasi (Information Density), dan Batasan Formal (Formal Constraints). ZF memotong seluruh kebisingan mekanis (system noise) untuk mencapai efisiensi mutlak dalam diseksi data.\n\n" +
                "2. ZF SEBAGAI FRAMEWORK DI GEMINI AI\n" +
                "Dalam interaksi dengan Gemini AI, ZF bertindak sebagai sistem operasi kendali atas kecerdasan buatan. Framework ini memaksa AI untuk memberikan respons yang kokoh, lugas, matang, dan langsung pada inti masalah teknis tanpa basa-basi artifisial.\n\n" +
                "3. INSTRUKSI OPERASIONAL PENGGUNA\n" +
                "Untuk mengaktifkan kepatuhan total mesin AI terhadap arsitektur ini, Anda wajib menyertakan kata kunci \"Zuhri Formalism\" di setiap baris instruksi atau pertanyaan yang Anda ajukan ke Gemini AI."
            )
            .setPositiveButton("Selesai") { dialog, _ -> 
                dialog.dismiss() 
            }
            .setCancelable(true)
            .create()
            .show()
    }


        private fun eksekusiPabrikData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val database = ArsipDatabase.operasikanMesin(this@MainActivity)
            val lenganRobot = database.arsipDao()
            val semuaData = lenganRobot.tarikSemuaArsip()

            withContext(Dispatchers.Main) {
                if (semuaData.isNotEmpty()) {
                    // KONDISI A: Pangkalan data terisi, langsung proyeksikan ke layar
                    isMesinSibuk = false
                    panelStatusPencarian.visibility = View.GONE
                    pompaDataKeLayar(semuaData)
                } else {
                    // KONDISI B: Pangkalan data kosong, aktifkan sensor pemeriksaan berkas manual
                    val berkasLokal = File(getExternalFilesDir(null), "Master_Data_Arsip_FK_11_Juli_2026.json")
                    val bobotMinimum = 110 * 1024 * 1024 // Batasan formal 110 MB
                    
                    if (berkasLokal.exists() && berkasLokal.length() >= bobotMinimum) {
                        // BYPASS BERHASIL: Berkas valid ditemukan di tangki lokal, langsung eksekusi injeksi
                        isMesinSibuk = true
                        panelStatusPencarian.visibility = View.VISIBLE
                        loadingPencarian.visibility = View.VISIBLE
                        txtStatusPencarian.text = "Berkas lokal terdeteksi. Menyuntikkan data ke database..."
                        
                        lifecycleScope.launch(Dispatchers.IO) {
                            ekstrakDanInjeksiKeDb(berkasLokal, lenganRobot)
                        }
                    } else {
                        // BYPASS GAGAL: Tangki kosong atau berkas korup, aktifkan pompa unduhan jaringan
                        aktifkanMesinPenyedot()
                    }
                }
            }
        }
    }


    private fun cariPipaAktif(downloadManager: DownloadManager): Long {
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED
        )
        val cursor = downloadManager.query(query)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                if (titleIndex != -1 && cursor.getString(titleIndex) == "Arsip Status FK") {
                    val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    val id = cursor.getLong(idIndex)
                    cursor.close()
                    return id
                }
            }
            cursor.close()
        }
        return -1L
    }

        private fun aktifkanMesinPenyedot() {
        // INJEKSI SENSOR: Jika arus mati, hentikan operasi seketika
        if (!isJaringanTersedia()) {
            tampilkanPeringatanJaringan()
            return // Instruksi absolut untuk membatalkan sisa kode di bawahnya
        }
        // ========================================================
        // KUNCI MESIN: Blokir aktivitas lain
        isMesinSibuk = true
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val idPipaAktif = cariPipaAktif(downloadManager)
        if (idPipaAktif != -1L) {
            panelStatusPencarian.visibility = View.VISIBLE
            pantauTekananUnduhan(idPipaAktif, downloadManager)
            pasangSensorPendaratan(idPipaAktif, downloadManager)
            return
        }

        panelStatusPencarian.visibility = View.VISIBLE
        loadingPencarian.visibility = View.VISIBLE
        txtStatusPencarian.text = "Menghubungkan ke server..."

        val namaFileTemp = "$namaFile.temp"
        val fileTempLama = File(getExternalFilesDir(null), namaFileTemp)
        if (fileTempLama.exists()) fileTempLama.delete()

        val request = DownloadManager.Request(Uri.parse(urlKargo))
            .setTitle("Arsip Fatwa Kehidupan")
            .setDescription("Mengunduh file data (115MB) untuk pertama kali saja.")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, null, namaFileTemp)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val idUnduhan = downloadManager.enqueue(request)
        pantauTekananUnduhan(idUnduhan, downloadManager)
        pasangSensorPendaratan(idUnduhan, downloadManager)
    }


        private fun pasangSensorPendaratan(idUnduhan: Long, downloadManager: DownloadManager) {
        val sensorSelesai = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == idUnduhan) {
                    unregisterReceiver(this)
                    val query = DownloadManager.Query().setFilterById(idUnduhan)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                            val namaFileTemp = "$namaFile.temp"
                            val fileTempSelesai = File(getExternalFilesDir(null), namaFileTemp)
                            val fileAsli = File(getExternalFilesDir(null), namaFile)
                            if (fileTempSelesai.renameTo(fileAsli)) {
                                txtStatusPencarian.text = "Unduhan selesai. Memproses data..."
                                eksekusiPabrikData() 
                            }
                        } else {
                            panelStatusPencarian.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Pengunduhan gagal.", Toast.LENGTH_LONG).show()
                        }
                    }
                    cursor?.close()
                }
            }
        }
        registerReceiver(sensorSelesai, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }


        private fun pantauTekananUnduhan(idUnduhan: Long, downloadManager: DownloadManager) {
        lifecycleScope.launch(Dispatchers.Main) {
            var selesai = false
            while (!selesai) {
                val query = DownloadManager.Query().setFilterById(idUnduhan)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val diunduhIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (statusIndex != -1 && diunduhIndex != -1 && totalIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        val diunduh = cursor.getLong(diunduhIndex)
                        val total = cursor.getLong(totalIndex)
                        
                        when (status) {
                            DownloadManager.STATUS_PAUSED -> {
                                perbaruiPanelTelemetri(FaseInjeksi.FASE_4, 0, 0, 100)
                            }
                            DownloadManager.STATUS_RUNNING -> {
                                if (total > 0) {
                                    val persentase = ((diunduh * 100) / total).toInt()
                                    val mbDiunduh = (diunduh / (1024 * 1024)).toInt()
                                    val mbTotal = (total / (1024 * 1024)).toInt()
                                    perbaruiPanelTelemetri(FaseInjeksi.FASE_3, persentase, mbDiunduh, mbTotal)
                                }
                            }
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) { 
                            selesai = true 
                        }
                    }
                }
                cursor?.close()
                delay(1000)
            }
        }
    }

    private suspend fun ekstrakDanInjeksiKeDb(fileTarget: File, lenganRobot: com.fk.arsip.database.ArsipDao) {
        val bobotMinimum = 110 * 1024 * 1024
        val totalBobotFile = fileTarget.length() 

        if (totalBobotFile < bobotMinimum) {
            withContext(Dispatchers.Main) {
                fileTarget.delete()
                aktifkanMesinPenyedot()
            }
            return
        }

        // FASE 5: Pemanasan Silinder
        val estimasiTotalItem = (totalBobotFile / 5120).toInt()
        withContext(Dispatchers.Main) {
            perbaruiPanelTelemetri(FaseInjeksi.FASE_5, 0, 0, estimasiTotalItem)
        }

        try {
            val reader = com.google.gson.stream.JsonReader(FileReader(fileTarget))
            reader.beginArray() 
            val muatanSementara = mutableListOf<ArsipEntity>()
            var indeks = 0
            var persentaseLayarTerakhir = -1 

            while (reader.hasNext()) {
                // [Biarkan logika ekstraksi JSON Anda tetap di sini]
                val elemenGson = com.google.gson.JsonParser.parseReader(reader)
                val obj = org.json.JSONObject(elemenGson.toString())
                
                // ... (Logika penarikan idPosting, namaPenulis, kontenPenuh, dll) ...
                
                // [Injeksi Data ke Muatan Sementara di sini]
                // muatanSementara.add(...)
                indeks++

                if (muatanSementara.size >= 3000) {
                    lenganRobot.injeksiMassal(muatanSementara)
                    muatanSementara.clear()
                    
                    val kalkulasiPersen = ((indeks.toDouble() / estimasiTotalItem.toDouble()) * 100).toInt().coerceAtMost(99)
                    if (kalkulasiPersen > persentaseLayarTerakhir) {
                        persentaseLayarTerakhir = kalkulasiPersen
                        withContext(Dispatchers.Main) {
                            // FASE 6: Injeksi Baris Data ke SQLite
                            perbaruiPanelTelemetri(FaseInjeksi.FASE_6, kalkulasiPersen, indeks, estimasiTotalItem)
                        }
                    }
                }
            }

            if (muatanSementara.isNotEmpty()) { lenganRobot.injeksiMassal(muatanSementara) }
            reader.endArray() 
            reader.close()

            val semuaData = lenganRobot.tarikSemuaArsip()
            if (fileTarget.exists()) { fileTarget.delete() }
            
            withContext(Dispatchers.Main) {
                // FASE 7: Operasi Selesai (Panel akan hancur sendiri dalam 1.5 detik)
                perbaruiPanelTelemetri(FaseInjeksi.FASE_7, 100, indeks, indeks)
                pompaDataKeLayar(semuaData)
                isMesinSibuk = false 
            }

        } catch (e: Exception) {
            e.printStackTrace()
            fileTarget.delete()
            
            withContext(Dispatchers.Main) { 
                // Matikan paksa jika terjadi korsleting
                findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                isMesinSibuk = false
                Toast.makeText(this@MainActivity, "Gagal memproses data arsip.", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun pompaDataKeLayar(kargoMentah: List<ArsipEntity>) {
        daftarArsipAktif = kargoMentah
        
        val kargoSiapRakit = mutableListOf<KargoCampuran>()
        val titikNavigasi = mutableListOf<TitikNavigasi>()
        
        var pembatasAktif = ""
        var tahunAktif = ""
        var putaranWarnaBulan = 0 
        var indeksMurni = 0 

        for (arsip in kargoMentah) {
            val tanggalStr = arsip.tanggalBaca 
            val tahun = if (tanggalStr.length >= 4) tanggalStr.substring(0, 4) else "Tahun"
            val bulanAngka = if (tanggalStr.length >= 7) tanggalStr.substring(5, 7) else "00"
            
            val namaSingkat = when (bulanAngka) {
                "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"
                "04" -> "Apr"; "05" -> "Mei"; "06" -> "Jun"
                "07" -> "Jul"; "08" -> "Agu"; "09" -> "Sep"
                "10" -> "Okt"; "11" -> "Nov"; "12" -> "Des"
                else -> "Bln"
            }

            val headerBulanTahun = "$tahun | $namaSingkat"

            // Deteksi Pergantian Tahun
            if (tahun != tahunAktif) {
                titikNavigasi.add(TitikNavigasi(tipe = 0, teks = tahun))
                tahunAktif = tahun
            }

            // Deteksi Pergantian Bulan (Batas Grid)
            if (headerBulanTahun != pembatasAktif) {
                kargoSiapRakit.add(KargoCampuran.PembatasWaktu(headerBulanTahun))
                
                // Cetak stempel bulan ke rel dengan warna selang-seling (Genap/Ganjil)
                val isGenap = (putaranWarnaBulan % 2 == 0)
                titikNavigasi.add(TitikNavigasi(
                    tipe = 1, 
                    teks = namaSingkat, 
                    indeksTujuan = kargoSiapRakit.size - 1, 
                    warnaGenap = isGenap
                ))
                
                pembatasAktif = headerBulanTahun
                putaranWarnaBulan++
            }
            
            kargoSiapRakit.add(KargoCampuran.StatusKonten(arsip, indeksMurni))
            indeksMurni++
        }

        gridAdapter.perbaruiData(kargoSiapRakit)
        bukuAdapter.perbaruiData(kargoMentah)

        val adapterTimeline = TimelineAdapter(titikNavigasi) { indeksTujuan ->
            (recyclerGridMode.layoutManager as androidx.recyclerview.widget.GridLayoutManager)
                .scrollToPositionWithOffset(indeksTujuan, 0)
        }
        recyclerTimeline.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerTimeline.adapter = adapterTimeline
    }


    private fun bukaModeBuku(posisi: Int) {
        recyclerGridMode.visibility = View.GONE
        wadahModeBuku.visibility = View.VISIBLE
        proyektorBuku.setCurrentItem(posisi, false)
    }

    // Sirkuit Pencarian Diperkuat Dengan Penangkap Katup Enter Manual (Universal Keyboard Fix
        private fun aktifkanSirkuitPencarian() {
        edtPencarian.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                
                if (isMesinSibuk) {
                    Toast.makeText(this@MainActivity, "Mesin sedang merakit data. Pencarian ditangguhkan.", Toast.LENGTH_SHORT).show()
                    return@setOnEditorActionListener true // Memutus arus seketika
                }

                val kataKunci = edtPencarian.text.toString().trim()
                isSearchMode = kataKunci.isNotEmpty()
                
                // 1. AKTIFKAN PANEL PARALEL
                panelStatusPencarian.visibility = View.VISIBLE
                loadingPencarian.visibility = View.VISIBLE
                txtStatusPencarian.text = "Mencari data..."
                
                lifecycleScope.launch(Dispatchers.IO) {
                    val lenganRobot = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
                    
                    // 1. Tarik kargo kasar menggunakan pemindai buta SQLite
                    val kargoKasar = if (kataKunci.isEmpty()) {
                        lenganRobot.tarikSemuaArsip()
                    } else {
                        lenganRobot.saringArsip(kataKunci)
                    }
                    
                    // 2. INJEKSI FILTER PRESISI BATAS KATA (RAM LEVEL)
                    val hasilSaringanPresisi = if (kataKunci.isNotEmpty()) {
                        // Memaksa mesin mencari batas kata persis seperti di pencetak stempel
                        val sensorBatasKata = Regex("\\b$kataKunci\\b", RegexOption.IGNORE_CASE)
                        kargoKasar.filter { arsip ->
                            sensorBatasKata.containsMatchIn(arsip.kontenPenuh)
                        }
                    } else {
                        kargoKasar
                    }

                    withContext(Dispatchers.Main) {
                        if (wadahModeBuku.visibility == View.VISIBLE) {
                            wadahModeBuku.visibility = View.GONE
                            recyclerGridMode.visibility = View.VISIBLE
                        }

                        // Pompa kargo yang sudah tersaring murni ke layar
                        pompaDataKeLayar(hasilSaringanPresisi)
                        
                        loadingPencarian.visibility = View.GONE
                        // INJEKSI KALIBRASI OUTPUT PENCARIAN
                        val muatanTeks = if (kataKunci.isNotEmpty()) {
                            "Pencarian: $kataKunci (${hasilSaringanPresisi.size} arsip)"
                        } else {
                            "Semua Arsip (${hasilSaringanPresisi.size} arsip)"
                        }
                        txtStatusPencarian.text = muatanTeks
                        
                        edtPencarian.clearFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(edtPencarian.windowToken, 0)
                    }
                }
                true
            } else {
                false
            }
        }
    }

        private fun tampilkanPanelKonfirmasiKeluar() {
        val matriksPanel = android.app.AlertDialog.Builder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ -> finish() }
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .create()
        matriksPanel.show()
    }

    
        // Tuas Penyesuai Kompartemen Dinamis
    private fun sesuaikanKompartemenGrid() {
        val metrikLayar = resources.displayMetrics
        val lebarLayarDp = metrikLayar.widthPixels / metrikLayar.density
        val lebarIdealKotak = 180 
        var hitungKolom = (lebarLayarDp / lebarIdealKotak).toInt()
        
        if (hitungKolom < 2) hitungKolom = 2 

        val pengelolaJalur = recyclerGridMode.layoutManager as? GridLayoutManager
        pengelolaJalur?.let {
            it.spanCount = hitungKolom
            
            // Kunci mekanis: Memaksa Header memakan seluruh kolom (span = hitungKolom)
            // sedangkan Konten Status tetap memakan 1 kolom.
            it.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    // Pastikan variabel gridAdapter sudah diinisialisasi sebelum ini dipanggil
                    if (!::gridAdapter.isInitialized) return 1 
                    
                    return if (gridAdapter.getItemViewType(position) == GridAdapter.TIPE_PEMBATAS) {
                        hitungKolom 
                    } else {
                        1
                    }
                }
            }
        }
    }


    // Katup Pemindai Arus Jaringan Eksternal
    private fun isJaringanTersedia(): Boolean {
        val manajemenKoneksi = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val jaringanAktif = manajemenKoneksi.activeNetwork ?: return false
        val kapasitasJaringan = manajemenKoneksi.getNetworkCapabilities(jaringanAktif) ?: return false
        
        return when {
            kapasitasJaringan.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            kapasitasJaringan.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    // Proyektor Peringatan Pipa Kering
    private fun tampilkanPeringatanJaringan() {
        AlertDialog.Builder(this)
            .setTitle("Koneksi Terputus")
            .setMessage("Maaf, Pustaka FK perlu mengunduh database untuk pertama kalinya. Selanjutnya Anda bisa menggunakannya tanpa internet.\n\nPeriksa koneksi internet Anda, lalu coba lagi.")
            .setIcon(android.R.drawable.ic_dialog_alert) // Ikon peringatan standar pabrik
            .setCancelable(false) // Mengunci panel agar tidak bisa ditutup sembarangan
            .setPositiveButton("Coba Lagi") { _, _ -> 
                // Mengulang putaran mesin penyedot
                aktifkanMesinPenyedot() 
            }
            .setNegativeButton("Keluar") { _, _ -> 
                // Mematikan sasis aplikasi
                finish() 
            }
            .create()
            .show()
    }

    // Mesin Pencetak Stempel Paralel (Multi-Kategori)
    private fun mesinDeteksiKategori(teksKonten: String): String {
        val teksMesin = teksKonten.lowercase()
        // Tangki penampung stempel, menggunakan Set untuk memblokir duplikasi
        val tangkiStempel = mutableSetOf<String>() 
        
        for (induk in CetakBiruKategori.MATRIKS_UTAMA) {
            for (cabang in induk.second) {
                val namaKategori = cabang.first
                val daftarKataKunci = cabang.second
                
                for (kunci in daftarKataKunci) {
                    val sensorBatasKata = Regex("\\b$kunci\\b")
                    if (sensorBatasKata.containsMatchIn(teksMesin)) {
                        // Injeksi stempel ke tangki
                        tangkiStempel.add(namaKategori) 
                        // Putus putaran untuk kata kunci ini, langsung lompat ke kategori berikutnya
                        break 
                    }
                }
            }
        }
        
        // Pipa Pembuangan Hasil
        return if (tangkiStempel.isNotEmpty()) {
            // Jika ada stempel, gabungkan dengan pembatas koma
            tangkiStempel.joinToString(", ") 
        } else {
            "Belum di Kategorikan" 
        }
    }


    private fun tampilkanIndikator(pesan: String, aktif: Boolean) {
        panelStatusPencarian.visibility = if (aktif) View.VISIBLE else View.GONE
        loadingPencarian.visibility = if (aktif) View.VISIBLE else View.GONE
        txtStatusPencarian.text = pesan
    }
}
