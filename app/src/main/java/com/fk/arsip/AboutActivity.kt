package com.fk.arsip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fk.arsip.databinding.ActivityTentangBinding
import com.google.android.material.card.MaterialCardView

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTentangBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Correct Binding Inflation
        binding = ActivityTentangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarTentang)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        binding.toolbarTentang.navigationIcon?.setTint(android.graphics.Color.WHITE)
        binding.toolbarTentang.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        aktifkanTuasNavigasi()
    }

    private fun aktifkanTuasNavigasi() {
        // HAPUS CASTING 'as MaterialCardView' BERLEBIHAN
        val btnLinkSanFK = binding.linkSanFK.root
        val btnLinkSaung = binding.linkSaung.root
        val btnLinkZF = binding.linkZF.root
        val btnLinkFB = binding.linkFB.root

        val bukaTautan = { url: String ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka tautan. Periksa koneksi.", Toast.LENGTH_SHORT).show()
            }
        }

        // Config Ripple
        val links = listOf(btnLinkSanFK, btnLinkSaung, btnLinkZF, btnLinkFB)
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        links.forEach { btn ->
            btn.isClickable = true
            btn.isFocusable = true
            btn.foreground = ContextCompat.getDrawable(this, outValue.resourceId)
        }

        // Inject Data Ke Included Layout
        val setLinkData = { card: MaterialCardView, text: String, iconRes: Int ->
            val tv = card.findViewById<TextView>(R.id.textLink)
            val iv = card.findViewById<ImageView>(R.id.iconLink)
            tv?.text = text
            iv?.setImageResource(iconRes)
            iv?.imageTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }

        setLinkData(btnLinkSanFK, "SanFK Kendal", android.R.drawable.ic_menu_mylocation)
        setLinkData(btnLinkSaung, "Saung Jagat Walikan (JAWA)", R.drawable.logo_saung)
        setLinkData(btnLinkFB, "Halaman FK", R.drawable.logo_fb)
        setLinkData(btnLinkZF, "ZF (Zuhri Formalism)", R.drawable.ic_launcher_zf)

        // Click Action
        btnLinkSanFK.setOnClickListener { bukaTautan("https://maps.app.goo.gl/b7iJKKg9hWMKsJEv8") }
        btnLinkSaung.setOnClickListener { bukaTautan("https://maps.app.goo.gl/F1qKiYjs2pUAa17j8") }
        btnLinkZF.setOnClickListener { tampilkanKeteranganZF() }
        btnLinkFB.setOnClickListener { bukaTautan("https://www.facebook.com/FK.FatwaKehidupan") }
    }

    private fun tampilkanKeteranganZF() {
        AlertDialog.Builder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(getString(R.string.zf_edukasi_judul))
            .setMessage(getString(R.string.zf_edukasi_pesan))
            .setPositiveButton("Selesai") { dialog, _ -> dialog.dismiss() }
            .setIcon(R.drawable.ic_launcher_zf)
            .show()
    }
}
