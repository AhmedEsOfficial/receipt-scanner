package com.pharadev.receiptscanner.presentation.ui.gallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.pharadev.receiptscanner.R
import com.pharadev.receiptscanner.databinding.ItemGalleryImageBinding

class GalleryAdapter(
    private val onImageClick: (Uri, Boolean) -> Unit
) : ListAdapter<GalleryImage, GalleryAdapter.GalleryImageViewHolder>(GalleryImageDiffCallback()) {
    
    private val selectedImages = mutableSetOf<Uri>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryImageViewHolder {
        val binding = ItemGalleryImageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GalleryImageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: GalleryImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun selectImage(uri: Uri) {
        selectedImages.add(uri)
        notifyItemChanged(getPositionForUri(uri))
    }
    
    fun deselectImage(uri: Uri) {
        selectedImages.remove(uri)
        notifyItemChanged(getPositionForUri(uri))
    }
    
    fun clearSelection() {
        val previouslySelected = selectedImages.toList()
        selectedImages.clear()
        previouslySelected.forEach { uri ->
            notifyItemChanged(getPositionForUri(uri))
        }
    }
    
    fun getUnselectedImages(): List<Uri> {
        return currentList.map { it.uri }.filter { it !in selectedImages }
    }
    
    private fun getPositionForUri(uri: Uri): Int {
        return currentList.indexOfFirst { it.uri == uri }
    }
    
    inner class GalleryImageViewHolder(
        private val binding: ItemGalleryImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(galleryImage: GalleryImage) {
            val isSelected = selectedImages.contains(galleryImage.uri)
            
            // Load image thumbnail
            Glide.with(binding.root.context)
                .load(galleryImage.uri)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .into(binding.imageView)
            
            // Update selection state
            binding.apply {
                selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
                selectionCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                
                // Set selection number if selected
                if (isSelected) {
                    val selectionIndex = selectedImages.indexOf(galleryImage.uri) + 1
                    selectionCheck.text = selectionIndex.toString()
                }
                
                // Add click listener
                root.setOnClickListener {
                    val newSelection = !isSelected
                    onImageClick(galleryImage.uri, newSelection)
                }
                
                // Add long press for image preview (optional)
                root.setOnLongClickListener {
                    // Could implement image preview on long press
                    true
                }
            }
        }
    }
}

class GalleryImageDiffCallback : DiffUtil.ItemCallback<GalleryImage>() {
    override fun areItemsTheSame(oldItem: GalleryImage, newItem: GalleryImage): Boolean {
        return oldItem.uri == newItem.uri
    }
    
    override fun areContentsTheSame(oldItem: GalleryImage, newItem: GalleryImage): Boolean {
        return oldItem == newItem
    }
}