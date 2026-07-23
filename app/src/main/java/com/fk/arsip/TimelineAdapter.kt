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

    // Variabel penyimpan posisi item bulan yang sedang aktif dipilih
    private var posisiTerpilih: Int = -1

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
val skala = holder.itemView.context.resources.displayMetrics.density

if (titik.tipe == 0) {
    // TAHUN
    holder.txtLabel.textSize = 12f
    holder.txtLabel.setTextColor(android.graphics.Color.parseColor("#004D40")) 
    holder.txtLabel.setTypeface(null, android.graphics.Typeface.BOLD)
    holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_tahun)
    
    // Injeksi Ruang Vektor Lancip: Kiri ekstra lebar untuk menghindari tabrakan dengan moncong lancip
    holder.txtLabel.setPadding(
        (12 * skala).toInt(), 
        (4 * skala).toInt(), 
        (8 * skala).toInt(), 
        (4 * skala).toInt()
    )
    
    holder.itemView.setOnClickListener(null)
    holder.itemView.isClickable = false
} else {
    // BULAN
    holder.txtLabel.textSize = 10f
    holder.txtLabel.setTypeface(null, android.graphics.Typeface.BOLD)
    
    // Injeksi Ruang Kapsul Normal (Menggantikan fungsi padding di XML lama)
    holder.txtLabel.setPadding(
        (2 * skala).toInt(), 
        (5 * skala).toInt(), 
        (2 * skala).toInt(), 
        (5 * skala).toInt()
    )

    // Evaluasi Status Aktif/Pasif
    if (position == posisiTerpilih) {
        holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_aktif)
        holder.txtLabel.setTextColor(android.graphics.Color.WHITE)
    } else {
        if (titik.warnaGenap) {
            holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_genap)
            holder.txtLabel.setTextColor(android.graphics.Color.parseColor("#004D40"))
        } else {
            holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_ganjil)
            holder.txtLabel.setTextColor(android.graphics.Color.parseColor("#424242"))
        }
    }

        // KATUP EKSEKUSI KLIK
    holder.itemView.setOnClickListener {
        val posisiLama = posisiTerpilih
        
        // Gunakan adapterPosition sebagai substitusi mesin lama
        val posisiBaru = holder.adapterPosition
        
        // Katup pengaman: cegah transmisi jika item sudah tidak ada di lintasan
        if (posisiBaru != RecyclerView.NO_POSITION) {
            posisiTerpilih = posisiBaru
            
            // Refresh visual item lama dan item baru
            notifyItemChanged(posisiLama)
            notifyItemChanged(posisiTerpilih)
            
            pemicuLompat(titik.indeksTujuan)
        }
    }
    holder.itemView.isClickable = true
}

    }

    override fun getItemCount(): Int = daftarTitik.size
}
