package com.chiaying.pictopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImageCompressor {

    enum class CompressionLevel(val quality: Int) {
        NONE(100),
        MEDIUM(80),
        MINIMUM(50)
    }
    
    companion object {
        private const val MAX_WIDTH = 1200
        private const val MAX_HEIGHT = 1600
    }
    
    suspend fun compressImages(context: Context, imageUris: List<Uri>, compressionLevel: CompressionLevel): List<File> {
        val compressedFiles = mutableListOf<File>()
        
        imageUris.forEachIndexed { index, uri ->
            try {
                val compressedFile = compressImage(context, uri, index, compressionLevel)
                compressedFile?.let { compressedFiles.add(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return compressedFiles
    }
    
    private suspend fun compressImage(context: Context, uri: Uri, index: Int, compressionLevel: CompressionLevel): File? {
        try {
            if (compressionLevel == CompressionLevel.NONE) {
                // For "No Compression", just copy the file to cache to get a File object
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val tempFile = File(context.cacheDir, "image_$index.jpg")
                val outputStream = FileOutputStream(tempFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                return tempFile
            }

            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            
            // Decode the image with proper scaling
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT)
            options.inJustDecodeBounds = false
            
            // Decode the actual bitmap
            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2.close()
            
            bitmap ?: return null
            
            // Rotate bitmap if needed based on EXIF data
            val rotatedBitmap = rotateBitmapIfNeeded(context, uri, bitmap)
            
            // Scale down further if still too large
            val scaledBitmap = scaleBitmapIfNeeded(rotatedBitmap, MAX_WIDTH, MAX_HEIGHT)
            
            // Compress and save to temporary file
            val compressedFile = File(context.cacheDir, "compressed_image_$index.jpg")
            val outputStream = FileOutputStream(compressedFile)
            
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, compressionLevel.quality, outputStream)
            outputStream.close()
            
            // Clean up bitmaps
            if (bitmap != rotatedBitmap) bitmap.recycle()
            if (rotatedBitmap != scaledBitmap) rotatedBitmap.recycle()
            scaledBitmap.recycle()
            
            return compressedFile
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    private fun rotateBitmapIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()
            
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            
        } catch (e: IOException) {
            e.printStackTrace()
            return bitmap
        }
    }
    
    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (aspectRatio > 1) {
            newWidth = maxWidth
            newHeight = (maxWidth / aspectRatio).toInt()
        } else {
            newHeight = maxHeight
            newWidth = (maxHeight * aspectRatio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}