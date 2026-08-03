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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
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
import kotlinx.coroutines.Job
import android.database.Cursor
import java.io.File
import android.os.Handler
import android.os.Looper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.WorkInfo
import androidx.core.view.GravityCompat

data class TitikNavigasi(
    val tipe: Int, 
    val teks: String,
    val indeksTujuan: Int = -1,
    val warnaGenap: Boolean = false // Setel nilai default di sini
)

object KoleksiNasehat {
    val DAFTAR_TEKS = listOf(
        "Mencari ilmu adalah proses menata pemahaman, sebagaimana sistem ini sedang menata data untuk Anda.",
        "Proses ini hanya dilakukan 1 kali di awal agar aplikasi bisa diakses secara cepat dan offline tanpa internet.",
        "Kesabaran dalam menunggu adalah bagian dari adab menuntut ilmu.",
        "Setiap data yang tersusun rapi akan memudahkan Anda menemukan jawaban dengan cepat.",
        "Mohon tidak menutup aplikasi saat penyusunan database berlangsung agar data tidak rusak."
    )
}

enum class FaseInjeksi(val kode: Int, val deskripsiMekanis: String) {
    FASE_1(1, "Mempersiapkan Jalur Data"),
    FASE_2(2, "Menghubungkan ke Server Data"),
    FASE_3(3, "Mengunduh Paket Data"),
    FASE_4(4, "Koneksi Terputus"),
    FASE_5(5, "Membongkar & Menyusun Data"),
    FASE_6(6, "Checking Keutuhan Data"),
    FASE_7(7, "Data Siap Digunakan");

    companion object {
        fun dariKode(kode: Int): FaseInjeksi {
            return values().find { it.kode == kode } ?: FASE_1
        }
    }
}

class MainActivity : AppCompatActivity() {

private var jobRotasiNasehat: Job? = null
// Variable global/pribadi untuk menyimpan status kalkulasi kecepatan sebelumnya
private var waktuTerakhirMs: Long = 0L
private var byteTerakhirDownloaded: Long = 0L
private var kecepatanEmaBytesPerSec: Double = 0.0 // Filter perata gerakan
    private val namaFile = "Master_Data_Arsip_FK_11_Juli_2026.json"
    private val urlKargo = "https://github.com/suyonoion/Pustaka-FK/releases/download/v1.0.0/Master_Data_Arsip_FK_11_Juli_2026.json"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navViewCustom: LinearLayout
    private lateinit var recyclerGridMode: RecyclerView
    private lateinit var wadahModeBuku: RelativeLayout
    private lateinit var proyektorBuku: ViewPager2
    private lateinit var edtPencarian: SearchView
    private lateinit var panelStatusPencarian: CardView
    private lateinit var loadingPencarian: ProgressBar
    private lateinit var txtStatusPencarian: TextView
    private lateinit var gridAdapter: GridAdapter
    private lateinit var bukuAdapter: BukuAdapter
    private lateinit var recyclerTimeline: RecyclerView
    private lateinit var kontainerJalurKanan: FrameLayout
    private lateinit var btnFilterSort: ImageButton

    private var daftarArsipAktif: List<ArsipEntity> = listOf()
    private var titikNolJendela = 0
    private val radiusMuatan = 50 

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
        edtPencarian = findViewById<SearchView>(R.id.edtPencarian)
        btnFilterSort = findViewById<ImageButton>(R.id.btnFilterSort)
        btnFilterSort.setOnClickListener {
        bukaKatupDialogFilter()
        }
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
        proyektorBuku.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            
            val indeksAbsolut = titikNolJendela + position
            val jarakKritis = 10
            val totalFragmenAktif = proyektorBuku.adapter?.itemCount ?: 0
            
