package com.mohitsharda.mydrawingapp

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import com.mohitsharda.mydrawingapp.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mImageButtonCurrentPaint: ImageButton? = null

    private lateinit var mProgressDialog: Dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.drawingView.setSizeForBrush(20.toFloat())

        mImageButtonCurrentPaint = binding.llColorPallet[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pallet_pressed
            )
        )

        binding.ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        binding.ibGallery.setOnClickListener {
            if (isReadStorageAllowed()) {
                val pickPhotoIntent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(pickPhotoIntent, GALLERY)
            } else {
                requestStoragePermission()
            }
        }

        binding.ibUndo.setOnClickListener {
            binding.drawingView.onClickUndo()
        }

        binding.ibSave.setOnClickListener {
            if (isReadStorageAllowed()) {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) { showProgressDialog() }
                    val result = withContext(Dispatchers.IO) {
                        bitmapAsyncTask(getBitmapFromView(binding.flDrawingViewContainer))
                    }

                    withContext(Dispatchers.Main) { onFileSaved(result) }
                }

            } else {
                requestStoragePermission()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GALLERY) {
                try {
                    if (data!!.data != null) {
                        binding.ivBackground.visibility = View.VISIBLE
                        binding.ivBackground.setImageURI(data.data)
                    } else {
                        Toast.makeText(
                            this,
                            "Error in parsing the image or it is corrupted",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)

        smallBtn.setOnClickListener {
            binding.drawingView.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        mediumBtn.setOnClickListener {
            binding.drawingView.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener {
            binding.drawingView.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view != mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()

            binding.drawingView.setColor(colorTag)

            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallet_normal
                )
            )
            mImageButtonCurrentPaint = view
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return result
    }

    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).toString()
            )
        ) {
            Toast.makeText(this, "Need permissions to add a background image", Toast.LENGTH_SHORT)
                .show()
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            STORAGE_PERMISSION_CODE
        )
    }

    private fun bitmapAsyncTask(mBitmap: Bitmap): String {
        var result: String
        try {
            val bytes = ByteArrayOutputStream()
            mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
            val f =
                File(externalCacheDir!!.absoluteFile.toString() + File.separator + "MyDrawingApp_" + System.currentTimeMillis() / 1000 + ".png")
            val fos = FileOutputStream(f)
            fos.write(bytes.toByteArray())
            fos.close()
            result = f.absolutePath
        } catch (e: Exception) {
            result = ""
            e.printStackTrace()
        }
        return result

    }

    private fun showProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog.show()
    }

    private fun cancelProgressDialog() {
        mProgressDialog.dismiss()
    }

    private fun onFileSaved(result: String) {
        cancelProgressDialog()
        if (result.isNotEmpty()) {
            Toast.makeText(
                this@MainActivity,
                "File saved successfully :$result",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this@MainActivity,
                "Something went wrong while saving the file.",
                Toast.LENGTH_SHORT
            ).show()
        }
        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result), null) { _, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"

            startActivity(
                Intent.createChooser(shareIntent, "Share")
            )

        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Permission granted now you can read the storage files.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Oops you just denied the permissions", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun isReadStorageAllowed(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1
        private const val GALLERY = 2
    }
}