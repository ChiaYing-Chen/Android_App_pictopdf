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
        supportActionBar?.hide()
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
        pdfAdapter.onSelectionChanged = { selectedCount ->
            binding.btnDeleteSelectedPdfs.isEnabled = selectedCount > 0
            binding.btnRenameSelectedPdf.isEnabled = selectedCount == 1
        }

        binding.btnSelectAllPdfs.setOnClickListener {
            pdfAdapter.selectAll()
        }

        binding.btnDeleteSelectedPdfs.setOnClickListener {
            val toDelete = pdfAdapter.getSelectedFiles()
            if (toDelete.isEmpty()) return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                var success = true
                toDelete.forEach { f ->
                    if (!f.delete()) success = false
                }
                withContext(Dispatchers.Main) {
                    loadPdfFiles()
                    if (success) {
                        Toast.makeText(this@MainActivity, "已刪除 ${toDelete.size} 個檔案", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "部分檔案刪除失敗", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnRenameSelectedPdf.setOnClickListener {
            val selected = pdfAdapter.getSelectedFiles()
            if (selected.size != 1) return@setOnClickListener
            val target = selected.first()
            showRenameDialog(target)
        }
    }
    
    private fun setupClickListeners() {
        // 依相片庫自行挑選
        binding.btnPickManually.setOnClickListener {
            if (hasPermissions()) openImagePicker() else requestPermissions()
        }

        // 依時間範圍挑選：顯示快速選項（近1小時/4小時/8小時/自訂）
        binding.btnPickByTime.setOnClickListener {
            showQuickTimeOptions()
        }

        binding.btnConvertToPdf.setOnClickListener {
            convertToPdf()
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

        // convert 可用性維持不變，其餘由手動挑選或時間挑選觸發
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

    private fun showRenameDialog(file: File) {
        val context = this
        val editText = android.widget.EditText(context).apply {
            setText(file.nameWithoutExtension)
            hint = "輸入新檔名"
        }
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("重新命名")
            .setView(editText)
            .setPositiveButton("確定") { dialog, _ ->
                val newNameRaw = editText.text?.toString()?.trim().orEmpty()
                if (newNameRaw.isEmpty()) {
                    Toast.makeText(context, "檔名不可為空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val safeName = newNameRaw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val newFile = File(file.parentFile, "$safeName.${file.extension}")
                if (newFile.exists()) {
                    Toast.makeText(context, "同名檔案已存在", Toast.LENGTH_SHORT).show()
                } else {
                    val ok = file.renameTo(newFile)
                    if (ok) {
                        Toast.makeText(context, "已重新命名", Toast.LENGTH_SHORT).show()
                        loadPdfFiles()
                    } else {
                        Toast.makeText(context, "重新命名失敗", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .show()
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
        binding.btnPickManually.isEnabled = !show
        binding.btnPickByTime.isEnabled = !show
        binding.btnConvertToPdf.isEnabled = !show && selectedImages.isNotEmpty()
        binding.btnClearSelection.isEnabled = !show && selectedImages.isNotEmpty()
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

    private fun showQuickTimeOptions() {
        val options = arrayOf("近1小時", "近4小時", "近8小時", "自訂")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("時間篩選")
            .setItems(options) { dialog, which ->
                val now = Calendar.getInstance()
                when (which) {
                    0 -> { // 近1小時
                        endDateTime = now
                        startDateTime = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -1) }
                        isDateRangeSet = true
                        updateDateRangeText()
                        filterPhotosByDate()
                    }
                    1 -> { // 近4小時
                        endDateTime = now
                        startDateTime = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -4) }
                        isDateRangeSet = true
                        updateDateRangeText()
                        filterPhotosByDate()
                    }
                    2 -> { // 近8小時
                        endDateTime = now
                        startDateTime = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -8) }
                        isDateRangeSet = true
                        updateDateRangeText()
                        filterPhotosByDate()
                    }
                    else -> {
                        // 自訂：沿用現有日期時間挑選流程
                        showDateTimePicker()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { d, _ -> d.dismiss() }
            .show()
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
                    // 時間設定完成後，自動依時間範圍篩選照片
                    filterPhotosByDate()
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