package com.fk.arsip

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.fk.arsip.database.ArsipDatabase
import com.fk.arsip.database.ArsipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class MainActivity : AppCompatActivity() {

    // Kordinat Awan
    private val namaFile = "Master_Data_Arsip_FK_11_Juli_2026.json"
    private val urlKargo = "https://github.com/suyonoion/Pustaka-FK/releases/download/v1.0.0/Master_Data_Arsip_FK_11_Juli_2026.json"

    // Komponen Sasis Visual
    private lateinit var recyclerGridMode: RecyclerView
    private lateinit var wadahModeBuku: RelativeLayout
    private lateinit var proyektorBuku: ViewPager2
    private lateinit var edtPencarian: EditText

    private lateinit var gridAdapter: GridAdapter
    private lateinit var bukuAdapter: BukuAdapter
    
    // Penampung Arus Data Aktif
    private var daftarArsipAktif: List<ArsipEntity> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inisialisasi Tuas Antarmuka
        recyclerGridMode = findViewById(R.id.recyclerGridMode)
        wadahModeBuku = findViewById(R.id.wadahModeBuku)
        proyektorBuku = findViewById(R.id.proyektorBuku)
        edtPencarian = findViewById(R.id.edtPencarian)

        // 2. Kalibrasi Etalase Kotak (Grid 2 Kolom)
        recyclerGridMode.layoutManager = GridLayoutManager(this, 2)

        // 3. Injeksi Efek Transmisi Kertas (Page Curl) pada Buku
        proyektorBuku.setPageTransformer { page, position ->
            page.pivotX = 0f
            page.pivotY = page.height / 2f
            if (position < -1) { page.alpha = 0f } 
            else if (position <= 0) { 
                page.alpha = 1f; page.translationX = 0f; page.rotationY = 90f * Math.abs(position)
            } 
            else if (position <= 1) { 
                page.alpha = 1f; page.translationX = -page.width * position
                val scaleFactor = 0.75f + (1 - 0.75f) * (1 - Math.abs(position))
                page.scaleX = scaleFactor; page.scaleY = scaleFactor
            } 
            else { page.alpha = 0f }
        }

        // 4. Pembajakan Sistem Rem (Tombol Kembali)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (wadahModeBuku.visibility == View.VISIBLE) {
                    wadahModeBuku.visibility = View.GONE
                    recyclerGridMode.visibility = View.VISIBLE
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // 5. Pemicu Sakelar Utama
        aktifkanSirkuitPencarian()
        eksekusiPabrikData()
    }

    // ==========================================
    // SIRKUIT 1: MANAJEMEN DATABASE & LOGISTIK
    // ==========================================
    private fun eksekusiPabrikData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mesinDb = ArsipDatabase.operasikanMesin(this@MainActivity)
            val lenganRobot = mesinDb.arsipDao()

            // Jika Database Kosong
            if (lenganRobot.hitungTotalArsip() == 0) {
                val fileTarget = File(getExternalFilesDir(null), namaFile)
                
                // Cek Keberadaan Biner Mentah
                if (!fileTarget.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Tangki biner kosong. Memulai transmisi awan...", Toast.LENGTH_LONG).show()
                        aktifkanMesinPenyedot()
                    }
                    return@launch // Putus arus coroutine di sini, tunggu unduhan selesai
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Mengekstrak kargo 115MB ke ruang mesin...", Toast.LENGTH_SHORT).show()
                    }
                    ekstrakDanInjeksiKeDb(fileTarget, lenganRobot)
                }
            }

            // Database Terisi -> Tarik ke Layar
            daftarArsipAktif = lenganRobot.tarikSemuaArsip()
            withContext(Dispatchers.Main) {
                pompaDataKeLayar(daftarArsipAktif)
            }
        }
    }

    private fun aktifkanMesinPenyedot() {
        val request = DownloadManager.Request(Uri.parse(urlKargo))
            .setTitle("Arsip Fatwa Kehidupan")
            .setDescription("Menyedot matriks data 115MB...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, null, namaFile)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val idUnduhan = downloadManager.enqueue(request)

        val sensorSelesai = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == idUnduhan) {
                    Toast.makeText(this@MainActivity, "Transmisi awan selesai. Menjalankan mesin injeksi...", Toast.LENGTH_LONG).show()
                    unregisterReceiver(this)
                    // Picu ulang siklus pabrik setelah kargo mendarat
                    eksekusiPabrikData()
                }
            }
        }
        registerReceiver(sensorSelesai, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun ekstrakDanInjeksiKeDb(fileTarget: File, lenganRobot: com.fk.arsip.database.ArsipDao) {
        try {
            // Perubahan Krusial: Baca dari fileTarget (Eksternal), bukan assets
            val reader = BufferedReader(FileReader(fileTarget))
            val jsonString = reader.readText()
            reader.close()

            val dataArray = JSONArray(jsonString)
            val totalBlok = dataArray.length()
            val muatanSementara = mutableListOf<ArsipEntity>() 

            for (i in 0 until totalBlok) {
                val obj = dataArray.getJSONObject(i)
                val idPosting = obj.optString("postId", "ID_$i")
                val userObj = obj.optJSONObject("user")
                val namaPenulis = userObj?.optString("name", "Fatwa Kehidupan") ?: "Fatwa Kehidupan"
                val urlProfilPic = userObj?.optString("profilePic", "") ?: ""
                val waktuRilis = obj.optLong("timestamp", 0L)
                val waktuMentah = obj.optString("time", "-")
                val tanggalBaca = if (waktuMentah.length >= 10) waktuMentah.substring(0, 10) else waktuMentah
                val tautanAsli = obj.optString("url", "")

                var kontenPenuh = obj.optString("text", "")
                val sharedObj = obj.optJSONObject("sharedPost")
                if (sharedObj != null) {
                    val namaAsli = sharedObj.optJSONObject("user")?.optString("name", "Entitas") ?: "Entitas"
                    val teksAsli = sharedObj.optString("text", "")
                    if (teksAsli.isNotEmpty()) kontenPenuh += "\n\n--- Membagikan Status: $namaAsli ---\n$teksAsli"
                }

                val kategori = mesinDeteksiKategori(kontenPenuh)

                val daftarFoto = mutableListOf<String>()
                val mediaArray = obj.optJSONArray("media") ?: sharedObj?.optJSONArray("media")
                if (mediaArray != null) {
                    for (m in 0 until mediaArray.length()) {
                        val mediaObj = mediaArray.getJSONObject(m)
                        if (mediaObj.optString("__typename", "") == "Video") {
                            val uriThumb = mediaObj.optJSONObject("thumbnailImage")?.optString("uri", "") ?: mediaObj.optString("thumbnail", "")
                            if (uriThumb.isNotEmpty()) daftarFoto.add(uriThumb)
                        } else {
                            val uriGbr = mediaObj.optJSONObject("image")?.optString("uri", "") ?: ""
                            if (uriGbr.isNotEmpty()) daftarFoto.add(uriGbr)
                        }
                    }
                }

                muatanSementara.add(ArsipEntity(idPosting, namaPenulis, urlProfilPic, waktuRilis, tanggalBaca, kontenPenuh, tautanAsli, daftarFoto.joinToString(","), kategori))

                // Injeksi per 500 blok agar RAM tidak kolaps
                if (muatanSementara.size >= 500 || i == totalBlok - 1) {
                    lenganRobot.injeksiMassal(muatanSementara)
                    muatanSementara.clear()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // SIRKUIT 2: PROYEKSI VISUAL & INTERAKSI
    // ==========================================
    private fun pompaDataKeLayar(dataLayar: List<ArsipEntity>) {
        gridAdapter = GridAdapter(dataLayar) { posisi ->
            bukaModeBuku(posisi)
        }
        recyclerGridMode.adapter = gridAdapter

        bukuAdapter = BukuAdapter(dataLayar)
        proyektorBuku.adapter = bukuAdapter
    }

    private fun bukaModeBuku(posisi: Int) {
        recyclerGridMode.visibility = View.GONE
        wadahModeBuku.visibility = View.VISIBLE
        proyektorBuku.setCurrentItem(posisi, false)
    }

    private fun aktifkanSirkuitPencarian() {
        edtPencarian.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val kataKunci = s.toString().trim()
                lifecycleScope.launch(Dispatchers.IO) {
                    val lenganRobot = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
                    val hasilSaringan = if (kataKunci.isEmpty()) {
                        lenganRobot.tarikSemuaArsip()
                    } else {
                        lenganRobot.saringArsip(kataKunci)
                    }
                    
                    withContext(Dispatchers.Main) {
                        daftarArsipAktif = hasilSaringan
                        pompaDataKeLayar(daftarArsipAktif)
                    }
                }
            }
        })
    }

    private fun mesinDeteksiKategori(teksKonten: String): String {
        val matriksTeks = teksKonten.lowercase()
        return when {
            matriksTeks.contains("rupiah") || matriksTeks.contains("dollar") || matriksTeks.contains("emas") || matriksTeks.contains("pajak") -> "Ekonomi"
            matriksTeks.contains("pemerintah") || matriksTeks.contains("pejabat") || matriksTeks.contains("polisi") || matriksTeks.contains("korupsi") -> "Politik & Negara"
            matriksTeks.contains("kiamat") || matriksTeks.contains("syahid") || matriksTeks.contains("hadist") || matriksTeks.contains("agama") -> "Agama & Spiritualitas"
            matriksTeks.contains("tani") || matriksTeks.contains("sawah") || matriksTeks.contains("kopi") -> "Pertanian"
            else -> "Umum"
        }
    }
}