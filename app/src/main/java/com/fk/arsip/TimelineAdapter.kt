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
            // KALIBRASI TAHUN: Teks lebih besar, tebal, tanpa warna latar, klik dinonaktifkan
            holder.txtLabel.textSize = 12f
            holder.txtLabel.setTextColor(Color.parseColor("#004D40")) 
            holder.txtLabel.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.txtLabel.setBackgroundColor(Color.TRANSPARENT)
            
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        } else {
            // KALIBRASI BULAN: Teks standar, background selang-seling tipis, klik diaktifkan
            holder.txtLabel.textSize = 10f
            holder.txtLabel.setTextColor(Color.parseColor("#555555"))
            holder.txtLabel.setTypeface(null, android.graphics.Typeface.NORMAL)
            
            // Mekanisme Warna Selang-Seling
            if (titik.warnaGenap) {
                holder.txtLabel.setBackgroundColor(Color.parseColor("#E0F2F1")) // Hijau sangat pudar
            } else {
                holder.txtLabel.setBackgroundColor(Color.parseColor("#FAFAFA")) // Putih keabuan
            }

            holder.itemView.setOnClickListener {
                pemicuLompat(titik.indeksTujuan)
            }
            holder.itemView.isClickable = true
        }
    }

    override fun getItemCount(): Int = daftarTitik.size
}
