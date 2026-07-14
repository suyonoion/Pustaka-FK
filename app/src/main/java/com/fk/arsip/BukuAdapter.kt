package com.fk.arsip

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fk.arsip.database.ArsipEntity

class BukuAdapter(private var daftarArsip: List<ArsipEntity>) : RecyclerView.Adapter<BukuAdapter.BukuViewHolder>() {

    class BukuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProfil = view.findViewById<ImageView>(R.id.imgProfilAbah)
        val txtTanggal = view.findViewById<TextView>(R.id.txtTanggal)
        val txtKategori = view.findViewById<TextView>(R.id.txtKategori)
        val txtNomorHalaman = view.findViewById<TextView>(R.id.txtNomorHalaman)
        val txtKonten = view.findViewById<TextView>(R.id.txtKonten)
        val btnTautan = view.findViewById<LinearLayout>(R.id.linkSumberTautan)
        val wadahFoto = view.findViewById<LinearLayout>(R.id.wadahMultiFoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BukuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_buku, parent, false)
        return BukuViewHolder(view)
    }

    override fun onBindViewHolder(holder: BukuViewHolder, position: Int) {
        val arsip = daftarArsip[position]

        // Jalur distribusi data teks
        holder.txtTanggal.text = arsip.tanggalBaca
        holder.txtKategori.text = arsip.kategori
        holder.txtKonten.text = arsip.kontenPenuh
        
        // Kalkulator dinamis penunjuk lembar halaman
        holder.txtNomorHalaman.text = "Hal: ${position + 1}/${daftarArsip.size}"

        // Render Gambar Profil
        if (arsip.urlProfilPic.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(arsip.urlProfilPic)
                .circleCrop()
                .into(holder.imgProfil)
        } else {
            holder.imgProfil.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // Jalur eksekusi tombol tautan eksternal Facebook
        holder.btnTautan.setOnClickListener {
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

              // Mesin Proyektor Gambar dan Sampul Video Dinamis
        holder.wadahFoto.removeAllViews()
        if (arsip.daftarFoto.isNotBlank()) {
            holder.wadahFoto.visibility = View.VISIBLE
            
            // Membelah kargo string menjadi daftar tautan terpisah
            val daftarTautan = arsip.daftarFoto.split(",")
            
            for (tautan in daftarTautan) {
                val urlBersih = tautan.trim()
                if (urlBersih.isNotEmpty()) {
                    
                    // Merakit sasis gambar baru untuk setiap kargo tautan
                    val injeksiGambar = ImageView(holder.itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 
                            LinearLayout.LayoutParams.WRAP_CONTENT // Menyesuaikan rasio proporsional gambar
                        ).apply {
                            setMargins(0, 0, 0, 16) // Jarak bumper bawah antar gambar sebesar 16dp
                        }
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    
                    // Memompa pixel gambar menggunakan pipa Glide
                    Glide.with(holder.itemView.context)
                        .load(urlBersih)
                        .into(injeksiGambar)
                        
                    // Sakelar Interaktif: Tekan gambar untuk meluncur ke tautan video/postingan asli
                    injeksiGambar.setOnClickListener {
                        if (arsip.tautanAsli.isNotBlank()) {
                            try {
                                val intentBrowser = Intent(Intent.ACTION_VIEW, Uri.parse(arsip.tautanAsli))
                                holder.itemView.context.startActivity(intentBrowser)
                            } catch (e: Exception) {
                                Toast.makeText(holder.itemView.context, "Tidak ada mesin peramban yang tersedia.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                        
                    // Memasang plat gambar ke dalam proyektor utama
                    holder.wadahFoto.addView(injeksiGambar)
                }
            }
        } else {
            holder.wadahFoto.visibility = View.GONE
        }

    }

    override fun getItemCount(): Int = daftarArsip.size

    fun perbaruiData(baru: List<ArsipEntity>) {
        this.daftarArsip = baru
        notifyDataSetChanged()
    }
}
