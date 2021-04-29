package com.riyazuddin.zing.repositories.implementation

import android.net.Uri
import android.util.Log
import com.algolia.search.client.ClientSearch
import com.algolia.search.model.APIKey
import com.algolia.search.model.ApplicationID
import com.algolia.search.model.IndexName
import com.algolia.search.model.response.ResponseSearch
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.riyazuddin.zing.BuildConfig
import com.riyazuddin.zing.R
import com.riyazuddin.zing.data.entities.*
import com.riyazuddin.zing.other.Constants.COMMENTS_COLLECTION
import com.riyazuddin.zing.other.Constants.DEFAULT_PROFILE_PICTURE_URL
import com.riyazuddin.zing.other.Constants.FOLLOWERS_COLLECTION
import com.riyazuddin.zing.other.Constants.FOLLOWING_COLLECTION
import com.riyazuddin.zing.other.Constants.POSTS_COLLECTION
import com.riyazuddin.zing.other.Constants.POST_LIKES_COLLECTION
import com.riyazuddin.zing.other.Constants.USERS_COLLECTION
import com.riyazuddin.zing.other.Constants.USERS_STATE_COLLECTION
import com.riyazuddin.zing.other.Resource
import com.riyazuddin.zing.other.safeCall
import com.riyazuddin.zing.repositories.abstraction.MainRepository
import io.ktor.client.features.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Singleton

