package com.fk.arsip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GaleriFotoAdapter(
    private var listFoto: List<GaleriFoto>,
    private val onItemClick: (GaleriFoto) -> Unit
) : RecyclerView.Adapter<GaleriFotoAdapter.GaleriViewHolder>() {

    class GaleriViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtSubKategori: TextView = itemView.findViewById(R.id.txtSubKategori)
        val txtNomorFoto: TextView = itemView.findViewById(R.id.txtNomorFoto)
        val imgFotoAbah: ImageView = itemView.findViewById(R.id.imgFotoAbah)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GaleriViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_galeri_grid, parent, false)
        return GaleriViewHolder(view)
    }

    override fun onBindViewHolder(holder: GaleriViewHolder, position: Int) {
        val item = listFoto[position]
        
        holder.txtSubKategori.text = item.subKategori.uppercase()
        holder.txtNomorFoto.text = item.nomorFoto

        // PERBAIKAN EFISIENSI: sebelumnya setiap bind memanggil assets.open()
        // + Drawable.createFromStream() secara SINKRON di main thread -> tiap
        // kali RecyclerView me-recycle item saat scroll, UI thread ikut nge-
        // block untuk decode gambar penuh resolusi. Glide (sudah dipakai di
        // BukuAdapter) men-decode di background thread, otomatis downsample
        // sesuai ukuran ImageView, dan meng-cache hasilnya di memori/disk.
        Glide.with(holder.itemView.context)
            .load("file:///android_asset/${item.fotoPath}")
            .centerCrop()
            .into(holder.imgFotoAbah)

        // AKSI KLIK UNTUK MEMBUKA DETAIL FOTO
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = listFoto.size

    override fun onViewRecycled(holder: GaleriViewHolder) {
        super.onViewRecycled(holder)
        // Batalkan request Glide yang belum selesai agar tidak menimpa
        // gambar item lain saat holder ini dipakai ulang.
        Glide.with(holder.itemView.context).clear(holder.imgFotoAbah)
    }

    fun updateData(newList: List<GaleriFoto>) {
        listFoto = newList
        notifyDataSetChanged()
    }
}
