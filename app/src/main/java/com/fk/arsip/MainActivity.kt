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
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import android.widget.ScrollView
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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.collectLatest

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


enum class FaseInjeksi(
    val pesan: String, 
    val idGambar: Int,
    val isIndeterminate: Boolean = true
) {
    FASE_1("Mempersiapkan Jalur Data", R.drawable.img_1_persiapan, isIndeterminate = true),
    FASE_2("Menghubungkan ke Server Data", R.drawable.img_2_koneksi, isIndeterminate = true),
    FASE_3("Mengunduh Arsip Status Fatwa Kehidupan", R.drawable.img_3_unduh, isIndeterminate = false), 
    KONEKSI_BURUK("Koneksi Terputus, Cek Koneksi ...", R.drawable.img_koneksi_buruk, isIndeterminate = true), 
    FASE_4("Membongkar & Menyusun Data...", R.drawable.img_4_bongkar, isIndeterminate = true),
    FASE_5("Checking Keutuhan Data & Injeksi baris data ke SQLite...", R.drawable.img_5_injeksi, isIndeterminate = false), 
    FASE_6("Proses selesai. Data Siap Digunakan.", R.drawable.img_6_selesai, isIndeterminate = false) 
}



class MainActivity : AppCompatActivity() {

private var katupFaseVisual: FaseInjeksi? = null
private var jobRotasiNasehat: Job? = null
private var waktuTerakhirMs: Long = 0L
private var byteTerakhirDownloaded: Long = 0L
private var kecepatanEmaBytesPerSec: Double = 0.0
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

    private var isSearchMode = false 
    private var isMesinSibuk = false
    private var modeKategoriAktif = false
    // PAGING 3: nama kategori & arah sortir yang sedang aktif, dipakai untuk
    // menentukan PagingSource mana yang harus dipasok ke bukuAdapter (lihat
    // mulaiPagingBuku()). Menggantikan mekanisme "jendela manual" lama
    // (titikNolJendela/radiusMuatan + subList + notifyDataSetChanged) yang
    // dulu dipakai khusus saat dataset > 5000 baris.
    private var kategoriAktifNama: String = "Semua Kategori"
    private var sortTerlamaAktif: Boolean = false
    private var jobPagingBuku: Job? = null
    
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
        findViewById<TasbihConnectorView>(R.id.connectorTasbih).apply {
    totalLangkah = 6
    tinggiBarisPx = 64f * resources.displayMetrics.density
}

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

        bukuAdapter = BukuAdapter()
        proyektorBuku.adapter = bukuAdapter
        proyektorBuku.offscreenPageLimit = 1
        val mesinProyeksi = proyektorBuku.getChildAt(0) as? RecyclerView
        mesinProyeksi?.overScrollMode = View.OVER_SCROLL_NEVER
        // PAGING 3: pergeseran halaman kini ditangani otomatis oleh Paging3
        // (prefetchDistance di PagingConfig, lihat mulaiPagingBuku()) sehingga
        // OnPageChangeCallback untuk "menggeser jendela data" manual sudah
        // tidak diperlukan lagi.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } 
                else if (wadahModeBuku.visibility == View.VISIBLE) {
                wadahModeBuku.visibility = View.GONE
                recyclerGridMode.visibility = View.VISIBLE
                kontainerJalurKanan.visibility = View.VISIBLE 
                recyclerTimeline.visibility = View.VISIBLE
                // FORMAT TELEMETRI KATEGORI SAAT KEMBALI
                panelStatusPencarian.visibility = View.VISIBLE
                val totalVolume = daftarArsipAktif.size
                val labelKategori = if (modeKategoriAktif && daftarArsipAktif.isNotEmpty()) {
                daftarArsipAktif.first().kategori} else {"Semua Status"}
                txtStatusPencarian.text = "$labelKategori • $totalVolume Status"
                // PAGING 3: tidak perlu lagi mengosongkan bukuAdapter secara manual
                // untuk dataset besar -- PagingDataAdapter sudah ringan dengan
                // sendirinya karena hanya memuat halaman yang sedang terlihat.
                }


                else if (isSearchMode || edtPencarian.query.toString().isNotEmpty() || modeKategoriAktif) {
                    isSearchMode = false
                    modeKategoriAktif = false 
                    sortTerlamaAktif = false
                    
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
        inisialisasiKategoriDrawer()
        aktifkanSirkuitPencarian()
        eksekusiPabrikData()
    }
    