            if (position <= jarakKritis && titikNolJendela > 0) {
                geserSabukProyektor(indeksAbsolut)
            } 
            else if (position >= totalFragmenAktif - jarakKritis && indeksAbsolut < daftarArsipAktif.size - 1) {
                geserSabukProyektor(indeksAbsolut)
            }
        }
    })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } 
                // Di dalam handleOnBackPressed() pada blok wadahModeBuku.visibility == View.VISIBLE:
                else if (wadahModeBuku.visibility == View.VISIBLE) {
    wadahModeBuku.visibility = View.GONE
    
    // PEMULIHAN JALUR VISUAL
    recyclerGridMode.visibility = View.VISIBLE
    kontainerJalurKanan.visibility = View.VISIBLE 
    recyclerTimeline.visibility = View.VISIBLE
    
    // FORMAT TELEMETRI KATEGORI SAAT KEMBALI
    panelStatusPencarian.visibility = View.VISIBLE
    val totalVolume = daftarArsipAktif.size
    val labelKategori = if (modeKategoriAktif && daftarArsipAktif.isNotEmpty()) {
        daftarArsipAktif.first().kategori
    } else {
        "Semua Status"
    }
    
    txtStatusPencarian.text = "$labelKategori • $totalVolume Status"
    
    if (daftarArsipAktif.size > 5000) {
        bukuAdapter.perbaruiData(emptyList()) 
    }
}


                else if (isSearchMode || edtPencarian.query.toString().isNotEmpty() || modeKategoriAktif) {
                    isSearchMode = false
                    modeKategoriAktif = false 
                    
                    edtPencarian.setQuery("", false)
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
                            
                            // PENYEDERHANAAN: Menggunakan fungsi terpusat
                            muatDataAwalKeSasis(semuaData)
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
    
    private fun bukaKatupDialogFilter() {
    if (isMesinSibuk) {
        Toast.makeText(this, "Mesin sedang bekerja, tahan instruksi.", Toast.LENGTH_SHORT).show()
        return
    }

    val mesinDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val panelDialog = layoutInflater.inflate(R.layout.dialog_filter, null)
    mesinDialog.setContentView(panelDialog)

    val rgUrutan = panelDialog.findViewById<android.widget.RadioGroup>(R.id.rgUrutan)
    val rbTerlama = panelDialog.findViewById<android.widget.RadioButton>(R.id.rbTerlama)
    val spinnerKategori = panelDialog.findViewById<android.widget.Spinner>(R.id.spinnerKategori)
    val btnTerapkan = panelDialog.findViewById<android.widget.Button>(R.id.btnTerapkanFilter)
    val btnReset = panelDialog.findViewById<android.widget.Button>(R.id.btnResetFilter)

    // Tarik daftar kategori dinamis dari Cetak Biru untuk menghindari Hardcoding murni
    val daftarKategoriBaku = mutableListOf("Semua Kategori")
    CetakBiruKategori.MATRIKS_UTAMA.forEach { induk ->
        if (induk.second.size == 1 && induk.second[0].first == induk.first) {
            daftarKategoriBaku.add(induk.first)
        } else {
            induk.second.forEach { anak -> daftarKategoriBaku.add(anak.first) }
        }
    }

    val adapterSpinner = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, daftarKategoriBaku)
    spinnerKategori.adapter = adapterSpinner

    btnTerapkan.setOnClickListener {
        val urutkanTerlama = rbTerlama.isChecked
        val kategoriTerpilih = spinnerKategori.selectedItem.toString()
        mesinDialog.dismiss()
        eksekusiSaringanKombinasi(kategoriTerpilih, urutkanTerlama)
    }

    btnReset.setOnClickListener {
        mesinDialog.dismiss()
        eksekusiSaringanKombinasi("Semua Kategori", false)
    }

    mesinDialog.show()
}

