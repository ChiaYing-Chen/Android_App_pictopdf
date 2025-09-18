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
        // 使用Application Context確保路徑一致性
        val appContext = applicationContext
        val pdfDirectory = appContext.getExternalFilesDir(null)
        
        // 詳細調試日誌
        android.util.Log.d("PdfListActivity", "=== PDF 清單載入開始 ===")
        android.util.Log.d("PdfListActivity", "Activity類型: ${this.javaClass.simpleName}")
        android.util.Log.d("PdfListActivity", "Application Context類型: ${appContext.javaClass.simpleName}")
        android.util.Log.d("PdfListActivity", "PDF目錄: $pdfDirectory")
        android.util.Log.d("PdfListActivity", "目錄是否存在: ${pdfDirectory?.exists()}")
        android.util.Log.d("PdfListActivity", "目錄是否可讀: ${pdfDirectory?.canRead()}")
        
        if (pdfDirectory != null && pdfDirectory.exists()) {
            // 列出所有檔案
            val allFiles = pdfDirectory.listFiles()
            android.util.Log.d("PdfListActivity", "目錄中總檔案數: ${allFiles?.size}")
            
            allFiles?.forEach { file ->
                android.util.Log.d("PdfListActivity", "檔案: ${file.name}")
                android.util.Log.d("PdfListActivity", "  - 大小: ${file.length()} bytes")
                android.util.Log.d("PdfListActivity", "  - 副檔名: ${file.extension}")
                android.util.Log.d("PdfListActivity", "  - 是否為PDF: ${file.extension.lowercase() == "pdf"}")
                android.util.Log.d("PdfListActivity", "  - 是否為檔案: ${file.isFile}")
                android.util.Log.d("PdfListActivity", "  - 最後修改: ${java.util.Date(file.lastModified())}")
            }
            
            // 過濾PDF檔案
            val files = pdfDirectory.listFiles { file ->
                val isPdf = file.isFile && file.extension.lowercase() == "pdf"
                android.util.Log.d("PdfListActivity", "過濾檔案 ${file.name}: isPdf=$isPdf")
                isPdf
            }
            
            if (files != null) {
                pdfFiles.clear()
                pdfFiles.addAll(files.sortedByDescending { it.lastModified() })
                pdfAdapter.updatePdfFiles(pdfFiles)
                android.util.Log.d("PdfListActivity", "找到 ${pdfFiles.size} 個PDF檔案")
                
                pdfFiles.forEach { file ->
                    android.util.Log.d("PdfListActivity", "PDF清單: ${file.name} (${file.length()} bytes)")
                }
            } else {
                android.util.Log.w("PdfListActivity", "listFiles() 返回 null")
            }
        } else {
            android.util.Log.e("PdfListActivity", "目錄不存在或無法存取: $pdfDirectory")
        }
        
        android.util.Log.d("PdfListActivity", "=== PDF 清單載入完成 ===")
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
            Toast.makeText(this, "無法開啟 PDF，請安裝 PDF 閱讀器", Toast.LENGTH_SHORT).show()
        }
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

        val chooser = Intent.createChooser(shareIntent, "分享 PDF")
        startActivity(chooser)
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