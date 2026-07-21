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
import android.widget.FrameLayout
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
import android.os.Handler
import android.os.Looper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.WorkInfo

data class TitikNavigasi(
    val tipe: Int, 
    val teks: String,
    val indeksTujuan: Int = -1,
    val warnaGenap: Boolean = false
)

enum class FaseInjeksi(val pesan: String, val idGambar: Int) {
    FASE_1("Memulai aplikasi pertama kali...", R.drawable.img_1),
    FASE_2("Menghubungkan ke server...", R.drawable.img_2),
    FASE_3("Mengunduh data status...", R.drawable.img_3),
    FASE_4("Menunggu jaringan stabil...", R.drawable.img_4), 
    FASE_5("Mengelas blok data ke memori...", R.drawable.img_5),
    FASE_6("Injeksi baris data ke SQLite...", R.drawable.img_6),
    FASE_7("Proses selesai.", R.drawable.img_7)
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
    private lateinit var recyclerTimeline: RecyclerView
    private lateinit var kontainerJalurKanan: FrameLayout

    private var daftarArsipAktif: List<ArsipEntity> = listOf()
    private var isSearchMode = false 
    private var isMesinSibuk = false
    private var modeKategoriAktif = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navViewCustom = findViewById(R.id.navViewCustom)
        
        findViewById<android.widget.ImageView>(R.id.btnMenuDrawer).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        recyclerGridMode = findViewById(R.id.recyclerGridMode)
        wadahModeBuku = findViewById(R.id.wadahModeBuku)
        proyektorBuku = findViewById(R.id.proyektorBuku)
        edtPencarian = findViewById(R.id.edtPencarian)
        panelStatusPencarian = findViewById(R.id.panelStatusPencarian)
        loadingPencarian = findViewById(R.id.loadingPencarian)
        txtStatusPencarian = findViewById(R.id.txtStatusPencarian)
        recyclerTimeline = findViewById(R.id.recyclerTimeline)
        kontainerJalurKanan = findViewById<FrameLayout>(R.id.kontainerJalurKanan)

        recyclerGridMode.layoutManager = GridLayoutManager(this, 2)
        sesuaikanKompartemenGrid() 
        proyektorBuku.setPageTransformer(null)

        gridAdapter = GridAdapter(emptyList()) { posisi -> bukaModeBuku(posisi) }
        
