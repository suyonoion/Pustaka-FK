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
    private lateinit var txtKosong: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_halaman_tersimpan)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarHalamanTersimpan)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rvHalamanTersimpan = findViewById(R.id.rvHalamanTersimpan)
        rvHalamanTersimpan.layoutManager = LinearLayoutManager(this)
        txtKosong = findViewById(R.id.txtHalamanTersimpanKosong)

        muatDaftarTersimpan()
    }

    // Nama file & key HARUS sama persis dgn MainActivity.prefsBaca()/idBookmarkTersimpan().
    private fun idBookmarkTersimpan(): Set<String> =
        getSharedPreferences("preferensi_baca", MODE_PRIVATE).getStringSet("bookmark_ids", emptySet()) ?: emptySet()

    private fun muatDaftarTersimpan() {
        val ids = idBookmarkTersimpan()
        if (ids.isEmpty()) {
            txtKosong.visibility = View.VISIBLE
            rvHalamanTersimpan.visibility = View.GONE
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val database = ArsipDatabase.operasikanMesin(this@HalamanTersimpanActivity).arsipDao()
            val semuaArsip = database.tarikSemuaArsip()
            val tersimpan = semuaArsip.filter { it.idPosting in ids }
                .sortedByDescending { it.waktuRilis }
            withContext(Dispatchers.Main) {
                if (tersimpan.isEmpty()) {
                    txtKosong.visibility = View.VISIBLE
                    rvHalamanTersimpan.visibility = View.GONE
                } else {
                    txtKosong.visibility = View.GONE
                    rvHalamanTersimpan.visibility = View.VISIBLE
                    rvHalamanTersimpan.adapter = HalamanTersimpanAdapter(tersimpan) { arsip ->
                        setResult(
                            RESULT_OK,
                            Intent().putExtra("idPosting", arsip.idPosting).putExtra("sumberArsip", arsip.sumberArsip)
                        )
                        finish()
                    }
                }
            }
        }
    }
}

private class HalamanTersimpanAdapter(
    private val data: List<ArsipEntity>,
    private val onTap: (ArsipEntity) -> Unit
) : RecyclerView.Adapter<HalamanTersimpanAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val txtTanggal: TextView = view.findViewById(R.id.txtTanggalTersimpan)
        val txtIsi: TextView = view.findViewById(R.id.txtIsiTersimpan)
        val wadah: View = view.findViewById(R.id.wadahItemTersimpan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_halaman_tersimpan, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val arsip = data[position]
        holder.txtTanggal.text = arsip.tanggalBaca.substringBefore(" ")
        holder.txtIsi.text = arsip.kontenPenuh.trim()
        holder.wadah.setOnClickListener { onTap(arsip) }
    }

    override fun getItemCount(): Int = data.size
}
