package com.example.openspeedtest

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RETRY_SECONDS = 5

@Composable
fun SpeedTestApp(vm: ServerViewModel = viewModel()) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val oledDetected = remember { isLikelyOled(context) }
    val oled = when (vm.oledMode) {
        OledMode.Auto -> oledDetected
        OledMode.On -> true
        OledMode.Off -> false
    }
    // When a server page is showing, follow the PAGE's light/dark mode (its ☀ button too).
    val dark = if (vm.selectedServer != null) vm.pageDark ?: systemDark else systemDark
    SpeedTestTheme(dark = dark, oled = oled) {
        SpeedTestScreen(vm, oled, oledDetected)
    }
}

@Composable
private fun SpeedTestScreen(vm: ServerViewModel, oled: Boolean, oledDetected: Boolean) {
    val context = LocalContext.current
    val look = LocalAppLook.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)        // servers (left)
    val historyState = rememberDrawerState(DrawerValue.Closed)       // history (right)
    val selected = vm.selectedServer
    var testPhase by remember { mutableStateOf(TestPhase.Idle) }
    var confirmClear by remember { mutableStateOf(false) }

    var pageState by remember { mutableStateOf<PageState>(PageState.Idle) }
    var retryIn by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) } // only bumps if the WebView engine crashes

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Server?>(null) }
    var pendingDelete by remember { mutableStateOf<Server?>(null) }

    val controller = remember { WebViewController() }
    val bridge = remember(generation) {
        SpeedTestBridge(
            onResult = { d, u, p, j ->
                vm.selectedServer?.let { server ->
                    vm.addResult(
                        TestResult(
                            serverName = server.name,
                            serverUrl = server.url,
                            download = d,
                            upload = u,
                            ping = p,
                            jitter = j,
                        )
                    )
                }
            },
            onPhase = { testPhase = it },
            onTheme = { dark, argb -> vm.setPageLook(dark, argb) },
        )
    }
    val client = remember(generation) {
        SpeedTestClient(
            onState = { pageState = it },
            onRendererGone = {
                controller.detach()
                generation++
            },
        )
    }

    // Send the OLED setting to the page.
    LaunchedEffect(oled, generation) {
        client.oled = oled
        controller.webView?.evaluateJavascript("window.__ostOled = $oled;", null)
    }

    // Status/navigation bar icons: white in dark mode, dark in light mode.
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !look.dark
                isAppearanceLightNavigationBars = !look.dark
            }
        }
    }

    // Switch pages ONLY when you pick a different server (or change its address).
    LaunchedEffect(selected?.id, selected?.url, selected?.allowSelfSigned) {
        controller.load(selected)
        if (selected == null) pageState = PageState.Idle
    }

    // If the server can't be reached, keep retrying forever. It stays selected.
    LaunchedEffect(pageState) {
        if (pageState !is PageState.Loaded) testPhase = TestPhase.Idle
        if (pageState is PageState.Error) {
            for (s in RETRY_SECONDS downTo 1) {
                retryIn = s
                delay(1000)
            }
            retryIn = 0
            controller.reload(vm.selectedServer)
        } else {
            retryIn = 0
        }
    }

    BackHandler {
        val wv = controller.webView
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            historyState.isOpen -> scope.launch { historyState.close() }
            wv != null && wv.canGoBack() -> wv.goBack()
            // Go to the background instead of closing, so the connection stays.
            else -> (context as? Activity)?.moveTaskToBack(true)
        }
    }

    // History sidebar slides in from the RIGHT (the RTL trick flips which side it opens on).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    ModalNavigationDrawer(
        drawerState = historyState,
        gesturesEnabled = historyState.isOpen,
        drawerContent = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                HistoryDrawer(
                    history = vm.history,
                    onDelete = { vm.deleteResult(it) },
                    onClearAll = { confirmClear = true },
                )
            }
        },
    ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen, // no edge-swipe, so it can't fight the test page
        drawerContent = {
            ServerDrawer(
                servers = vm.servers,
                selectedId = vm.selectedId,
                onSelect = {
                    vm.select(it)
                    scope.launch { drawerState.close() }
                },
                onReload = if (selected != null) {
                    {
                        controller.reload(vm.selectedServer)
                        scope.launch { drawerState.close() }
                    }
                } else null,
                onAdd = {
                    editing = null
                    showEditor = true
                },
                onEdit = {
                    editing = it
                    showEditor = true
                },
                onDelete = { pendingDelete = it },
                oledMode = vm.oledMode,
                oledDetected = oledDetected,
                onOledMode = { vm.changeOledMode(it) },
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        selected == null -> MaterialTheme.colorScheme.background
                        look.oledBlack -> Color.Black
                        else -> vm.pageBackground?.let { Color(it) }
                            ?: if (look.dark) Color(0xFF1A1A1A) else Color.White
                    }
                )
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // The speed test page, full screen. Always in the layout so it's never torn down.
            key(generation) {
                AndroidView(
                    factory = { ctx ->
                        createSpeedTestWebView(ctx, client, bridge).also {
                            controller.attach(it, client)
                            controller.load(vm.selectedServer)
                        }
                    },
                    onRelease = {
                        it.stopLoading()
                        it.destroy()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (selected == null) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        EmptyState(
                            hasServers = vm.servers.isNotEmpty(),
                            onOpenServers = { scope.launch { drawerState.open() } },
                            onAdd = {
                                editing = null
                                showEditor = true
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            } else {
                if (pageState is PageState.Loading) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
                (pageState as? PageState.Error)?.let { err ->
                    ErrorCard(
                        message = err.message,
                        retryIn = retryIn,
                        onRetry = { controller.reload(vm.selectedServer) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // Small floating button (top-left) that opens the server sidebar.
            ServerPill(
                name = selected?.name ?: "Servers",
                state = if (selected == null) PageState.Idle else pageState,
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )

            // History button (top-right).
            HistoryPill(
                onClick = { scope.launch { historyState.open() } },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )

            // "Try again" on the results screen: reloads the server's start page.
            if (selected != null && testPhase == TestPhase.Done && pageState is PageState.Loaded) {
                Button(
                    onClick = {
                        testPhase = TestPhase.Idle
                        controller.reload(vm.selectedServer)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .widthIn(min = 220.dp)
                        .height(56.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Try again", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    }
    }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This deletes all ${vm.history.size} saved test results.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    confirmClear = false
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }

    if (showEditor) {
        ServerEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = {
                vm.save(it)
                showEditor = false
            },
        )
    }

    pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete server?") },
            text = {
                Text(
                    if (server.id == vm.selectedId)
                        "\"${server.name}\" is the connected server. Deleting it will disconnect."
                    else
                        "Remove \"${server.name}\" from your list?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(server)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ServerPill(name: String, state: PageState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor = when (state) {
        PageState.Loaded -> Color(0xFF2E7D32)
        PageState.Loading -> Color(0xFFF9A825)
        is PageState.Error -> MaterialTheme.colorScheme.error
        PageState.Idle -> MaterialTheme.colorScheme.outline
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = pillContainerColor(),
        contentColor = pillContentColor(),
        border = pillBorder(),
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Servers", modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

@Composable
private fun HistoryPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = pillContainerColor(),
        contentColor = pillContentColor(),
        border = pillBorder(),
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.DateRange, contentDescription = "Test history", modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("History", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ErrorCard(message: String, retryIn: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.padding(24.dp)) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("Can't reach the server", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text(
                if (retryIn > 0) "Trying again in ${retryIn}s" else "Trying again…",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "This server stays selected until you pick another one.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("Retry now") }
        }
    }
}

@Composable
private fun EmptyState(
    hasServers: Boolean,
    onOpenServers: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("No server connected", style = MaterialTheme.typography.titleLarge)
        Text(
            if (hasServers) "Open the sidebar and pick a server." else "Add your OpenSpeedTest server to get started.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasServers) {
            Button(onClick = onOpenServers) { Text("Choose server") }
            OutlinedButton(onClick = onAdd) { Text("Add server") }
        } else {
            Button(onClick = onAdd) { Text("Add server") }
        }
    }
}
