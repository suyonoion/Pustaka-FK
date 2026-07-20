package com.fk.arsip

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fk.arsip.database.ArsipDatabase
import com.fk.arsip.database.ArsipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader

class MesinInjeksiWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jalurFile = inputData.getString("URI_JSON_KARGO") ?: return@withContext Result.failure()
        val fileTarget = File(jalurFile)

        // KALIBRASI PIPA: Menggunakan operasikanMesin sesuai dengan cetak biru database Anda
        val database = ArsipDatabase.operasikanMesin(applicationContext)
        val lenganRobot = database.arsipDao()

        val bobotMinimum = 110 * 1024 * 1024
        val totalBobotFile = fileTarget.length()

        if (totalBobotFile < bobotMinimum) {
            fileTarget.delete()
            return@withContext Result.failure(workDataOf("KODE_GAGAL" to "BOBOT_KURANG"))
        }

        val estimasiTotalItem = (totalBobotFile / 5120).toInt()
        
        setProgress(workDataOf(
            "FASE" to 5,
            "PERSENTASE" to 0,
            "INDEKS" to 0,
            "TOTAL" to estimasiTotalItem
        ))

        try {
            val reader = com.google.gson.stream.JsonReader(FileReader(fileTarget))
            if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                throw Exception("Struktur berkas tidak valid.")
            }

            lenganRobot.kurasTangkiKotor()
            reader.beginArray()

            val muatanSementara = mutableListOf<ArsipEntity>()
            var indeks = 0
            var persentaseLayarTerakhir = -1

            while (reader.hasNext()) {
                if (indeks % 500 == 0) {
                    val kalkulasiFase5 = ((indeks.toDouble() / estimasiTotalItem.toDouble()) * 100).toInt().coerceAtMost(99)
                    setProgress(workDataOf(
                        "FASE" to 5,
                        "PERSENTASE" to kalkulasiFase5,
                        "INDEKS" to indeks,
                        "TOTAL" to estimasiTotalItem
                    ))
                }

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

                // MENGEKSEKUSI MESIN SORTIR LOKAL
                val kategori = mesinDeteksiKategori(kontenPenuh)

                val daftarFoto = mutableListOf<String>()
                val mediaArray = obj.optJSONArray("media") ?: sharedObj?.optJSONArray("media")
                if (mediaArray != null) {
                    for (m in 0 until mediaArray.length()) {
                        val mediaObj = mediaArray.getJSONObject(m)
                        if (mediaObj.optString("__typename", "") == "Video") {
                            val uriThumb = mediaObj.optJSONObject("thumbnailImage")?.optString("uri", "") ?: mediaObj.optString("thumbnail", "")
                            if (uriThumb.isNotEmpty()) daftarFoto.add("video:$uriThumb")
                        } else {
                            val uriGbr = mediaObj.optJSONObject("image")?.optString("uri", "") ?: ""
                            if (uriGbr.isNotEmpty()) daftarFoto.add("image:$uriGbr")
                        }
                    }
                }

                muatanSementara.add(ArsipEntity(idPosting, namaPenulis, urlProfilPic, waktuRilis, tanggalBaca, kontenPenuh, tautanAsli, daftarFoto.joinToString(","), kategori))
                indeks++

                if (muatanSementara.size >= 3000) {
                    lenganRobot.injeksiMassal(muatanSementara)
                    muatanSementara.clear()

                    val kalkulasiPersen = ((indeks.toDouble() / estimasiTotalItem.toDouble()) * 100).toInt().coerceAtMost(99)
                    if (kalkulasiPersen > persentaseLayarTerakhir) {
                        persentaseLayarTerakhir = kalkulasiPersen
                        
                        setProgress(workDataOf(
                            "FASE" to 6,
                            "PERSENTASE" to kalkulasiPersen,
                            "INDEKS" to indeks,
                            "TOTAL" to estimasiTotalItem
                        ))
                    }
                }
            }

            if (muatanSementara.isNotEmpty()) { lenganRobot.injeksiMassal(muatanSementara) }
            reader.endArray()
            reader.close()

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
