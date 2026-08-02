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
import com.fk.arsip.databinding.ActivityLetnanBinding

data class DataLetnan(
    val nama: String,
    val wilayah: String,
    val deskripsi: String,
    val fotoResId: Int
)

class LetnanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLetnanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLetnanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarLetnan)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarLetnan.navigationIcon?.setTint(android.graphics.Color.WHITE)
        binding.toolbarLetnan.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        muatKartuSanad()
        eksekusiMesinData()
    }

private fun muatKartuSanad() {
    val bukaTautan = { url: String ->
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka tautan", Toast.LENGTH_SHORT).show()
        }
    }

    // KARTU SANAD 1
    binding.cardSanad1.apply {
        txtTanggalSanad.text = getString(R.string.sanad1_tanggal)
        txtKategoriSanad.text = getString(R.string.sanad1_kategori)
        txtIsiSanad.text = getString(R.string.sanad1_isi)
        txtIndeksSanad.text = getString(R.string.sanad1_indeks)

        txtIsiSanad.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        // HAPUS KLIK KARTU UTAMA, PINDAHKAN KE TOMBOL SUMBER FB
        root.setOnClickListener(null)
        root.isClickable = false

        btnSumberFb.setOnClickListener { 
            bukaTautan("https://www.facebook.com/FK.FatwaKehidupan/posts/1479281705618099") 
        }
    }

    // KARTU SANAD 2
    binding.cardSanad2.apply {
        txtTanggalSanad.text = getString(R.string.sanad2_tanggal)
        txtKategoriSanad.text = getString(R.string.sanad2_kategori)
        txtIsiSanad.text = getString(R.string.sanad2_isi)
        txtIndeksSanad.text = getString(R.string.sanad2_indeks)

        txtIsiSanad.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        // HAPUS KLIK KARTU UTAMA, PINDAHKAN KE TOMBOL SUMBER FB
        root.setOnClickListener(null)
        root.isClickable = false

        btnSumberFb.setOnClickListener { 
            bukaTautan("https://www.facebook.com/FK.FatwaKehidupan") 
        }
    }
}


    private fun eksekusiMesinData() {
        binding.rvLetnan.layoutManager = LinearLayoutManager(this)

        val daftarLetnan = listOf(
            DataLetnan(
                nama = "Muiz Abdulism",
                wilayah = "Taliwang, Nusa Tenggara Barat, Indonesia",
                deskripsi = "Kontak Pembimbing: http://www.facebook.com/muiz.abdulism",
                fotoResId = R.drawable.letnan_muiz
            ),
            DataLetnan(
                nama = "Ibnu Athoillah",
                wilayah = "Martapura, Kalimantan",
                deskripsi = "Kontak Pembimbing: http://www.facebook.com/ibnu.ibnu.378",
                fotoResId = R.drawable.letnan_ibnu
            ),
            DataLetnan(
                nama = "Ahmad Fatih Basitul 'ulum",
                wilayah = "Jawa Tengah",
                deskripsi = "Kontak Pembimbing: http://www.facebook.com/ahmad.f.ulum",
                fotoResId = R.drawable.letnan_fatih
            )
        )

        binding.rvLetnan.adapter = LetnanAdapter(daftarLetnan)
    }
}

class LetnanAdapter(private val list: List<DataLetnan>) : RecyclerView.Adapter<LetnanAdapter.LetnanViewHolder>() {

    class LetnanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNama: TextView = itemView.findViewById(R.id.tvNamaLetnan)
        val tvWilayah: TextView = itemView.findViewById(R.id.tvWilayahLetnan)
        val tvDeskripsi: TextView = itemView.findViewById(R.id.tvDeskripsiLetnan)
        val imgFoto: ImageView = itemView.findViewById(R.id.imgFotoLetnan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetnanViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_letnan, parent, false)
        return LetnanViewHolder(view)
    }

 override fun onBindViewHolder(holder: LetnanViewHolder, position: Int) {
    val data = list[position]
    holder.tvNama.text = data.nama
    holder.tvWilayah.text = data.wilayah
    holder.tvDeskripsi.text = data.deskripsi
    
    // AKTIFKAN KLIK TAUTAN PADA DESKRIPSI LETNAN
    holder.tvDeskripsi.movementMethod = android.text.method.LinkMovementMethod.getInstance()

    try {
        holder.imgFoto.setImageResource(data.fotoResId)
    } catch (e: Exception) {
        holder.imgFoto.setImageResource(R.drawable.ic_launcher_fk)
    }
}

    override fun getItemCount(): Int = list.size
}
