package com.riyazuddin.zing.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.riyazuddin.zing.data.entities.Comment
import com.riyazuddin.zing.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class CommentAdapter @Inject constructor(private val glide: RequestManager) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root)

    private val differCallback = object : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.commentId == newItem.commentId
        }
    }
    private val differ = AsyncListDiffer(this, differCallback)

    var comments: List<Comment>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        return CommentViewHolder(
            ItemCommentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = comments.size

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.binding.apply {
            val comment = comments[position]
            glide.load(comment.userProfilePic).into(CIVProfilePic)
            tvUsername.text = comment.username
            tvCommentText.text = comment.comment
            val date =
                SimpleDateFormat("d MMM yyyy HH:mm", Locale.ENGLISH).format(Date(comment.date))
            tvTime.text = date

            CIVProfilePic.setOnClickListener {
                onUserClickListener?.let { click ->
                    click(comment)
                }
            }
            tvUsername.setOnClickListener {
                onUserClickListener?.let { click ->
                    click(comment)
                }
            }
        }
    }

    private var onUserClickListener: ((Comment) -> Unit)? = null
    fun setOnUserClickListener(listener: (Comment) -> Unit) {
        onUserClickListener = listener
    }
}