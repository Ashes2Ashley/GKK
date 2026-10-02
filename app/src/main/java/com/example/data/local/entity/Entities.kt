package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.api.model.Post

/**
 * Room Entity representing locally cached posts for offline-first support.
 */
@Entity(tableName = "cached_posts")
data class CachedPostEntity(
    @PrimaryKey
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toPost(): Post = Post(
        id = id,
        userId = userId,
        title = title,
        body = body
    )

    companion object {
        fun fromPost(post: Post): CachedPostEntity? {
            val id = post.id ?: return null
            return CachedPostEntity(
                id = id,
                userId = post.userId,
                title = post.title,
                body = post.body
            )
        }
    }
}

/**
 * Room Entity representing user-starred/favorited posts with notes and tags.
 */
@Entity(tableName = "favorite_posts")
data class FavoritePostEntity(
    @PrimaryKey
    val postId: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val note: String = "",
    val tag: String = "General",
    val savedAt: Long = System.currentTimeMillis()
)
