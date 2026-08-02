package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class ItemBiografi {
    data class BioText(val judul: String, val isi: String) : ItemBiografi()
    data class BioStatus(val isi: String) : ItemBiografi()
    data class BioQuote(val quote: String, val sumber: String) : ItemBiografi()
}

class BiografiAdapter(
    private val list: List<ItemBiografi>,
    private var textSizeSp: Float = 12f // Default ukuran teks isi (12sp)
) : RecyclerView.Adapter<BiografiAdapter.BaseViewHolder>() {

    abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class PeristiwaViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val tvJudulPeristiwa: TextView = itemView.findViewById(R.id.tvJudulPeristiwa)
        val tvIsiPeristiwa: TextView = itemView.findViewById(R.id.tvIsiPeristiwa)
    }

    class StatusViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val tvStatusIsi: TextView = itemView.findViewById(R.id.tvStatusIsi)
    }

    class QuoteViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val tvQuoteIsi: TextView = itemView.findViewById(R.id.tvQuoteIsi)
        val tvQuoteSumber: TextView = itemView.findViewById(R.id.tvQuoteSumber)
    }

    // Fungsi untuk memperbarui ukuran teks secara dinamis
    fun updateTextSize(newSizeSp: Float) {
        this.textSizeSp = newSizeSp
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (list[position]) {
            is ItemBiografi.BioText -> 0
            is ItemBiografi.BioStatus -> 1
            is ItemBiografi.BioQuote -> 2
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val wrapper = inflater.inflate(R.layout.item_biografi_dengan_penghubung, parent, false)
        val container = wrapper.findViewById<LinearLayout>(R.id.cardContainer)

        return when (viewType) {
            0 -> {
                val view = inflater.inflate(R.layout.item_peristiwa_biografi, container, false)
                container.addView(view)
                PeristiwaViewHolder(wrapper)
            }
            1 -> {
                val view = inflater.inflate(R.layout.item_status_biografi, container, false)
                container.addView(view)
                StatusViewHolder(wrapper)
            }
            2 -> {
                val view = inflater.inflate(R.layout.item_quote_biografi, container, false)
                container.addView(view)
                QuoteViewHolder(wrapper)
            }
            else -> throw IllegalArgumentException("ViewType tidak valid")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        // Kontrol garis jaringan
        val lineTop = holder.itemView.findViewById<View>(R.id.lineTop)
        val lineBottom = holder.itemView.findViewById<View>(R.id.lineBottom)
        lineTop?.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        lineBottom?.visibility = if (position == list.size - 1) View.INVISIBLE else View.VISIBLE

        // Distribusi data & Penerapan Ukuran Teks
        when (val item = list[position]) {
            is ItemBiografi.BioText -> {
                holder as PeristiwaViewHolder
                holder.tvJudulPeristiwa.text = item.judul
                holder.tvIsiPeristiwa.text = item.isi
                holder.tvIsiPeristiwa.textSize = textSizeSp
            }
            is ItemBiografi.BioStatus -> {
                holder as StatusViewHolder
                holder.tvStatusIsi.text = item.isi
                holder.tvStatusIsi.textSize = textSizeSp
            }
            is ItemBiografi.BioQuote -> {
                holder as QuoteViewHolder
                holder.tvQuoteIsi.text = item.quote
                holder.tvQuoteSumber.text = item.sumber
                holder.tvQuoteIsi.textSize = textSizeSp
            }
        }
    }

    override fun getItemCount(): Int = list.size
}
