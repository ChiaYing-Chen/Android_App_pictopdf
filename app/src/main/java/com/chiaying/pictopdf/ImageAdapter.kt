package com.chiaying.pictopdf

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chiaying.pictopdf.databinding.ItemImageBinding

class ImageAdapter(
    private val context: Context,
    private var images: List<Uri>
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
    
    class ImageViewHolder(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUri = images[position]
        
        // Load image using Glide
        Glide.with(context)
            .load(imageUri)
            .centerCrop()
            .into(holder.binding.ivImage)
        
        // Set image name (simplified - just showing the last part of the URI)
        val imageName = imageUri.lastPathSegment ?: "Image ${position + 1}"
        holder.binding.tvImageName.text = imageName
    }
    
    override fun getItemCount(): Int = images.size
    
    fun updateImages(newImages: List<Uri>) {
        images = newImages
        notifyDataSetChanged()
    }
}