package com.fk.arsip

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
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
            
        val txtKontenUtama = view.findViewById<TextView>(R.id.txtKontenUtama)
        val wadahDinamisKonten = view.findViewById<LinearLayout>(R.id.wadahDinamisKonten)
        val wadahHeaderShared = view.findViewById<LinearLayout>(R.id.wadahHeaderShared)
        val txtNamaPemilikShared = view.findViewById<TextView>(R.id.txtNamaPemilikShared)
        val txtKontenShared = view.findViewById<TextView>(R.id.txtKontenShared)
        val wadahFoto = view.findViewById<LinearLayout>(R.id.wadahMultiFoto)
        
        val btnTautan = view.findViewById<LinearLayout>(R.id.linkSumberTautan)
        val btnBagikan = view.findViewById<LinearLayout>(R.id.btnBagikanKonten)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BukuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_buku, parent, false)
        return BukuViewHolder(view)
    }

    override fun onBindViewHolder(holder: BukuViewHolder, position: Int) {
        val arsip = daftarArsip[position]

          val kontenBersih = arsip.kontenPenuh
        if (kontenBersih.contains("--- Membagikan Status:")) {
            // ========================================================
            // MODE SHARED POST: Mengaktifkan Bingkai Pengunci
            // ========================================================
            val bagianSaringan = kontenBersih.split("\n\n--- Membagikan Status: ")
            
            // 1. Teks Asli Pemosting ("Mantap... DJ nya...")
            val teksAsli = bagianSaringan[0].trim()
            if (teksAsli.isNotEmpty()) {
                holder.txtKontenUtama.text = teksAsli
                holder.txtKontenUtama.visibility = View.VISIBLE
            } else {
                holder.txtKontenUtama.visibility = View.GONE
            }

            // 2. Modifikasi Bumper (Bingkai Abu-abu + Bantalan Dalam)
            holder.wadahDinamisKonten.setBackgroundResource(R.drawable.bg_border_sharedpost)
            val bantalanPx = (12 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.wadahDinamisKonten.setPadding(bantalanPx, bantalanPx, bantalanPx, bantalanPx)

            // 3. Merakit Teks & Header Shared
            if (bagianSaringan.size > 1) {
                val detailShared = bagianSaringan[1].split(" ---\n", limit = 2)
                holder.wadahHeaderShared.visibility = View.VISIBLE
                holder.txtKontenShared.visibility = View.VISIBLE
                
                // Jika Pipa JSON gagal menyedot nama grup, mesin ini hanya menampilkan nama penulis
                holder.txtNamaPemilikShared.text = detailShared[0].trim() 
                
                if (detailShared.size > 1) {
                    holder.txtKontenShared.text = detailShared[1].trim()
                } else {
                    holder.txtKontenShared.visibility = View.GONE
                }
            }
} else {
    // ========================================================
    // MODE POST NORMAL: Menerapkan Warna Sanad
    // ========================================================
    // GANTI BARIS LAMA DENGAN INI:
    holder.txtKontenUtama.text = terapkanWarna(kontenBersih)
    holder.txtKontenUtama.visibility = View.VISIBLE
    
    holder.wadahDinamisKonten.setBackgroundResource(0)
    holder.wadahDinamisKonten.setPadding(0, 0, 0, 0)
    
    holder.wadahHeaderShared.visibility = View.GONE
    holder.txtKontenShared.visibility = View.GONE
}

        holder.txtTanggal.text = arsip.tanggalBaca
        holder.txtKategori.text = arsip.kategori
        holder.txtNomorHalaman.text = "Halaman : ${position + 1}/${daftarArsip.size}"
        holder.imgProfil.setImageResource(R.drawable.profil_abah)

        holder.btnTautan.setOnClickListener {
            if (arsip.tautanAsli.isNotBlank()) {
                try { contextStart(holder.itemView.context, arsip.tautanAsli) } 
                catch (e: Exception) { Toast.makeText(holder.itemView.context, "Gagal membuka.", Toast.LENGTH_SHORT).show() }
            }
        }

        holder.btnBagikan.setOnClickListener {
            val intentShare = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${arsip.kontenPenuh}\n\nSumber: ${arsip.tautanAsli}")
            }
            holder.itemView.context.startActivity(Intent.createChooser(intentShare, "Bagikan fatwa via..."))
        }

        // PEMETAAN VISUAL DUAL GRID MEDIA
        holder.wadahFoto.removeAllViews()
        if (arsip.daftarFoto.isNotBlank()) {
            holder.wadahFoto.visibility = View.VISIBLE
            val daftarTautan = arsip.daftarFoto.split(",")
            val totalMedia = daftarTautan.size
            val konteks = holder.itemView.context

            if (totalMedia == 1) {
                val itemMedia = daftarTautan[0].trim()
                val isVideo = itemMedia.startsWith("video:")
                val urlBersih = itemMedia.removePrefix("video:").removePrefix("image:")

                val sasisBingkai = FrameLayout(konteks).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 12, 0, 12) }
                    setBackgroundResource(R.drawable.bg_border_media)
                    setPadding(6, 6, 6, 6)
                }
                val proyektorGambar = ImageView(konteks).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 700)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                Glide.with(konteks).load(urlBersih).error(android.R.drawable.ic_menu_report_image).into(proyektorGambar)
                sasisBingkai.addView(proyektorGambar)

                if (isVideo) {
                    val tombolPlay = ImageView(konteks).apply {
                        layoutParams = FrameLayout.LayoutParams(120, 120).apply { gravity = android.view.Gravity.CENTER }
                        setImageResource(android.R.drawable.ic_media_play)
                        setColorFilter(android.graphics.Color.WHITE)
                    }
                    val labelWatermark = TextView(konteks).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL; bottomMargin = 24 }
                        text = "Klik untuk memutar di Facebook"
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 10f
                        setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
                        setPadding(12, 6, 12, 6)
                    }
                    sasisBingkai.addView(tombolPlay)
                    sasisBingkai.addView(labelWatermark)
                    sasisBingkai.setOnClickListener { contextStart(konteks, arsip.tautanAsli) }
                } else {
                    sasisBingkai.setOnClickListener { tampilkanPerbesaranFoto(konteks, urlBersih) }
                }
                holder.wadahFoto.addView(sasisBingkai)
            } else {
                var barisSasis = LinearLayout(konteks).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    orientation = LinearLayout.HORIZONTAL
                }
                for (indeks in 0 until totalMedia) {
                    val itemMedia = daftarTautan[indeks].trim()
                    val isVideo = itemMedia.startsWith("video:")
                    val urlBersih = itemMedia.removePrefix("video:").removePrefix("image:")

                    val sasisGrid = FrameLayout(konteks).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 350, 1.0f).apply { setMargins(6, 6, 6, 6) }
                        setBackgroundResource(R.drawable.bg_border_media)
                        setPadding(4, 4, 4, 4)
                    }
                    val proyektorGrid = ImageView(konteks).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    Glide.with(konteks).load(urlBersih).error(android.R.drawable.ic_menu_report_image).into(proyektorGrid)
                    sasisGrid.addView(proyektorGrid)

                    if (isVideo) {
                        val playMini = ImageView(konteks).apply {
                            layoutParams = FrameLayout.LayoutParams(60, 60).apply { gravity = android.view.Gravity.CENTER }
                            setImageResource(android.R.drawable.ic_media_play)
                            setColorFilter(android.graphics.Color.WHITE)
                        }
                        sasisGrid.addView(playMini)
                        sasisGrid.setOnClickListener { contextStart(konteks, arsip.tautanAsli) }
                    } else {
                        sasisGrid.setOnClickListener { tampilkanPerbesaranFoto(konteks, urlBersih) }
                    }
                    barisSasis.addView(sasisGrid)

                    if ((indeks + 1) % 2 == 0 || (indeks + 1) == totalMedia) {
                        holder.wadahFoto.addView(barisSasis)
                        barisSasis = LinearLayout(konteks).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            orientation = LinearLayout.HORIZONTAL
                        }
                    }
                }
            }
        } else { holder.wadahFoto.visibility = View.GONE }
    }

