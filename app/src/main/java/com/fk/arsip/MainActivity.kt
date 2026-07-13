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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationView
import com.fk.arsip.database.ArsipDatabase
import com.fk.arsip.database.ArsipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileReader

class MainActivity : AppCompatActivity() {

    private val namaFile = "Master_Data_Arsip_FK_11_Juli_2026.json"
    private val urlKargo = "https://github.com/suyonoion/Pustaka-FK/releases/download/v1.0.0/Master_Data_Arsip_FK_11_Juli_2026.json"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var recyclerGridMode: RecyclerView
    private lateinit var wadahModeBuku: RelativeLayout
    private lateinit var proyektorBuku: ViewPager2
    private lateinit var edtPencarian: EditText
    private lateinit var panelIndikator: LinearLayout
    private lateinit var txtIndikatorProses: TextView

    private lateinit var gridAdapter: GridAdapter
    private lateinit var bukuAdapter: BukuAdapter
    private var daftarArsipAktif: List<ArsipEntity> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        recyclerGridMode = findViewById(R.id.recyclerGridMode)
        wadahModeBuku = findViewById(R.id.wadahModeBuku)
        proyektorBuku = findViewById(R.id.proyektorBuku)
        edtPencarian = findViewById(R.id.edtPencarian)
        panelIndikator = findViewById(R.id.panelIndikator)
        txtIndikatorProses = findViewById(R.id.txtIndikatorProses)

        recyclerGridMode.layoutManager = GridLayoutManager(this, 2)

        // Hapus PageTransformer tumpang tindih, biarkan bergeser horizontal secara default dan stabil
        proyektorBuku.setPageTransformer(null)

                // Fase IV: Pembajakan Rem Sistem Utama (Tombol Fisik HP)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Katup 1: Jika Laci Navigasi terbuka -> Tutup Laci
                if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } 
                // Katup 2: Jika Proyektor Buku menyala -> Matikan, kembali ke Grid
                else if (wadahModeBuku.visibility == View.VISIBLE) {
                    wadahModeBuku.visibility = View.GONE
                    recyclerGridMode.visibility = View.VISIBLE
                } 
                // Katup 3: Jika Kolom Pencarian terisi -> Kosongkan dan Muat Ulang Data Awal
                else if (edtPencarian.text.toString().isNotEmpty()) {
                    edtPencarian.text.clear()
                    edtPencarian.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(edtPencarian.windowToken, 0)
                    eksekusiPabrikData() 
                } 
                // Katup 4: Konfirmasi Pemutusan Arus (Keluar Aplikasi)
                else {
                    tampilkanPanelKonfirmasiKeluar()
                }
            }
        })


        inisialisasiSirkuitAppDrawer()
        aktifkanSirkuitPencarian()
        eksekusiPabrikData()
    }

    private fun inisialisasiSirkuitAppDrawer() {
        navView.setNavigationItemSelectedListener { menuItem ->
            val kategoriSaringan = when (menuItem.itemId) {
                R.id.nav_ekonomi -> "Ekonomi"
                R.id.nav_politik -> "Politik & Negara"
                R.id.nav_agama -> "Agama & Spiritualitas"
                R.id.nav_pertanian -> "Pertanian"
                else -> ""
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val lenganRobot = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
                val hasilSaringan = if (kategoriSaringan.isEmpty()) {
                    lenganRobot.tarikSemuaArsip()
                } else {
                    lenganRobot.saringArsip(kategoriSaringan) // Re-use saringArsip berdasarkan teks kategori
                }

                withContext(Dispatchers.Main) {
                    wadahModeBuku.visibility = View.GONE
                    recyclerGridMode.visibility = View.VISIBLE
                    daftarArsipAktif = hasilSaringan
                    pompaDataKeLayar(daftarArsipAktif)
                    drawerLayout.closeDrawers()
                }
            }
            true
        }
    }

    private fun eksekusiPabrikData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mesinDb = ArsipDatabase.operasikanMesin(this@MainActivity)
            val lenganRobot = mesinDb.arsipDao()

            if (lenganRobot.hitungTotalArsip() == 0) {
                val fileTarget = File(getExternalFilesDir(null), namaFile)
                if (!fileTarget.exists()) {
                    withContext(Dispatchers.Main) { aktifkanMesinPenyedot() }
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
                            Toast.makeText(this@MainActivity, "Transmisi Gagal.", Toast.LENGTH_LONG).show()
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
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) { selesai = true }
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
                fileTarget.delete()
                aktifkanMesinPenyedot()
            }
            return
        }

        withContext(Dispatchers.Main) {
            panelIndikator.visibility = View.VISIBLE
            txtIndikatorProses.text = "Mengekstrak Matriks..."
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

            if (muatanSementara.isNotEmpty()) { lenganRobot.injeksiMassal(muatanSementara) }
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
            withContext(Dispatchers.Main) { panelIndikator.visibility = View.GONE }
        }
    }

    private fun pompaDataKeLayar(dataLayar: List<ArsipEntity>) {
        gridAdapter = GridAdapter(dataLayar) { posisi -> bukaModeBuku(posisi) }
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
        edtPencarian.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val kataKunci = edtPencarian.text.toString().trim()
                
                lifecycleScope.launch(Dispatchers.IO) {
                    val lenganRobot = ArsipDatabase.operasikanMesin(this@MainActivity).arsipDao()
                    val hasilSaringan = if (kataKunci.isEmpty()) {
                        lenganRobot.tarikSemuaArsip()
                    } else {
                        lenganRobot.saringArsip(kataKunci)
                    }
                    
                    withContext(Dispatchers.Main) {
                        // KOREKSI UTAMA: Tutup paksa mode Buku jika sedang terbuka, paksa visual kembali ke Grid
                        if (wadahModeBuku.visibility == View.VISIBLE) {
                            wadahModeBuku.visibility = View.GONE
                            recyclerGridMode.visibility = View.VISIBLE
                        }

                        daftarArsipAktif = hasilSaringan
                        pompaDataKeLayar(daftarArsipAktif)
                        
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

    // Sakelar Pengaman Pemutusan Arus
    private fun tampilkanPanelKonfirmasiKeluar() {
        val matriksPanel = android.app.AlertDialog.Builder(this)
            .setTitle("Pemutusan Arus")
            .setMessage("Apakah Anda yakin ingin mematikan mesin dan keluar dari arsip?")
            .setCancelable(false) // Mengunci panel agar tidak hilang saat disentuh di luarnya
            .setPositiveButton("Matikan") { _, _ ->
                finish() // Memutus arus utama aplikasi secara absolut
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss() // Menutup panel dan membiarkan mesin tetap hidup
            }
            .create()
        
        matriksPanel.show()
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