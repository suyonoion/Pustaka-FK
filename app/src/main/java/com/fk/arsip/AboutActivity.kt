package com.fk.arsip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        
        // HUBUNGKAN TUAS KE MESIN UTAMA DI SINI
        aktifkanTuasNavigasi()
    }
    
    private fun aktifkanTuasNavigasi() {
        val btnLinkSanFK = findViewById<LinearLayout>(R.id.linkSanFK)
        val btnLinkSaung = findViewById<LinearLayout>(R.id.linkSaung)
        val btnLinkZF = findViewById<LinearLayout>(R.id.linkZF)
        val btnLinkFB = findViewById<LinearLayout>(R.id.linkFB)
        
        val bukaTautan = { url: String ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka jalur ke peramban.", Toast.LENGTH_SHORT).show()
            }
        }

        btnLinkSanFK.setOnClickListener { bukaTautan("https://maps.app.goo.gl/b7iJKKg9hWMKsJEv8") }
        btnLinkSaung.setOnClickListener { bukaTautan("https://maps.app.goo.gl/F1qKiYjs2pUAa17j8") }
        btnLinkZF.setOnClickListener { tampilkanKeteranganZF() }
        btnLinkFB.setOnClickListener { bukaTautan("https://www.facebook.com/FK.FatwaKehidupan") }
    }
    
    private fun tampilkanKeteranganZF() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.zf_edukasi_judul))
            .setMessage(getString(R.string.zf_edukasi_pesan))
            .setPositiveButton("Selesai") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .create()
            .show()
    }
}