        val layoutManagerGrid = GridLayoutManager(this, 2)
        layoutManagerGrid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (gridAdapter.getItemViewType(position) == GridAdapter.TIPE_PEMBATAS) 2 else 1
            }
        }
        recyclerGridMode.layoutManager = layoutManagerGrid
        recyclerGridMode.adapter = gridAdapter

        bukuAdapter = BukuAdapter(daftarArsipAktif)
        proyektorBuku.adapter = bukuAdapter

        proyektorBuku.offscreenPageLimit = 1
        
        val mesinProyeksi = proyektorBuku.getChildAt(0) as? RecyclerView
        mesinProyeksi?.overScrollMode = View.OVER_SCROLL_NEVER

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } 
               else if (wadahModeBuku.visibility == View.VISIBLE) {
    wadahModeBuku.visibility = View.GONE
    
    // PEMULIHAN SEREMPAK KEDUA JALUR
    recyclerGridMode.visibility = View.VISIBLE
    kontainerJalurKanan.visibility = View.VISIBLE // Jalur kanan tegak kembali selebar 45dp
    recyclerTimeline.visibility = View.VISIBLE
    
    if (daftarArsipAktif.size > 5000) {
        bukuAdapter.perbaruiData(emptyList()) 
    }
}


                else if (isSearchMode || edtPencarian.text.toString().isNotEmpty() || modeKategoriAktif) {
                    isSearchMode = false
                    modeKategoriAktif = false 
                    
                    edtPencarian.text.clear()
                    edtPencarian.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(edtPencarian.windowToken, 0)
    // --- INJEKSI KATUP TIMELINE: BUKA PAKSA SEBELUM MEMOMPA DATA ---
    wadahModeBuku.visibility = View.GONE
    recyclerGridMode.visibility = View.VISIBLE
    kontainerJalurKanan.visibility = View.VISIBLE
    recyclerTimeline.visibility = View.VISIBLE
    // ---------------------------------------------------------------
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
    
    private fun perbaruiPanelTelemetri(fase: FaseInjeksi, persentase: Int, volumeSelesai: Int, volumeTotal: Int) {
    val panelUtama = findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama)
    val teksStatus = findViewById<TextView>(R.id.teksStatusFase)
    val indikatorVisual = findViewById<ImageView>(R.id.indikatorVisualMesin)
    val lingkarProgres = findViewById<ProgressBar>(R.id.lingkarPersentaseUtama)
    val teksPersen = findViewById<TextView>(R.id.teksPersentaseSentral)
    val teksTelemetri = findViewById<TextView>(R.id.teksTelemetriData)
    if (fase == FaseInjeksi.FASE_1 || fase == FaseInjeksi.FASE_7) {
        // Matikan total pada Fase 1 dan Fase 7 agar tidak ada sisa visual mengganggu
        lingkarProgres.visibility = View.GONE
        teksPersen.visibility = View.GONE
    } else {
        // Hidupkan hanya untuk fase unduhan dan injeksi tengah
        lingkarProgres.visibility = View.VISIBLE
        teksPersen.visibility = View.VISIBLE
    }
    teksStatus.text = fase.pesan
    indikatorVisual.setImageResource(fase.idGambar)

    // KALIBRASI INDIKATOR VISUAL BERDASARKAN FASE
    when (fase) {
        FaseInjeksi.FASE_1, FaseInjeksi.FASE_2, FaseInjeksi.FASE_4 -> {
            // Aktifkan mode animasi berputar kontinu karena angka kepastian belum ada
            lingkarProgres.isIndeterminate = true
            teksPersen.text = "---"
            teksTelemetri.text = "Sistem sedang menginisialisasi modul internal..."
        }
        else -> {
            // Kembalikan ke mode perhitungan angka pasti untuk fase unduhan dan injeksi
            lingkarProgres.isIndeterminate = false
            lingkarProgres.progress = persentase
            teksPersen.text = "$persentase%"
            
            if (fase != FaseInjeksi.FASE_7) {
                teksTelemetri.text = "Arsip Status Digital Fatwa Kehidupan\n $volumeSelesai / $volumeTotal baris\nSistem sedang bekerja..."
            } else {
                teksTelemetri.text = "Seluruh blok data berhasil dilas ke dalam memori SQLite."
                Handler(Looper.getMainLooper()).postDelayed({
                    panelUtama.visibility = View.GONE
                }, 1500)
            }
        }
    }
}


    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
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

            if (daftarCabang.size == 1 && daftarCabang[0].first == namaInduk) {
                barisInduk.setOnClickListener { eksekusiSaringanKategori(namaInduk) }
                wadah.addView(barisInduk)
            } else {
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
    
    val muatanTeks = "$labelKategori (${hasilSaringanAkhir.size} arsip)"
    tampilkanIndikator(muatanTeks, false)
    
    panelStatusPencarian.visibility = View.VISIBLE 
    wadahModeBuku.visibility = View.GONE
    
    // --- INJEKSI KATUP TIMELINE: BUKA PAKSA ---
    recyclerGridMode.visibility = View.VISIBLE
    kontainerJalurKanan.visibility = View.VISIBLE
    recyclerTimeline.visibility = View.VISIBLE
    // ------------------------------------------
    
    pompaDataKeLayar(hasilSaringanAkhir) 
    drawerLayout.closeDrawers()
}

        }
    }

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
        btnZF.setOnClickListener { tampilkanEdukasiZuhriFormalism() }
        menuAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun tampilkanEdukasiZuhriFormalism() {
        AlertDialog.Builder(this)
            .setTitle("Zuhri Formalism (ZF) Framework")
            .setMessage("1. APA ITU ZUHRI FORMALISM?\nZuhri Formalism (ZF) adalah sebuah protokol arsitektur logika...")
            .setPositiveButton("Selesai") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .create()
            .show()
    }

    private fun eksekusiPabrikData() {
    lifecycleScope.launch(Dispatchers.IO) {
        delay(1500)
        val database = ArsipDatabase.operasikanMesin(this@MainActivity)
        val lenganRobot = database.arsipDao()
        
        val jumlahBarisData = lenganRobot.hitungTotalArsip() 
        val berkasLokal = File(getExternalFilesDir(null), "Master_Data_Arsip_FK_11_Juli_2026.json")
        val bobotMinimum = 110 * 1024 * 1024 

        // KUNCI ABSOLUT: Hanya lolos jika data SQLite mendekati atau sama dengan total array riil (17934)
        val batasAmanAbsolut = 17900 

        if (jumlahBarisData >= batasAmanAbsolut) {
            val semuaData = lenganRobot.tarikSemuaArsip()
            withContext(Dispatchers.Main) {
                findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                isMesinSibuk = false
                panelStatusPencarian.visibility = View.GONE
                pompaDataKeLayar(semuaData)
                
                if (berkasLokal.exists()) { berkasLokal.delete() }
            }
            return@launch
        }

        // KONDISI B: Jika data di bawah batas aman, tetapi file JSON mentah masih ada
        if (berkasLokal.exists() && berkasLokal.length() >= bobotMinimum) {
            withContext(Dispatchers.Main) {
                isMesinSibuk = true
                findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.VISIBLE
                perbaruiPanelTelemetri(FaseInjeksi.FASE_5, 0, 0, 0)
                jalankanMesinInjeksiOtonom(berkasLokal.absolutePath)
            }
            return@launch
        }

        // KONDISI C: Jika data di bawah batas aman DAN file JSON tidak ada (berarti unduhan sebelumnya korup)
        withContext(Dispatchers.Main) {
            // Hapus data setengah matang (9000 item) agar tidak menumpuk saat diunduh ulang
            lifecycleScope.launch(Dispatchers.IO) { lenganRobot.kurasTangkiKotor() }
            
            findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.VISIBLE
            perbaruiPanelTelemetri(FaseInjeksi.FASE_1, 0, 0, 0)
            delay(1000)
            aktifkanMesinPenyedot()
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
                if (titleIndex != -1 && cursor.getString(titleIndex) == "Arsip Fatwa Kehidupan") {
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
        isMesinSibuk = true
    findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.VISIBLE

    if (!isJaringanTersedia()) {
        // Alihkan langsung ke FASE_4 tanpa memunculkan dialog pemblokir mekanis
        perbaruiPanelTelemetri(FaseInjeksi.FASE_4, 0, 0, 0)
        
        lifecycleScope.launch(Dispatchers.IO) {
            while (!isJaringanTersedia()) {
                delay(3000) // Cek berkala setiap 3 detik hingga modem aktif
            }
            withContext(Dispatchers.Main) {
                // Sinyal terdeteksi, panggil ulang mesin penyedot untuk inisialisasi download
                aktifkanMesinPenyedot()
            }
        }
        return
    }
    
    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val idPipaAktif = cariPipaAktif(downloadManager)
        
        findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.VISIBLE
        
        if (idPipaAktif != -1L) {
            perbaruiPanelTelemetri(FaseInjeksi.FASE_3, 0, 0, 100)
            pantauTekananUnduhan(idPipaAktif, downloadManager)
            pasangSensorPendaratan(idPipaAktif, downloadManager)
            return
        }

        perbaruiPanelTelemetri(FaseInjeksi.FASE_2, 0, 0, 100)

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
                                isMesinSibuk = true
                                jalankanMesinInjeksiOtonom(fileAsli.absolutePath)
                            } else {
                                eksekusiPabrikData()
                            }
                        } else {
                            findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                            isMesinSibuk = false
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
    lifecycleScope.launch(Dispatchers.IO) {
        var isMengunduh = true
        var beradaDiFaseGagalJaringan = false

        while (isMengunduh) {
            // SENSOR CEK SINYAL
            if (!isJaringanTersedia()) {
                beradaDiFaseGagalJaringan = true
                withContext(Dispatchers.Main) {
                    // Pindahkan indikator visual ke FASE_4 secara otomatis
                    perbaruiPanelTelemetri(FaseInjeksi.FASE_4, 0, 0, 0)
                }
                // Tahan perulangan selama 3 detik sebelum memeriksa kekuatan sinyal kembali
                delay(3000)
                continue
            }

            // Jika sinyal kembali pulih setelah mati
            if (beradaDiFaseGagalJaringan) {
                beradaDiFaseGagalJaringan = false
                withContext(Dispatchers.Main) {
                    // Kembalikan visualisasi ke FASE_3 untuk melanjutkan pembacaan byte kargo
                    perbaruiPanelTelemetri(FaseInjeksi.FASE_3, 0, 0, 0)
                }
            }

            val query = DownloadManager.Query().setFilterById(idUnduhan)
            val cursor = downloadManager.query(query)
            
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    isMengunduh = false 
                } else if (status == DownloadManager.STATUS_FAILED) {
                    // Jika kegagalan sistem bersifat mutlak (bukan sekadar kehilangan sinyal sementara)
                    isMengunduh = false
                    withContext(Dispatchers.Main) {
                        findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                        isMesinSibuk = false
                        Toast.makeText(this@MainActivity, "Kargo rusak fatal. Mengulang proses dari awal...", Toast.LENGTH_SHORT).show()
                        eksekusiPabrikData()
                    }
                } else {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                        val downloaded = cursor.getLong(bytesDownloadedIndex)
                        val total = cursor.getLong(bytesTotalIndex)
                        if (total > 0) {
                            val persentase = ((downloaded * 100L) / total).toInt()
                            withContext(Dispatchers.Main) {
                                perbaruiPanelTelemetri(FaseInjeksi.FASE_3, persentase, 0, 0)
                            }
                        }
                    }
                }
            } else {
                isMengunduh = false
                withContext(Dispatchers.Main) {
                    findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                    isMesinSibuk = false
                }
            }
            cursor?.close()
            delay(1000) // Frekuensi pembacaan tangki unduhan setiap 1 detik
        }
    }
}


    // SIRKUIT BARU: Antena Pemantau Sinyal Kerja WorkManager Latar Belakang
    private fun jalankanMesinInjeksiOtonom(jalurFileJson: String) {
        val kargo = workDataOf("URI_JSON_KARGO" to jalurFileJson)
        val instruksiKerja = OneTimeWorkRequestBuilder<MesinInjeksiWorker>().setInputData(kargo).build()
        val manajerKerja = WorkManager.getInstance(applicationContext)
        
        manajerKerja.enqueue(instruksiKerja)

        manajerKerja.getWorkInfoByIdLiveData(instruksiKerja.id).observe(this) { informasiKerja ->
            if (informasiKerja != null) {
                val faseAktif = informasiKerja.progress.getInt("FASE", 0)
                val persentase = informasiKerja.progress.getInt("PERSENTASE", 0)
                val indeks = informasiKerja.progress.getInt("INDEKS", 0)
                val total = informasiKerja.progress.getInt("TOTAL", 0)
                
                when (informasiKerja.state) {
                    WorkInfo.State.RUNNING -> {
                        if (faseAktif > 0) {
                            val faseEnum = if (faseAktif == 6) FaseInjeksi.FASE_6 else FaseInjeksi.FASE_5
                            perbaruiPanelTelemetri(faseEnum, persentase, indeks, total)
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        perbaruiPanelTelemetri(FaseInjeksi.FASE_7, 100, indeks, indeks)
                        lifecycleScope.launch(Dispatchers.Main) {
                            delay(1500)
                            val database = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
                            val semuaData = withContext(Dispatchers.IO) { database.tarikSemuaArsip() }
                            pompaDataKeLayar(semuaData)
                            isMesinSibuk = false
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val kodeGagal = informasiKerja.outputData.getString("KODE_GAGAL") ?: "Unknown"
                        findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                        isMesinSibuk = false
                        if (kodeGagal == "BOBOT_KURANG") {
                            aktifkanMesinPenyedot() 
                        } else {
                            Toast.makeText(this@MainActivity, "Gagal memproses data arsip.", Toast.LENGTH_LONG).show()
                        }
                    }
                    else -> {}
                }
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

            if (tahun != tahunAktif) {
                titikNavigasi.add(TitikNavigasi(tipe = 0, teks = tahun))
                tahunAktif = tahun
            }

            if (headerBulanTahun != pembatasAktif) {
                kargoSiapRakit.add(KargoCampuran.PembatasWaktu(headerBulanTahun))
                val isGenap = (putaranWarnaBulan % 2 == 0)
                titikNavigasi.add(TitikNavigasi(tipe = 1, teks = namaSingkat, indeksTujuan = kargoSiapRakit.size - 1, warnaGenap = isGenap))
                pembatasAktif = headerBulanTahun
                putaranWarnaBulan++
            }
            
            kargoSiapRakit.add(KargoCampuran.StatusKonten(arsip, indeksMurni))
            indeksMurni++
        }
        gridAdapter.perbaruiData(kargoSiapRakit)
        if (kargoMentah.size > 5000) {
            bukuAdapter.perbaruiData(emptyList())
        } else {
            bukuAdapter.perbaruiData(kargoMentah)
        }
        
        val adapterTimeline = TimelineAdapter(titikNavigasi) { indeksTujuan ->
            recyclerGridMode.post {
                val manager = recyclerGridMode.layoutManager as? GridLayoutManager
                if (indeksTujuan in 0 until gridAdapter.itemCount) {
                    manager?.scrollToPositionWithOffset(indeksTujuan, 0)
                }
            }
        }

        recyclerTimeline.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerTimeline.adapter = adapterTimeline
    }
    
    private fun bukaModeBuku(posisi: Int) {
    if (isMesinSibuk) return
    isMesinSibuk = true 
    
    lifecycleScope.launch(Dispatchers.Main) {
        // 1. Matikan seluruh instrumen navigasi luar untuk melegakan beban RAM
        recyclerTimeline.visibility = View.GONE
        kontainerJalurKanan.visibility = View.GONE 
        recyclerGridMode.visibility = View.GONE
        wadahModeBuku.visibility = View.VISIBLE

        // 2. Periksa apakah tangki proyektor kosong
        if (bukuAdapter.itemCount == 0 && daftarArsipAktif.isNotEmpty()) {
            tampilkanIndikator("Menyiapkan proyektor buku...", true)
            
            // Berikan mesin jeda 100 milidetik agar OS Android selesai 
            // menggambar perpindahan sasis layar sebelum menembakkan 17rb data
            delay(100) 
            
            bukuAdapter.perbaruiData(daftarArsipAktif) 
            tampilkanIndikator("", false)
        }
        
        // 3. Eksekusi perpindahan roda gigi setelah adaptor siap menerima beban
        proyektorBuku.post {
            proyektorBuku.setCurrentItem(posisi, false)
            isMesinSibuk = false 
        }
    }
}


    private fun aktifkanSirkuitPencarian() {
        edtPencarian.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                
                if (isMesinSibuk) {
                    Toast.makeText(this@MainActivity, "Mesin sedang merakit data. Pencarian ditangguhkan.", Toast.LENGTH_SHORT).show()
                    return@setOnEditorActionListener true 
                }

                val kataKunci = edtPencarian.text.toString().trim()
                isSearchMode = kataKunci.isNotEmpty()
                
                panelStatusPencarian.visibility = View.VISIBLE
                loadingPencarian.visibility = View.VISIBLE
                txtStatusPencarian.text = "Mencari data..."
                
                lifecycleScope.launch(Dispatchers.IO) {
                    val lenganRobot = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
                    val kargoKasar = if (kataKunci.isEmpty()) {
                        lenganRobot.tarikSemuaArsip()
                    } else {
                        lenganRobot.saringArsip(kataKunci)
                    }
                    
                    val hasilSaringanPresisi = if (kataKunci.isNotEmpty()) {
                        val sensorBatasKata = Regex("\\b$kataKunci\\b", RegexOption.IGNORE_CASE)
                        kargoKasar.filter { arsip -> sensorBatasKata.containsMatchIn(arsip.kontenPenuh) }
                    } else {
                        kargoKasar
                    }

                    withContext(Dispatchers.Main) {
                        if (wadahModeBuku.visibility == View.VISIBLE) {
                            wadahModeBuku.visibility = View.GONE
                            recyclerGridMode.visibility = View.VISIBLE
                        }

                        pompaDataKeLayar(hasilSaringanPresisi)
                        loadingPencarian.visibility = View.GONE
                        
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
        android.app.AlertDialog.Builder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ -> finish() }
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun sesuaikanKompartemenGrid() {
        val metrikLayar = resources.displayMetrics
        val lebarLayarDp = metrikLayar.widthPixels / metrikLayar.density
        val lebarIdealKotak = 180 
        var hitungKolom = (lebarLayarDp / lebarIdealKotak).toInt()
        if (hitungKolom < 2) hitungKolom = 2 

        val pengelolaJalur = recyclerGridMode.layoutManager as? GridLayoutManager
        pengelolaJalur?.let {
            it.spanCount = hitungKolom
            it.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                init { isSpanIndexCacheEnabled = true }
                override fun getSpanSize(position: Int): Int {
                    return if (gridAdapter.getItemViewType(position) == GridAdapter.TIPE_PEMBATAS) hitungKolom else 1
                }
            }
        }
    }

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



    private fun tampilkanIndikator(pesan: String, aktif: Boolean) {
        panelStatusPencarian.visibility = if (aktif) View.VISIBLE else View.GONE
        loadingPencarian.visibility = if (aktif) View.VISIBLE else View.GONE
        txtStatusPencarian.text = pesan
    }
}
