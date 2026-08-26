package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fk.arsip.database.ArsipEntity
import com.google.android.material.imageview.ShapeableImageView // IMPORT TAMBAHAN UNTUK PROFIL BULAT

// TAHAP 2: Kontainer Universal (Sealed Class)
sealed class KargoCampuran {
    data class PembatasWaktu(val label: String) : KargoCampuran()
    data class StatusKonten(val data: ArsipEntity, val posisiAsli: Int) : KargoCampuran()
}

// PERBAIKAN EFISIENSI: GridAdapter sekarang ListAdapter (DiffUtil) alih-alih
// notifyDataSetChanged() penuh setiap ganti data. Untuk list beranggota ribuan
// item (mode "Semua Kategori"), DiffUtil menghitung perbedaan di background
// thread (AsyncListDiffer) dan hanya me-refresh baris yang benar-benar berubah,
// bukan memaksa RecyclerView menganggap semuanya baru.
class GridAdapter(
    private val pemicuBuku: (Int) -> Unit
) : ListAdapter<KargoCampuran, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val TIPE_PEMBATAS = 0
        const val TIPE_KONTEN = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<KargoCampuran>() {
            override fun areItemsTheSame(oldItem: KargoCampuran, newItem: KargoCampuran): Boolean {
                return when {
                    oldItem is KargoCampuran.PembatasWaktu && newItem is KargoCampuran.PembatasWaktu ->
                        oldItem.label == newItem.label
                    oldItem is KargoCampuran.StatusKonten && newItem is KargoCampuran.StatusKonten ->
                        oldItem.data.idPosting == newItem.data.idPosting
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: KargoCampuran, newItem: KargoCampuran): Boolean =
                oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is KargoCampuran.PembatasWaktu -> TIPE_PEMBATAS
            else -> TIPE_KONTEN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TIPE_PEMBATAS) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header_timeline, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid, parent, false)
            KontenViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val material = getItem(position)
        
        if (holder is HeaderViewHolder && material is KargoCampuran.PembatasWaktu) {
            holder.txtHeader.text = material.label
        } 
                else if (holder is KontenViewHolder && material is KargoCampuran.StatusKonten) {
            val arsip = material.data
            
            // 1. PENYESUAIAN KATEGORI & TANGGAL
            holder.txtTanggal.text = arsip.tanggalBaca.substringBefore(" ")
            
            if (arsip.kategori.isNullOrEmpty()) {
                holder.txtKategori.visibility = View.GONE
            } else {
                holder.txtKategori.visibility = View.VISIBLE
                holder.txtKategori.text = arsip.kategori
            }
            
            // 2. PENGELASAN CUPLIKAN TEKS (Dikalibrasi ke batas 200 karakter untuk ruang 5 baris)
            val cuplikan = if (arsip.kontenPenuh.length > 200) {
                arsip.kontenPenuh.substring(0, 200) + "..."
            } else {
                arsip.kontenPenuh
            }
            holder.txtCuplikan.text = cuplikan 
            
            // 3. NOMOR URUT INDEKS
            val nomorUrut = material.posisiAsli + 1 
            holder.txtIndeksGrid.text = "#$nomorUrut"

            // 4. ESEKUSI KLIK ITEM
            holder.itemView.setOnClickListener {
                pemicuBuku(material.posisiAsli) 
            }
        }
    }

    fun perbaruiData(dataBaru: List<KargoCampuran>, afterSubmit: (() -> Unit)? = null) {
        // PERBAIKAN BUG: GridLayoutManager meng-cache posisi span-index,
        // dan cache ini HANYA di-invalidate otomatis saat notifyDataSetChanged()
        // penuh (mekanisme adapter lama). Dengan ListAdapter + DiffUtil, cache
        // tidak ter-invalidate otomatis -> grid bisa macet saat data berubah drastis
        // (filter/kategori/pencarian). submitList() menerima callback yang dipanggil
        // SETELAH diff selesai diproses; callback ini digunakan untuk invalidasi cache.
        //
        // PERBAIKAN PERFORMA: setiap ganti pencarian/filter/kategori adalah
        // pergantian TOTAL konteks data (daftar lama & baru nyaris tidak
        // beririsan sama sekali). Kalau langsung submitList(dataBaru), AsyncListDiffer
        // menjalankan algoritma Myers diff antara dua daftar besar yang hampir
        // seluruhnya berbeda -- itu skenario mendekati kasus terburuk DiffUtil
        // (biaya bisa mendekati O(N*M)), dan inilah kontributor utama jeda lama
        // saat ganti kategori/pencarian. Solusinya: submitList(null) dulu supaya
        // AsyncListDiffer diff dari "kosong", lalu submitList(dataBaru) diff dari
        // kosong ke isi penuh -- itu O(N), jauh lebih murah daripada diff
        // "penuh-ke-penuh-yang-berbeda-total".
        submitList(null) {
            submitList(dataBaru, afterSubmit)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtHeader: TextView = view.findViewById(R.id.txtHeaderWaktu)
    }

    class KontenViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // PENYESUAIAN TIPE DATA: Menggunakan ShapeableImageView untuk foto profil bulat
        val imgProfil: ShapeableImageView = view.findViewById(R.id.imgGridProfil)
        val txtTanggal: TextView = view.findViewById(R.id.txtGridTanggal)
        val txtKategori: TextView = view.findViewById(R.id.txtGridKategori)
        val txtCuplikan: TextView = view.findViewById(R.id.txtGridCuplikan)
        val txtIndeksGrid: TextView = view.findViewById(R.id.txtIndeksGrid) 
    }
}

