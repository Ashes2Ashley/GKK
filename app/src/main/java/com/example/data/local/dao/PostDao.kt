package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CachedPostEntity
import com.example.data.local.entity.FavoritePostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    // --- Cached Posts (Offline Mode) ---
    @Query("SELECT * FROM cached_posts ORDER BY id ASC")
    fun getAllCachedPosts(): Flow<List<CachedPostEntity>>

    @Query("SELECT COUNT(*) FROM cached_posts")
    suspend fun getCachedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedPosts(posts: List<CachedPostEntity>)

    @Query("DELETE FROM cached_posts")
    suspend fun clearCachedPosts()

    @Query("DELETE FROM cached_posts WHERE id = :id")
    suspend fun deleteCachedPost(id: Int)

    // --- Favorite Bookmarks ---
    @Query("SELECT * FROM favorite_posts ORDER BY savedAt DESC")
    fun getAllFavorites(): Flow<List<FavoritePostEntity>>

    @Query("SELECT * FROM favorite_posts WHERE postId = :postId")
    fun getFavoriteById(postId: Int): Flow<FavoritePostEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_posts WHERE postId = :postId)")
    suspend fun isFavorite(postId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoritePostEntity)

    @Query("DELETE FROM favorite_posts WHERE postId = :postId")
    suspend fun deleteFavorite(postId: Int)

    @Query("SELECT DISTINCT tag FROM favorite_posts ORDER BY tag ASC")
    fun getAllTags(): Flow<List<String>>
}