private fun eksekusiSaringanKombinasi(kategori: String, urutTerlama: Boolean) {
    if (isMesinSibuk) return
    isMesinSibuk = true
    
    tampilkanIndikator("Mereset jalur dan menyaring kargo...", true)

    lifecycleScope.launch(Dispatchers.IO) {
        val lenganRobot = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
        
        // Pemilah Arah Kueri
        val kargoSaringan = if (kategori == "Semua Kategori") {
            if (urutTerlama) lenganRobot.tarikSemuaArsipTerlama() else lenganRobot.tarikSemuaArsip()
        } else {
            if (urutTerlama) lenganRobot.saringBerdasarkanKategoriTerlama(kategori) else lenganRobot.saringBerdasarkanKolomKategori(kategori)
        }

        withContext(Dispatchers.Main) {
            isSearchMode = false
            modeKategoriAktif = (kategori != "Semua Kategori")
            
            // Bersihkan tangki pencarian
            edtPencarian.setQuery("", false)
            edtPencarian.clearFocus()

            // Injeksi ulang katup antarmuka ke mode default
            wadahModeBuku.visibility = View.GONE
            recyclerGridMode.visibility = View.VISIBLE
            kontainerJalurKanan.visibility = View.VISIBLE
            recyclerTimeline.visibility = View.VISIBLE

            val indikatorTeks = if (kategori == "Semua Kategori") {
                "Semua Arsip (${kargoSaringan.size} status)"
            } else {
                "$kategori (${kargoSaringan.size} status)"
            }
            
            tampilkanIndikator(indikatorTeks, false)
            panelStatusPencarian.visibility = View.VISIBLE

            // Dorong muatan baru ke rantai grid dan buku
            pompaDataKeLayar(kargoSaringan)
            isMesinSibuk = false
        }
    }
}

    
// 3. FUNGSI UNTUK MENJALANKAN ROTASI TEKS SECARA OTOMATIS
private fun jalankanRotasiNasehat(vTeksNasehat: TextView) {
    jobRotasiNasehat?.cancel() // Hentikan rotasi lama jika ada
    jobRotasiNasehat = lifecycleScope.launch {
        var indeks = 0
        while (isMesinSibuk) {
            vTeksNasehat.text = KoleksiNasehat.DAFTAR_TEKS[indeks]
            indeks = (indeks + 1) % KoleksiNasehat.DAFTAR_TEKS.size
            delay(6000) // Berganti setiap 6 detik
        }
    }
}

private fun hentikanRotasiNasehat() {
    jobRotasiNasehat?.cancel()
    jobRotasiNasehat = null
}

