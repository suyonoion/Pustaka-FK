package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fk.arsip.database.ArsipEntity

class BukuAdapter(private var daftarArsip: List<ArsipEntity>) : RecyclerView.Adapter<BukuAdapter.BukuViewHolder>() {

    class BukuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNamaPenulis: TextView = view.findViewById(R.id.txtNamaPenulis)
        val txtTanggalWaktu: TextView = view.findViewById(R.id.txtTanggalWaktu)
        val txtKategori: TextView = view.findViewById(R.id.txtKategori)
        val txtIsiKonten: TextView = view.findViewById(R.id.txtIsiKonten)
        val wadahFotoPenuh: LinearLayout = view.findViewById(R.id.wadahFotoPenuh)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BukuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_buku, parent, false)
        return BukuViewHolder(view)
    }

    override fun onBindViewHolder(holder: BukuViewHolder, position: Int) {
        val arsip = daftarArsip[position]

        holder.txtNamaPenulis.text = arsip.namaPenulis
        holder.txtTanggalWaktu.text = arsip.tanggalBaca
        holder.txtKategori.text = arsip.kategori
        holder.txtIsiKonten.text = arsip.kontenPenuh

        // Mesin Pemutar Visual (Sampul Gambar)
        holder.wadahFotoPenuh.removeAllViews()
        if (arsip.daftarFoto.isNotEmpty()) {
            holder.wadahFotoPenuh.visibility = View.VISIBLE
            val tautanGambar = arsip.daftarFoto.split(",").firstOrUrl() // Mengambil gambar pertama
            
            val injeksiGambar = ImageView(holder.itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    800 // Tinggi statis proyektor gambar
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            
            Glide.with(holder.itemView.context)
                .load(tautanGambar)
                .into(injeksiGambar)
                
            holder.wadahFotoPenuh.addView(injeksiGambar)
        } else {
            holder.wadahFotoPenuh.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = daftarArsip.size

    private fun String.firstOrUrl(): String {
        return this.trim()
    }
}