@Singleton
class DefaultMainRepository : MainRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection(USERS_COLLECTION)
    private val postsCollection = firestore.collection(POSTS_COLLECTION)
    private val commentsCollection = firestore.collection(COMMENTS_COLLECTION)
    private val followingCollection = firestore.collection(FOLLOWING_COLLECTION)
    private val followersCollection = firestore.collection(FOLLOWERS_COLLECTION)
    private val postLikesCollection = firestore.collection(POST_LIKES_COLLECTION)
    private val storage = FirebaseStorage.getInstance()

    override suspend fun onlineOfflineToggle(uid: String) {
        withContext(Dispatchers.IO) {
            safeCall {
                val userStatusDatabaseRef = FirebaseDatabase.getInstance().reference.child("status/$uid")
                val usersStateCollection = firestore.collection(USERS_STATE_COLLECTION).document(uid)

                val isOfflineForDatabase = mapOf(
                    "state" to "offline",
                    "last_change" to ServerValue.TIMESTAMP
                )

                val isOnlineForDatabase = mapOf(
                    "state" to "online",
                    "last_change" to ServerValue.TIMESTAMP
                )

                val isOfflineForFirestore = mapOf(
                    "state" to "offline",
                    "last_change" to FieldValue.serverTimestamp()
                )

                val isOnlineForFirestore = mapOf(
                    "state" to "online",
                    "last_change" to FieldValue.serverTimestamp()
                )
                Log.i(TAG, "onlineOfflineToggle: addingValueEvenListener")

//                val database = Firebase.database.reference
//                database.child("user").setValue(User("riyaz",uid)).await()
                FirebaseDatabase.getInstance().getReference(".info/connected")
                    .addValueEventListener(object :
                        ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            Log.i(TAG, "onDataChange: indise")
                            if (snapshot.value == false) {
                                GlobalScope.launch {
                                    usersStateCollection
                                        .set(isOfflineForFirestore, SetOptions.merge())
                                }
                                return
                            }
                            Log.i(TAG, "onDataChange: local")
                            userStatusDatabaseRef.onDisconnect().setValue(isOfflineForDatabase)
                                .addOnCompleteListener {
                                    Log.i(TAG, "onDataChange: inside")
                                    userStatusDatabaseRef.setValue(isOnlineForDatabase)
                                    usersStateCollection.set(isOnlineForFirestore, SetOptions.merge())
                                }
                        }

                        override fun onCancelled(error: DatabaseError) {

                        }
                    })
                Resource.Success(Any())
            }
        }
    }

    override suspend fun createPost(imageUri: Uri, caption: String) = withContext(Dispatchers.IO) {
        safeCall {
            val uid = auth.uid!!
            val postID = UUID.randomUUID().toString()
            val postDownloadUrl = storage.reference.child("posts/$uid/$postID").putFile(imageUri)
                .await().metadata?.reference?.downloadUrl?.await().toString()
            val post = Post(postID, uid, System.currentTimeMillis(), postDownloadUrl, caption)
            postsCollection.document(postID).set(post).await()
            usersCollection.document(uid).update("postCount", FieldValue.increment(1)).await()
            postLikesCollection.document(postID).set(PostLikes()).await()
            Resource.Success(Any())
        }
    }

    override suspend fun getUserProfile(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            val user = usersCollection.document(uid).get().await().toObject(User::class.java)
                ?: throw IllegalStateException()

            val currentUid = auth.uid!!

            val currentUserFollowing =
                followingCollection.document(currentUid).get().await()
                    .toObject(Following::class.java)
                    ?: throw IllegalStateException()

            user.isFollowing = uid in currentUserFollowing.following
            Resource.Success(user)
        }
    }

    override suspend fun getFollowing(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            val following =
                followingCollection.document(uid).get().await().toObject(Following::class.java)
                    ?: throw IllegalStateException()
            val result = getUsers(following.following)
            result
        }
    }

    override suspend fun getFollowers(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            val followers =
                followersCollection.document(uid).get().await().toObject(Followers::class.java)
                    ?: throw IllegalStateException()
            val result = getUsers(followers.followers)
            result
        }
    }

    override suspend fun getPostLikes(postId: String) = withContext(Dispatchers.IO) {
        safeCall {
            val postLikes =
                postLikesCollection.document(postId).get().await().toObject(PostLikes::class.java)
                    ?: throw IllegalStateException()
            Resource.Success(postLikes)
        }
    }

    override suspend fun getPostLikedUsers(postId: String): Resource<List<User>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val postLikes = getPostLikes(postId).data!!
                val usersList = getUsers(postLikes.likedBy).data!!
                Resource.Success(usersList)
            }
        }

    override suspend fun getUsers(uids: List<String>) = withContext(Dispatchers.IO) {
        safeCall {
            val chunks = uids.chunked(10)
            val resultList = mutableListOf<User>()
            chunks.forEach { chunk ->
                val usersList =
                    usersCollection.whereIn("uid", chunk).orderBy("username").get().await()
                        .toObjects(User::class.java)
                resultList.addAll(usersList)
            }

            Resource.Success(resultList.toList())
        }
    }

    override suspend fun getPostForProfile(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            val posts = postsCollection
                .whereEqualTo("postedBy", uid)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Post::class.java)
                .onEach { post ->
                    val user = getUserProfile(uid).data!!
                    post.username = user.username
                    post.userProfilePic = user.profilePicUrl
                    post.isLiked = uid in getPostLikes(post.postId).data!!.likedBy
                }
            Resource.Success(posts)
        }
    }

    override suspend fun toggleLikeForPost(post: Post) = withContext(Dispatchers.IO) {
        safeCall {
            var isLiked = false
            firestore.runTransaction { transition ->
                val uid = auth.uid!!

                val currentLikes = transition.get(postLikesCollection.document(post.postId))
                    .toObject(PostLikes::class.java)?.likedBy ?: listOf()

                if (uid in currentLikes) {
                    transition.update(
                        postLikesCollection.document(post.postId),
                        "likedBy",
                        currentLikes - uid
                    )
                    transition.update(
                        postsCollection.document(post.postId),
                        "likeCount", FieldValue.increment(-1)
                    )
                } else {
                    isLiked = true
                    transition.update(
                        postLikesCollection.document(post.postId),
                        "likedBy",
                        currentLikes + uid
                    )
                    transition.update(
                        postsCollection.document(post.postId),
                        "likeCount", FieldValue.increment(1)
                    )
                }
            }.await()
            Resource.Success(isLiked)
        }
    }

    override suspend fun deletePost(post: Post) = withContext(Dispatchers.IO) {
        safeCall {
            postsCollection.document(post.postId).delete().await()
            storage.getReferenceFromUrl(post.imageUrl).delete().await()
            usersCollection.document(post.postedBy).update("postCount", FieldValue.increment(-1))
                .await()
            Resource.Success(post)
        }
    }

    override suspend fun toggleFollowForUser(uid: String) = withContext(Dispatchers.IO) {
        safeCall {
            var isFollowing = false
            val currentUserUid = auth.uid!!

            firestore.runTransaction { transition ->

                val otherUserFollowersList = transition
                    .get(followersCollection.document(uid))
                    .toObject(Followers::class.java)!!


                isFollowing = currentUserUid in otherUserFollowersList.followers


                if (isFollowing) {
                    //unfollow
                    transition.update(
                        followingCollection.document(currentUserUid),
                        "following",
                        FieldValue.arrayRemove(uid)
                    )
                    transition.update(
                        followersCollection.document(uid),
                        "followers",
                        FieldValue.arrayRemove(currentUserUid)
                    )
                    transition.update(
                        usersCollection.document(currentUserUid),
                        "following",
                        FieldValue.increment(-1)
                    )
                    transition.update(
                        usersCollection.document(uid),
                        "followers",
                        FieldValue.increment(-1)
                    )

                } else {
                    //follow
                    transition.update(
                        followingCollection.document(currentUserUid),
                        "following",
                        FieldValue.arrayUnion(uid)
                    )
                    transition.update(
                        followersCollection.document(uid),
                        "followers",
                        FieldValue.arrayUnion(currentUserUid)
                    )
                    transition.update(
                        usersCollection.document(currentUserUid),
                        "following",
                        FieldValue.increment(1)
                    )
                    transition.update(
                        usersCollection.document(uid),
                        "followers",
                        FieldValue.increment(1)
                    )
                }
            }.await()
            Resource.Success(!isFollowing)
        }
    }

    override suspend fun getPostForFollows() = withContext(Dispatchers.IO) {
        safeCall {
            val uid = auth.uid!!
            val followsList = getFollowingList(uid).data!!.following
            val allPosts = postsCollection.whereIn("postedBy", followsList)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Post::class.java)
                .onEach { post ->
                    val user = getUserProfile(post.postedBy).data!!
                    post.username = user.username
                    post.userProfilePic = user.profilePicUrl
                    post.isLiked = uid in getPostLikes(post.postId).data!!.likedBy
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
                    val user = getUserProfile(comment.commentedBy).data!!
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

    /**
     * method used to check username availability
     */
    override suspend fun searchUsername(query: String): Resource<Boolean> {
        return withContext(Dispatchers.IO) {
            safeCall {
                val result = usersCollection.whereEqualTo("username", query).get().await()
                if (result.isEmpty)
                    Resource.Success(true)
                else
                    Resource.Success(false)
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

    override suspend fun getFollowersList(uid: String): Resource<Followers> =
        withContext(Dispatchers.IO) {
            safeCall {
                val followersList = followersCollection.document(uid)
                    .get()
                    .await()
                    .toObject(Followers::class.java)!!

                Resource.Success(followersList)
            }
        }

    override suspend fun getFollowingList(uid: String): Resource<Following> =
        withContext(Dispatchers.IO) {
            safeCall {
                val followingList = followingCollection.document(uid)
                    .get()
                    .await()
                    .toObject(Following::class.java)!!

                Resource.Success(followingList)
            }
        }

    override suspend fun algoliaSearch(searchQuery: String): Resource<ResponseSearch> =
        withContext(Dispatchers.IO) {
            safeCall {
                val client = ClientSearch(
                    ApplicationID(BuildConfig.ALGOLIA_APP_ID),
                    APIKey(BuildConfig.ALGOLIA_SEARCH_KEY),
                    LogLevel.ALL
                )
                val index = client.initIndex(IndexName("user_search"))

                val queryObj = com.algolia.search.model.search.Query(searchQuery)
                val result = index.search(queryObj)
                Resource.Success(result)
            }
        }

    companion object {
        const val TAG = "MainRepository"
    }
}