package com.example.data.api.service

import com.example.data.api.model.Post
import com.example.data.api.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit Service Interface defining endpoints for JSON API communication.
 */
interface ApiService {

    /**
     * Fetch a list of posts from the API.
     */
    @GET("posts")
    @Headers("Accept: application/json")
    suspend fun getPosts(
        @Query("_limit") limit: Int = 25
    ): Response<List<Post>>

    /**
     * Fetch a single post by ID.
     */
    @GET("posts/{id}")
    @Headers("Accept: application/json")
    suspend fun getPostById(
        @Path("id") id: Int
    ): Response<Post>

    /**
     * Create a new post resource via POST.
     * The Post object is serialized to JSON by Moshi before transmission.
     */
    @POST("posts")
    @Headers("Content-Type: application/json; charset=UTF-8")
    suspend fun createPost(
        @Body post: Post
    ): Response<Post>

    /**
     * Fetch list of users.
     */
    @GET("users")
    @Headers("Accept: application/json")
    suspend fun getUsers(): Response<List<User>>

    /**
     * Fetch a paginated and sorted list of posts.
     */
    @GET("posts")
    @Headers("Accept: application/json")
    suspend fun getPostsPaged(
        @Query("_page") page: Int,
        @Query("_limit") limit: Int,
        @Query("_sort") sort: String? = null,
        @Query("_order") order: String? = null,
        @Query("userId") userId: Int? = null
    ): Response<List<Post>>

    /**
     * Update an existing post resource via PUT.
     */
    @retrofit2.http.PUT("posts/{id}")
    @Headers("Content-Type: application/json; charset=UTF-8")
    suspend fun updatePost(
        @Path("id") id: Int,
        @Body post: Post
    ): Response<Post>

    /**
     * Fetch comments associated with a specific post.
     */
    @GET("posts/{id}/comments")
    @Headers("Accept: application/json")
    suspend fun getPostComments(
        @Path("id") id: Int
    ): Response<List<com.example.data.api.model.Comment>>

    /**
     * Delete a post by ID.
     */
    @DELETE("posts/{id}")
    suspend fun deletePost(
        @Path("id") id: Int
    ): Response<Unit>
}
