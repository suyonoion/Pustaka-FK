package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter statis 1-item untuk halaman SAMPUL (depan atau belakang) yang
 * ditempelkan ke ViewPager2 lewat ConcatAdapter, di depan & belakang
 * BukuAdapter (yang isinya arsip sungguhan dari Paging3).
 *
 * PENTING kalau dipasang: karena ConcatAdapter menggabungkan sampul depan +
 * bukuAdapter + sampul belakang jadi satu deret posisi, SETIAP pemanggilan
 * proyektorBuku.setCurrentItem(posisi, ...) yang sebelumnya memakai index
 * arsip mentah HARUS ditambah offset +1 (kalau sampul depan dipasang) supaya
 * tetap menunjuk ke arsip yang benar. Lihat MainActivity.OFFSET_SAMPUL_DEPAN.
 */
class SampulAdapter(
    private val judul: String,
    private val subjudul: String,
    private val layoutRes: Int
) : RecyclerView.Adapter<SampulAdapter.SampulViewHolder>() {

    class SampulViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtJudul: TextView? = view.findViewById(R.id.txtJudulSampul)
        val txtSubjudul: TextView? = view.findViewById(R.id.txtSubjudulSampul)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SampulViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return SampulViewHolder(view)
    }

    override fun onBindViewHolder(holder: SampulViewHolder, position: Int) {
        holder.txtJudul?.text = judul
        holder.txtSubjudul?.text = subjudul
    }

    override fun getItemCount(): Int = 1
}
