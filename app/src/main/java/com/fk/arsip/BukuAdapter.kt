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
        
        // Kompartemen Teks Utama
        val txtKonten = view.findViewById<TextView>(R.id.txtKonten)
        
        // Kompartemen SharedPost Bersensor
        val wadahSharedPost = view.findViewById<LinearLayout>(R.id.wadahSharedPost)
        val txtSharedAuthor = view.findViewById<TextView>(R.id.txtSharedAuthor)
        val txtSharedKonten = view.findViewById<TextView>(R.id.txtSharedKonten)

        val btnTautan = view.findViewById<LinearLayout>(R.id.linkSumberTautan)
        val btnBagikan = view.findViewById<LinearLayout>(R.id.btnBagikanKonten)
        val wadahFoto = view.findViewById<LinearLayout>(R.id.wadahMultiFoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BukuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_buku, parent, false)
        return BukuViewHolder(view)
    }

    override fun onBindViewHolder(holder: BukuViewHolder, position: Int) {
        val arsip = daftarArsip[position]

        // 1. DISTRIBUSI TEKS UTAMA & SENSOR SHAREPOST BERSARANG
        val kontenBersih = arsip.kontenPenuh
        if (kontenBersih.contains("--- Membagikan Status:")) {
            val bagianSaringan = kontenBersih.split("\n\n--- Membagikan Status: ")
            holder.txtKonten.text = bagianSaringan[0].trim()
            
            if (bagianSaringan.size > 1) {
                val detailShared = bagianSaringan[1].split(" ---\n", limit = 2)
                if (detailShared.size > 1) {
                    holder.wadahSharedPost.visibility = View.VISIBLE
                    holder.txtSharedAuthor.text = "Membagikan Status: ${detailShared[0].trim()}"
                    holder.txtSharedKonten.text = detailShared[1].trim()
                } else {
                    holder.wadahSharedPost.visibility = View.GONE
                }
            }
        } else {
            holder.txtKonten.text = kontenBersih
            holder.wadahSharedPost.visibility = View.GONE
        }

        holder.txtTanggal.text = arsip.tanggalBaca
        holder.txtKategori.text = arsip.kategori
        holder.txtNomorHalaman.text = "Hal: ${position + 1}/${daftarArsip.size}"

        // 2. PROYEKTOR PROFIL LOKAL drawable
        holder.imgProfil.setImageResource(R.drawable.profil_abah)

        // 3. JALUR AKSES SUMBER ASLI
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

        // 4. JALUR TRANSMISI BAGIKAN
        holder.btnBagikan.setOnClickListener {
            val teksBagikan = "${arsip.kontenPenuh}\n\nSumber Asli: ${arsip.tautanAsli}"
            val intentShare = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Arsip Pustaka FK")
                putExtra(Intent.EXTRA_TEXT, teksBagikan)
            }
            holder.itemView.context.startActivity(Intent.createChooser(intentShare, "Bagikan fatwa via..."))
        }

        // 5. PROYEKTOR MULTI-MEDIA GRID (FOTO & VIDEO) DENGAN BINGKAI & WATERMARK
        holder.wadahFoto.removeAllViews()
        if (arsip.daftarFoto.isNotBlank()) {
            holder.wadahFoto.visibility = View.VISIBLE
            val daftarTautan = arsip.daftarFoto.split(",")
            val totalMedia = daftarTautan.size

            val konteks = holder.itemView.context

            if (totalMedia == 1) {
                // Mode Tunggal: Tampilan Penuh Maksimal
                val itemMedia = daftarTautan[0].trim()
                val isVideo = itemMedia.startsWith("video:")
                val urlBersih = itemMedia.removePrefix("video:").removePrefix("image:")

                val sasisBingkai = FrameLayout(konteks).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 12, 0, 12) }
                    setBackgroundResource(R.drawable.bg_border_media)
                    setPadding(6, 6, 6, 6)
                }

                val proyektorGambar = ImageView(konteks).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        700
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                Glide.with(konteks).load(urlBersih).into(proyektorGambar)
                sasisBingkai.addView(proyektorGambar)

                if (isVideo) {
                    // Beri indikator play video di atas gambar
                    val tombolPlay = ImageView(konteks).apply {
                        layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                            gravity = android.view.Gravity.CENTER
                        }
                        setImageResource(android.R.drawable.ic_media_play)
                        setColorFilter(android.graphics.Color.WHITE)
                    }
                    val labelWatermark = TextView(konteks).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                            bottomMargin = 24
                        }
                        text = "Klik untuk memutar di Facebook"
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 10sp
                        setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
                        setPadding(12, 6, 12, 6)
                    }
                    sasisBingkai.addView(tombolPlay)
                    sasisBingkai.addView(labelWatermark)

                    sasisBingkai.setOnClickListener {
                        bukaSumberEksternal(konteks, arsip.tautanAsli)
                    }
                } else {
                    sasisBingkai.setOnClickListener {
                        tampilkanPerbesaranFoto(konteks, urlBersih)
                    }
                }

                holder.wadahFoto.addView(sasisBingkai)

            } else {
                // Mode Multi-Media: Susun Gambar ke dalam Baris Grid Dinamis (2 Kolom)
                var barisSasis = LinearLayout(konteks).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                }

                for (indeks in 0 until totalMedia) {
                    val itemMedia = daftarTautan[indeks].trim()
                    val isVideo = itemMedia.startsWith("video:")
                    val urlBersih = itemMedia.removePrefix("video:").removePrefix("image:")

                    val sasisBingkaiGrid = FrameLayout(konteks).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            350,
                            1.0f
                        ).apply {
                            setMargins(6, 6, 6, 6)
                        }
                        setBackgroundResource(R.drawable.bg_border_media)
                        setPadding(4, 4, 4, 4)
                    }

                    val proyektorGambarGrid = ImageView(konteks).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                    Glide.with(konteks).load(urlBersih).into(proyektorGambarGrid)
                    sasisBingkaiGrid.addView(proyektorGambarGrid)

                    if (isVideo) {
                        val tombolPlayMini = ImageView(konteks).apply {
                            layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                                gravity = android.view.Gravity.CENTER
                            }
                            setImageResource(android.R.drawable.ic_media_play)
                            setColorFilter(android.graphics.Color.WHITE)
                        }
                        sasisBingkaiGrid.addView(tombolPlayMini)
                        sasisBingkaiGrid.setOnClickListener {
                            bukaSumberEksternal(konteks, arsip.tautanAsli)
                        }
                    } else {
                        sasisBingkaiGrid.setOnClickListener {
                            tampilkanPerbesaranFoto(konteks, urlBersih)
                        }
                    }

                    barisSasis.addView(sasisBingkaiGrid)

                    // Jika baris sudah terisi 2 item atau merupakan item terakhir, dorong ke wadah utama
                    if ((indeks + 1) % 2 == 0 || (indeks + 1) == totalMedia) {
                        holder.wadahFoto.addView(barisSasis)
                        barisSasis = LinearLayout(konteks).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            orientation = LinearLayout.HORIZONTAL
                        }
                    }
                }
            }
        } else {
            holder.wadahFoto.visibility = View.GONE
        }
    }

    private fun bukaSumberEksternal(konteks: android.content.Context, url: String) {
        if (url.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                konteks.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(konteks, "Gagal meluncur ke Facebook.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Sirkuit Pintu Pembesar Foto (Zoom Viewer)
    private fun tampilkanPerbesaranFoto(konteks: android.content.Context, urlGambar: String) {
        val sasisDialog = FrameLayout(konteks).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(12, 12, 12, 12)
        }

        val proyektorBesar = ImageView(konteks).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        Glide.with(konteks).load(urlGambar).into(proyektorBesar)
        sasisDialog.addView(proyektorBesar)

        val dialogTampil = android.app.AlertDialog.Builder(konteks, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(sasisDialog)
            .create()

        sasisDialog.setOnClickListener {
            dialogTampil.dismiss() // Sentuh layar hitam di manapun untuk melipat kembali peninjau
        }

        dialogTampil.show()
    }

    override fun getItemCount(): Int = daftarArsip.size

    fun perbaruiData(baru: List<ArsipEntity>) {
        this.daftarArsip = baru
        notifyDataSetChanged()
    }
}