// 4. PERBAIKAN FUNGSI TELEMETRI (Menampilkan Bahasa Ramah & Rotasi Nasehat)
private fun perbaruiPanelTelemetri(
    fase: FaseInjeksi, 
    itemTerproses: Int = 0, 
    totalItem: Int = 0, 
    persenProgress: Int = 0
) {
    val panelUtama = findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama)
    val vTeksStatus = findViewById<TextView>(R.id.teksStatusInisialisasi)
    val vTeksDetail = findViewById<TextView>(R.id.teksDetailProgress)
    val vTeksNasehat = findViewById<TextView>(R.id.teksNasehatInisialisasi) // ID TextView untuk nasehat
    val progressBar = findViewById<ProgressBar>(R.id.progressBarInisialisasi)

    panelUtama.visibility = View.VISIBLE

    // Mulai rotasi nasehat jika mesin sedang sibuk
    if (jobRotasiNasehat == null || !jobRotasiNasehat!!.isActive) {
        jalankanRotasiNasehat(vTeksNasehat)
    }

    when (fase) {
        FaseInjeksi.FASE_1 -> {
            vTeksStatus.text = "Mempersiapkan Jalur Data..."
            vTeksDetail.text = "Pemeriksaan awal sistem"
            progressBar.isIndeterminate = true
        }
        FaseInjeksi.FASE_2 -> {
            vTeksStatus.text = "Menghubungkan ke Server Data..."
            vTeksDetail.text = "Menginisialisasi jalur kargo..."
            progressBar.isIndeterminate = true
        }
        FaseInjeksi.FASE_3 -> {
            vTeksStatus.text = "Mengunduh Paket Data..."
            vTeksDetail.text = "Progres: $persenProgress%"
            progressBar.isIndeterminate = false
            progressBar.progress = persenProgress
        }
        FaseInjeksi.FASE_4 -> {
            vTeksStatus.text = "Koneksi Terputus"
            vTeksDetail.text = "Menunggu sinyal internet kembali..."
            progressBar.isIndeterminate = true
        }
        FaseInjeksi.FASE_5 -> {
            vTeksStatus.text = "Membongkar & Menyusun Data..."
            if (totalItem > 0) {
                vTeksDetail.text = "Menata $itemTerproses dari $totalItem arsip"
                progressBar.isIndeterminate = false
                progressBar.max = totalItem
                progressBar.progress = itemTerproses
            } else {
                vTeksDetail.text = "Mengekstrak berkas ke database..."
                progressBar.isIndeterminate = true
            }
        }
        FaseInjeksi.FASE_6 -> {
            vTeksStatus.text = "Checking Keutuhan Data..."
            vTeksDetail.text = "Membersihkan berkas sisa..."
            progressBar.isIndeterminate = true
        }
        FaseInjeksi.FASE_7 -> {
            hentikanRotasiNasehat()
            vTeksStatus.text = "Data Siap Digunakan!"
            vTeksDetail.text = "Selesai"
            panelUtama.visibility = View.GONE
        }
    }
}


    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        sesuaikanKompartemenGrid() 
    }    
    
    private fun inisialisasiSirkuitAppDrawer() {
        val menuBiografi = findViewById<TextView>(R.id.menuBiografi)
    val menuSosmed = findViewById<TextView>(R.id.menuSosmed)
    val menuLetnan = findViewById<TextView>(R.id.menuLetnan)
    val menuGaleriFoto = findViewById<TextView>(R.id.menuGaleriFoto)
    

    menuBiografi.setOnClickListener {
        sorotMenuTerpilih(menuBiografi) // biar ada efek highlight
        drawerLayout.closeDrawer(GravityCompat.START) // tutup drawer dulu
        val intent = Intent(this, BiografiActivity::class.java)
        startActivity(intent)
    }

    menuSosmed.setOnClickListener {
        sorotMenuTerpilih(menuSosmed)
        drawerLayout.closeDrawer(GravityCompat.START)
        val intent = Intent(this, SosmedActivity::class.java)
        startActivity(intent)
    }

    menuLetnan.setOnClickListener {
        sorotMenuTerpilih(menuLetnan)
        drawerLayout.closeDrawer(GravityCompat.START)
        val intent = Intent(this, LetnanActivity::class.java)
        startActivity(intent)
    }

    menuGaleriFoto.setOnClickListener {
    sorotMenuTerpilih(menuGaleriFoto)
    drawerLayout.closeDrawer(GravityCompat.START)    
    val intent = Intent(this, GaleriActivity::class.java)
    startActivity(intent)
}
    
    }

        // Variabel penampung View yang sedang aktif/terpilih
    private var viewAktifTerpilih: View? = null

    private fun inisialisasiKategoriDrawer() {
    val wadah = findViewById<LinearLayout>(R.id.wadahKategoriDinamis)
    wadah.removeAllViews()

    val scale = resources.displayMetrics.density
    val padHorizontal = (16 * scale).toInt()
    val padVerticalInduk = (10 * scale).toInt()
    val padVerticalAnak = (8 * scale).toInt()
    
    // INJEKSI PROTOKOL DIMENSI: Kunci sasis agar tidak kolaps menjadi 0x0
    val parameterSasisPenuh = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    for (kategori in CetakBiruKategori.MATRIKS_UTAMA) {
        val namaInduk = kategori.first
        val daftarCabang = kategori.second

        // 1. Eksekusi Sasis Induk
        val barisInduk = TextView(this).apply {
            layoutParams = parameterSasisPenuh // Kunci dimensi
            text = namaInduk
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#212121"))
            setPadding(padHorizontal, padVerticalInduk, padHorizontal, padVerticalInduk)
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            gravity = android.view.Gravity.CENTER_VERTICAL
            compoundDrawablePadding = (12 * scale).toInt()
        }

        if (daftarCabang.size == 1 && daftarCabang[0].first == namaInduk) {
            barisInduk.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_kategori_induk, 0, 0, 0
            )
            barisInduk.setOnClickListener { v ->
                sorotMenuTerpilih(v)
                eksekusiSaringanKategori(namaInduk)
            }
            wadah.addView(barisInduk)
        } else {
            barisInduk.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_kategori_induk, 0, android.R.drawable.arrow_down_float, 0
            )
            
            // 2. Eksekusi Sasis Anak (Wadah Pembungkus)
            val wadahAnak = LinearLayout(this).apply {
                layoutParams = parameterSasisPenuh // Kunci dimensi
                orientation = LinearLayout.HORIZONTAL 
                visibility = View.GONE
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            // Rel Vertikal Utama (Trunk)
            val relVertikal = View(this).apply {
                layoutParams = LinearLayout.LayoutParams((1.5f * scale).toInt(), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    setMargins((24 * scale).toInt(), 0, 0, 0) 
                }
                setBackgroundColor(android.graphics.Color.parseColor("#B0BEC5"))
            }

            val wadahTeksAnak = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            for (cabang in daftarCabang) {
                val namaAnak = cabang.first
                
                // 3. Eksekusi Sasis Baris Anak (Elemen Klik)
                val barisAnak = LinearLayout(this).apply {
                    layoutParams = parameterSasisPenuh // Kunci dimensi
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    isClickable = true
                    isFocusable = true
                }

                // Rel Horizontal Penghubung
                val relHorizontal = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams((12 * scale).toInt(), (1.5f * scale).toInt())
                    setBackgroundColor(android.graphics.Color.parseColor("#B0BEC5"))
                }

                val teksAnak = TextView(this).apply {
                    text = namaAnak 
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#555555"))
                    setPadding((8 * scale).toInt(), padVerticalAnak, padHorizontal, padVerticalAnak)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                
                barisAnak.setOnClickListener {
                    sorotMenuTerpilih(barisAnak)
                    eksekusiSaringanKategori(namaAnak)
                }
                
                barisAnak.addView(relHorizontal)
                barisAnak.addView(teksAnak)
                wadahTeksAnak.addView(barisAnak)
            }
            
            wadahAnak.addView(relVertikal)
            wadahAnak.addView(wadahTeksAnak)

            barisInduk.setOnClickListener {
                if (wadahAnak.visibility == View.VISIBLE) {
                    wadahAnak.visibility = View.GONE
                    barisInduk.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_kategori_induk, 0, android.R.drawable.arrow_down_float, 0
                    )
                } else {
                    wadahAnak.visibility = View.VISIBLE
                    barisInduk.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_kategori_induk, 0, android.R.drawable.arrow_up_float, 0
                    )
                }
            }
            wadah.addView(barisInduk)
            wadah.addView(wadahAnak)
        }
    }
}

    // FUNGSI INJEKSI WARNA AKTIF PADA MENU YANG DITEKAN
    private fun sorotMenuTerpilih(viewBaru: View) {
        // Reset warna item sebelumnya
        viewAktifTerpilih?.let { viewLama ->
            viewLama.setBackgroundResource(android.R.drawable.list_selector_background)
            if (viewLama is TextView) {
                viewLama.setTextColor(android.graphics.Color.parseColor("#555555"))
            }
        }

        // Terapkan warna aktif pada item baru (Hijau Transparan #E0F2F1)
        viewBaru.setBackgroundColor(android.graphics.Color.parseColor("#E0F2F1"))
        if (viewBaru is TextView) {
            viewBaru.setTextColor(android.graphics.Color.parseColor("#004D40"))
        }

        // Simpan acuan
        viewAktifTerpilih = viewBaru
    }

    private fun eksekusiSaringanKategori(labelKategori: String) {
        if (isMesinSibuk) {
            Toast.makeText(this, "Sistem sedang merakit data. Harap tunggu.", Toast.LENGTH_SHORT).show()
            return
        }
        tampilkanIndikator("Memuat Kategori: $labelKategori...", true)

        lifecycleScope.launch(Dispatchers.IO) {
            val database = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
            val hasilSaringanAkhir = database.saringBerdasarkanKolomKategori(labelKategori)

            withContext(Dispatchers.Main) {
    isSearchMode = false
    modeKategoriAktif = true
    edtPencarian.setQuery("", false)
    edtPencarian.clearFocus()

    
    val muatanTeks = "$labelKategori • ${hasilSaringanAkhir.size} Status"
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
        .setTitle(getString(R.string.zf_edukasi_judul))
        .setMessage(getString(R.string.zf_edukasi_pesan))
        .setPositiveButton("Selesai") { dialog, _ -> dialog.dismiss() }
        .setCancelable(true)
        .create()
        .show()
}

    private fun eksekusiPabrikData() {
    lifecycleScope.launch(Dispatchers.IO) {
        val database = ArsipDatabase.operasikanMesin(this@MainActivity)
        val lenganRobot = database.arsipDao()
        
        val jumlahBarisData = lenganRobot.hitungTotalArsip() 
        val berkasLokal = File(getExternalFilesDir(null), "Master_Data_Arsip_FK_11_Juli_2026.json")
        val bobotMinimum = 110 * 1024 * 1024 
        val batasAmanAbsolut = 17900 

        // KONDISI A: Data SQLite Sudah Utuh
        if (jumlahBarisData >= batasAmanAbsolut) {
            val semuaData = lenganRobot.tarikSemuaArsip()
            withContext(Dispatchers.Main) {
                findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                isMesinSibuk = false
                pompaDataKeLayar(semuaData)
                muatDataAwalKeSasis(semuaData)
                if (berkasLokal.exists()) { berkasLokal.delete() }
            }
            return@launch
        }

        // KONDISI B: Data Kurang, Tapi File JSON Mentah Sudah Siap Dieksekusi
        if (berkasLokal.exists() && berkasLokal.length() >= bobotMinimum) {
            withContext(Dispatchers.Main) {
                isMesinSibuk = true
                findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.VISIBLE
                perbaruiPanelTelemetri(FaseInjeksi.FASE_5, 0, 0, 0)
                jalankanMesinInjeksiOtonom(berkasLokal.absolutePath)
            }
            return@launch
        }

        // KONDISI C: Data Belum Lengkap & Berkas Belum Siap -> Paksa Masuk Jalur Penyedot
        withContext(Dispatchers.Main) {
            isMesinSibuk = true
            findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.VISIBLE
            perbaruiPanelTelemetri(FaseInjeksi.FASE_1, 0, 0, 0)
            
            // Langsung aktifkan mesin penyedot tanpa delay berlebihan
            aktifkanMesinPenyedot()
        }
    }
}

private fun aktifkanMesinPenyedot() {
    isMesinSibuk = true
    findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.VISIBLE

    // Sensor Jaringan
    if (!isJaringanTersedia()) {
        perbaruiPanelTelemetri(FaseInjeksi.FASE_4, 0, 0, 0)
        lifecycleScope.launch(Dispatchers.IO) {
            while (!isJaringanTersedia()) {
                delay(3000)
            }
            withContext(Dispatchers.Main) {
                aktifkanMesinPenyedot()
            }
        }
        return
    }
    
    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val idPipaAktif = cariPipaAktif(downloadManager)
    
    // IF 1: Jika Unduhan Sedang Berjalan di Latar Belakang (Pasca Recent Apps Closed)
    if (idPipaAktif != -1L) {
        // PERALIHAN INSTAN KE FASE 3
        perbaruiPanelTelemetri(FaseInjeksi.FASE_3, 0, 0, 100)
        pantauTekananUnduhan(idPipaAktif, downloadManager)
        pasangSensorPendaratan(idPipaAktif, downloadManager)
        return
    }

    // IF 2: Inisialisasi Pipa Baru -> TRANSISI KE FASE 2
    perbaruiPanelTelemetri(FaseInjeksi.FASE_2, 0, 0, 100)

    val namaFileTemp = "$namaFile.temp"
    val fileTempLama = File(getExternalFilesDir(null), namaFileTemp)
    if (fileTempLama.exists()) fileTempLama.delete()

    val request = DownloadManager.Request(Uri.parse(urlKargo))
        .setTitle("Arsip Fatwa Kehidupan")
        .setDescription("Mengunduh file data arsip...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(this, null, namaFileTemp)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val idUnduhan = downloadManager.enqueue(request)
    
    // PERALIHAN KANTONG VISUAL KE FASE 3 (MEMANTAU TRANSMISI)
    perbaruiPanelTelemetri(FaseInjeksi.FASE_3, 0, 0, 100)
    pantauTekananUnduhan(idUnduhan, downloadManager)
    pasangSensorPendaratan(idUnduhan, downloadManager)
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
                        
                        val direktoriTarget = android.os.Environment.DIRECTORY_DOWNLOADS
                        val namaFileTemp = "$namaFile.temp"
                        val fileTempSelesai = File(getExternalFilesDir(direktoriTarget), namaFileTemp)
                        val fileAsli = File(getExternalFilesDir(null), namaFile)
                        
                        // SENSOR BOBOT: Tolak kargo di bawah 110MB
                        val bobotMinimum = 110 * 1024 * 1024
                        if (fileTempSelesai.length() < bobotMinimum) {
                            fileTempSelesai.delete()
                            downloadManager.remove(idUnduhan)
                            aktifkanMesinPenyedot() // Minta kargo ulang
                            cursor.close()
                            return
                        }
                        
                        if (fileTempSelesai.renameTo(fileAsli)) {
                            isMesinSibuk = true
                            jalankanMesinInjeksiOtonom(fileAsli.absolutePath)
                        } else {
                            eksekusiPabrikData()
                        }
                    } else {
                        findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama).visibility = View.GONE
                        isMesinSibuk = false
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
        // Inisialisasi awal kalkulasi
        waktuTerakhirMs = System.currentTimeMillis()
        byteTerakhirDownloaded = 0L
        kecepatanEmaBytesPerSec = 0.0

        var sedangMengunduh = true

        while (sedangMengunduh && isMesinSibuk) {
            val query = DownloadManager.Query().setFilterById(idUnduhan)
            val cursor: Cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val indexBytesSoFar = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val indexTotalBytes = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val indexStatus = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                if (indexBytesSoFar != -1 && indexTotalBytes != -1 && indexStatus != -1) {
                    val bytesDownloaded = cursor.getLong(indexBytesSoFar)
                    val totalBytes = cursor.getLong(indexTotalBytes)
                    val status = cursor.getInt(indexStatus)

                    val waktuSekarangMs = System.currentTimeMillis()
                    val selisihWaktuSec = (waktuSekarangMs - waktuTerakhirMs) / 1000.0

                    // Kalkulasi kecepatan instan jika jeda waktu cukup (> 0.2 detik)
                    if (selisihWaktuSec >= 0.2) {
                        val selisihByte = bytesDownloaded - byteTerakhirDownloaded
                        val kecepatanInstan = if (selisihByte > 0) (selisihByte / selisihWaktuSec) else 0.0

                        // PENGERING/FILTER EMA: Alpha 0.25 memuluskan penurunan angka agar tidak mengagetkan (Soft Drop)
                        val alpha = 0.25 
                        kecepatanEmaBytesPerSec = if (kecepatanEmaBytesPerSec == 0.0) {
                            kecepatanInstan
                        } else {
                            (alpha * kecepatanInstan) + ((1 - alpha) * kecepatanEmaBytesPerSec)
                        }

                        waktuTerakhirMs = waktuSekarangMs
                        byteTerakhirDownloaded = bytesDownloaded
                    }

                    // Hitung Persentase
                    val persen = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0

                    // Format Teks Kecepatan (Kondisional KB/s vs MB/s)
                    val teksKecepatan = formatKecepatanPersisi(kecepatanEmaBytesPerSec)

                    withContext(Dispatchers.Main) {
                        // Perbarui UI Telemetri
                        perbaruiDetailKecepatan(persen, bytesDownloaded, totalBytes, teksKecepatan)
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        sedangMengunduh = false
                    }
                }
            }
            cursor.close()
            delay(500) // Polling interval 500ms
        }
    }
}

