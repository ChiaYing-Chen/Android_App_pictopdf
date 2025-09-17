package com.chiaying.pictopdf

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.chiaying.pictopdf.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ImageAdapter
    private val selectedImages = mutableListOf<Uri>()
    private var generatedPdfFile: File? = null
    private var startDateTime = Calendar.getInstance()
    private var endDateTime = Calendar.getInstance()
    private var isDateRangeSet = false
    
    private val pdfGenerator = PdfGenerator()
    private val imageCompressor = ImageCompressor()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    }
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.clear()
            selectedImages.addAll(uris)
            updateUI()
            imageAdapter.updateImages(selectedImages)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupClickListeners()
        checkPermissions()
    }
    
    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(this, selectedImages)
        binding.rvImages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = imageAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSelectImages.setOnClickListener {
            if (hasPermissions()) {
                openImagePicker()
            } else {
                requestPermissions()
            }
        }
        
        binding.btnConvertToPdf.setOnClickListener {
            convertToPdf()
        }

        binding.btnOpenPdf.setOnClickListener {
            openPdf()
        }
        
        binding.btnSharePdf.setOnClickListener {
            shareGenerally()
        }

        binding.tvDateRange.setOnClickListener {
            showDateTimePicker()
        }

        binding.btnOrganizePhotos.setOnClickListener {
            organizePhotos()
        }
    }
    
    private fun checkPermissions() {
        if (!hasPermissions()) {
            requestPermissions()
        } else {
            loadPhotosFromWorkFolder()
        }
    }
    
    private fun hasPermissions(): Boolean {
        val readPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        return readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        EasyPermissions.requestPermissions(
            this,
            getString(R.string.permission_required),
            PERMISSION_REQUEST_CODE,
            *permissions
        )
    }
    
    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }
    
    private fun updateUI() {
        val count = selectedImages.size
        binding.tvSelectedCount.text = if (count > 0) {
            getString(R.string.selected_images, count)
        } else {
            getString(R.string.no_images_selected)
        }
        
        binding.btnConvertToPdf.isEnabled = count > 0
        
        val hasPdf = generatedPdfFile?.exists() == true
        binding.btnOpenPdf.isEnabled = hasPdf
        binding.btnSharePdf.isEnabled = hasPdf
        binding.btnOrganizePhotos.isEnabled = isDateRangeSet
    }
    
    private fun convertToPdf() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_selected), Toast.LENGTH_SHORT).show()
            return
        }
        
        showProgress(true, getString(R.string.converting))
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update progress for compression
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = getString(R.string.compressing_images)
                }
                
                // Compress images
                val compressedImages = imageCompressor.compressImages(this@MainActivity, selectedImages)
                
                // Update progress for PDF generation
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = getString(R.string.converting)
                }
                
                // Generate PDF
                val pdfFile = pdfGenerator.createPdfFromImages(this@MainActivity, compressedImages)
                
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    generatedPdfFile = pdfFile
                    updateUI()
                    Toast.makeText(this@MainActivity, getString(R.string.pdf_created), Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(this@MainActivity, getString(R.string.error_creating_pdf), Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }
    }
    
    private fun showProgress(show: Boolean, message: String = "") {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvProgress.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvProgress.text = message
        
        // Disable buttons during processing
        binding.btnSelectImages.isEnabled = !show
        binding.btnConvertToPdf.isEnabled = !show && selectedImages.isNotEmpty()
        binding.btnOrganizePhotos.isEnabled = !show && isDateRangeSet
    }
    
    private fun shareGenerally() {
        generatedPdfFile?.let { file ->
            val fileUri = FileProvider.getUriForFile(
                this,
                "com.chiaying.pictopdf.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(shareIntent, getString(R.string.share_pdf))
            startActivity(chooser)
        }
    }
    
    private fun openPdf() {
        generatedPdfFile?.let { file ->
            val fileUri = FileProvider.getUriForFile(
                this,
                "com.chiaying.pictopdf.fileprovider",
                file
            )

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(openIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "No application available to view PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDateTimePicker() {
        val currentDateTime = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, dayOfMonth)
            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedDate.set(Calendar.MINUTE, minute)
                startDateTime = selectedDate
                // Now pick end time
                showEndDateTimePicker()
            }, currentDateTime.get(Calendar.HOUR_OF_DAY), currentDateTime.get(Calendar.MINUTE), true).show()
        }, currentDateTime.get(Calendar.YEAR), currentDateTime.get(Calendar.MONTH), currentDateTime.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showEndDateTimePicker() {
        val currentDateTime = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, dayOfMonth)
            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedDate.set(Calendar.MINUTE, minute)
                endDateTime = selectedDate

                if (endDateTime.after(startDateTime)) {
                    isDateRangeSet = true
                    updateDateRangeText()
                    updateUI()
                } else {
                    Toast.makeText(this, "結束時間必須晚於開始時間", Toast.LENGTH_SHORT).show()
                }
            }, currentDateTime.get(Calendar.HOUR_OF_DAY), currentDateTime.get(Calendar.MINUTE), true).show()
        }, currentDateTime.get(Calendar.YEAR), currentDateTime.get(Calendar.MONTH), currentDateTime.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateRangeText() {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val startStr = sdf.format(startDateTime.time)
        val endStr = sdf.format(endDateTime.time)
        binding.tvDateRange.text = "$startStr - $endStr"
    }

    private fun organizePhotos() {
        if (!isDateRangeSet) {
            Toast.makeText(this, "請先設定日期時間範圍", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasPermissions()) {
            Toast.makeText(this, "需要儲存權限才能整理照片", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }

        showProgress(true, "正在整理照片...")

        CoroutineScope(Dispatchers.IO).launch {
            val movedCount = movePhotosToWorkFolder()
            withContext(Dispatchers.Main) {
                showProgress(false)
                Toast.makeText(this@MainActivity, "成功移動 $movedCount 張照片至 Work 資料夾", Toast.LENGTH_LONG).show()
                loadPhotosFromWorkFolder() // Refresh the list
            }
        }
    }

    private fun movePhotosToWorkFolder(): Int {
        var movedCount = 0
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN
        )

        val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ?"
        val selectionArgs = arrayOf(
            startDateTime.timeInMillis.toString(),
            endDateTime.timeInMillis.toString()
        )

        contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                try {
                    val values = ContentValues().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Work")
                        }
                    }
                    val updatedRows = contentResolver.update(contentUri, values, null, null)
                    if (updatedRows > 0) {
                        movedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return movedCount
    }

    private fun loadPhotosFromWorkFolder() {
        if (!hasPermissions()) {
            return // Don't load if permissions are not granted
        }
        CoroutineScope(Dispatchers.IO).launch {
            val workPhotos = mutableListOf<Uri>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Pictures/Work%")

            contentResolver.query(collection, projection, selection, selectionArgs, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    workPhotos.add(contentUri)
                }
            }

            withContext(Dispatchers.Main) {
                selectedImages.clear()
                selectedImages.addAll(workPhotos)
                imageAdapter.updateImages(selectedImages)
                updateUI()
            }
        }
    }
    
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        // Permissions granted
        loadPhotosFromWorkFolder()
    }
    
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }
}