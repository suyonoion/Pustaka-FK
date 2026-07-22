package com.fk.arsip

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TimelineAdapter(
    private val daftarTitik: List<TitikNavigasi>,
    private val pemicuLompat: (Int) -> Unit
) : RecyclerView.Adapter<TimelineAdapter.TitikViewHolder>() {

    class TitikViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtLabel: TextView = view.findViewById(R.id.txtLabelTimeline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TitikViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false)
        return TitikViewHolder(view)
    }

    override fun onBindViewHolder(holder: TitikViewHolder, position: Int) {
    val titik = daftarTitik[position]
    holder.txtLabel.text = titik.teks

    if (titik.tipe == 0) {
        // TAHUN: Tanpa latar kartu, teks menempel langsung
        holder.txtLabel.textSize = 11f // atau set ukuran via sp
        holder.txtLabel.setTextColor(Color.parseColor("#004D40")) 
        holder.txtLabel.setTypeface(null, android.graphics.Typeface.BOLD)
        holder.txtLabel.setBackgroundColor(Color.TRANSPARENT)
        
        holder.itemView.setOnClickListener(null)
        holder.itemView.isClickable = false
    } else {
        // BULAN: Kartu warna selang-seling
        holder.txtLabel.textSize = 10f
        holder.txtLabel.setTextColor(Color.WHITE)
        holder.txtLabel.setTypeface(null, android.graphics.Typeface.BOLD)
        
        // SUNTIKKAN BACKGROUND DRWAABLE SELANG-SELING
        if (titik.warnaGenap) {
            holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_genap)
        } else {
            holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_ganjil)
        }

        holder.itemView.setOnClickListener {
            pemicuLompat(titik.indeksTujuan)
        }
        holder.itemView.isClickable = true
    }
}



    override fun getItemCount(): Int = daftarTitik.size
}
