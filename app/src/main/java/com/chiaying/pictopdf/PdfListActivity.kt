package com.chiaying.pictopdf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.chiaying.pictopdf.databinding.ActivityPdfListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfListActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPdfListBinding
    private lateinit var pdfAdapter: PdfAdapter
    private val pdfFiles = mutableListOf<File>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupClickListeners()
        loadPdfFiles()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次回到這個頁面時重新載入PDF檔案
        loadPdfFiles()
    }
    
    private fun setupRecyclerView() {
        pdfAdapter = PdfAdapter(pdfFiles)
        binding.rvPdfs.apply {
            layoutManager = LinearLayoutManager(this@PdfListActivity)
            adapter = pdfAdapter
        }
        
        pdfAdapter.onOpenClickListener = { file ->
            openPdf(file)
        }
        pdfAdapter.onShareClickListener = { file ->
            sharePdf(file)
        }
        pdfAdapter.onSplitClickListener = { file ->
            splitPdf(file)
        }
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun loadPdfFiles() {
        // 使用Documents/pictopdf目錄
        val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val pdfDirectory = File(documentsDir, "pictopdf")
        
        if (pdfDirectory.exists()) {
            // 過濾PDF檔案
            val files = pdfDirectory.listFiles { file ->
                file.isFile && file.extension.lowercase() == "pdf"
            }
            
            if (files != null) {
                pdfFiles.clear()
                pdfFiles.addAll(files.sortedByDescending { it.lastModified() })
                pdfAdapter.updatePdfFiles(pdfFiles)
            }
        } else {
            // 如果目錄不存在，嘗試創建
            pdfDirectory.mkdirs()
        }
        
        updateUI()
    }
    
    private fun updateUI() {
        if (pdfFiles.isEmpty()) {
            binding.rvPdfs.visibility = android.view.View.GONE
            binding.tvEmptyMessage.visibility = android.view.View.VISIBLE
        } else {
            binding.rvPdfs.visibility = android.view.View.VISIBLE
            binding.tvEmptyMessage.visibility = android.view.View.GONE
        }
    }
    
    private fun openPdf(file: File) {
        try {
            val fileUri = FileProvider.getUriForFile(
                this,
                "com.chiaying.pictopdf.fileprovider",
                file
            )

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(openIntent)
        } catch (e: Exception) {
            android.util.Log.e("PdfListActivity", "開啟PDF失敗", e)
            Toast.makeText(this, "無法開啟 PDF，請安裝 PDF 閱讀器", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sharePdf(file: File) {
        try {
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

            val chooser = Intent.createChooser(shareIntent, "分享 PDF")
            startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("PdfListActivity", "分享PDF失敗", e)
            Toast.makeText(this, "分享失敗，請重試", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun splitPdf(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val splitFiles = PdfSplitter.splitPdfBySize(file, 12 * 1024 * 1024)
                withContext(Dispatchers.Main) {
                    if (splitFiles.isNotEmpty()) {
                        loadPdfFiles() // 重新載入PDF清單
                        Toast.makeText(this@PdfListActivity, "分割完成，共 ${splitFiles.size} 個檔案", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PdfListActivity, "分割失敗，請確認檔案格式", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfListActivity, "分割過程發生錯誤", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }
}