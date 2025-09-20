package com.chiaying.pictopdf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chiaying.pictopdf.databinding.ItemPdfBinding
import java.io.File

class PdfAdapter(private var pdfFiles: List<File>) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    var onOpenClickListener: ((File) -> Unit)? = null
    var onShareClickListener: ((File) -> Unit)? = null
    var onSplitClickListener: ((File) -> Unit)? = null
    var onSelectionChanged: ((selectedCount: Int) -> Unit)? = null

    private val selectedSet = linkedSetOf<File>()
    var selectionMode: Boolean = true // always show checkbox for bulk actions

    class PdfViewHolder(val binding: ItemPdfBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val binding = ItemPdfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PdfViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val pdfFile = pdfFiles[position]
        holder.binding.tvPdfName.text = pdfFile.name
        holder.binding.tvPdfSize.text = String.format("%.1fMB", pdfFile.length() / 1024f / 1024f)

        // checkbox visibility and state
        holder.binding.cbSelect.isChecked = selectedSet.contains(pdfFile)
        holder.binding.cbSelect.setOnCheckedChangeListener(null)
        holder.binding.cbSelect.isChecked = selectedSet.contains(pdfFile)
        holder.binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedSet.add(pdfFile) else selectedSet.remove(pdfFile)
            onSelectionChanged?.invoke(selectedSet.size)
        }

        holder.binding.btnOpen.setOnClickListener {
            onOpenClickListener?.invoke(pdfFile)
        }

        holder.binding.btnShare.setOnClickListener {
            onShareClickListener?.invoke(pdfFile)
        }

        holder.binding.btnSplit.setOnClickListener {
            onSplitClickListener?.invoke(pdfFile)
        }
        holder.binding.btnSplit.visibility =
            if (pdfFile.length() > 12 * 1024 * 1024) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun getItemCount(): Int = pdfFiles.size

    fun updatePdfFiles(newPdfFiles: List<File>) {
        pdfFiles = newPdfFiles
        selectedSet.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun selectAll() {
        selectedSet.clear()
        selectedSet.addAll(pdfFiles)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedSet.size)
    }

    fun clearSelection() {
        selectedSet.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedFiles(): List<File> = selectedSet.toList()
}
