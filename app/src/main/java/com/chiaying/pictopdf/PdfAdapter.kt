package com.chiaying.pictopdf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chiaying.pictopdf.databinding.ItemPdfBinding
import java.io.File

class PdfAdapter(private var pdfFiles: List<File>) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    var onOpenClickListener: ((File) -> Unit)? = null
    var onShareClickListener: ((File) -> Unit)? = null

    class PdfViewHolder(val binding: ItemPdfBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val binding = ItemPdfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PdfViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val pdfFile = pdfFiles[position]
        holder.binding.tvPdfName.text = pdfFile.name

        holder.binding.btnOpen.setOnClickListener {
            onOpenClickListener?.invoke(pdfFile)
        }

        holder.binding.btnShare.setOnClickListener {
            onShareClickListener?.invoke(pdfFile)
        }
    }

    override fun getItemCount(): Int = pdfFiles.size

    fun updatePdfFiles(newPdfFiles: List<File>) {
        pdfFiles = newPdfFiles
        notifyDataSetChanged()
    }
}