private fun terapkanWarna(teksLengkap: String): android.text.SpannableString {
    val spannable = android.text.SpannableString(teksLengkap)
    val pembatasIndex = teksLengkap.indexOf("=====")
    
    if (pembatasIndex != -1) {
        // Pertanyaan (Biru) - Sebelum pembatas
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#004D40")), 
            0, pembatasIndex, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // Jawaban (Hijau) - Setelah pembatas
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#212121")), 
            pembatasIndex, teksLengkap.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    } else {
        // Jika tidak ada pembatas, warnai semua biru sebagai default status jamaah
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#212121")), 
            0, teksLengkap.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return spannable
}


    private fun contextStart(context: android.content.Context, url: String) {
        if (url.isNotBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    private fun tampilkanPerbesaranFoto(konteks: android.content.Context, urlGambar: String) {
        val sasisDialog = FrameLayout(konteks).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val proyektorBesar = ImageView(konteks).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(konteks).load(urlGambar).into(proyektorBesar)
        sasisDialog.addView(proyektorBesar)

        val dialogTampil = android.app.AlertDialog.Builder(konteks, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(sasisDialog)
            .create()
        sasisDialog.setOnClickListener { dialogTampil.dismiss() }
        dialogTampil.show()
    }

    override fun getItemCount(): Int = daftarArsip.size

    fun perbaruiData(baru: List<ArsipEntity>) {
        this.daftarArsip = baru
        notifyDataSetChanged()
    }
}
