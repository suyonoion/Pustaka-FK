package com.fk.arsip

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class GaleriActivity : AppCompatActivity() {

    private lateinit var rvGaleri: RecyclerView
    private lateinit var adapter: GaleriFotoAdapter
    private lateinit var btnTabGuru: MaterialButton
    private lateinit var btnTabUmum: MaterialButton
    
    // PENYIMPANAN STATE TAB SAAT ROTASI
    private var currentTab: String = "Guru"
    private var fotoAkanDisimpan: GaleriFoto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_galeri)

        // BACA STATE ROTASI JIKA ADA
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getString("KEY_TAB", "Guru")
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarGaleri)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rvGaleri = findViewById(R.id.rvGaleri)
        rvGaleri.layoutManager = GridLayoutManager(this, 2)

        val layoutFilter = findViewById<View>(R.id.layoutFilter)
        btnTabGuru = layoutFilter.findViewById(R.id.btnTabGuru)
        btnTabUmum = layoutFilter.findViewById(R.id.btnTabUmum)

        adapter = GaleriFotoAdapter(emptyList()) { fotoSelected ->
            tampilkanDetailFotoDialog(fotoSelected)
        }
        rvGaleri.adapter = adapter

        btnTabGuru.setOnClickListener { 
            muatKategori("Guru")
        }

        btnTabUmum.setOnClickListener { 
            muatKategori("Umum")
        }

        // MUAT SESUAI TAB TERAKHIR
        muatKategori(currentTab)
    }

    // SIMPAN POSISI TAB SAAT LAYAR DIPUTAR
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("KEY_TAB", currentTab)
    }

    private fun muatKategori(kategori: String) {
        currentTab = kategori
        val filteredList = DataGaleri.listFotoAbah.filter { it.subKategori == kategori }
        adapter.updateData(filteredList)

        if (kategori == "Guru") {
            setTabActive(btnTabGuru, btnTabUmum)
        } else {
            setTabActive(btnTabUmum, btnTabGuru)
        }
    }

    private fun setTabActive(activeButton: MaterialButton, inactiveButton: MaterialButton) {
        activeButton.setBackgroundColor(android.graphics.Color.parseColor("#D4AF37"))
        activeButton.setTextColor(android.graphics.Color.parseColor("#2C1D11"))

        inactiveButton.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        inactiveButton.setTextColor(android.graphics.Color.parseColor("#D4AF37"))
    }

    private fun tampilkanDetailFotoDialog(foto: GaleriFoto) {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_detail_foto, null)
    val imgDetail = dialogView.findViewById<ImageView>(R.id.imgFotoDetail)
    val txtNomor = dialogView.findViewById<TextView>(R.id.txtNomorDetail)
    val txtDeskripsi = dialogView.findViewById<TextView>(R.id.txtDeskripsiDetail)

    txtNomor.text = "ARSIP ${foto.nomorFoto}"
    txtDeskripsi.text = foto.deskripsi

    // 1. ATUR SCALETYPE AWAL AGAR GAMBAR DITAMPILKAN UTUH KESELURUHAN
    imgDetail.scaleType = ImageView.ScaleType.FIT_CENTER

    // PERBAIKAN EFISIENSI: decode gambar dari assets di background thread
    // (Dispatchers.IO) agar tidak memblokir UI thread saat dialog dibuka --
    // sebelumnya decode dilakukan langsung di main thread.
    lifecycleScope.launch(Dispatchers.IO) {
        val drawable = try {
            assets.open(foto.fotoPath).use { Drawable.createFromStream(it, null) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        withContext(Dispatchers.Main) {
            if (drawable != null) imgDetail.setImageDrawable(drawable)
        }
    }

    // 2. PASANG SKRIP ZOOM YANG AMAN TANPA MEMOTONG GAMBAR AWAL
    pasangPinchToZoom(imgDetail)

    AlertDialog.Builder(this)
        .setView(dialogView)
        .setPositiveButton("TUTUP", null)
        .setNeutralButton("SIMPAN KE GALERI") { _, _ ->
            simpanFotoKeGaleri(foto)
        }
        .create()
        .show()
}

private fun pasangPinchToZoom(imageView: ImageView) {
    val matrix = Matrix()
    var scaleFactor = 1.0f
    
    // Koordinat untuk pergeseran (drag)
    var lastTouchX = 0f
    var lastTouchY = 0f
    var posX = 0f
    var posY = 0f

    val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1.0f, 4.0f) // Batas Zoom 1x - 4x

            if (scaleFactor > 1.0f) {
                imageView.scaleType = ImageView.ScaleType.MATRIX
                val factor = scaleFactor / prevScale
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageView.imageMatrix = matrix
            } else {
                // Reset posisi jika kembali ke skala normal (1x)
                scaleFactor = 1.0f
                posX = 0f
                posY = 0f
                matrix.reset()
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            }
            return true
        }
    })

    imageView.setOnTouchListener { _, event ->
        scaleGestureDetector.onTouchEvent(event)

        // LOGIKA DRAG / GESER SAAT ZOOM IN
        if (scaleFactor > 1.0f) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY

                        posX += dx
                        posY += dy

                        matrix.postTranslate(dx, dy)
                        imageView.imageMatrix = matrix

                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }
        }
        true
    }
}

    private fun simpanFotoKeGaleri(foto: GaleriFoto) {
        // PERBAIKAN EFISIENSI: seluruh proses (baca asset, decode bitmap,
        // compress JPEG, tulis file) dipindah ke Dispatchers.IO -- sebelumnya
        // semua ini berjalan di main thread dan bisa memicu jank/ANR,
        // terutama untuk foto beresolusi besar.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream = assets.open(foto.fotoPath)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val fileName = "PustakaFK_${foto.nomorFoto.replace("#", "")}_${System.currentTimeMillis()}.jpg"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PustakaFK")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { os ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                        }
                    }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(imagesDir, "PustakaFK")
                    if (!appDir.exists()) appDir.mkdirs()
                    val imageFile = File(appDir, fileName)
                    FileOutputStream(imageFile).use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GaleriActivity, "Foto tersimpan di Galeri!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GaleriActivity, "Gagal menyimpan foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
