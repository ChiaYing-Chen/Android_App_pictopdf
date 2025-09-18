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
        
        val pdfFile = File(context.getExternalFilesDir(null), pdfFileName)
        val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)
        
        try {
            imageFiles.forEach { imageFile ->
                addImageToDocument(document, imageFile)
            }
        } finally {
            document.close()
            pdfDocument.close()
            pdfWriter.close()
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