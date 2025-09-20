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
    private lateinit var pdfAdapter: PdfAdapter
    private val selectedImages = mutableListOf<Uri>()
    private val pdfFiles = mutableListOf<File>()
    private var startDateTime = Calendar.getInstance()
    private var endDateTime = Calendar.getInstance()
    private var isDateRangeSet = false

    private val pdfGenerator = PdfGenerator()
    private val imageCompressor = ImageCompressor()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
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
        setupModeToggle()
        loadPdfFiles()
    }
    
    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(this, selectedImages)
        binding.rvImages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = imageAdapter
        }
        imageAdapter.onRemoveClickListener = { uri ->
            selectedImages.remove(uri)
            imageAdapter.updateImages(selectedImages)
            updateUI()
        }

        pdfAdapter = PdfAdapter(pdfFiles)
        binding.rvPdfs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = pdfAdapter
        }

        pdfAdapter.onOpenClickListener = { file -> openPdf(file) }
        pdfAdapter.onShareClickListener = { file -> sharePdf(file) }
        pdfAdapter.onSplitClickListener = { file -> splitPdf(file) }
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

    // 已移除舊的分頁導覽，改用首頁切換

        binding.tvDateRange.setOnClickListener {
            showDateTimePicker()
        }

        binding.btnOrganizePhotos.setOnClickListener {
            filterPhotosByDate()
        }

        binding.btnClearSelection.setOnClickListener {
            clearSelectedImages()
        }
    }
    
    private fun checkPermissions() {
        if (!hasPermissions()) {
            requestPermissions()
        }
    }
    
    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        EasyPermissions.requestPermissions(
            this,
            getString(R.string.permission_required),
            PERMISSION_REQUEST_CODE,
            *REQUIRED_PERMISSIONS
        )
    }
    
    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun clearSelectedImages() {
        selectedImages.clear()
        imageAdapter.updateImages(selectedImages)
        updateUI()
    }
    
    private fun updateUI() {
        val count = selectedImages.size
        binding.tvSelectedCount.text = if (count > 0) {
            getString(R.string.selected_images, count)
        } else {
            getString(R.string.no_images_selected)
        }
        
        binding.btnConvertToPdf.isEnabled = count > 0
        binding.btnClearSelection.isEnabled = count > 0

        binding.btnOrganizePhotos.isEnabled = isDateRangeSet
    }

    private fun setupModeToggle() {
        // 預設選擇照片模式
        binding.toggleMode.check(R.id.btnModePhotos)
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnModePhotos -> {
                    binding.groupPhotos.visibility = android.view.View.VISIBLE
                    binding.groupPdfs.visibility = android.view.View.GONE
                }
                R.id.btnModePdfs -> {
                    binding.groupPhotos.visibility = android.view.View.GONE
                    binding.groupPdfs.visibility = android.view.View.VISIBLE
                    loadPdfFiles()
                }
            }
        }
    }

    private fun loadPdfFiles() {
        val pdfDirectory = File(filesDir, "pdfs")
        if (!pdfDirectory.exists()) {
            pdfDirectory.mkdirs()
        }
        val files = pdfDirectory.listFiles { f -> f.isFile && f.extension.lowercase() == "pdf" }?.toList() ?: emptyList()
        pdfFiles.clear()
        pdfFiles.addAll(files.sortedByDescending { it.lastModified() })
        pdfAdapter.updatePdfFiles(pdfFiles.toList())
        binding.tvPdfEmptyMessage.visibility = if (pdfFiles.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    private fun convertToPdf() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_selected), Toast.LENGTH_SHORT).show()
            return
        }
        
        showProgress(true, getString(R.string.converting))
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val compressionLevel = when (binding.rgCompression.checkedRadioButtonId) {
                    R.id.rbNoCompression -> ImageCompressor.CompressionLevel.NONE
                    R.id.rbMediumCompression -> ImageCompressor.CompressionLevel.MEDIUM
                    else -> ImageCompressor.CompressionLevel.MINIMUM
                }

                // Update progress for compression
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = getString(R.string.compressing_images)
                }
                
                // Compress images
                val compressedImages = imageCompressor.compressImages(this@MainActivity, selectedImages, compressionLevel)
                
                // Update progress for PDF generation
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = getString(R.string.converting)
                }
                
                // Generate PDF
                val pdfFiles = pdfGenerator.createPdfFromImages(this@MainActivity, compressedImages)
                
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    if (pdfFiles.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "成功產生 ${pdfFiles.size} 個 PDF 檔案", Toast.LENGTH_SHORT).show()
                        // 切換到首頁 PDF 區並重新載入清單
                        binding.toggleMode.check(R.id.btnModePdfs)
                        loadPdfFiles()
                    } else {
                        Toast.makeText(this@MainActivity, "PDF 產生失敗", Toast.LENGTH_SHORT).show()
                    }
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
        binding.btnClearSelection.isEnabled = !show && selectedImages.isNotEmpty()
        binding.btnOrganizePhotos.isEnabled = !show && isDateRangeSet
    }
    
    private fun sharePdf(file: File) {
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
    
    private fun openPdf(file: File) {
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

    private fun splitPdf(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val splitFiles = PdfSplitter.splitPdfBySize(file, 12 * 1024 * 1024)
                withContext(Dispatchers.Main) {
                    if (splitFiles.isNotEmpty()) {
                        loadPdfFiles()
                        Toast.makeText(this@MainActivity, "分割完成，共 ${splitFiles.size} 個檔案", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "分割失敗，請確認檔案格式", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "分割過程發生錯誤", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
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

    private fun filterPhotosByDate() {
        if (!isDateRangeSet) {
            Toast.makeText(this, "請先設定日期時間範圍", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasPermissions()) {
            Toast.makeText(this, "需要讀取權限才能篩選照片", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }

        showProgress(true, "正在篩選照片...")

        CoroutineScope(Dispatchers.IO).launch {
            val filteredPhotos = mutableListOf<Uri>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ?"
            val selectionArgs = arrayOf(
                startDateTime.timeInMillis.toString(),
                endDateTime.timeInMillis.toString()
            )

            contentResolver.query(collection, projection, selection, selectionArgs, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    filteredPhotos.add(contentUri)
                }
            }

            withContext(Dispatchers.Main) {
                showProgress(false)
                selectedImages.clear()
                selectedImages.addAll(filteredPhotos)
                imageAdapter.updateImages(selectedImages)
                updateUI()
                Toast.makeText(this@MainActivity, "篩選出 ${filteredPhotos.size} 張照片", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        // Permissions granted, user can now use the features
        Toast.makeText(this, "權限已授予", Toast.LENGTH_SHORT).show()
    }
    
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }
}