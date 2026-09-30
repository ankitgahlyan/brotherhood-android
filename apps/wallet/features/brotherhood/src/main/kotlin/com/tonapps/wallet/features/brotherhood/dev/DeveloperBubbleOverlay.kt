package com.tonapps.wallet.features.brotherhood.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tonapps.wallet.data.brotherhood.hydrator.HydratedAccountState
import com.tonapps.wallet.data.brotherhood.network.TelemetrySnapshot
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodCard
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodColors
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodMetricRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodPrimaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSecondaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodStatusBadge
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSubTabRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodTextField
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * Draggable Floating Developer Bubble overlay unlocked by 7 rapid taps on `"brotherhood"` in Settings.
 * Displays live RPC in-flight count & cache stats on the pill, and expands into a 4-tab Developer Telemetry Sheet.
 */
@Composable
fun DeveloperBubbleOverlay(
    modifier: Modifier = Modifier,
    viewModel: DeveloperBubbleFeature = koinViewModel(),
) {
    val isDevModeEnabled by viewModel.isDeveloperModeEnabled.collectAsState()
    val isSheetExpanded by viewModel.isSheetExpanded.collectAsState()
    val snapshot by viewModel.telemetrySnapshot.collectAsState()
    val hydratedStates by viewModel.hydratedStates.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isTestnet by viewModel.isTestnetEnabled.collectAsState()
    val isAutoFund by viewModel.isAutoFundEnabled.collectAsState()
    val inspectInput by viewModel.inspectAddressInput.collectAsState()
    val inspectedState by viewModel.inspectedState.collectAsState()

    if (!isDevModeEnabled) {
        return
    }

    var offsetX by remember { mutableFloatStateOf(24f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    val totalInFlight = snapshot.providerMetrics.values.sumOf { it.inFlightCount }
    val totalRequests = snapshot.providerMetrics.values.sumOf { it.totalRequests }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .clip(CircleShape)
                .background(BrotherhoodColors.CardElevated.copy(alpha = 0.94f))
                .border(
                    width = 1.5.dp,
                    color = if (totalInFlight > 0) {
                        BrotherhoodColors.AccentGreen
                    } else {
                        BrotherhoodColors.AccentBlue
                    },
                    shape = CircleShape,
                )
                .clickable { viewModel.toggleSheetExpanded() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "⚡ DEV",
                color = BrotherhoodColors.AccentAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "${totalRequests}req • ${snapshot.cacheHits}hit",
                color = BrotherhoodColors.TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }

    if (isSheetExpanded) {
        DeveloperTelemetryDialog(
            snapshot = snapshot,
            hydratedStates = hydratedStates,
            selectedTab = selectedTab,
            isTestnet = isTestnet,
            isAutoFund = isAutoFund,
            inspectInput = inspectInput,
            inspectedState = inspectedState,
            onSelectTab = { viewModel.selectTab(it) },
            onToggleTestnet = { viewModel.setTestnetEnabled(!isTestnet) },
            onToggleAutoFund = { viewModel.setAutoFundEnabled(!isAutoFund) },
            onDisableDevMode = { viewModel.setDeveloperModeEnabled(false) },
            onClearTelemetry = { viewModel.clearTelemetry() },
            onInspectInputChange = { viewModel.updateInspectAddressInput(it) },
            onInspectAddress = { viewModel.inspectContractAddress() },
            onDismiss = { viewModel.closeSheet() },
        )
    }
}

@Composable
private fun DeveloperTelemetryDialog(
    snapshot: TelemetrySnapshot,
    hydratedStates: Map<String, HydratedAccountState>,
    selectedTab: DeveloperPanelTab,
    isTestnet: Boolean,
    isAutoFund: Boolean,
    inspectInput: String,
    inspectedState: HydratedAccountState?,
    onSelectTab: (DeveloperPanelTab) -> Unit,
    onToggleTestnet: () -> Unit,
    onToggleAutoFund: () -> Unit,
    onDisableDevMode: () -> Unit,
    onClearTelemetry: () -> Unit,
    onInspectInputChange: (String) -> Unit,
    onInspectAddress: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(620.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BrotherhoodColors.Background)
                .border(1.dp, BrotherhoodColors.Border, RoundedCornerShape(24.dp))
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "BrotherHood Developer Console",
                        color = BrotherhoodColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Zero-Getter BOC Hydrator • Rate Limiters • Cache Inspector",
                        color = BrotherhoodColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = "Close",
                    color = BrotherhoodColors.AccentBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onDismiss() },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            BrotherhoodSubTabRow(
                tabs = DeveloperPanelTab.entries.map { it.id to it.label },
                selectedKey = selectedTab.id,
                onSelect = { id ->
                    DeveloperPanelTab.entries.firstOrNull { it.id == id }?.let(onSelectTab)
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (selectedTab) {
                    DeveloperPanelTab.TELEMETRY -> TelemetryEventsPanel(
                        snapshot = snapshot,
                        onClear = onClearTelemetry,
                    )
                    DeveloperPanelTab.PROVIDERS -> ProvidersPanel(snapshot = snapshot)
                    DeveloperPanelTab.CACHE -> BocCacheInspectorPanel(
                        hydratedStates = hydratedStates,
                        inspectInput = inspectInput,
                        inspectedState = inspectedState,
                        onInspectInputChange = onInspectInputChange,
                        onInspectAddress = onInspectAddress,
                    )
                    DeveloperPanelTab.CONTROLS -> ControlsPanel(
                        isTestnet = isTestnet,
                        isAutoFund = isAutoFund,
                        onToggleTestnet = onToggleTestnet,
                        onToggleAutoFund = onToggleAutoFund,
                        onDisableDevMode = onDisableDevMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryEventsPanel(
    snapshot: TelemetrySnapshot,
    onClear: () -> Unit,
) {
    BrotherhoodCard(
        title = "Hydrator & Cache Metrics",
        subtitle = "50ms Coalesced Batching (Max 30 Addresses/Batch)",
        badgeText = "${snapshot.batchHydrationCount} Batches",
        badgeColor = BrotherhoodColors.AccentGreen,
    ) {
        BrotherhoodMetricRow(label = "Cache Hits", value = snapshot.cacheHits.toString())
        BrotherhoodMetricRow(label = "Cache Misses", value = snapshot.cacheMisses.toString())
        BrotherhoodMetricRow(label = "Recorded RPC Events", value = snapshot.events.size.toString())
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSecondaryButton(
            text = "Clear Telemetry Ring Buffer",
            onClick = onClear,
        )
    }

    BrotherhoodCard(
        title = "Live RPC Event Log",
        subtitle = "Most recent ${snapshot.events.size} requests",
    ) {
        if (snapshot.events.isEmpty()) {
            Text(
                text = "No RPC events recorded yet.",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 12.sp,
            )
        } else {
            snapshot.events.take(30).forEach { ev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrotherhoodColors.CardElevated)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${ev.provider.wireName} • ${ev.endpoint} (${ev.addressCount} addrs)",
                            color = BrotherhoodColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!ev.errorMessage.isNullOrBlank()) {
                            Text(
                                text = ev.errorMessage.orEmpty(),
                                color = BrotherhoodColors.AccentRed,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    BrotherhoodStatusBadge(
                        text = "${ev.statusCode} • ${ev.latencyMs}ms",
                        color = if (ev.isSuccess) {
                            BrotherhoodColors.AccentGreen
                        } else {
                            BrotherhoodColors.AccentRed
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProvidersPanel(snapshot: TelemetrySnapshot) {
    snapshot.providerMetrics.values.forEach { metric ->
        BrotherhoodCard(
            title = metric.provider.wireName.uppercase(),
            subtitle = "Limit: ${metric.provider.maxRps} RPS • Max Concurrent: ${metric.provider.maxConcurrent}",
            badgeText = "${metric.inFlightCount} In-Flight",
            badgeColor = if (metric.rateLimit429Count > 0) {
                BrotherhoodColors.AccentAmber
            } else {
                BrotherhoodColors.AccentBlue
            },
        ) {
            BrotherhoodMetricRow(label = "Total Requests", value = metric.totalRequests.toString())
            BrotherhoodMetricRow(label = "Success / Error", value = "${metric.successCount} / ${metric.errorCount}")
            BrotherhoodMetricRow(label = "HTTP 429 Backoffs", value = metric.rateLimit429Count.toString())
            BrotherhoodMetricRow(label = "Addresses Hydrated", value = metric.totalAddressesHydrated.toString())
            BrotherhoodMetricRow(label = "Avg Latency", value = "${metric.avgLatencyMs} ms")
        }
    }
}

@Composable
private fun BocCacheInspectorPanel(
    hydratedStates: Map<String, HydratedAccountState>,
    inspectInput: String,
    inspectedState: HydratedAccountState?,
    onInspectInputChange: (String) -> Unit,
    onInspectAddress: () -> Unit,
) {
    BrotherhoodCard(
        title = "On-Demand BOC Inspector",
        subtitle = "Fetch & decode raw data_boc via UniversalBocDeserializer",
    ) {
        BrotherhoodTextField(
            value = inspectInput,
            onValueChange = onInspectInputChange,
            label = "Contract Address (0:... or EQ...)",
            placeholder = "0:...",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodPrimaryButton(
            text = "Hydrate & Decode BOC",
            onClick = onInspectAddress,
        )

        if (inspectedState != null) {
            Spacer(modifier = Modifier.height(10.dp))
            BrotherhoodMetricRow(
                label = "Address",
                value = BrotherhoodUiFormatters.shortAddress(inspectedState.address),
                monospaceValue = true,
            )
            BrotherhoodMetricRow(
                label = "Status",
                value = inspectedState.status,
            )
            BrotherhoodMetricRow(
                label = "Contract Type",
                value = inspectedState.contractType.wireName,
                valueColor = BrotherhoodColors.AccentGreen,
            )
            BrotherhoodMetricRow(
                label = "TON Balance",
                value = BrotherhoodUiFormatters.formatNanoAmount(inspectedState.balanceNano, "TON"),
            )
            BrotherhoodMetricRow(
                label = "Decoded Store",
                value = inspectedState.decodedStore?.javaClass?.simpleName ?: "Raw / Undecoded",
            )
        }
    }

    BrotherhoodCard(
        title = "In-Memory Hydrated Contracts (${hydratedStates.size})",
        subtitle = "Stripped code_boc • Decoded Tolk stores",
    ) {
        hydratedStates.values.take(20).forEach { state ->
            BrotherhoodMetricRow(
                label = "${state.contractType.wireName} (${BrotherhoodUiFormatters.shortAddress(state.address)})",
                value = "${state.status} • ${BrotherhoodUiFormatters.formatNanoAmount(state.balanceNano, "TON")}",
                monospaceValue = true,
            )
        }
    }
}

@Composable
private fun ControlsPanel(
    isTestnet: Boolean,
    isAutoFund: Boolean,
    onToggleTestnet: () -> Unit,
    onToggleAutoFund: () -> Unit,
    onDisableDevMode: () -> Unit,
) {
    val envValue = if (isTestnet) {
        "TON Testnet (Default)"
    } else {
        "TON Mainnet"
    }
    val envColor = if (isTestnet) {
        BrotherhoodColors.AccentAmber
    } else {
        BrotherhoodColors.AccentGreen
    }
    val envButtonText = if (isTestnet) {
        "Switch Hydrator to Mainnet"
    } else {
        "Switch Hydrator to Testnet"
    }
    val autoFundValue = if (isAutoFund) {
        "Enabled (2.0 TON Top-Up)"
    } else {
        "Disabled"
    }
    val autoFundColor = if (isAutoFund) {
        BrotherhoodColors.AccentGreen
    } else {
        BrotherhoodColors.TextSecondary
    }
    val autoFundButtonText = if (isAutoFund) {
        "Disable Auto-Funding"
    } else {
        "Enable Auto-Funding"
    }

    BrotherhoodCard(
        title = "Developer Switches",
        subtitle = "Runtime flags persisted in brotherhood_dev_prefs",
    ) {
        BrotherhoodMetricRow(
            label = "Network Environment",
            value = envValue,
            valueColor = envColor,
        )
        BrotherhoodSecondaryButton(
            text = envButtonText,
            onClick = onToggleTestnet,
        )

        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodMetricRow(
            label = "Auto FiWallet Funder (< 2.0 TON)",
            value = autoFundValue,
            valueColor = autoFundColor,
        )
        BrotherhoodSecondaryButton(
            text = autoFundButtonText,
            onClick = onToggleAutoFund,
        )

        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = "Hide Floating Developer Bubble",
            onClick = onDisableDevMode,
            color = BrotherhoodColors.AccentRed,
        )
    }
}
