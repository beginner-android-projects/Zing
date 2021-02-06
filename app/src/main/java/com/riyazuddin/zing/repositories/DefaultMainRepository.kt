package com.riyazuddin.zing.repositories

import android.net.Uri
import android.util.Log
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import com.riyazuddin.zing.data.entities.*
import com.riyazuddin.zing.other.Constants.CHATS_COLLECTION
import com.riyazuddin.zing.other.Constants.COMMENTS_COLLECTION
import com.riyazuddin.zing.other.Constants.DEFAULT_PROFILE_PICTURE_URL
import com.riyazuddin.zing.other.Constants.LAST_MESSAGE_COLLECTION
import com.riyazuddin.zing.other.Constants.MESSAGES
import com.riyazuddin.zing.other.Constants.POSTS_COLLECTION
import com.riyazuddin.zing.other.Constants.USERS_COLLECTION
import com.riyazuddin.zing.other.Resource
import com.riyazuddin.zing.other.safeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class DefaultMainRepository : MainRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection(USERS_COLLECTION)
    private val postsCollection = firestore.collection(POSTS_COLLECTION)
    private val commentsCollection = firestore.collection(COMMENTS_COLLECTION)
    private val chatsCollection = firestore.collection(CHATS_COLLECTION)
    private val lastMessagesCollection = firestore.collection(LAST_MESSAGE_COLLECTION)
    private val storage = FirebaseStorage.getInstance()

    override suspend fun createPost(imageUri: Uri, caption: String) = withContext(Dispatchers.IO) {
        safeCall {
            val uid = auth.uid!!
            val postID = UUID.randomUUID().toString()
            val postDownloadUrl = storage.reference.child("posts/$uid/$postID").putFile(imageUri)
                .await().metadata?.reference?.downloadUrl?.await().toString()
            val post = Post(postID, uid, System.currentTimeMillis(), postDownloadUrl, caption)
            postsCollection.document(postID).set(post).await()
            Resource.Success(Any())
        }
    }

    override suspend fun searchUser(query: String) = withContext(Dispatchers.IO) {
        safeCall {
            val usersList = usersCollection.whereGreaterThanOrEqualTo(
                "username",
                query.toUpperCase(Locale.ROOT)
            )
                .get().await().toObjects(User::class.java)
            Resource.Success(usersList)
        }
    }

    override suspend fun getUserProfile(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            val user = usersCollection.document(uid).get().await().toObject(User::class.java)
                ?: throw IllegalStateException()
            val currentUid = auth.uid!!
            val currentUser =
                usersCollection.document(currentUid).get().await().toObject(User::class.java)
                    ?: throw IllegalStateException()

            user.isFollowing = uid in currentUser.follows
            Resource.Success(user)
        }
    }

    override suspend fun getUsers(uids: List<String>) = withContext(Dispatchers.IO) {
        safeCall {
            val usersList =
                usersCollection.whereIn("uid", uids).orderBy("username").get().await()
                    .toObjects(User::class.java)
            Resource.Success(usersList)
        }
    }

    override suspend fun getPostForProfile(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            val posts = postsCollection
                .whereEqualTo("authorUid", uid)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Post::class.java)
                .onEach { post ->
                    val user = getUserProfile(uid).data!!
                    post.username = user.username
                    post.userProfilePic = user.profilePicUrl
                    post.isLiked = uid in post.likedBy
                }
            Resource.Success(posts)
        }
    }

    override suspend fun toggleLikeForPost(post: Post) = withContext(Dispatchers.IO) {
        safeCall {
            var isLiked = false
            firestore.runTransaction { transition ->
                val uid = auth.uid!!
                val currentLikes = transition.get(postsCollection.document(post.postId))
                    .toObject(Post::class.java)?.likedBy ?: listOf()
                transition.update(
                    postsCollection.document(post.postId),
                    "likedBy",
                    if (uid in currentLikes) currentLikes - uid
                    else {
                        isLiked = true
                        currentLikes + uid
                    }
                )
            }.await()
            Resource.Success(isLiked)
        }
    }

    override suspend fun deletePost(post: Post) = withContext(Dispatchers.IO) {
        safeCall {
            postsCollection.document(post.postId).delete().await()
            storage.getReferenceFromUrl(post.imageUrl).delete().await()
            Resource.Success(post)
        }
    }

    override suspend fun toggleFollowForUser(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            var isFollowing = false
            firestore.runTransaction { transition ->
                val currentUid = auth.uid!!
                val currentUser =
                    transition.get(usersCollection.document(currentUid))
                        .toObject(User::class.java)!!
                isFollowing = uid in currentUser.follows
                val newFollows =
                    if (isFollowing)
                        currentUser.follows - uid
                    else {
                        currentUser.follows + uid
                    }
                transition.update(
                    usersCollection.document(currentUid), "follows", newFollows
                )
            }.await()
            Resource.Success(!isFollowing)
        }
    }

    override suspend fun getPostForFollows() = withContext(Dispatchers.IO) {
        safeCall {
            val uid = auth.uid!!
            val followsList = getUserProfile(uid).data!!.follows
            val allPosts = postsCollection.whereIn("authorUid", followsList)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Post::class.java)
                .onEach { post ->
                    val user = getUserProfile(post.authorUid).data!!
                    post.username = user.username
                    post.userProfilePic = user.profilePicUrl
                    post.isLiked = uid in post.likedBy
                }
            Resource.Success(allPosts)
        }
    }

    override suspend fun createComment(commentText: String, postId: String) =
        withContext(Dispatchers.IO) {
            safeCall {
                val uid = auth.uid!!
                val commentId = UUID.randomUUID().toString()
                val date = System.currentTimeMillis()
                val comment = Comment(commentId, commentText, postId, date, uid)
                commentsCollection.document(commentId).set(comment).await()
                Resource.Success(comment)
            }
        }

    override suspend fun getPostComments(postId: String) = withContext(Dispatchers.IO) {
        safeCall {
            val comments = commentsCollection.whereEqualTo("postId", postId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Comment::class.java)
                .onEach { comment ->
                    val user = getUserProfile(comment.authorUid).data!!
                    comment.username = user.username
                    comment.userProfilePic = user.profilePicUrl
                }
            Resource.Success(comments)
        }
    }

    override suspend fun updateProfilePic(uid: String, imageUri: Uri) =
        withContext(Dispatchers.IO) {
            val storageRef = storage.reference.child("profilePics/$uid")
            val user = getUserProfile(uid).data!!
            if (user.profilePicUrl != DEFAULT_PROFILE_PICTURE_URL) {
                storage.getReferenceFromUrl(user.profilePicUrl).delete().await()
            }
            storageRef.putFile(imageUri).await().metadata?.reference?.downloadUrl?.await()
                .toString()
        }

    override suspend fun updateProfile(updateProfile: UpdateProfile, imageUri: Uri?) =
        withContext(Dispatchers.IO) {
            safeCall {
                val imageDownloadUrl = imageUri?.let {
                    updateProfilePic(updateProfile.uidToUpdate, it)
                }

                val map = mutableMapOf(
                    "name" to updateProfile.name,
                    "username" to updateProfile.username,
                    "bio" to updateProfile.bio
                )
                imageDownloadUrl?.let {
                    map["profilePicUrl"] = it
                }

                usersCollection.document(updateProfile.uidToUpdate).update(map.toMap()).await()
                Resource.Success(Any())
            }
        }

    override suspend fun searchUsername(query: String): Resource<QuerySnapshot> {
        return withContext(Dispatchers.IO) {
            safeCall {
                val result = usersCollection.whereEqualTo("username", query).get().await()
                Resource.Success(result)
            }
        }
    }

    override suspend fun verifyAccount(currentPassword: String): Resource<Any> =
        withContext(Dispatchers.IO) {
            safeCall {
                val currentUser = auth.currentUser!!
                val email = currentUser.email.toString()
                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                currentUser.reauthenticate(credential).await()
                Resource.Success("Verification Success")
            }
        }

    override suspend fun changePassword(
        newPassword: String
    ): Resource<Any> = withContext(Dispatchers.IO) {
        safeCall {
            val currentUser = auth.currentUser!!
            currentUser.updatePassword(newPassword).await()
            Resource.Success("Password Changed Successfully")
        }
    }

    override suspend fun getFollowersList(uid: String): Resource<List<User>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val list = usersCollection.whereArrayContains("follows", uid)
                    .orderBy("name", Query.Direction.ASCENDING)
                    .get()
                    .await()
                    .toObjects(User::class.java)

                Resource.Success(list)
            }
        }

    override suspend fun sendMessage(
        currentUid: String,
        receiverUid: String,
        message: String,
        type: String,
        uri: Uri?,
        senderName: String,
        senderUsername: String,
        senderProfilePicUrl: String,
        receiverName: String,
        receiverUsername: String,
        receiveProfileUrl: String
    ): Resource<Message> = withContext(Dispatchers.IO) {
        safeCall {
            val chatThread = getChatThread(currentUid, receiverUid)
            val messageId = UUID.randomUUID().toString()

            val messageMediaUrl = uri?.let {
                Log.e("TAG", "sendMessage: Uri not null")
                storage.reference.child("chatMedia/$chatThread/$messageId").putFile(it)
                    .await().metadata?.reference?.downloadUrl?.await().toString()
            }


            Log.e("TAG", "sendMessage: stored $messageMediaUrl")

            val messageOb = Message(
                messageId = messageId,
                message = message,
                type = type,
                date = System.currentTimeMillis(),
                senderAddReceiverUid = listOf(currentUid, receiverUid),
                url = messageMediaUrl ?: ""
            )

            Log.e("TAG", "sendMessage: message created")
            //upload message and lastMessage
            chatsCollection.document(chatThread)
                .collection(MESSAGES).document(messageId)
                .set(messageOb)
                .await()
            Log.e("TAG", "sendMessage: message posted")

            val lastMessage = LastMessage(
                messageId = messageId,
                message = message,
                type = type,
                date = System.currentTimeMillis(),
                senderAddReceiverUid = listOf(currentUid, receiverUid),
                url = messageMediaUrl ?: "",

                senderName = senderName,
                senderUserName = senderUsername,
                senderProfilePicUrl = senderProfilePicUrl,

                receiverName = receiverName,
                receiverUsername = receiverUsername,
                receiverProfilePicUrl = receiveProfileUrl
            )

            chatsCollection.document(chatThread).set(lastMessage).await()

            Log.e("TAG", "sendMessage: last message updated")


            Resource.Success(messageOb)
        }
    }

    override suspend fun deleteChatMessage(
        currentUid: String,
        receiverUid: String, message: Message
    ): Resource<Message> = withContext(Dispatchers.IO) {
        safeCall {
            chatsCollection.document(getChatThread(currentUid, receiverUid)).collection(MESSAGES)
                .document(message.messageId)
                .delete().await()
            Resource.Success(message)
        }
    }

    override suspend fun getChat(
        currentUid: String,
        otherEndUserUid: String
    ): Resource<FirestoreRecyclerOptions<Message>> = withContext(Dispatchers.IO) {
        val query = FirebaseFirestore.getInstance()
            .collection(CHATS_COLLECTION)
            .document(getChatThread(currentUid, otherEndUserUid))
            .collection(MESSAGES).orderBy("date", Query.Direction.ASCENDING)

        val options =
            FirestoreRecyclerOptions.Builder<Message>().setQuery(query, Message::class.java)
                .build()

        Resource.Success(options)
    }

    override suspend fun getLastMessageFirestoreRecyclerOptions(uid: String): Resource<FirestoreRecyclerOptions<LastMessage>> =
        withContext(Dispatchers.IO) {

            val query = FirebaseFirestore.getInstance()
                .collection(CHATS_COLLECTION)
                .whereArrayContains("senderAddReceiverUid", uid)
                .orderBy("date", Query.Direction.DESCENDING)

            val options =
                FirestoreRecyclerOptions.Builder<LastMessage>()
                    .setQuery(query, LastMessage::class.java)
                    .build()

            Resource.Success(options)

        }

    private fun getChatThread(currentUid: String, otherEndUserUid: String) =
        if (currentUid < otherEndUserUid)
            currentUid + otherEndUserUid
        else
            otherEndUserUid + currentUid
}