package com.fk.arsip

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fk.arsip.database.ArsipEntity

class BukuAdapter(private var daftarArsip: List<ArsipEntity>) : RecyclerView.Adapter<BukuAdapter.BukuViewHolder>() {

    class BukuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNamaPenulis: TextView = view.findViewById(R.id.txtNamaPenulis)
        val txtTanggalWaktu: TextView = view.findViewById(R.id.txtTanggalWaktu)
        val txtKategori: TextView = view.findViewById(R.id.txtKategori)
        val txtIsiKonten: TextView = view.findViewById(R.id.txtIsiKonten)
        val imgProfilBuku: ImageView = view.findViewById(R.id.imgProfilBuku)
        val btnSumberTautan: Button = view.findViewById(R.id.btnSumberTautan)
        val wadahFotoPenuh: LinearLayout = view.findViewById(R.id.wadahFotoPenuh)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BukuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_buku, parent, false)
        return BukuViewHolder(view)
    }

    override fun onBindViewHolder(holder: BukuViewHolder, position: Int) {
        val arsip = daftarArsip[position]

        holder.txtNamaPenulis.text = arsip.namaPenulis
        holder.txtTanggalWaktu.text = arsip.tanggalBaca
        holder.txtKategori.text = arsip.kategori
        holder.txtIsiKonten.text = arsip.kontenPenuh

        // Render Foto Profil Penulis
        if (arsip.urlProfilPic.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(arsip.urlProfilPic)
                .circleCrop()
                .into(holder.imgProfilBuku)
        } else {
            holder.imgProfilBuku.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // Pipa Akses Sumber Tautan Asli
        holder.btnSumberTautan.setOnClickListener {
            if (arsip.tautanAsli.isNotBlank()) {
                try {
                    val intentBrowser = Intent(Intent.ACTION_VIEW, Uri.parse(arsip.tautanAsli))
                    holder.itemView.context.startActivity(intentBrowser)
                } catch (e: Exception) {
                    Toast.makeText(holder.itemView.context, "Gagal membuka tautan.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(holder.itemView.context, "Tautan asli tidak tersedia.", Toast.LENGTH_SHORT).show()
            }
        }

        // Proyektor Gambar Postingan
        holder.wadahFotoPenuh.removeAllViews()
        if (arsip.daftarFoto.isNotBlank()) {
            holder.wadahFotoPenuh.visibility = View.VISIBLE
            val tautanGambar = arsip.daftarFoto.split(",").firstOrNull()?.trim()
            
            if (!tautanGambar.isNullOrEmpty()) {
                val injeksiGambar = ImageView(holder.itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        750
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                
                Glide.with(holder.itemView.context)
                    .load(tautanGambar)
                    .into(injeksiGambar)
                    
                holder.wadahFotoPenuh.addView(injeksiGambar)
            }
        } else {
            holder.wadahFotoPenuh.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = daftarArsip.size
}
