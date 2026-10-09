package com.fk.arsip

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fk.arsip.database.ArsipDatabase
import com.fk.arsip.database.ArsipEntity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layar "Halaman Tersimpan" -- daftar status yang ditandai/di-bookmark user
 * (bintang di panel ikon mode baca, lihat MainActivity.toggleBookmarkArsipAktif()).
 * Dipindah kesini dari dialog kecil di dalam mode baca krn perlu ruang lebih
 * lega utk daftar panjang & preview 3 baris per status (sesuai permintaan).
 *
 * Bookmark disimpan di SharedPreferences "preferensi_baca" / key "bookmark_ids"
 * (dibaca & ditulis MainActivity juga, lihat idBookmarkTersimpan() disana --
 * nama file & key HARUS sama persis dgn di sana).
 *
 * Tap satu item -> kembalikan idPosting + sumberArsip lewat ActivityResult
 * (RESULT_OK), MainActivity yang urus pindah Tab & lompat halamannya
 * (lihat launcherHalamanTersimpan/tanganiHasilHalamanTersimpan di sana),
 * krn arsip yg dipilih bisa jadi ada di Tab (sumber) yang BERBEDA dari yg
 * sedang aktif di MainActivity saat drawer dibuka.
 */
class HalamanTersimpanActivity : AppCompatActivity() {

    private lateinit var rvHalamanTersimpan: RecyclerView
    private lateinit var wadahKosong: View
    private lateinit var wadahRingkas: View
    private lateinit var txtJumlah: TextView
    private val daftar = mutableListOf<ArsipEntity>()
    private lateinit var adapter: HalamanTersimpanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_halaman_tersimpan)
        // Tema belum mewarisi warna status bar kayu (tampil ungu bawaan) -- samakan dgn header.
        window.statusBarColor = android.graphics.Color.parseColor("#2A1810")

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarHalamanTersimpan)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rvHalamanTersimpan = findViewById(R.id.rvHalamanTersimpan)
        rvHalamanTersimpan.layoutManager = LinearLayoutManager(this)
        wadahKosong = findViewById(R.id.txtHalamanTersimpanKosong)
        wadahRingkas = findViewById(R.id.wadahRingkasTersimpan)
        txtJumlah = findViewById(R.id.txtJumlahTersimpan)

        adapter = HalamanTersimpanAdapter(daftar, onTap = { arsip ->
            setResult(
                RESULT_OK,
                Intent().putExtra("idPosting", arsip.idPosting).putExtra("sumberArsip", arsip.sumberArsip)
            )
            finish()
        }, onHapus = { arsip -> hapusDariTersimpan(arsip) })
        rvHalamanTersimpan.adapter = adapter

        muatDaftarTersimpan()
    }

    // Nama file & key HARUS sama persis dgn MainActivity.prefsBaca()/idBookmarkTersimpan().
    private fun prefs() = getSharedPreferences("preferensi_baca", MODE_PRIVATE)
    private fun idBookmarkTersimpan(): Set<String> =
        prefs().getStringSet("bookmark_ids", emptySet()) ?: emptySet()

    private fun perbaruiTampilan() {
        findViewById<View>(R.id.spinnerTersimpan).visibility = View.GONE
        val kosong = daftar.isEmpty()
        wadahKosong.visibility = if (kosong) View.VISIBLE else View.GONE
        rvHalamanTersimpan.visibility = if (kosong) View.GONE else View.VISIBLE
        wadahRingkas.visibility = if (kosong) View.GONE else View.VISIBLE
        txtJumlah.text = "${daftar.size} status tersimpan"
    }

    private fun muatDaftarTersimpan() {
        val ids = idBookmarkTersimpan()
        if (ids.isEmpty()) { perbaruiTampilan(); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val database = ArsipDatabase.operasikanMesin(this@HalamanTersimpanActivity).arsipDao()
            // chunked: batas variabel SQLite (999) pada Android lama
            val tersimpan = ids.toList().chunked(500).flatMap { database.ambilBerdasarkanId(it) }
                .sortedByDescending { it.waktuRilis }
            withContext(Dispatchers.Main) {
                daftar.clear(); daftar.addAll(tersimpan)
                adapter.notifyDataSetChanged()
                perbaruiTampilan()
            }
        }
    }

    /** Satu-satunya tempat menghapus status tersimpan (ikon bintang di mode baca hanya menambah). */
    private fun hapusDariTersimpan(arsip: ArsipEntity) {
        val posisi = daftar.indexOfFirst { it.idPosting == arsip.idPosting }
        if (posisi < 0) return
        val set = idBookmarkTersimpan().toMutableSet().apply { remove(arsip.idPosting) }
        prefs().edit().putStringSet("bookmark_ids", set).apply()
        daftar.removeAt(posisi)
        adapter.notifyItemRemoved(posisi)
        perbaruiTampilan()
        com.google.android.material.snackbar.Snackbar
            .make(rvHalamanTersimpan, "Dihapus dari tersimpan", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAction("URUNGKAN") {
                val lagi = idBookmarkTersimpan().toMutableSet().apply { add(arsip.idPosting) }
                prefs().edit().putStringSet("bookmark_ids", lagi).apply()
                val tujuan = posisi.coerceAtMost(daftar.size)
                daftar.add(tujuan, arsip)
                adapter.notifyItemInserted(tujuan)
                perbaruiTampilan()
            }
            .setActionTextColor(android.graphics.Color.parseColor("#E8C77A"))
            .setBackgroundTint(android.graphics.Color.parseColor("#3A2313"))
            .show()
    }
}

class HalamanTersimpanAdapter(
    private val data: MutableList<ArsipEntity>,
    private val onTap: (ArsipEntity) -> Unit,
    private val onHapus: (ArsipEntity) -> Unit
) : RecyclerView.Adapter<HalamanTersimpanAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val txtTanggal: TextView = view.findViewById(R.id.txtTanggalTersimpan)
        val txtSumber: TextView = view.findViewById(R.id.txtSumberTersimpan)
        val txtKategori: TextView = view.findViewById(R.id.txtKategoriTersimpan)
        val txtIsi: TextView = view.findViewById(R.id.txtIsiTersimpan)
        val wadah: View = view.findViewById(R.id.wadahItemTersimpan)
        val btnHapus: View = view.findViewById(R.id.btnHapusTersimpan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_halaman_tersimpan, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val arsip = data[position]
        holder.txtTanggal.text = arsip.tanggalBaca.substringBefore(" ")
        holder.txtSumber.text = arsip.namaPenulis.ifBlank { "Fatwa Kehidupan" }
        if (arsip.kategori.isNullOrBlank()) {
            holder.txtKategori.visibility = View.GONE
        } else {
            holder.txtKategori.visibility = View.VISIBLE
            holder.txtKategori.text = arsip.kategori
        }
        holder.txtIsi.text = arsip.kontenPenuh.trim()
        holder.wadah.setOnClickListener { onTap(arsip) }
        holder.btnHapus.setOnClickListener { onHapus(arsip) }
    }

    override fun getItemCount(): Int = data.size
}
