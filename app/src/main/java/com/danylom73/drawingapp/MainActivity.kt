package com.danylom73.drawingapp

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get

class MainActivity : AppCompatActivity() {
    private lateinit var drawingContainer: FrameLayout
    private lateinit var drawingView: DrawingView
    private lateinit var brushIb: ImageButton
    private lateinit var galleryIb: ImageButton
    private lateinit var undoIb: ImageButton
    private lateinit var saveIb: ImageButton
    private lateinit var mImageButtonCurrentPaint: ImageButton

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground: ImageView = findViewById(R.id.backgroundIv)

                imageBackground.setImageURI(result.data?.data)
            }
        }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission for read storage files is granted",
                        Toast.LENGTH_LONG
                    ).show()

                    val pickIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (permissionName == Manifest.permission.READ_MEDIA_IMAGES) {
                        Toast.makeText(
                            this@MainActivity,
                            "Permission denied",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        drawingContainer = findViewById(R.id.drawingContainer)

        drawingView = findViewById(R.id.drawingView)
        drawingView.setSizeForBrush(20.toFloat())

        val paintColorsLl = findViewById<LinearLayout>(R.id.paintColorsLl)

        mImageButtonCurrentPaint = paintColorsLl[1] as ImageButton
        mImageButtonCurrentPaint.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        brushIb = findViewById(R.id.brushIb)
        brushIb.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        galleryIb = findViewById(R.id.galleryIb)
        galleryIb.setOnClickListener {
            requestStoragePermission()
        }

        undoIb = findViewById(R.id.undoIb)
        undoIb.setOnClickListener {
            drawingView.onUndo()
        }

        saveIb = findViewById(R.id.saveIb)
        saveIb.setOnClickListener {
            saveDrawing()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveDrawing() {
        val bitmap = drawingView.getBitmap(drawingContainer)
        val filename = "Drawing_${System.currentTimeMillis()}.png"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DrawingApp")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(it, contentValues, null, null)

            Toast.makeText(this, "Saved to gallery!", Toast.LENGTH_LONG).show()

            shareImage(uri)
        } ?: run {
            Toast.makeText(this, "Error while saving", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareImage(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share with friends"))
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size:")

        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.smallBrushIb)
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.mediumBrushIb)
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.largeBrushIb)

        smallBtn.setOnClickListener {
            drawingView.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        mediumBtn.setOnClickListener {
            drawingView.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        largeBtn.setOnClickListener {
            drawingView.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.READ_MEDIA_IMAGES)
        ) {
            showRationaleDialog("Drawing App", "Drawing App " +
                    "needs to access your external storage")
        } else {
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            ))
        }
    }

    private fun showRationaleDialog(
        title: String,
        message: String
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }
}