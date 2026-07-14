package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fk.arsip.database.ArsipEntity

class GridAdapter(
    private var daftarArsip: List<ArsipEntity>,
    private val pemicuBuku: (Int) -> Unit 
) : RecyclerView.Adapter<GridAdapter.GridViewHolder>() {

    class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProfil = view.findViewById<ImageView>(R.id.imgGridProfil)
        val txtTanggal = view.findViewById<TextView>(R.id.txtGridTanggal)
        val txtKategori = view.findViewById<TextView>(R.id.txtGridKategori)
        val txtCuplikan = view.findViewById<TextView>(R.id.txtGridCuplikan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid, parent, false)
        return GridViewHolder(view)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val arsip = daftarArsip[position]
        
        // Penyelarasan arus variabel ke soket penerima ViewHolder
        holder.txtKategori.text = arsip.kategori
        holder.txtTanggal.text = arsip.tanggalBaca.substringBefore(" ") 
        holder.txtCuplikan.text = arsip.kontenPenuh

        holder.itemView.setOnClickListener {
            pemicuBuku(position)
        }
    }

    override fun getItemCount(): Int = daftarArsip.size

    fun perbaruiData(dataBaru: List<ArsipEntity>) {
        this.daftarArsip = dataBaru
        notifyDataSetChanged()
    }
}
