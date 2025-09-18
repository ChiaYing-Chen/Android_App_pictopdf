package com.chiaying.pictopdf

import android.content.Context
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PdfGenerator {

    companion object {
        private const val MAX_PDF_SIZE_BYTES = 12 * 1024 * 1024 // 12 MB
    }
    
    suspend fun createPdfFromImages(context: Context, imageFiles: List<File>): List<File> {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val pdfFileName = "PictoPDF_$timestamp.pdf"
        
        // 使用Application Context確保路徑一致性
        val appContext = context.applicationContext
        val pdfDirectory = appContext.getExternalFilesDir(null)
        val pdfFile = File(pdfDirectory, pdfFileName)
        
        // 詳細調試日誌
        android.util.Log.d("PdfGenerator", "=== PDF 創建開始 ===")
        android.util.Log.d("PdfGenerator", "原始Context類型: ${context.javaClass.simpleName}")
        android.util.Log.d("PdfGenerator", "Application Context類型: ${appContext.javaClass.simpleName}")
        android.util.Log.d("PdfGenerator", "PDF目錄: $pdfDirectory")
        android.util.Log.d("PdfGenerator", "PDF檔案路徑: ${pdfFile.absolutePath}")
        android.util.Log.d("PdfGenerator", "目錄是否存在: ${pdfDirectory?.exists()}")
        android.util.Log.d("PdfGenerator", "目錄是否可寫: ${pdfDirectory?.canWrite()}")
        
        // 確保目錄存在
        if (pdfDirectory != null && !pdfDirectory.exists()) {
            val created = pdfDirectory.mkdirs()
            android.util.Log.d("PdfGenerator", "目錄創建結果: $created")
        }
        
        val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)
        
        try {
            android.util.Log.d("PdfGenerator", "開始添加 ${imageFiles.size} 張圖片")
            imageFiles.forEachIndexed { index, imageFile ->
                android.util.Log.d("PdfGenerator", "添加圖片 ${index + 1}: ${imageFile.absolutePath}")
                addImageToDocument(document, imageFile)
            }
        } finally {
            document.close()
            pdfDocument.close()
            pdfWriter.close()
        }
        
        // 檔案創建後驗證
        android.util.Log.d("PdfGenerator", "=== PDF 創建完成 ===")
        android.util.Log.d("PdfGenerator", "檔案是否存在: ${pdfFile.exists()}")
        android.util.Log.d("PdfGenerator", "檔案大小: ${pdfFile.length()} bytes")
        android.util.Log.d("PdfGenerator", "檔案可讀: ${pdfFile.canRead()}")
        
        // 列出目錄中的所有檔案
        pdfDirectory?.listFiles()?.let { files ->
            android.util.Log.d("PdfGenerator", "目錄中所有檔案 (${files.size} 個):")
            files.forEach { file ->
                android.util.Log.d("PdfGenerator", "  - ${file.name} (${file.length()} bytes)")
            }
        }
        
        return listOf(pdfFile)
    }
    
    private fun addImageToDocument(document: Document, imageFile: File) {
        try {
            val imageData = ImageDataFactory.create(imageFile.absolutePath)
            val image = Image(imageData)
            
            // Get page dimensions
            val pageWidth = document.pdfDocument.defaultPageSize.width
            val pageHeight = document.pdfDocument.defaultPageSize.height
            
            // Calculate margins (20 points on each side)
            val margin = 20f
            val availableWidth = pageWidth - 2 * margin
            val availableHeight = pageHeight - 2 * margin
            
            // Scale image to fit page while maintaining aspect ratio
            val imageWidth = image.imageWidth
            val imageHeight = image.imageHeight
            val aspectRatio = imageWidth / imageHeight
            
            var newWidth = availableWidth
            var newHeight = newWidth / aspectRatio
            
            // If height is too large, scale based on height instead
            if (newHeight > availableHeight) {
                newHeight = availableHeight
                newWidth = newHeight * aspectRatio
            }
            
            image.setWidth(UnitValue.createPointValue(newWidth))
            image.setHeight(UnitValue.createPointValue(newHeight))
            image.setHorizontalAlignment(HorizontalAlignment.CENTER)
            
            // Add the image to a new page
            document.add(image)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}