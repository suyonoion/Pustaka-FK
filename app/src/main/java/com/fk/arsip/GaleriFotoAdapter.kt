package com.fk.arsip

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.InputStream

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

        try {
            val inputStream: InputStream = holder.itemView.context.assets.open(item.fotoPath)
            val drawable = Drawable.createFromStream(inputStream, null)
            holder.imgFotoAbah.setImageDrawable(drawable)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // AKSI KLIK UNTUK MEMBUKA DETAIL FOTO
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = listFoto.size

    fun updateData(newList: List<GaleriFoto>) {
        listFoto = newList
        notifyDataSetChanged()
    }
}
