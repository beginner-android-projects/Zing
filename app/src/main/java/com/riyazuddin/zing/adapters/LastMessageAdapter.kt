package com.riyazuddin.zing.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.riyazuddin.zing.data.entities.LastMessage
import com.riyazuddin.zing.data.entities.User
import com.riyazuddin.zing.databinding.ItemRecentChatBinding
import com.riyazuddin.zing.other.Constants.IMAGE
import com.riyazuddin.zing.other.Constants.SEEN
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class LastMessageAdapter @Inject constructor(private val glide: RequestManager) :
    RecyclerView.Adapter<LastMessageAdapter.LastMessageViewHolder>() {

    inner class LastMessageViewHolder(val binding: ItemRecentChatBinding) :
        RecyclerView.ViewHolder(binding.root)

    private val differCallback = object : DiffUtil.ItemCallback<LastMessage>() {
        override fun areItemsTheSame(oldItem: LastMessage, newItem: LastMessage): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }

        override fun areContentsTheSame(oldItem: LastMessage, newItem: LastMessage): Boolean {
            return oldItem.message.messageId == newItem.message.messageId
        }
    }

    private val differ = AsyncListDiffer(this, differCallback)

    var lastMessages: List<LastMessage>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LastMessageViewHolder {
        return LastMessageViewHolder(
            ItemRecentChatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: LastMessageViewHolder, position: Int) {
        val lastMessage = lastMessages[position]
        holder.binding.apply {
            val isCurrentUserIsSender =
                Firebase.auth.uid == lastMessage.message.senderAndReceiverUid[0]

            if (isCurrentUserIsSender) {
                glide.load(lastMessage.receiverProfilePicUrl).into(CIVProfilePic)
                tvName.text = lastMessage.receiverName
            } else {
                glide.load(lastMessage.senderProfilePicUrl).into(CIVProfilePic)
                tvName.text = lastMessage.senderName

                if (lastMessage.message.status != SEEN) {
                    tvLastMessage.typeface = Typeface.DEFAULT_BOLD
                    ivUnSeen.isVisible = true
                }
            }
            val date =
                SimpleDateFormat("hh:mm a", Locale.US).format(Date(lastMessage.message.date))
                    .replace("AM", "am").replace("PM", "pm")
            tvDate.text = date
            if (lastMessage.message.type == IMAGE) {
                val s = "🖼 Photo"
                tvLastMessage.text = s
            } else {
                tvLastMessage.text = lastMessage.message.message
            }
            root.setOnClickListener {
                onItemClickListener?.let {
                    if (lastMessage.message.senderAndReceiverUid[0] == Firebase.auth.uid) {
                        val user = User(
                            name = lastMessage.receiverName,
                            uid = lastMessage.message.senderAndReceiverUid[1],
                            username = lastMessage.receiverUsername,
                            profilePicUrl = lastMessage.receiverProfilePicUrl
                        )
                        it(lastMessage, user)
                    } else {
                        val user = User(
                            name = lastMessage.senderName,
                            uid = lastMessage.message.senderAndReceiverUid[0],
                            username = lastMessage.senderUserName,
                            profilePicUrl = lastMessage.senderProfilePicUrl
                        )
                        it(lastMessage, user)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = lastMessages.size

    private var onItemClickListener: ((LastMessage, User) -> Unit)? = null
    fun setOnItemClickListener(listener: (LastMessage, User) -> Unit) {
        onItemClickListener = listener
    }
}