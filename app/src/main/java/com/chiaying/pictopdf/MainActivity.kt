package com.chiaying.pictopdf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ImageAdapter
    private val selectedImages = mutableListOf<Uri>()
    private var generatedPdfFile: File? = null
    
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
        
        binding.btnEmailPdf.setOnClickListener {
            shareViaEmail()
        }
        
        binding.btnSharePdf.setOnClickListener {
            shareGenerally()
        }
    }
    
    private fun checkPermissions() {
        if (!hasPermissions()) {
            requestPermissions()
        }
    }
    
    private fun hasPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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
        binding.btnEmailPdf.isEnabled = hasPdf
        binding.btnSharePdf.isEnabled = hasPdf
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
    }
    
    private fun shareViaEmail() {
        generatedPdfFile?.let { file ->
            val fileUri = FileProvider.getUriForFile(
                this,
                "com.chiaying.pictopdf.fileprovider",
                file
            )
            
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(""))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.email_body))
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            try {
                val chooser = Intent.createChooser(emailIntent, getString(R.string.choose_email_app))
                startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
            }
        }
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
    
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        // Permissions granted
    }
    
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }
}