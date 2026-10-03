package com.fk.arsip

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TimelineAdapter(
    private val daftarTitik: List<TitikNavigasi>,
    private val pemicuLompat: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var posisiTerpilih: Int = -1

    companion object {
        const val TIPE_TAHUN = 0
        const val TIPE_BULAN = 1
    }

    class TahunViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtLabel: TextView = view.findViewById(R.id.txtLabelTimeline)
    }

    class BulanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtLabel: TextView = view.findViewById(R.id.txtLabelTimeline)
    }

    override fun getItemViewType(position: Int): Int {
        return daftarTitik[position].tipe
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TIPE_TAHUN) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false)
            TahunViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline_bulan, parent, false)
            BulanViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val titik = daftarTitik[position]
        val ctx = holder.itemView.context
        val skala = ctx.resources.displayMetrics.density

        if (holder is TahunViewHolder && titik.tipe == TIPE_TAHUN) {
            // Tahun = biji tasbih agak runcing
            holder.txtLabel.text = titik.teks
            holder.txtLabel.textSize = 12f
            holder.txtLabel.setTextColor(Color.parseColor("#F0CF94"))
            holder.txtLabel.setTypeface(null, Typeface.BOLD)
            holder.txtLabel.background = ManikDrawable(ManikDrawable.Gaya.RUNCING)

            holder.itemView.isClickable = false
            holder.itemView.setOnClickListener(null)

        } else if (holder is BulanViewHolder && titik.tipe == TIPE_BULAN) {
            // Bulan = biji tasbih bulat; terpilih = emas menyala
            holder.txtLabel.text = titik.teks
            holder.txtLabel.textSize = 9f
            holder.txtLabel.setTypeface(null, Typeface.BOLD)

            if (position == posisiTerpilih) {
                holder.txtLabel.background = ManikDrawable(ManikDrawable.Gaya.MENYALA)
                holder.txtLabel.setTextColor(Color.parseColor("#4A2A0C"))
            } else {
                holder.txtLabel.background =
                    ManikDrawable(ManikDrawable.Gaya.KAYU, if (titik.warnaGenap) 0 else 1)
                holder.txtLabel.setTextColor(Color.parseColor("#F5E3BC"))
            }

            holder.itemView.setOnClickListener {
                val posisiLama = posisiTerpilih
                val posisiBaru = holder.bindingAdapterPosition

                if (posisiBaru!= RecyclerView.NO_POSITION) {
                    posisiTerpilih = posisiBaru
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