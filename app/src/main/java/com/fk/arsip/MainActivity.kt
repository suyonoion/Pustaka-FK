package com.fk.arsip

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class MainActivity : AppCompatActivity() {

    // 1. Parameter Transmisi Awan & Logistik
    private val namaFile = "Master_Data_Arsip_FK_11_Juli_2026.json"
    private val urlKargo = "https://github.com/suyonoion/Pustaka-FK/releases/download/v1.0.0/Master_Data_Arsip_FK_11_Juli_2026.json"

    // 2. Komponen Sasis Visual (UI)
    private lateinit var recyclerGridMode: RecyclerView
    private lateinit var wadahModeBuku: RelativeLayout
    private lateinit var proyektorBuku: ViewPager2
    private lateinit var edtPencarian: EditText
    
    // 3. Komponen Indikator Kontrol
    private lateinit var panelIndikator: LinearLayout
    private lateinit var txtIndikatorProses: TextView

    // 4. Sabuk Transmisi Data (Adapter)
    private lateinit var gridAdapter: GridAdapter
    private lateinit var bukuAdapter: BukuAdapter
    
    // 5. Penampung Arus Data Aktif
    private var daftarArsipAktif: List<ArsipEntity> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Fase I: Pengait Pelat Fisik (XML) ke Struktur Logika
        recyclerGridMode = findViewById(R.id.recyclerGridMode)
        wadahModeBuku = findViewById(R.id.wadahModeBuku)
        proyektorBuku = findViewById(R.id.proyektorBuku)
        edtPencarian = findViewById(R.id.edtPencarian)
        panelIndikator = findViewById(R.id.panelIndikator)
        txtIndikatorProses = findViewById(R.id.txtIndikatorProses)

        // Fase II: Konfigurasi Rel Jalur (Grid 2 Kolom)
        recyclerGridMode.layoutManager = GridLayoutManager(this, 2)

        // Fase III: Sirkuit Efek Mekanis Buku (Page Curl Transformer Terkalibrasi)
        proyektorBuku.setPageTransformer { page, position ->
            page.pivotX = 0f
            page.pivotY = page.height / 2f
            
            when {
                position < -1 -> { 
                    // Halaman jauh di sebelah kiri (Tidak Terlihat)
                    page.alpha = 0f
                }
                position <= 0 -> { 
                    // Halaman utama terbuka ke kiri
                    page.alpha = 1f
                    page.translationX = 0f
                    page.rotationY = 90f * Math.abs(position)
                    page.scaleX = 1f
                    page.scaleY = 1f
                }
                position <= 1 -> { 
                    // Halaman penyangga di sebelah kanan (Mengikuti tarikan sasis)
                    page.alpha = 1f
                    page.translationX = -page.width * position
                    val scaleFactor = 0.75f + (1f - 0.75f) * (1f - Math.abs(position))
                    page.scaleX = scaleFactor
                    page.scaleY = scaleFactor
                    page.rotationY = 0f // Reset rotasi agar tidak tumpang tindih
                }
                else -> { 
                    // Halaman jauh di sebelah kanan (Tidak Terlihat)
                    page.alpha = 0f
                }
            }
        }

        // Fase IV: Pembajakan Rem Sistem (Tombol Kembali)
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

        // Fase V: Pengaktifan Sakelar Sirkuit
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

            if (lenganRobot.hitungTotalArsip() == 0) {
                val fileTarget = File(getExternalFilesDir(null), namaFile)
                
                if (!fileTarget.exists()) {
                    withContext(Dispatchers.Main) {
                        aktifkanMesinPenyedot()
                    }
                    return@launch 
                } else {
                    ekstrakDanInjeksiKeDb(fileTarget, lenganRobot)
                }
            } else {
                daftarArsipAktif = lenganRobot.tarikSemuaArsip()
                withContext(Dispatchers.Main) {
                    panelIndikator.visibility = View.GONE
                    pompaDataKeLayar(daftarArsipAktif)
                }
            }
        }
    }

    private fun cariPipaAktif(downloadManager: DownloadManager): Long {
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or 
            DownloadManager.STATUS_PENDING or 
            DownloadManager.STATUS_PAUSED
        )
        val cursor = downloadManager.query(query)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                if (titleIndex != -1) {
                    val title = cursor.getString(titleIndex)
                    if (title == "Arsip Fatwa Kehidupan") {
                        val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                        val id = cursor.getLong(idIndex)
                        cursor.close()
                        return id
                    }
                }
            }
            cursor.close()
        }
        return -1L
    }

    private fun aktifkanMesinPenyedot() {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val idPipaAktif = cariPipaAktif(downloadManager)
        if (idPipaAktif != -1L) {
            panelIndikator.visibility = View.VISIBLE
            pantauTekananUnduhan(idPipaAktif, downloadManager)
            pasangSensorPendaratan(idPipaAktif, downloadManager)
            return
        }

        panelIndikator.visibility = View.VISIBLE
        txtIndikatorProses.text = "Menyalakan Pipa Transmisi..."

        val namaFileTemp = "$namaFile.temp"
        val fileTempLama = File(getExternalFilesDir(null), namaFileTemp)
        if (fileTempLama.exists()) fileTempLama.delete()

        val request = DownloadManager.Request(Uri.parse(urlKargo))
            .setTitle("Arsip Fatwa Kehidupan")
            .setDescription("Menyedot matriks data 115MB...")
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
                                Toast.makeText(this@MainActivity, "Kargo Valid. Memulai Ekstraksi...", Toast.LENGTH_LONG).show()
                                eksekusiPabrikData() 
                            }
                        } else {
                            panelIndikator.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Transmisi Awan Gagal.", Toast.LENGTH_LONG).show()
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

                        if (total > 0) {
                            val persentase = ((diunduh * 100) / total).toInt()
                            txtIndikatorProses.text = "Menyedot Kargo: $persentase%"
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
        if (fileTarget.length() < bobotMinimum) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Kargo Cacat. Mengulang unduhan...", Toast.LENGTH_LONG).show()
                fileTarget.delete()
                aktifkanMesinPenyedot()
            }
            return
        }

        withContext(Dispatchers.Main) {
            panelIndikator.visibility = View.VISIBLE
            txtIndikatorProses.text = "Mengekstrak Matriks Ke Ruang Mesin..."
        }

        try {
            val reader = com.google.gson.stream.JsonReader(FileReader(fileTarget))
            reader.beginArray() 

            val muatanSementara = mutableListOf<ArsipEntity>()
            var indeks = 0

            while (reader.hasNext()) {
                val elemenGson = com.google.gson.JsonParser.parseReader(reader)
                val obj = org.json.JSONObject(elemenGson.toString())

                val idPosting = obj.optString("postId", "ID_$indeks")
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
                indeks++

                if (muatanSementara.size >= 500) {
                    lenganRobot.injeksiMassal(muatanSementara)
                    muatanSementara.clear()
                }
            }

            if (muatanSementara.isNotEmpty()) {
                lenganRobot.injeksiMassal(muatanSementara)
            }

            reader.endArray() 
            reader.close()

            daftarArsipAktif = lenganRobot.tarikSemuaArsip()
            withContext(Dispatchers.Main) {
                panelIndikator.visibility = View.GONE
                pompaDataKeLayar(daftarArsipAktif)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            fileTarget.delete()
            withContext(Dispatchers.Main) {
                panelIndikator.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Korsleting Penguraian. Data Dihancurkan.", Toast.LENGTH_LONG).show()
            }
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

    // Katup Filter Terkunci Bersama Tombol Search Keyboard
    private fun aktifkanSirkuitPencarian() {
        edtPencarian.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val kataKunci = edtPencarian.text.toString().trim()
                
                // Eksekusi penyaringan data
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
                        
                        // Tutup paksa katup keyboard setelah perintah selesai
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