// FUNGSI KONVERSI UNIT DENGAN SKALA PRESISI (KB/s vs MB/s)
private fun formatKecepatanPersisi(bytesPerSec: Double): String {
    val kbps = bytesPerSec / 1024.0
    val mbps = kbps / 1024.0

    return when {
        mbps >= 1.0 -> String.format("%.2f MB/s", mbps)
        kbps >= 1.0 -> String.format("%.1f KB/s", kbps)
        bytesPerSec > 0 -> String.format("%.0f B/s", bytesPerSec)
        else -> "0 KB/s"
    }
}

// FUNGSI PENJAGA TAMPILAN PANEL TELEMETRI DENGAN RINCIAN SPEED METER
private fun perbaruiDetailKecepatan(persen: Int, byteDiterima: Long, totalByte: Long, kecepatanTeks: String) {
    val vTeksDetail = findViewById<TextView>(R.id.teksDetailProgress)
    val progressBar = findViewById<ProgressBar>(R.id.progressBarInisialisasi)

    val mbDiterima = String.format("%.1f", byteDiterima / (1024.0 * 1024.0))
    val mbTotal = String.format("%.1f", totalByte / (1024.0 * 1024.0))

    vTeksDetail.text = "Progres: $persen% ($mbDiterima MB / $mbTotal MB) • $kecepatanTeks"
    progressBar.isIndeterminate = false
    progressBar.progress = persen
}
    // SIRKUIT BARU: Antena Pemantau Sinyal Kerja WorkManager Latar Belakang
    private fun jalankanMesinInjeksiOtonom(jalurFileJson: String) {
    val kargo = workDataOf("URI_JSON_KARGO" to jalurFileJson)
    val instruksiKerja = OneTimeWorkRequestBuilder<MesinInjeksiWorker>()
        .setInputData(kargo)
        .build()
    
    val manajerKerja = WorkManager.getInstance(applicationContext)
    
    // PENGUNCI MEKANIS: Jika mesin lama masih menyala, ganti dengan yang baru
    manajerKerja.enqueueUniqueWork(
        "INJEKSI_MASTER_DATA", 
        androidx.work.ExistingWorkPolicy.KEEP, 
        instruksiKerja
    )

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
                    val panelUtama = findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama)
                    
                    if (kodeGagal == "BOBOT_KURANG") {
                        aktifkanMesinPenyedot() 
                    } else {
                        panelUtama.visibility = View.GONE
                        isMesinSibuk = false
                        Toast.makeText(this@MainActivity, "Gagal mengelas data arsip.", Toast.LENGTH_LONG).show()
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
        recyclerTimeline.visibility = View.GONE
        kontainerJalurKanan.visibility = View.GONE 
        recyclerGridMode.visibility = View.GONE
        wadahModeBuku.visibility = View.VISIBLE

        // AKTIFKAN PANEL TELEMETRI DENGAN FORMAT BARU
        panelStatusPencarian.visibility = View.VISIBLE
        loadingPencarian.visibility = View.GONE
        
        val totalVolume = daftarArsipAktif.size
        // Menggunakan label kategori aktif jika ada, atau default jika dalam mode umum
        val labelKategori = if (modeKategoriAktif && daftarArsipAktif.isNotEmpty()) {
            daftarArsipAktif.first().kategori
        } else {
            "Semua Status"
        }

        txtStatusPencarian.text = "$labelKategori • $totalVolume Status"
        
        delay(100) 
        geserSabukProyektor(posisi)
        
        proyektorBuku.post {
            isMesinSibuk = false 
        }
    }
}

    private fun aktifkanSirkuitPencarian() {
    edtPencarian.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            eksekusiLogikaPencarian(query)
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            if (newText.isNullOrEmpty()) {
                eksekusiLogikaPencarian("")
            }
            return true
        }
    })
}

