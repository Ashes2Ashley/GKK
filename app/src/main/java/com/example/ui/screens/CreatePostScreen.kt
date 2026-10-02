package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.api.RetrofitClient
import com.example.data.api.model.NetworkResult
import com.example.data.api.model.Post
import com.example.ui.UiState
import com.example.ui.components.ApiStatusBanner
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.JsonCodeView

@Composable
fun CreatePostScreen(
    uiState: UiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDismissSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isEditing = uiState.isEditingPostId != null

    val pendingPostJson = remember(uiState.newPostTitle, uiState.newPostBody, uiState.isEditingPostId) {
        val post = Post(
            id = uiState.isEditingPostId,
            userId = 1,
            title = uiState.newPostTitle.ifEmpty { "Sample Title" },
            body = uiState.newPostBody.ifEmpty { "Sample content..." }
        )
        try {
            RetrofitClient.postToJson(post)
        } catch (_: Exception) {
            "{}"
        }
    }

    val isFormValid = remember(uiState.newPostTitle, uiState.newPostBody) {
        uiState.newPostTitle.isNotBlank() && uiState.newPostBody.isNotBlank()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ApiStatusBanner(
            baseUrl = uiState.baseUrl,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditing) "Edit Post #${uiState.isEditingPostId} (@PUT)" else "Create Post via Retrofit @POST",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEditing) "Updates resource on server using HTTP PUT" else "Moshi serializes this Kotlin Post data class to JSON for the HTTP body.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isEditing) {
                        OutlinedButton(
                            onClick = onCancelEdit,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.newPostTitle,
                    onValueChange = onTitleChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_title_input"),
                    label = { Text("Title") },
                    placeholder = { Text("Enter post title...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.newPostBody,
                    onValueChange = onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("post_body_input"),
                    label = { Text("Body") },
                    placeholder = { Text("Enter post description/content...") },
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSubmit,
                    enabled = isFormValid && !uiState.isCreatingPost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_post_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (uiState.isCreatingPost) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditing) "Updating via PUT..." else "Sending POST...")
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditing) "Save Updates via PUT" else "Send via Retrofit POST")
                    }
                }
            }
        }

        // Live Moshi Serialized JSON Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Moshi JSON Serialization Preview",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "MoshiConverterFactory will serialize this object into JSON for transmission:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                JsonCodeView(json = pendingPostJson)
            }
        }

        // API Response Card
        when (val result = uiState.createPostResult) {
            is NetworkResult.Success -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_success_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFECFDF5)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "HTTP ${result.statusCode} Success",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF065F46)
                                )
                            }
                            Surface(
                                color = Color(0xFFD1FAE5),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${result.latencyMs}ms",
                                    color = Color(0xFF065F46),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Server response deserialized by Moshi into Kotlin Post:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF047857)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val responseJson = remember(result.data) { RetrofitClient.postToJson(result.data) }
                        JsonCodeView(json = responseJson)

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onDismissSuccess,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Dismiss", color = Color(0xFF065F46))
                        }
                    }
                }
            }
            is NetworkResult.Error -> {
                ErrorStateCard(
                    message = result.message,
                    onRetry = onSubmit
                )
            }
            else -> {}
        }
    }
}
