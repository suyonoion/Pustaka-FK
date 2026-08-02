package com.fk.arsip

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fk.arsip.databinding.ActivityBiografiBinding

class BiografiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBiografiBinding
    private lateinit var adapter: BiografiAdapter
    private var currentTextSizeSp = 12f // Ukuran awal teks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBiografiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupRecyclerView()
        setupTextSizeController()
    }

    private fun setupRecyclerView() {
        binding.rvBiografi.layoutManager = LinearLayoutManager(this)

        val data = listOf(
            ItemBiografi.BioText(getString(R.string.bio_judul1_nama), getString(R.string.bio_isi1_nama)),
            ItemBiografi.BioText(getString(R.string.bio_judul2_kelahiran_dan_keluarga), getString(R.string.bio_isi2_kelahiran_dan_keluarga)),
            ItemBiografi.BioText(getString(R.string.bio_judul3_mulai_ikuti_thoriqoh), getString(R.string.bio_isi3_mulai_ikuti_thoriqoh)),
            ItemBiografi.BioText(getString(R.string.bio_judul4_pendidikan), getString(R.string.bio_isi4_pendidikan)),
            ItemBiografi.BioText(getString(R.string.bio_judul6_nama_yang_ditinggikan), getString(R.string.bio_isi6_nama_yang_ditinggikan)),
            ItemBiografi.BioText(getString(R.string.bio_judul5_guru_sambung_ke1), getString(R.string.bio_isi5_guru_sambung_ke1)),
            ItemBiografi.BioText(getString(R.string.bio_judul7_guru_sambung_ke2), getString(R.string.bio_isi7_guru_sambung_ke2)),
            ItemBiografi.BioText(getString(R.string.bio_judul8_lahirnya_shirot), getString(R.string.bio_isi8_lahirnya_shirot)),
            ItemBiografi.BioStatus(getString(R.string.bio_isi9_kaitannya_nama_dengan_dj)),
            ItemBiografi.BioQuote(getString(R.string.bio_quote_isi), getString(R.string.bio_quote_sumber))
        )

        adapter = BiografiAdapter(data, currentTextSizeSp)
        binding.rvBiografi.adapter = adapter
    }

    private fun setupTextSizeController() {
        // Tambah ukuran teks (Maksimal 20sp)
        binding.btnZoomIn.setOnClickListener {
            if (currentTextSizeSp < 20f) {
                currentTextSizeSp += 2f
                adapter.updateTextSize(currentTextSizeSp)
            }
        }

        // Kurangi ukuran teks (Minimal 10sp)
        binding.btnZoomOut.setOnClickListener {
            if (currentTextSizeSp > 10f) {
                currentTextSizeSp -= 2f
                adapter.updateTextSize(currentTextSizeSp)
            }
        }
    }
}
