package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AirborneWebRtcScreen
import com.example.ui.screens.CloudflareEdgeScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FiveGAdaptabilityScreen
import com.example.ui.screens.KaliConsoleScreen
import com.example.ui.screens.LiveScreen
import com.example.ui.screens.PostsScreen
import com.example.ui.screens.RestClientScreen
import com.example.ui.screens.SecurityConfigScreen
import com.example.ui.screens.SimPayloadScreen

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

    // 5G, WebRTC, SIM, Kali, and Config States
    val linkState by viewModel.linkPerformanceState.collectAsState()
    val adaptationHistory by viewModel.adaptationHistory.collectAsState()
    val airborneTelemetry by viewModel.airborneTelemetry.collectAsState()
    val webrtcSession by viewModel.webrtcSession.collectAsState()
    val simHardwareState by viewModel.simHardwareState.collectAsState()
    val simEnvelopes by viewModel.simEnvelopes.collectAsState()
    val kaliTerminalBuffer by viewModel.kaliTerminalBuffer.collectAsState()
    val modularRemoteConfig by viewModel.modularRemoteConfig.collectAsState()
    val systemLogs by viewModel.systemLogs.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.activeTab) {
                            0 -> "Kali Infra • 5G Adaptability"
                            1 -> "Kali Infra • Airborne WebRTC"
                            2 -> "Kali Infra • SIM Payload Studio"
                            3 -> "Kali Infra • Tactical Terminal"
                            4 -> "Kali Infra • Security Policies"
                            5 -> "Kali Infra • REST Diagnostics"
                            6 -> "Kali Infra • Offline Data Sync"
                            8 -> "Kali Infra • Live Monitors"
                            else -> "Kali Infra • System Administration"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            when (uiState.activeTab) {
                                0 -> viewModel.simulateRfDegradation()
                                1 -> viewModel.initiateWebRtcSignaling()
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

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("appbar_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("5G Adaptability Mesh") },
                                onClick = { viewModel.setTab(0); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Airborne WebRTC Infra") },
                                onClick = { viewModel.setTab(1); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("SIM Administrative Payloads") },
                                onClick = { viewModel.setTab(2); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Kali Tactical Console") },
                                onClick = { viewModel.setTab(3); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Modular Remote Config") },
                                onClick = { viewModel.setTab(4); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("REST API Workbench") },
                                onClick = { viewModel.setTab(5); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Root Admin & Stabilizer") },
                                onClick = { viewModel.setTab(7); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Live Monitors") },
                                onClick = { viewModel.setTab(8); showMenu = false }
                            )
                        }
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
                    icon = { Icon(Icons.Default.CellTower, contentDescription = "5G Mesh") },
                    label = { Text("5G Mesh") },
                    modifier = Modifier.testTag("tab_fiveg")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = { Icon(Icons.Default.Podcasts, contentDescription = "WebRTC") },
                    label = { Text("WebRTC") },
                    modifier = Modifier.testTag("tab_webrtc")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(Icons.Default.SimCard, contentDescription = "SIM") },
                    label = { Text("SIM") },
                    modifier = Modifier.testTag("tab_sim")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 3,
                    onClick = { viewModel.setTab(3) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Kali") },
                    label = { Text("Kali") },
                    modifier = Modifier.testTag("tab_kali")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 4,
                    onClick = { viewModel.setTab(4) },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Security") },
                    label = { Text("Security") },
                    modifier = Modifier.testTag("tab_security")
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
                0 -> FiveGAdaptabilityScreen(
                    linkState = linkState,
                    adaptationHistory = adaptationHistory,
                    onSelectTopology = viewModel::selectTopology,
                    onSelectSlice = viewModel::selectSlice,
                    onToggleAutoAdaptation = viewModel::toggleAutoAdaptation,
                    onFecChange = viewModel::setFecRedundancy,
                    onSimulateRfDegradation = viewModel::simulateRfDegradation
                )
                1 -> AirborneWebRtcScreen(
                    telemetry = airborneTelemetry,
                    session = webrtcSession,
                    onInitiateSignaling = viewModel::initiateWebRtcSignaling,
                    onDisconnect = viewModel::disconnectWebRtc,
                    onSendDataChannelMessage = viewModel::sendWebRtcDataChannelMessage
                )
                2 -> SimPayloadScreen(
                    hardwareState = simHardwareState,
                    envelopes = simEnvelopes,
                    onSelectSlot = viewModel::selectSimSlot,
                    onCreatePayload = viewModel::createAndSignAdminPayload,
                    onDispatchEnvelope = viewModel::dispatchAdminEnvelope
                )
                3 -> KaliConsoleScreen(
                    terminalBuffer = kaliTerminalBuffer,
                    onExecuteCommand = viewModel::executeKaliCommand
                )
                4 -> SecurityConfigScreen(
                    config = modularRemoteConfig,
                    logs = systemLogs,
                    onApplyPreset = viewModel::applyTacticalPreset,
                    onBitrateCeilingChange = viewModel::setWebRtcBitrateCeiling,
                    onToggleCertPinning = viewModel::toggleStrictCertPinning,
                    onToggleAutoAdaptation = viewModel::toggleAutoAdaptation,
                    onClearLogs = viewModel::clearSystemLogs
                )
                5 -> RestClientScreen(
                    uiState = uiState,
                    onMethodChange = viewModel::setWorkbenchMethod,
                    onUrlChange = viewModel::setWorkbenchUrl,
                    onBodyChange = viewModel::setWorkbenchBody,
                    onSend = viewModel::executeWorkbenchRequest
                )
                6 -> FavoritesScreen(
                    favorites = favorites,
                    onRemoveFavorite = viewModel::removeFavorite
                )
                7 -> AdminScreen(
                    uiState = uiState,
                    startupHealth = startupHealth,
                    onToggleStrictTls = viewModel::toggleStrictTls,
                    onToggleCertPinning = viewModel::toggleCertPinning
                )
                8 -> LiveScreen()
                else -> FiveGAdaptabilityScreen(
                    linkState = linkState,
                    adaptationHistory = adaptationHistory,
                    onSelectTopology = viewModel::selectTopology,
                    onSelectSlice = viewModel::selectSlice,
                    onToggleAutoAdaptation = viewModel::toggleAutoAdaptation,
                    onFecChange = viewModel::setFecRedundancy,
                    onSimulateRfDegradation = viewModel::simulateRfDegradation
                )
            }
        }
    }
}
