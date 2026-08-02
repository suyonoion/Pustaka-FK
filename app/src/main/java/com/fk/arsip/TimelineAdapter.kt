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
            holder.txtLabel.text = titik.teks
            holder.txtLabel.textSize = 11f
            holder.txtLabel.setTextColor(Color.parseColor("#FFFFFF"))
            holder.txtLabel.setTypeface(null, Typeface.BOLD)
            holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_tahun)

            holder.itemView.isClickable = false
            holder.itemView.setOnClickListener(null)

        } else if (holder is BulanViewHolder && titik.tipe == TIPE_BULAN) {
            holder.txtLabel.text = titik.teks
            holder.txtLabel.textSize = 10f
            holder.txtLabel.setTypeface(null, Typeface.BOLD)

            holder.txtLabel.setPadding(
                (2 * skala).toInt(),
                (5 * skala).toInt(),
                (2 * skala).toInt(),
                (5 * skala).toInt()
            )

            if (position == posisiTerpilih) {
                // Aktif: pakai warna clickable kita tadi #B2DFDB
                holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_aktif)
                holder.txtLabel.setTextColor(ContextCompat.getColor(ctx, R.color.timeline_pil_aktif_text)) // #263238

            } else {
                if (titik.warnaGenap) {
                    // Genap: #E0F2F1
                    holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_genap)
                    holder.txtLabel.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                } else {
                    // Ganjil: #F0FDFD
                    holder.txtLabel.setBackgroundResource(R.drawable.bg_timeline_ganjil)
                    holder.txtLabel.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                }
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