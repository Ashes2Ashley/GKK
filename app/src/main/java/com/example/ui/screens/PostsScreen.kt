package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.RetrofitClient
import com.example.data.api.model.Comment
import com.example.data.api.model.NetworkResult
import com.example.data.api.model.Post
import com.example.ui.UiState
import com.example.ui.components.ApiStatusBanner
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.JsonCodeView
import com.example.ui.components.LoadingStateView

@Composable
fun PostsScreen(
    uiState: UiState,
    filteredPosts: List<Post>,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onDeletePost: (Int) -> Unit,
    onSelectPost: (Post?) -> Unit,
    onToggleOffline: () -> Unit,
    onToggleFavorite: (Post) -> Unit,
    onEditPost: (Post) -> Unit,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    onSortChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val statusCode = (uiState.postsResult as? NetworkResult.Success)?.statusCode
        val latencyMs = (uiState.postsResult as? NetworkResult.Success)?.latencyMs

        ApiStatusBanner(
            baseUrl = uiState.baseUrl,
            statusCode = if (uiState.isOfflineMode) null else statusCode,
            latencyMs = if (uiState.isOfflineMode) null else latencyMs,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )

        // Offline Mode Banner & Toggle (Feature 2)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            color = if (uiState.isOfflineMode) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.isOfflineMode) Icons.Default.CloudOff else Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (uiState.isOfflineMode) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (uiState.isOfflineMode) "Room Offline-First Active" else "Room Cache Enabled",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.isOfflineMode) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiState.isOfflineMode) "Serving from SQLite local database" else "Cache: ${uiState.cacheHitCount} hits, ${uiState.cacheNetworkCount} net",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (uiState.isOfflineMode) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = uiState.isOfflineMode,
                    onCheckedChange = { onToggleOffline() },
                    modifier = Modifier.testTag("offline_mode_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD97706),
                        checkedTrackColor = Color(0xFFFDE68A)
                    )
                )
            }
        }

        // Search Bar & Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_posts_input"),
                placeholder = { Text("Search posts...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("refresh_posts_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Posts",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Pagination & Sorting Controls (Feature 5)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Chips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = uiState.sortField == "id",
                    onClick = {
                        val nextOrder = if (uiState.sortField == "id" && uiState.sortOrder == "asc") "desc" else "asc"
                        onSortChange("id", nextOrder)
                    },
                    label = { Text("ID ${if (uiState.sortField == "id") (if (uiState.sortOrder == "asc") "↑" else "↓") else ""}", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = uiState.sortField == "title",
                    onClick = {
                        val nextOrder = if (uiState.sortField == "title" && uiState.sortOrder == "asc") "desc" else "asc"
                        onSortChange("title", nextOrder)
                    },
                    label = { Text("Title ${if (uiState.sortField == "title") (if (uiState.sortOrder == "asc") "↑" else "↓") else ""}", fontSize = 11.sp) }
                )
            }

            // Pagination Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onPageChange(uiState.currentPage - 1) },
                    enabled = uiState.currentPage > 1,
                    modifier = Modifier.size(32.dp).testTag("prev_page_button")
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                }
                Text(
                    text = "Page ${uiState.currentPage}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { onPageChange(uiState.currentPage + 1) },
                    modifier = Modifier.size(32.dp).testTag("next_page_button")
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                }
            }
        }

        when (val result = uiState.postsResult) {
            is NetworkResult.Loading -> {
                if (filteredPosts.isNotEmpty()) {
                    // Show cached posts with top subtle loading
                    Text(
                        text = "Updating from server via Retrofit...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    PostsList(
                        posts = filteredPosts,
                        favoriteIds = uiState.favoritePostIds,
                        onInspectJson = onSelectPost,
                        onDelete = onDeletePost,
                        onToggleFavorite = onToggleFavorite,
                        onEdit = onEditPost
                    )
                } else {
                    LoadingStateView(message = "Executing Retrofit GET /posts...")
                }
            }
            is NetworkResult.Error -> {
                if (filteredPosts.isNotEmpty()) {
                    // Show cached with error banner
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Network offline: ${result.message}. Showing Room local cache.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    PostsList(
                        posts = filteredPosts,
                        favoriteIds = uiState.favoritePostIds,
                        onInspectJson = onSelectPost,
                        onDelete = onDeletePost,
                        onToggleFavorite = onToggleFavorite,
                        onEdit = onEditPost
                    )
                } else {
                    ErrorStateCard(
                        message = result.message,
                        onRetry = onRefresh
                    )
                }
            }
            is NetworkResult.Success -> {
                PostsList(
                    posts = filteredPosts,
                    favoriteIds = uiState.favoritePostIds,
                    onInspectJson = onSelectPost,
                    onDelete = onDeletePost,
                    onToggleFavorite = onToggleFavorite,
                    onEdit = onEditPost
                )
            }
        }
    }

    // Inspect Post Moshi Dialog with Comments (Feature 1 & Feature 5)
    if (uiState.selectedPost != null) {
        val post = uiState.selectedPost
        val moshiJson = remember(post) { RetrofitClient.postToJson(post) }
        AlertDialog(
            onDismissRequest = { onSelectPost(null) },
            title = {
                Text(
                    text = "Post #${post.id} Moshi Inspection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = post.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Moshi Serialized JSON:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    JsonCodeView(json = moshiJson)

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Comments (${when (val comments = uiState.postCommentsResult) {
                            is NetworkResult.Success -> comments.data.size
                            is NetworkResult.Loading -> "Loading..."
                            else -> 0
                        }}):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    when (val comments = uiState.postCommentsResult) {
                        is NetworkResult.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                comments.data.take(3).forEach { c ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Text(text = c.name, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            Text(text = c.body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }
                        is NetworkResult.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSelectPost(null) },
                    modifier = Modifier.testTag("close_moshi_dialog")
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun PostsList(
    posts: List<Post>,
    favoriteIds: Set<Int>,
    onInspectJson: (Post) -> Unit,
    onDelete: (Int) -> Unit,
    onToggleFavorite: (Post) -> Unit,
    onEdit: (Post) -> Unit
) {
    if (posts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No posts available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("posts_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(posts, key = { it.id ?: it.title.hashCode() }) { post ->
                val isFav = post.id != null && favoriteIds.contains(post.id)
                PostCard(
                    post = post,
                    isFavorite = isFav,
                    onInspectJson = { onInspectJson(post) },
                    onDelete = { post.id?.let { onDelete(it) } },
                    onToggleFavorite = { onToggleFavorite(post) },
                    onEdit = { onEdit(post) }
                )
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    isFavorite: Boolean,
    onInspectJson: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}")
            .clickable { onInspectJson() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${post.id ?: "New"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "User ${post.userId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Star/Favorite button (Feature 8)
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp).testTag("fav_post_${post.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "Remove Bookmark" else "Bookmark Post",
                            tint = if (isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Post",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onInspectJson,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Inspect Moshi JSON",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Post",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
