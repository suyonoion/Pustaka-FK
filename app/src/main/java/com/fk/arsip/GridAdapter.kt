package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fk.arsip.database.ArsipEntity

class GridAdapter(
    private var daftarArsip: List<ArsipEntity>,
    private val pemicuBuku: (Int) -> Unit // Tuas pelontar ke mode buku
) : RecyclerView.Adapter<GridAdapter.GridViewHolder>() {

    class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtGridKategori: TextView = view.findViewById(R.id.txtGridKategori)
        val txtGridTanggal: TextView = view.findViewById(R.id.txtGridTanggal)
        val txtGridCuplikan: TextView = view.findViewById(R.id.txtGridCuplikan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid, parent, false)
        return GridViewHolder(view)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val arsip = daftarArsip[position]
        
        holder.txtGridKategori.text = arsip.kategori
        // Memotong jam mutlak agar tata letak kotak tidak pecah
        holder.txtGridTanggal.text = arsip.tanggalBaca.substringBefore(" ") 
        holder.txtGridCuplikan.text = arsip.kontenPenuh

        holder.itemView.setOnClickListener {
            pemicuBuku(position)
        }
    }

    override fun getItemCount(): Int = daftarArsip.size

    // Katup pembaruan saat mesin pencari bekerja
    fun perbaruiData(dataBaru: List<ArsipEntity>) {
        this.daftarArsip = dataBaru
        notifyDataSetChanged()
    }
}