private fun eksekusiLogikaPencarian(kataKunciMentah: String?) {
    if (isMesinSibuk) {
        Toast.makeText(this@MainActivity, "Mesin sedang merakit data. Pencarian ditangguhkan.", Toast.LENGTH_SHORT).show()
        return
    }

    val kataKunci = kataKunciMentah?.trim() ?: ""
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
    private fun geserSabukProyektor(indeksAbsolutFokus: Int) {
    val totalData = daftarArsipAktif.size
    val batasKiri = maxOf(0, indeksAbsolutFokus - radiusMuatan)
    val batasKanan = minOf(totalData, indeksAbsolutFokus + radiusMuatan + 1)
    
    // Kunci titik koordinat baru
    titikNolJendela = batasKiri
    
    val fragmenData = daftarArsipAktif.subList(batasKiri, batasKanan)
    val posisiRelatif = indeksAbsolutFokus - batasKiri

    // Heningkan pembaruan: Jangan gunakan animasi (false) agar transisi 
    // pemotongan data tidak terasa oleh usapan jari pengguna
    bukuAdapter.perbaruiData(fragmenData)
    proyektorBuku.setCurrentItem(posisiRelatif, false)
}
    private fun muatDataAwalKeSasis(daftarArsipGlobal: List<ArsipEntity>) {
    // Pastikan loading dimatikan jika pemrosesan selesai
    loadingPencarian.visibility = View.GONE
    
    if (daftarArsipGlobal.isNotEmpty()) {
        val tglMentah = daftarArsipGlobal.first().tanggalBaca.substringBefore(" ")
        val elemen = tglMentah.split("-")
        val tanggalTerbaruFormatted = if (elemen.size == 3) {
            "${elemen[2]}/${elemen[1]}/${elemen[0]}"
        } else {
            tglMentah
        }

        val totalVolume = daftarArsipGlobal.size
        txtStatusPencarian.text = "Arsip 24/03/2014 s.d $tanggalTerbaruFormatted Total $totalVolume Status"
    } else {
        // INSTRUKSI LAMA: panelStatusPencarian.visibility = View.GONE (Dihapus sepenuhnya)
        
        // Gantikan dengan pelaporan status hampa data
        txtStatusPencarian.text = "Sistem Telemetri: 0 Arsip Terdeteksi"
    }
}

private fun cekKapasitasTangkiMemadai(konteks: Context): Boolean {
    val batasAmanMB = 150L // Batas toleransi ruang kosong 150 MB
    val tangki = konteks.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
    val sisaRuangByte = tangki?.freeSpace ?: 0L
    val sisaRuangMB = sisaRuangByte / (1024 * 1024)
    return sisaRuangMB > batasAmanMB
}


}

