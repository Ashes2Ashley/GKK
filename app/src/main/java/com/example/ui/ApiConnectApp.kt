package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.CloudflareEdgeScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.PostsScreen
import com.example.ui.screens.RestClientScreen
import com.example.ui.screens.TelecomScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConnectApp(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredPosts by viewModel.filteredPosts.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val startupHealth by viewModel.startupHealth.collectAsState()
    val webSocketStatus by viewModel.webSocketStatus.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.activeTab) {
                            0 -> "ApiConnect • Posts"
                            1 -> "REST Client Workbench"
                            2 -> "Telecom Gateway • SMS & VoIP"
                            3 -> "Cloudflare Edge & Tunnel"
                            4 -> "Room Bookmarks"
                            else -> "Administration & Diagnostics"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            when (uiState.activeTab) {
                                0 -> viewModel.loadPostsPaged()
                                3 -> viewModel.testCloudflareTunnelHealth()
                                else -> viewModel.loadPostsPaged()
                            }
                        },
                        modifier = Modifier.testTag("appbar_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Data"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation")
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Posts") },
                    label = { Text("Posts") },
                    modifier = Modifier.testTag("tab_posts")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "REST") },
                    label = { Text("REST") },
                    modifier = Modifier.testTag("tab_rest")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(Icons.Default.CellTower, contentDescription = "Telecom") },
                    label = { Text("Telecom") },
                    modifier = Modifier.testTag("tab_telecom")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 3,
                    onClick = { viewModel.setTab(3) },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = "Cloudflare") },
                    label = { Text("Edge") },
                    modifier = Modifier.testTag("tab_cloudflare")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 4,
                    onClick = { viewModel.setTab(4) },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Saved") },
                    label = { Text("Saved") },
                    modifier = Modifier.testTag("tab_saved")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 5,
                    onClick = { viewModel.setTab(5) },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                    label = { Text("Admin") },
                    modifier = Modifier.testTag("tab_admin")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                0 -> PostsScreen(
                    uiState = uiState,
                    filteredPosts = filteredPosts,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onRefresh = viewModel::loadPostsPaged,
                    onDeletePost = viewModel::deletePost,
                    onSelectPost = viewModel::selectPost,
                    onToggleOffline = viewModel::toggleOfflineMode,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onEditPost = viewModel::startEditingPost,
                    onPageChange = viewModel::setPage,
                    onPageSizeChange = viewModel::setPageSize,
                    onSortChange = viewModel::setSorting
                )
                1 -> RestClientScreen(
                    uiState = uiState,
                    onMethodChange = viewModel::setWorkbenchMethod,
                    onUrlChange = viewModel::setWorkbenchUrl,
                    onBodyChange = viewModel::setWorkbenchBody,
                    onSend = viewModel::executeWorkbenchRequest
                )
                2 -> TelecomScreen(
                    uiState = uiState,
                    onSmsRecipientChange = viewModel::setSmsRecipient,
                    onSmsBodyChange = viewModel::setSmsBodyText,
                    onSendSms = viewModel::sendSms,
                    onStartWebRtc = viewModel::startWebRtcSignaling,
                    onCloseWebRtc = viewModel::closeWebRtcSession,
                    onSipDialChange = viewModel::setSipDialNumber,
                    onRegisterSip = viewModel::registerSipEndpoint,
                    onInitiateSipCall = viewModel::initiateSipCall,
                    onEndSipCall = viewModel::endSipCall
                )
                3 -> CloudflareEdgeScreen(
                    uiState = uiState,
                    webSocketStatus = webSocketStatus,
                    onUpdateCloudflareConfig = viewModel::updateCloudflareConfig,
                    onTestTunnel = viewModel::testCloudflareTunnelHealth,
                    onWebSocketUrlChange = viewModel::setWebSocketUrl,
                    onWebSocketInputChange = viewModel::setWebSocketInput,
                    onConnectWebSocket = viewModel::connectWebSocket,
                    onDisconnectWebSocket = viewModel::disconnectWebSocket,
                    onSendWebSocketMessage = viewModel::sendWebSocketMessage
                )
                4 -> FavoritesScreen(
                    favorites = favorites,
                    onRemoveFavorite = viewModel::removeFavorite
                )
                5 -> AdminScreen(
                    uiState = uiState,
                    startupHealth = startupHealth,
                    onToggleStrictTls = viewModel::toggleStrictTls,
                    onToggleCertPinning = viewModel::toggleCertPinning
                )
            }
        }
    }
}
