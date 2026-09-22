package com.fk.arsip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fk.arsip.database.ArsipDatabase
import com.fk.arsip.database.ArsipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileReader

class MesinInjeksiWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val ID_KANAL_NOTIFIKASI = "kanal_injeksi_arsip"
        private const val ID_NOTIFIKASI = 4471
    }

    private suspend fun buatInfoForeground(teks: String): ForegroundInfo {
        val manajerNotif = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(
                ID_KANAL_NOTIFIKASI,
                "Penyusunan Database Arsip",
                NotificationManager.IMPORTANCE_LOW
            )
            manajerNotif.createNotificationChannel(kanal)
        }
        val notifikasi: Notification = NotificationCompat.Builder(applicationContext, ID_KANAL_NOTIFIKASI)
            .setContentTitle("Pustaka FK")
            .setContentText(teks)
            .setSmallIcon(R.drawable.ic_launcher_fk)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_NOTIFIKASI, notifikasi, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_NOTIFIKASI, notifikasi)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jalurFile = inputData.getString("URI_JSON_KARGO") ?: return@withContext Result.failure()
        val fileTarget = File(jalurFile)

        try {
            setForeground(buatInfoForeground("Mempersiapkan data arsip..."))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val database = ArsipDatabase.operasikanMesin(applicationContext)
        val lenganRobot = database.arsipDao()

        // PENURUNAN SENSITIVITAS SENSOR BEBAN KE 50 MB
        val bobotMinimum = 50 * 1024 * 1024
        val totalBobotFile = fileTarget.length()

        if (totalBobotFile < bobotMinimum) {
            fileTarget.delete()
            return@withContext Result.failure(workDataOf("KODE_GAGAL" to "BOBOT_KURANG"))
        }

// PERBAIKAN: sebelumnya hardcode 17934 (total baris file FK-saja yang
// lama). Setelah digabung dgn YW total jadi 27583 (lihat ringkasan
// gabung_arsip.py). PENTING: kalau nanti nambah sumber lagi / re-generate
// file gabungan dgn isi beda, angka ini HARUS disesuaikan lagi manual --
// cuma dipakai utk tampilan persentase progres, tidak memengaruhi jumlah
// baris yang benar2 diproses (loop tetap jalan sampai array JSON habis,
// brp pun isinya).
val targetPasti = 27583
val estimasiTotalItem = if (totalBobotFile > 100_000_000) targetPasti else maxOf(1, (totalBobotFile / 5120).toInt())

setProgress(workDataOf(
    "FASE" to 6,
    "PERSENTASE" to 0,
    "INDEKS" to 0,
    "TOTAL" to estimasiTotalItem
))
kotlinx.coroutines.delay(1200)
// 4. Buka Katup Pipa JSON
try {
    val reader = com.google.gson.stream.JsonReader(java.io.FileReader(fileTarget))
    if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
        throw Exception("Integritas struktur berkas gagal diverifikasi.")
    }
    
    lenganRobot.kurasTangkiKotor()
    reader.beginArray()

    val muatanSementara = mutableListOf<ArsipEntity>()
    var indeks = 0

    while (reader.hasNext()) {
    // PERBAIKAN "2X INJEKSI": cek isStopped() SETIAP iterasi, bukan cuma
    // bergantung pada suspend-cancellation di setProgress()/injeksiMassal().
    // Sebelumnya, saat ExistingWorkPolicy.REPLACE membatalkan worker ini
    // (mis. karena eksekusiPabrikData() dipanggil ulang di onCreate sementara
    // WorkManager sendiri sedang auto-resume worker yang sama setelah app
    // di-force-close), instance LAMA ini bisa sempat memproses ratusan baris
    // lagi (sampai batch 500 penuh) SEBELUM pembatalan benar-benar berhenti
    // -- bertabrakan dengan worker BARU yang di waktu bersamaan sudah
    // memanggil kurasTangkiKotor() & mulai menginjeksi dari awal ke tabel
    // yang sama. Berhenti secepat mungkin di sini menutup jendela race itu.
    if (isStopped) {
        reader.close()
        return@withContext Result.failure(workDataOf("KODE_GAGAL" to "DIBATALKAN"))
    }
    // Naikkan frekuensi pelaporan panel agar tidak melompat patah-patah
    if (indeks % 200 == 0 || indeks == estimasiTotalItem) {
        // Ganti coerceAtMost dengan coerceIn untuk limit batas bawah dan atas absolut
        val kalkulasiPersen = ((indeks.toDouble() / estimasiTotalItem.toDouble()) * 100).toInt().coerceIn(0, 100)
        setProgress(workDataOf(
            "FASE" to 6,
            "PERSENTASE" to kalkulasiPersen,
            "INDEKS" to indeks,
            "TOTAL" to estimasiTotalItem
        ))
        try {
            setForeground(buatInfoForeground("Menyusun database... $kalkulasiPersen% ($indeks/$estimasiTotalItem)"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        System.gc()
    }

    val obj = com.google.gson.JsonParser.parseReader(reader).asJsonObject

    val idPosting = obj.str("postId", "ID_$indeks")
    val userObj = obj.objek("user")
    val namaPenulis = userObj?.str("name", "Fatwa Kehidupan") ?: "Fatwa Kehidupan"
    val urlProfilPic = userObj?.str("profilePic", "") ?: ""
    val waktuRilis = obj.lng("timestamp", 0L)
    val waktuMentah = obj.str("time", "-")
    val tanggalBaca = if (waktuMentah.length >= 10) waktuMentah.substring(0, 10) else waktuMentah
    val tautanAsli = obj.str("url", "")

    var kontenPenuh = obj.str("text", "")
    val sharedObj = obj.objek("sharedPost")
    if (sharedObj != null) {
        val namaAsli = sharedObj.objek("user")?.str("name", "Entitas") ?: "Entitas"
        val teksAsli = sharedObj.str("text", "")
        if (teksAsli.isNotEmpty()) kontenPenuh += "\n\n--- Membagikan Status: $namaAsli ---\n$teksAsli"
    }

    val kategori = mesinDeteksiKategori(kontenPenuh)

    // Field "sumber" (FK/YW) ditambahkan oleh gabung_arsip.py saat
    // menggabung 2 file master jadi satu -- fallback "" kalau file lama
    // (belum digabung ulang) dipakai, supaya tidak crash.
    val sumberArsip = obj.str("sumber", "")

    val daftarFoto = mutableListOf<String>()
    val mediaArray = obj.larik("media") ?: sharedObj?.larik("media")
    if (mediaArray != null) {
        for (m in 0 until mediaArray.size()) {
            val mediaObj = mediaArray[m].asJsonObject
            if (mediaObj.str("__typename", "") == "Video") {
                val uriThumb = mediaObj.objek("thumbnailImage")?.str("uri", "") ?: mediaObj.str("thumbnail", "")
                if (uriThumb.isNotEmpty()) daftarFoto.add("video:$uriThumb")
            } else {
                val uriGbr = mediaObj.objek("image")?.str("uri", "") ?: ""
                if (uriGbr.isNotEmpty()) daftarFoto.add("image:$uriGbr")
            }
        }
    }

  muatanSementara.add(ArsipEntity(idPosting, namaPenulis, urlProfilPic, waktuRilis, tanggalBaca, kontenPenuh, tautanAsli, daftarFoto.joinToString(","), kategori, sumberArsip))
        indeks++

        // INJEKSI TANPA DELAY UI JUMPING
if (muatanSementara.size >= 500) {
        lenganRobot.injeksiMassal(muatanSementara)
        muatanSementara.clear()
        
        val kalkulasiPersen = ((indeks.toDouble() / estimasiTotalItem.toDouble()) * 100).toInt().coerceIn(0, 100)
        setProgress(workDataOf(
            "FASE" to 6,
            "PERSENTASE" to kalkulasiPersen,
            "INDEKS" to indeks,
            "TOTAL" to estimasiTotalItem
        ))
    }
}
    

    if (muatanSementara.isNotEmpty()) { lenganRobot.injeksiMassal(muatanSementara) }
reader.endArray()
reader.close()

// PENGAPUSAN BERKAS HANYA JIKA BERHASIL TOTAL
if (fileTarget.exists()) { fileTarget.delete() }

            setProgress(workDataOf(
                "FASE" to 7,
                "PERSENTASE" to 100,
                "INDEKS" to indeks,
                "TOTAL" to indeks
            ))

            return@withContext Result.success()
} catch (e: Exception) {
    e.printStackTrace()
    // Kuras data setengah matang agar tidak mengontaminasi SQLite
    val database = ArsipDatabase.operasikanMesin(applicationContext)
    database.arsipDao().kurasTangkiKotor() 
    
    if (fileTarget.exists()) { fileTarget.delete() }
    return@withContext Result.failure(workDataOf("KODE_GAGAL" to "ERROR_SISTEM"))
}


    }
    
    // ============================================================
    // Helper ekstensi Gson JsonObject, meniru semantik org.json.optString/
    // optJSONObject/optJSONArray/optLong -- dipakai di doWork() di atas
    // supaya field JSON cukup di-parse SEKALI oleh Gson (bukan 2x: sekali
    // oleh Gson lalu di-toString()-kan & di-reparse ulang oleh org.json
    // seperti sebelumnya). Nama sengaja dibedakan dari opt* org.json
    // (str/objek/larik/lng) supaya tidak ambigu dgn method org.json.JSONObject
    // yang masih dipakai di tempat lain (mis. MainActivity).
    private fun com.google.gson.JsonObject.str(kunci: String, default: String = ""): String {
        val e = this.get(kunci)
        return if (e != null && !e.isJsonNull) e.asString else default
    }

    private fun com.google.gson.JsonObject.objek(kunci: String): com.google.gson.JsonObject? {
        val e = this.get(kunci)
        return if (e != null && e.isJsonObject) e.asJsonObject else null
    }

    private fun com.google.gson.JsonObject.larik(kunci: String): com.google.gson.JsonArray? {
        val e = this.get(kunci)
        return if (e != null && e.isJsonArray) e.asJsonArray else null
    }

    private fun com.google.gson.JsonObject.lng(kunci: String, default: Long = 0L): Long {
        val e = this.get(kunci)
        return if (e != null && !e.isJsonNull) e.asLong else default
    }

    // INJEKSI AMUNISI: Logika pemindai kategori jamak yang dipindahkan dari MainActivity
    private fun mesinDeteksiKategori(teksKonten: String): String {
        val teksMesin = teksKonten.lowercase()
        val tangkiStempel = mutableSetOf<String>() 
        
        for (induk in CetakBiruKategori.MATRIKS_UTAMA) {
            for (cabang in induk.second) {
                val namaKategori = cabang.first
                val daftarKataKunci = cabang.second
                
                for (kunci in daftarKataKunci) {
                    val sensorBatasKata = Regex("\\b$kunci\\b")
                    if (sensorBatasKata.containsMatchIn(teksMesin)) {
                        tangkiStempel.add(namaKategori) 
                        break 
                    }
                }
            }
        }
        
        return if (tangkiStempel.isNotEmpty()) {
            tangkiStempel.joinToString(", ") 
        } else {
            "Belum di Kategorikan" 
        }
    }
}
