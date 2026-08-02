package com.fk.arsip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fk.arsip.databinding.ActivitySosmedBinding

data class DataSosmed(
    val kategori: String,
    val nama: String,
    val deskripsi: String,
    val link: String,
    val fotoResId: Int
)

class SosmedActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySosmedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySosmedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSosmed)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarSosmed.navigationIcon?.setTint(android.graphics.Color.WHITE)
        binding.toolbarSosmed.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        eksekusiDaftarSosmed()
    }

    private fun eksekusiDaftarSosmed() {
        binding.rvSosmed.layoutManager = LinearLayoutManager(this)

        val daftarSosmed = listOf(
            // 1. GURU
            DataSosmed(
                kategori = "AKUN PRIBADI GURU",
                nama = getString(R.string.sosmed_guru_pribadi_nama),
                deskripsi = getString(R.string.sosmed_guru_pribadi_desc),
                link = getString(R.string.sosmed_guru_pribadi_link),
                fotoResId = R.drawable.profil_abah
            ),
            DataSosmed(
                kategori = "HALAMAN RESMI GURU",
                nama = getString(R.string.sosmed_guru_halaman_nama),
                deskripsi = getString(R.string.sosmed_guru_halaman_desc),
                link = getString(R.string.sosmed_guru_halaman_link),
                fotoResId = R.drawable.profil_abah
            ),

            // 2. PARA LETNAN
            DataSosmed(
                kategori = "AKUN LETNAN PEMBIMBING",
                nama = getString(R.string.sosmed_letnan_muiz_nama),
                deskripsi = getString(R.string.sosmed_letnan_muiz_desc),
                link = getString(R.string.sosmed_letnan_muiz_link),
                fotoResId = R.drawable.letnan_muiz
            ),
            DataSosmed(
                kategori = "AKUN LETNAN PEMBIMBING",
                nama = getString(R.string.sosmed_letnan_ibnu_nama),
                deskripsi = getString(R.string.sosmed_letnan_ibnu_desc),
                link = getString(R.string.sosmed_letnan_ibnu_link),
                fotoResId = R.drawable.letnan_ibnu
            ),
            DataSosmed(
                kategori = "AKUN LETNAN PEMBIMBING",
                nama = getString(R.string.sosmed_letnan_fatih_nama),
                deskripsi = getString(R.string.sosmed_letnan_fatih_desc),
                link = getString(R.string.sosmed_letnan_fatih_link),
                fotoResId = R.drawable.letnan_fatih
            ),

            // 3. GRUB & YAYASAN
            DataSosmed(
                kategori = "GRUB FACEBOOK",
                nama = getString(R.string.sosmed_grup_nama),
                deskripsi = getString(R.string.sosmed_grup_desc),
                link = getString(R.string.sosmed_grup_link),
                fotoResId = R.drawable.cover_pdfk
            ),
            DataSosmed(
                kategori = "YAYASAN PDFK (Padepokan Fatwa Kehidupan",
                nama = getString(R.string.sosmed_yayasan_nama),
                deskripsi = getString(R.string.sosmed_yayasan_desc),
                link = getString(R.string.sosmed_yayasan_link),
                fotoResId = R.drawable.sosmed_yayasan_img
            )
        )

        binding.rvSosmed.adapter = SosmedAdapter(daftarSosmed) { url ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka tautan", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class SosmedAdapter(
    private val list: List<DataSosmed>,
    private val onLinkClick: (String) -> Unit
) : RecyclerView.Adapter<SosmedAdapter.SosmedViewHolder>() {

    class SosmedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgFoto: ImageView = itemView.findViewById(R.id.imgFotoSosmed)
        val tvKategori: TextView = itemView.findViewById(R.id.tvKategoriSosmed)
        val tvNama: TextView = itemView.findViewById(R.id.tvNamaSosmed)
        val tvDeskripsi: TextView = itemView.findViewById(R.id.tvDeskripsiSosmed)
        val tvTautan: TextView = itemView.findViewById(R.id.tvTautanSosmed)
        val btnBukaTautan: View = itemView.findViewById(R.id.btnBukaTautanSosmed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SosmedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sosmed, parent, false)
        return SosmedViewHolder(view)
    }

    override fun onBindViewHolder(holder: SosmedViewHolder, position: Int) {
        val data = list[position]
        holder.tvKategori.text = data.kategori
        holder.tvNama.text = data.nama
        holder.tvDeskripsi.text = data.deskripsi
        holder.tvTautan.text = data.link

        try {
            holder.imgFoto.setImageResource(data.fotoResId)
        } catch (e: Exception) {
            holder.imgFoto.setImageResource(R.drawable.ic_launcher_fk)
        }

        holder.tvTautan.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        holder.btnBukaTautan.setOnClickListener {
            onLinkClick(data.link)
        }
    }

    override fun getItemCount(): Int = list.size
}
