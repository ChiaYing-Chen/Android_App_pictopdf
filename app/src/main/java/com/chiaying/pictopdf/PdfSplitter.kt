package com.chiaying.pictopdf

import android.content.Context
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream

object PdfSplitter {
    fun splitPdfBySize(pdfFile: File, maxSizeBytes: Int): List<File> {
        val context = pdfFile.parentFile // 用於產生新檔案
        val result = mutableListOf<File>()
        try {
            val reader = PdfReader(pdfFile)
            val pdfDoc = PdfDocument(reader)
            val totalPages = pdfDoc.numberOfPages
            var startPage = 1
            var partIndex = 1
            while (startPage <= totalPages) {
                // 估算每次分割的頁數
                var endPage = startPage
                var tempFile: File
                do {
                    tempFile = File(context, getSplitFileName(pdfFile, partIndex))
                    val writer = PdfWriter(FileOutputStream(tempFile))
                    val splitDoc = PdfDocument(writer)
                    pdfDoc.copyPagesTo((startPage..endPage).toList(), splitDoc)
                    splitDoc.close()
                    writer.close()
                    // 若檔案超過 maxSizeBytes 且頁數大於1，則減少頁數
                    if (tempFile.length() > maxSizeBytes && endPage > startPage) {
                        tempFile.delete()
                        endPage--
                    } else {
                        break
                    }
                } while (endPage > startPage)
                result.add(tempFile)
                startPage = endPage + 1
                partIndex++
            }
            pdfDoc.close()
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun getSplitFileName(original: File, index: Int): String {
        val name = original.nameWithoutExtension
        val ext = original.extension
        return "${name}分割${index}.$ext"
    }
}
