package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fk.arsip.database.ArsipEntity

// TAHAP 2: Kontainer Universal (Sealed Class)
sealed class KargoCampuran {
    data class PembatasWaktu(val label: String) : KargoCampuran()
    data class StatusKonten(val data: ArsipEntity, val posisiAsli: Int) : KargoCampuran()
}



class GridAdapter(
    private var daftarKargo: List<KargoCampuran>,
    private val pemicuBuku: (Int) -> Unit // Mengirim posisi asli (absolut) ke ViewPager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TIPE_PEMBATAS = 0
        const val TIPE_KONTEN = 1
    }

        override fun getItemViewType(position: Int): Int {
        // Akses langsung list via index tanpa logika bercabang yang rumit
        return when (daftarKargo[position]) {
            is KargoCampuran.PembatasWaktu -> TIPE_PEMBATAS
            else -> TIPE_KONTEN // TIPE_KONTEN adalah default, memangkas pengecekan tipe
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
        val material = daftarKargo[position]
        
        if (holder is HeaderViewHolder && material is KargoCampuran.PembatasWaktu) {
            holder.txtHeader.text = material.label
        } 
                else if (holder is KontenViewHolder && material is KargoCampuran.StatusKonten) {
            val arsip = material.data
            holder.txtKategori.text = arsip.kategori
            holder.txtTanggal.text = arsip.tanggalBaca.substringBefore(" ")
            
            // PENGELASAN: Potong string agar tidak membebani layout
            val cuplikan = if (arsip.kontenPenuh.length > 100) {
                arsip.kontenPenuh.substring(0, 100) + "..."
            } else {
                arsip.kontenPenuh
            }
            // HANYA PASANG SATU KALI:
            holder.txtCuplikan.text = cuplikan 
            
            // HAPUS: holder.txtCuplikan.text = arsip.kontenPenuh  <-- INI PENYEBAB FREEZE
            
            val nomorUrut = material.posisiAsli + 1 
            holder.txtIndeksGrid.text = "#$nomorUrut"

            holder.itemView.setOnClickListener {
                pemicuBuku(material.posisiAsli) 
            }
        }

    }

    override fun getItemCount(): Int = daftarKargo.size

    fun perbaruiData(dataBaru: List<KargoCampuran>) {
        this.daftarKargo = dataBaru
        notifyDataSetChanged()
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtHeader: TextView = view.findViewById(R.id.txtHeaderWaktu)
    }

        class KontenViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProfil: ImageView = view.findViewById(R.id.imgGridProfil)
        val txtTanggal: TextView = view.findViewById(R.id.txtGridTanggal)
        val txtKategori: TextView = view.findViewById(R.id.txtGridKategori)
        val txtCuplikan: TextView = view.findViewById(R.id.txtGridCuplikan)
        // INJEKSI KABEL PENGIKAT
        val txtIndeksGrid: TextView = view.findViewById(R.id.txtIndeksGrid) 
    }

}
