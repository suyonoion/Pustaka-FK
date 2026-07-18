package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Komponen ini menggerakkan rel bulan ("Jan", "Feb") di sisi kanan layar
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
        holder.txtLabel.text = titik.labelBulan
        
        // Katup pengirim sinyal lompatan ke layar utama (Grid)
        holder.itemView.setOnClickListener { 
            pemicuLompat(titik.indeksTujuan) 
        }
    }

    override fun getItemCount(): Int = daftarTitik.size
}