    private fun bukaKatupDialogFilter() {
    if (isMesinSibuk) {
        Toast.makeText(this, "Mesin sedang bekerja, tahan instruksi.", Toast.LENGTH_SHORT).show()
        aturKunciDrawer(true)
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
            kategoriAktifNama = kategori
            sortTerlamaAktif = urutTerlama
            
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




private fun perbaruiPanelTelemetri(fase: FaseInjeksi, persentase: Int = 0, volumeSelesai: Int = 0, volumeTotal: Int = 0, metrikKhusus: String = "") {
    
    // 1. Ambil semua view - ini boleh, tapi lebih bagus di bind di onCreate sekali aja
    val indikatorVisual = findViewById<ImageView>(R.id.indikatorVisualMesin)
    val teksStatus = findViewById<TextView>(R.id.teksStatusInisialisasi)
    val progressBar = findViewById<ProgressBar>(R.id.progressBarInisialisasi)
    val teksDetail = findViewById<TextView>(R.id.teksDetailProgress)
    val teksNasehat = findViewById<TextView>(R.id.teksNasehatInisialisasi)
    val pembatas = findViewById<View>(R.id.pembatasSektor)
    val panelStepper = findViewById<ScrollView>(R.id.panelStepperUtama)

    if (katupFaseVisual == fase && progressBar.progress == persentase) return // cegah kedobel + spam update
    katupFaseVisual = fase

    // 2. Atur ProgressBar: muter2 atau pake %
    progressBar.isIndeterminate = fase.isIndeterminate
    
    if (fase.isIndeterminate) {
        // Mode muter2: sembunyikan teks %
        teksDetail.visibility = View.GONE
    } else {
        // Mode %: tampilkan teks % dan set progress
        val progressAkhir = if (fase == FaseInjeksi.FASE_6) 100 else persentase
        progressBar.progress = progressAkhir // SET DULU
        teksDetail.text = "$progressAkhir%"  // BARU SET TEXT
        teksDetail.visibility = View.VISIBLE // BARU TAMPILIN
    }

    // 3. Atur Visibilitas panel
    teksStatus.visibility = View.VISIBLE
progressBar.visibility = View.VISIBLE
teksNasehat.visibility = View.VISIBLE
pembatas.visibility = View.VISIBLE
panelStepper.visibility = View.VISIBLE
    // teksDetail visibility udah diatur di atas, jangan ditimpa lagi

    // 4. Set teks dan gambar dari enum
    teksStatus.text = when(fase) {
        FaseInjeksi.FASE_3 -> metrikKhusus
        FaseInjeksi.FASE_5 -> "Progres: $persentase% • Baris diinjeksi: $volumeSelesai / $volumeTotal"
        else -> fase.pesan
    }
    
    indikatorVisual.setImageResource(fase.idGambar)
    (indikatorVisual.drawable as? android.graphics.drawable.Animatable)?.start()
    
    perbaruiVisualStepper(fase)

    // 5. Logic khusus
    // 5. Logic khusus
when (fase) {
    FaseInjeksi.FASE_6 -> {
        Handler(Looper.getMainLooper()).postDelayed({ 
            aturVisibilitasOverlayInisialisasi(false)
        }, 1500)
    }
    else -> {}
}

    if (jobRotasiNasehat == null || !jobRotasiNasehat!!.isActive) {
        jalankanRotasiNasehat(teksNasehat)
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
    val menuGammaLocking = findViewById<TextView>(R.id.menuGammaLocking)
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
    
    menuGammaLocking.setOnClickListener {
        sorotMenuTerpilih(menuGammaLocking)
        drawerLayout.closeDrawer(GravityCompat.START)
        tampilkanDialogAbout()
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
        aturKunciDrawer(true)
        tampilkanIndikator("Memuat Kategori: $labelKategori...", true)

        lifecycleScope.launch(Dispatchers.IO) {
            val database = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
            val hasilSaringanAkhir = database.saringBerdasarkanKolomKategori(labelKategori)

            withContext(Dispatchers.Main) {
    isSearchMode = false
    modeKategoriAktif = true
    kategoriAktifNama = labelKategori
    sortTerlamaAktif = false
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
        // PERBAIKAN: overlay stepper (FASE_1..6) JANGAN ditampilkan secara optimis
        // di sini. Membuka koneksi Room/SQLite pertama kali bisa memakan waktu
        // cukup lama (bukan cuma 1 frame), sehingga stepper sempat ter-render
        // penuh dan terlihat jelas oleh user meskipun data sudah lengkap dan
        // harusnya langsung ke halaman utama TANPA stepper sama sekali.
        // Jeda saat pengecekan database ini sudah cukup ditutupi oleh skeleton
        // loading di grid & timeline (wadahLoadingGrid / loadingTimelineKecil)
        // yang sudah tampil instan sejak activity_main.xml di-inflate — overlay
        // stepper baru dipanggil di cabang yang memang benar-benar butuh
        // download/injeksi (lihat cabang di bawah).
        lifecycleScope.launch(Dispatchers.IO) {
            val database = ArsipDatabase.operasikanMesin(this@MainActivity)
            val lenganRobot = database.arsipDao()
            
            val jumlahBarisData = lenganRobot.hitungTotalArsip() 
            val berkasLokal = File(getExternalFilesDir(null), namaFile)
            
            // PENURUNAN SENSITIVITAS SENSOR BEBAN KE 50 MB AGAR KARGO LOLOS INSPEKSI
            val bobotMinimum = 50 * 1024 * 1024 
            val batasAmanAbsolut = 17900 

            if (jumlahBarisData >= batasAmanAbsolut) {
                val semuaData = lenganRobot.tarikSemuaArsip()
                withContext(Dispatchers.Main) {
                    aturVisibilitasOverlayInisialisasi(false)
                    isMesinSibuk = false
                    pompaDataKeLayar(semuaData)
                    muatDataAwalKeSasis(semuaData)
                    if (berkasLokal.exists()) { berkasLokal.delete() }
                }
                return@launch
            }

            if (berkasLokal.exists() && berkasLokal.length() >= bobotMinimum) {
                withContext(Dispatchers.Main) {
                    isMesinSibuk = true
                    aturVisibilitasOverlayInisialisasi(true)
                    perbaruiPanelTelemetri(FaseInjeksi.FASE_4, 0, 0, 0)
                    jalankanMesinInjeksiOtonom(berkasLokal.absolutePath)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                lifecycleScope.launch(Dispatchers.IO) { lenganRobot.kurasTangkiKotor() }
                aturVisibilitasOverlayInisialisasi(true)
                perbaruiPanelTelemetri(FaseInjeksi.FASE_1, 0, 0, 0)
                delay(1000)
                aktifkanMesinPenyedot()
            }
        }
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
                            val fileTempSelesai = File(getExternalFilesDir(null), "$namaFile.temp")
                            val fileAsli = File(getExternalFilesDir(null), namaFile)
                            
                            // HANCURKAN SISA FILE LAMA SEBELUM MENGELAS NAMA BARU
                            if (fileAsli.exists()) { fileAsli.delete() }
                            
                            if (fileTempSelesai.exists() && fileTempSelesai.renameTo(fileAsli)) {
                                isMesinSibuk = true
                                aturKunciDrawer(true)
                                jalankanMesinInjeksiOtonom(fileAsli.absolutePath)
                            } else {
                                // Putus putaran loop dengan mematikan mesin secara paksa jika sistem operasi menolak rename
                                aturVisibilitasOverlayInisialisasi(false)
                                isMesinSibuk = false
                                Toast.makeText(this@MainActivity, "Gagal memproses pendaratan file. Ruang penuh atau terkunci.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            aturVisibilitasOverlayInisialisasi(false)
                            isMesinSibuk = false
                            Toast.makeText(this@MainActivity, "Tekanan unduhan gagal. Cek jaringan.", Toast.LENGTH_LONG).show()
                        }
                    }
                    cursor?.close()
                }
            }
        }
        registerReceiver(sensorSelesai, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }


private fun aktifkanMesinPenyedot() {
    isMesinSibuk = true
    aturVisibilitasOverlayInisialisasi(true)

    // Sensor Jaringan
    if (!isJaringanTersedia()) {
        perbaruiPanelTelemetri(FaseInjeksi.KONEKSI_BURUK, 0, 0, 0)
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
    lifecycleScope.launch(Dispatchers.Main) {
    delay(3000) // Katup jeda agar Fase 2 dirender oleh layar
    val idUnduhan = downloadManager.enqueue(request)
    
    // PERALIHAN KANTONG VISUAL KE FASE 3 (MEMANTAU TRANSMISI)
    perbaruiPanelTelemetri(FaseInjeksi.FASE_3, 0, 0, 100)
    pantauTekananUnduhan(idUnduhan, downloadManager)
    pasangSensorPendaratan(idUnduhan, downloadManager)
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
private fun pantauTekananUnduhan(idUnduhan: Long, downloadManager: DownloadManager) {
    lifecycleScope.launch(Dispatchers.IO) {
        var isMengunduh = true
        var beradaDiFaseGagalJaringan = false
        var waktuTerakhir = System.currentTimeMillis()
        var byteTerakhir = 0L
        
        // BUFFER PENAHAN FLUKTUASI (Mencegah kedip kedip)
        var hitunganArusNol = 0
        var tangkiMemoriTelemetri = "Membuka katup aliran data..."
        
        while (isMengunduh) {
            val jaringanAktif = isJaringanTersedia()
            
            if (!jaringanAktif) {
                beradaDiFaseGagalJaringan = true
                withContext(Dispatchers.Main) { perbaruiPanelTelemetri(FaseInjeksi.KONEKSI_BURUK, 0, 0, 0) }
                delay(3000)
                continue
            }

            if (beradaDiFaseGagalJaringan && jaringanAktif) {
                beradaDiFaseGagalJaringan = false
                withContext(Dispatchers.Main) { perbaruiPanelTelemetri(FaseInjeksi.FASE_3, 0, 0, 0) }
            }

            val query = DownloadManager.Query().setFilterById(idUnduhan)
            val cursor = downloadManager.query(query)
            
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    isMengunduh = false
              
                    withContext(Dispatchers.Main) {
                        perbaruiPanelTelemetri(FaseInjeksi.FASE_4, 0, 0, 0)
                        eksekusiPabrikData()
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    isMengunduh = false
                    withContext(Dispatchers.Main) {
                        perbaruiPanelTelemetri(FaseInjeksi.KONEKSI_BURUK, 0, 0, 0)
                        delay(3000)
                        eksekusiPabrikData() 
                    }
                } else {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                        val downloaded = cursor.getLong(bytesDownloadedIndex)
                        val total = cursor.getLong(bytesTotalIndex)
                        
                        if (status == DownloadManager.STATUS_PAUSED && downloaded == byteTerakhir) {
                            withContext(Dispatchers.Main) { perbaruiPanelTelemetri(FaseInjeksi.KONEKSI_BURUK, 0, 0, 0) }
                        } else if (total > 0) {
                            val persentase = ((downloaded * 100L) / total).toInt()
                            val waktuSekarang = System.currentTimeMillis()
                            val selisihWaktu = waktuSekarang - waktuTerakhir
                            
                            if (selisihWaktu > 0 && byteTerakhir > 0) {
                                val deltaByte = downloaded - byteTerakhir
                                val kecepatanBps = (deltaByte * 1000) / selisihWaktu
                                val mbSelesai = String.format("%.1f", downloaded / (1024.0 * 1024.0))
                                val mbTotal = String.format("%.1f", total / (1024.0 * 1024.0))
                                
                                if (kecepatanBps > 0) {
                                    hitunganArusNol = 0
                                    val sisaByte = total - downloaded
                                    val sisaDetik = sisaByte / kecepatanBps
                                    
                                    val teksWaktu = when {
                                        sisaDetik >= 3600 -> "${sisaDetik / 3600}j ${(sisaDetik % 3600) / 60}m"
                                        sisaDetik >= 60 -> "${sisaDetik / 60}m ${sisaDetik % 60}d"
                                        else -> "$sisaDetik detik"
                                    }
                                    
                                    val konversiKbps = kecepatanBps / 1024.0
                                    val konversiMbps = konversiKbps / 1024.0
                                    val teksKecepatan = if (konversiMbps < 1.0) {
                                        String.format("%.0f Kbps", konversiKbps)
                                    } else {
                                        String.format("%.2f Mbps", konversiMbps)
                                    }
                                    
                                    tangkiMemoriTelemetri = "$teksWaktu tersisa • $teksKecepatan | $mbSelesai MB / $mbTotal MB"
                                } else {
                                    hitunganArusNol++
                                    if (hitunganArusNol >= 3) {    tangkiMemoriTelemetri = "Menunggu dorongan arus... • 0 Kbps | $mbSelesai MB / $mbTotal MB"
                                    }
                                }
                            } else if (byteTerakhir == 0L && downloaded > 0) {
                                val mbSelesai = String.format("%.1f", downloaded / (1024.0 * 1024.0))
                                val mbTotal = String.format("%.1f", total / (1024.0 * 1024.0))
                                tangkiMemoriTelemetri = "Mengunci metrik awal... | $mbSelesai MB / $mbTotal MB"
                            }
                            
                            waktuTerakhir = waktuSekarang
                            byteTerakhir = downloaded
                            
                            withContext(Dispatchers.Main) {
                                perbaruiPanelTelemetri(FaseInjeksi.FASE_3, persentase, 0, 0, tangkiMemoriTelemetri)
                            }
                        }
                    }
                }
            } else {
                isMengunduh = false
                withContext(Dispatchers.Main) {
                    aturVisibilitasOverlayInisialisasi(false)
                    isMesinSibuk = false
                }
            }
            cursor?.close()
            delay(1000) 
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
                            val faseEnum = when (faseAktif) {
                                1 -> FaseInjeksi.FASE_1
                                2 -> FaseInjeksi.FASE_2
                                3 -> FaseInjeksi.FASE_3
                                4 -> FaseInjeksi.KONEKSI_BURUK
                                5 -> FaseInjeksi.FASE_5
                                6 -> FaseInjeksi.FASE_5
                                else -> FaseInjeksi.FASE_6 // Poros operasi default
                            }
                            perbaruiPanelTelemetri(faseEnum, persentase, indeks, total)
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        perbaruiPanelTelemetri(FaseInjeksi.FASE_6, 100, indeks, indeks)
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
                        aturVisibilitasOverlayInisialisasi(false)
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
        // PERBAIKAN: reset posisi scroll grid ke atas setiap kali data baru
        // (hasil filter/kategori/pencarian) didorong masuk. Tanpa ini, jika
        // pengguna sedang scroll ke bawah saat menekan filter, grid akan
        // tetap berada di posisi scroll lama -- daftar baru sudah benar di
        // adapter, tapi secara visual terlihat seperti filter "tidak bekerja"
        // karena konten yang berubah berada di luar area yang terlihat.
        // Timeline di sampingnya selalu terlihat ter-update karena adapternya
        // diganti baru (recyclerTimeline.adapter = adapterTimeline) di bawah.
        recyclerGridMode.scrollToPosition(0)
        // PAGING 3: ganti mekanisme lama (kirim List penuh ke bukuAdapter, atau
        // dikosongkan paksa jika >5000 baris) dengan aliran PagingData yang
        // otomatis menyesuaikan sumber query sesuai filter aktif saat ini
        // (browse biasa / kategori / pencarian) -- lihat mulaiPagingBuku().
        mulaiPagingBuku()
        
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

        // PERBAIKAN: begitu grid & timeline sudah benar-benar terisi data,
        // sembunyikan skeleton/placeholder loading agar tidak menutupi konten.
        findViewById<View>(R.id.wadahLoadingGrid)?.visibility = View.GONE
        findViewById<View>(R.id.loadingTimelineKecil)?.visibility = View.GONE
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
        // PAGING 3: dengan placeholder aktif, Paging3 sudah tahu total itemCount
        // sejak awal (dari COUNT query Room) sehingga ViewPager2 bisa langsung
        // lompat ke posisi absolut manapun tanpa perlu memotong/menggeser
        // jendela data secara manual seperti mekanisme lama.
        proyektorBuku.setCurrentItem(posisi, false)
        
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

    // =========================================================================
    // PERBAIKAN BUG UTAMA: "Halaman utama blank setelah inisialisasi selesai"
    // -------------------------------------------------------------------------
    // Root cause: layout_inisialisasi_mesin.xml membungkus panel init dengan
    // sebuah ScrollView ber-id "panelStepperUtama" yang match_parent (menutupi
    // SELURUH layar) dan punya background @drawable/bg_kayu_mandala.
    // Sebelumnya, seluruh kode di file ini hanya meng-GONE-kan CHILD-nya
    // (ConstraintLayout "panelInisialisasiUtama"), sementara ScrollView
    // pembungkusnya sendiri TIDAK PERNAH disembunyikan. Akibatnya, begitu
    // proses inisialisasi selesai, yang terjadi bukan "kembali ke halaman
    // utama", melainkan ScrollView kosong (tanpa child) yang tetap full-screen
    // menutupi grid/timeline di belakangnya -> layar terlihat blank.
    // Fix: sembunyikan/tampilkan KEDUA view (ScrollView pembungkus + child-nya)
    // lewat satu fungsi terpusat agar tidak ada lagi celah seperti ini.
    // =========================================================================
    private fun aturVisibilitasOverlayInisialisasi(tampil: Boolean) {
        val statusVisibilitas = if (tampil) View.VISIBLE else View.GONE
        findViewById<ScrollView>(R.id.panelStepperUtama)?.visibility = statusVisibilitas
        findViewById<ConstraintLayout>(R.id.panelInisialisasiUtama)?.visibility = statusVisibilitas
    }

    private fun tampilkanIndikator(pesan: String, aktif: Boolean) {
        panelStatusPencarian.visibility = if (aktif) View.VISIBLE else View.GONE
        loadingPencarian.visibility = if (aktif) View.VISIBLE else View.GONE
        txtStatusPencarian.text = pesan
    }
    // =========================================================================
    // PAGING 3: mulai/ganti aliran data untuk mode buku (ViewPager2)
    // -------------------------------------------------------------------------
    // Menggantikan mekanisme lama geserSabukProyektor() (subList manual +
    // notifyDataSetChanged berdasarkan radiusMuatan di sekitar posisi baca).
    // Sumber data dipilih sesuai filter yang sedang aktif:
    //  - Pencarian aktif      -> bungkus daftarArsipAktif (hasil presisi client-
    //                            side) lewat ListPagingSource, karena regex
    //                            batas-kata tidak bisa diwakili satu query SQL.
    //  - Kategori aktif       -> PagingSource DB langsung (saringKategoriPaged /
    //                            saringKategoriTerlamaPaged) -> hemat memori
    //                            walau kategori berisi ribuan status.
    //  - Browse biasa         -> PagingSource DB langsung (tarikSemuaArsipPaged /
    //                            tarikSemuaArsipTerlamaPaged) -> puluhan ribu
    //                            baris tidak pernah ditarik penuh ke RAM.
    // =========================================================================
    private fun mulaiPagingBuku() {
        jobPagingBuku?.cancel()

        val lenganRobot by lazy { ArsipDatabase.operasikanMesin(this).arsipDao() }
        val sourceFactory: () -> PagingSource<Int, ArsipEntity> = when {
            isSearchMode -> {
                { ListPagingSource(daftarArsipAktif) }
            }
            modeKategoriAktif -> {
                if (sortTerlamaAktif) {
                    { lenganRobot.saringKategoriTerlamaPaged(kategoriAktifNama) }
                } else {
                    { lenganRobot.saringKategoriPaged(kategoriAktifNama) }
                }
            }
            else -> {
                if (sortTerlamaAktif) {
                    { lenganRobot.tarikSemuaArsipTerlamaPaged() }
                } else {
                    { lenganRobot.tarikSemuaArsipPaged() }
                }
            }
        }

        val pagingFlow = Pager(
            config = PagingConfig(
                pageSize = 40,
                prefetchDistance = 20,
                initialLoadSize = 80,
                enablePlaceholders = true
            ),
            pagingSourceFactory = sourceFactory
        ).flow.cachedIn(lifecycleScope)

        jobPagingBuku = lifecycleScope.launch {
            pagingFlow.collectLatest { data ->
                bukuAdapter.submitData(data)
            }
        }
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

private fun perbaruiVisualStepper(faseAktif: FaseInjeksi) {
    val nomorUrut = getNomorVisualUrut(faseAktif)

    val dataStepper = listOf(
        "Mempersiapkan Jalur Data",
        "Menghubungkan ke Server",
        "Mengunduh Arsip",
        "Membongkar Arsip",
        "Injeksi ke SQLite",
        "Inisialisasi Selesai"
    )

    val warnaAktif = android.graphics.Color.parseColor("#FFB300")
    val warnaSelesai = android.graphics.Color.parseColor("#EEDC9A")
    val warnaInaktif = android.graphics.Color.parseColor("#8D7B68")
    val warnaTeksTasbihNyala = android.graphics.Color.parseColor("#3E2723")
    val warnaTeksTasbihMati = android.graphics.Color.parseColor("#FFFFFF")

    val connectorTasbih = findViewById<TasbihConnectorView>(R.id.connectorTasbih)
    connectorTasbih.langkahAktif = nomorUrut // memicu redraw kurva + bead

    val listInclude = listOf(
        findViewById<View>(R.id.fase1), findViewById<View>(R.id.fase2),
        findViewById<View>(R.id.fase3), findViewById<View>(R.id.fase4),
        findViewById<View>(R.id.fase5), findViewById<View>(R.id.fase6)
    )

    for (i in 0..5) {
        val includeView = listInclude[i] ?: continue
        val vNum = includeView.findViewById<TextView>(R.id.numFase)
        val vLbl = includeView.findViewById<TextView>(R.id.lblFase)
        if (vNum == null || vLbl == null) continue

        vNum.text = (i + 1).toString()
        vLbl.text = dataStepper[i]
        vNum.translationX = connectorTasbih.offsetXUntukBaris(i)

        when {
            i + 1 < nomorUrut -> {
                vNum.setTextColor(warnaTeksTasbihNyala)
                vLbl.setTextColor(warnaSelesai)
            }
            i + 1 == nomorUrut -> {
                vNum.setTextColor(warnaTeksTasbihNyala)
                vLbl.setTextColor(warnaAktif)
            }
            else -> {
                vNum.setTextColor(warnaTeksTasbihMati)
                vLbl.setTextColor(warnaInaktif)
            }
        }
    }
}


private fun getNomorVisualUrut(faseAsli: FaseInjeksi): Int {
    return when (faseAsli) {
        FaseInjeksi.FASE_1 -> 1
        FaseInjeksi.FASE_2 -> 2
        FaseInjeksi.FASE_3, FaseInjeksi.KONEKSI_BURUK -> 3 // KONEKSI_BURUK tetep di step 3
        FaseInjeksi.FASE_4 -> 4
        FaseInjeksi.FASE_5 -> 5
        FaseInjeksi.FASE_6 -> 6
    }
}

private fun aturKunciDrawer(kunci: Boolean) {
    val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout) // Sesuaikan ID DrawerLayout Anda
    if (drawerLayout != null) {
        if (kunci) {
            // Mengunci laci agar tidak bisa ditarik (swipe) dan disentuh
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else {
            // Membuka kembali kunci laci setelah mesin selesai
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        }
    }
}
// 3. FUNGSI UNTUK MENJALANKAN ROTASI TEKS SECARA OTOMATIS
private fun jalankanRotasiNasehat(teksNasehat: TextView) {
    jobRotasiNasehat?.cancel()
    jobRotasiNasehat = lifecycleScope.launch {
        var indeks = 0
        while (isMesinSibuk) {
            teksNasehat.text = KoleksiNasehat.DAFTAR_TEKS[indeks]
            indeks = (indeks + 1) % KoleksiNasehat.DAFTAR_TEKS.size
            delay(6000) // Berganti setiap 6 detik
        }
    }
}

private fun hentikanRotasiNasehat() {
    jobRotasiNasehat?.cancel()
    jobRotasiNasehat = null
}
private fun tampilkanDialogAbout() {
        val dialogView = layoutInflater.inflate(R.layout.activity_gammalocking, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<android.view.View>(R.id.linkSanFK).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com/?q=Kendal+Regency"))) }
        dialogView.findViewById<android.view.View>(R.id.linkSaung).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com/?q=-6.9385,110.2031"))) }
        dialogView.findViewById<android.view.View>(R.id.linkFB).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.facebook.com/FK.FatwaKehidupan"))) }
        dialogView.findViewById<android.view.View>(R.id.linkZF).setOnClickListener { Toast.makeText(this, "Zuhri Formalism - Absolute Internal Circuit", Toast.LENGTH_SHORT).show() }
        dialogView.findViewById<Button>(R.id.btnCloseAbout).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}

