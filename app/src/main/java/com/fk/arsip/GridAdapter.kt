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

// Struktur untuk rel timeline kanan
data class TitikNavigasi(val labelBulan: String, val indeksTujuan: Int)

class GridAdapter(
    private var daftarKargo: List<KargoCampuran>,
    private val pemicuBuku: (Int) -> Unit // Mengirim posisi asli (absolut) ke ViewPager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TIPE_PEMBATAS = 0
        const val TIPE_KONTEN = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (daftarKargo[position]) {
            is KargoCampuran.PembatasWaktu -> TIPE_PEMBATAS
            is KargoCampuran.StatusKonten -> TIPE_KONTEN
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
            holder.txtCuplikan.text = arsip.kontenPenuh

            holder.itemView.setOnClickListener {
                pemicuBuku(material.posisiAsli) // Buka ViewPager pada indeks arsip murni
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
    }
}
