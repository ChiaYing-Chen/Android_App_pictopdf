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
    // 使用應用程式內部存儲的 pdfs 目錄
    val pdfDirectory = File(context.filesDir, "pdfs")
    // 依據流水號取得下一個可用檔名（PIC000001.pdf ...）
    val (pdfFile, seqUsed) = getNextPdfFile(context, pdfDirectory)
        
        // 確保目錄存在
        if (!pdfDirectory.exists()) {
            pdfDirectory.mkdirs()
        }
        
        val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)
        
        var success = false
        try {
            imageFiles.forEach { imageFile ->
                addImageToDocument(document, imageFile)
            }
            success = true
        } finally {
            document.close()
            pdfDocument.close()
            pdfWriter.close()
        }
        // 僅在成功產生時，更新下一次的流水號
        if (success) {
            updateNextSequence(context, seqUsed + 1)
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

private const val PREFS_NAME = "pictopdf_prefs"
private const val KEY_PDF_SEQUENCE = "pdf_sequence"

private fun getNextPdfFile(context: Context, dir: File): Pair<File, Int> {
    if (!dir.exists()) dir.mkdirs()
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var seq = prefs.getInt(KEY_PDF_SEQUENCE, 1)
    var candidate: File
    do {
        val name = String.format(Locale.getDefault(), "PIC%06d.pdf", seq)
        candidate = File(dir, name)
        if (candidate.exists()) seq++ else break
    } while (true)
    return candidate to seq
}

private fun updateNextSequence(context: Context, nextSeq: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_PDF_SEQUENCE, nextSeq).apply()
}