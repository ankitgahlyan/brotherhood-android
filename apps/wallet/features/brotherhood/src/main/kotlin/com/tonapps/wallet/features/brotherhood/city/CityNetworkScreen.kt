@file:Suppress("MagicNumber")

package com.tonapps.wallet.features.brotherhood.city

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tonapps.brotherhood.h3.H3Helper
import com.tonapps.wallet.data.brotherhood.db.WatchedLocationEntity
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodCard
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodColors
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodMetricRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodPrimaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSecondaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodStatusBadge
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodTextField
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.LocalBrotherhoodTxLauncher
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun CityNetworkScreen(
    modifier: Modifier = Modifier,
    feature: CityNetworkFeature = koinInject(),
) {
    val watchedLocations by feature.watchedLocations.collectAsState()
    val selectedH3Cell by feature.selectedH3Cell.collectAsState()
    val inspectedLocation by feature.inspectedLocation.collectAsState()
    val cityPolls by feature.cityPolls.collectAsState()
    val h3SearchQuery by feature.h3SearchQuery.collectAsState()
    val showH3VisualizerModal by feature.showH3VisualizerModal.collectAsState()
    val statusMessage by feature.statusMessage.collectAsState()
    val pendingIntent by feature.pendingIntent.collectAsState()

    val txLauncher = LocalBrotherhoodTxLauncher.current
    val context = LocalContext.current

    var relocateH3Input by remember(selectedH3Cell) { mutableStateOf(selectedH3Cell) }
    var relocateCountryInput by remember(inspectedLocation) {
        mutableStateOf((inspectedLocation?.countryCode ?: CityNetworkFeature.DEFAULT_COUNTRY_CODE).toString())
    }
    var proposalTitle by remember { mutableStateOf("") }
    var proposalDescription by remember { mutableStateOf("") }

    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent
        if (intent != null) {
            txLauncher.launch(intent)
            feature.consumePendingIntent()
        }
    }

    if (showH3VisualizerModal) {
        H3VisualizerModal(
            selectedH3Cell = selectedH3Cell,
            watchedLocations = watchedLocations,
            onSelectCell = { cellHex ->
                feature.selectH3Cell(cellHex)
            },
            onAutoDetectGps = {
                val coords = resolveDeviceOrFallbackLatLng(context)
                feature.autoDetectFromLatLng(coords.first, coords.second)
            },
            onDismiss = {
                feature.setShowH3VisualizerModal(false)
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrotherhoodColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!statusMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrotherhoodColors.CardElevated)
                    .border(1.dp, BrotherhoodColors.AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { feature.clearStatusMessage() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusMessage.orEmpty(),
                    color = BrotherhoodColors.TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dismiss",
                    color = BrotherhoodColors.AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        BrotherhoodCard(
            title = "H3 Spatial City Network",
            subtitle = "Resolution-1 global hexagonal shards & regional treasuries",
            badgeText = "Res ${H3Helper.getResolution(selectedH3Cell) ?: 1}",
            badgeColor = BrotherhoodColors.AccentBlue,
        ) {
            BrotherhoodTextField(
                value = h3SearchQuery,
                onValueChange = { query -> feature.updateSearchQuery(query) },
                label = "H3 Cell Index (15-char hex)",
                placeholder = CityNetworkFeature.DEFAULT_H3_CELL,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrotherhoodPrimaryButton(
                    text = "Inspect H3 Cell",
                    onClick = { feature.inspectCurrentCell(forceRefresh = true) },
                    modifier = Modifier.weight(1f),
                )
                BrotherhoodSecondaryButton(
                    text = "84-Cell World Map",
                    onClick = { feature.setShowH3VisualizerModal(true) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            BrotherhoodSecondaryButton(
                text = "Auto-Detect GPS Region",
                onClick = {
                    val coords = resolveDeviceOrFallbackLatLng(context)
                    feature.autoDetectFromLatLng(coords.first, coords.second)
                },
            )
        }

        val currentHydrated = inspectedLocation
        BrotherhoodCard(
            title = currentHydrated?.label ?: "H3 Region $selectedH3Cell",
            subtitle = "Sharded Location Contract State",
            badgeText = if (currentHydrated?.isPrimaryResidence == true) {
                "Primary Residence"
            } else {
                "Inspected Sector"
            },
            badgeColor = if (currentHydrated?.isPrimaryResidence == true) {
                BrotherhoodColors.AccentGreen
            } else {
                BrotherhoodColors.AccentPurple
            },
        ) {
            BrotherhoodMetricRow(
                label = "H3 Cell Index",
                value = selectedH3Cell,
                monospaceValue = true,
                valueColor = BrotherhoodColors.AccentBlue,
            )
            BrotherhoodMetricRow(
                label = "Location Contract",
                value = BrotherhoodUiFormatters.shortAddress(currentHydrated?.locationAddress),
                monospaceValue = true,
            )
            BrotherhoodMetricRow(
                label = "Regional Treasury Balance",
                value = BrotherhoodUiFormatters.formatNanoAmount(
                    rawNano = currentHydrated?.treasuryBalanceNano,
                    symbol = "TON",
                ),
                valueColor = BrotherhoodColors.AccentGreen,
            )
            BrotherhoodMetricRow(
                label = "Registered Citizen Count",
                value = "${currentHydrated?.memberCount ?: 0L} citizens",
            )
            BrotherhoodMetricRow(
                label = "ISO Country Code",
                value = "#${currentHydrated?.countryCode ?: CityNetworkFeature.DEFAULT_COUNTRY_CODE}",
            )
            BrotherhoodMetricRow(
                label = "Base Cell / Store Version",
                value = "Base #${H3Helper.getBaseCellNumber(selectedH3Cell) ?: 0} • v${currentHydrated?.store?.version ?: 0}",
            )
        }

        BrotherhoodCard(
            title = "Relocate Primary Residence",
            subtitle = "Update your on-chain H3 sector & register in Location shard",
            badgeText = "0.5 TON Gas",
            badgeColor = BrotherhoodColors.AccentAmber,
        ) {
            BrotherhoodTextField(
                value = relocateH3Input,
                onValueChange = { text -> relocateH3Input = text },
                label = "Target H3 Resolution-1 Cell",
                placeholder = CityNetworkFeature.DEFAULT_H3_CELL,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BrotherhoodTextField(
                value = relocateCountryInput,
                onValueChange = { text -> relocateCountryInput = text },
                label = "ISO Numeric Country Code (e.g. 356, 840, 826)",
                placeholder = "356",
                keyboardType = KeyboardType.Number,
            )
            Spacer(modifier = Modifier.height(10.dp))
            BrotherhoodPrimaryButton(
                text = "Set Primary Residence on TON",
                onClick = {
                    val parsedCountry = relocateCountryInput.trim().toIntOrNull()
                        ?: CityNetworkFeature.DEFAULT_COUNTRY_CODE
                    feature.relocatePrimaryResidence(
                        newH3Cell = relocateH3Input,
                        countryCode = parsedCountry,
                    )
                },
            )
        }

        BrotherhoodCard(
            title = "Watched H3 Cells (${watchedLocations.size})",
            subtitle = "Tap any watched region to inspect its treasury & polls",
        ) {
            if (watchedLocations.isEmpty()) {
                Text(
                    text = "No watched H3 cells cached yet. Tap 'Inspect H3 Cell' or open the 84-Cell World Map.",
                    color = BrotherhoodColors.TextSecondary,
                    fontSize = 12.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (watched in watchedLocations) {
                        WatchedLocationRow(
                            entity = watched,
                            isSelected = watched.h3Cell == selectedH3Cell,
                            onClick = { feature.selectH3Cell(watched.h3Cell) },
                        )
                    }
                }
            }
        }

        BrotherhoodCard(
            title = "City Network Polls & Local Proposals",
            subtitle = "Regional governance for H3 cell $selectedH3Cell",
            badgeText = "${cityPolls.size} Polls",
            badgeColor = BrotherhoodColors.AccentPurple,
        ) {
            BrotherhoodTextField(
                value = proposalTitle,
                onValueChange = { text -> proposalTitle = text },
                label = "New City Proposal Title",
                placeholder = "e.g. Fund local community mesh node",
            )
            Spacer(modifier = Modifier.height(8.dp))
            BrotherhoodTextField(
                value = proposalDescription,
                onValueChange = { text -> proposalDescription = text },
                label = "Proposal Scope & Budget Breakdown",
                placeholder = "Describe regional benefit for H3 cell $selectedH3Cell",
            )
            Spacer(modifier = Modifier.height(10.dp))
            BrotherhoodPrimaryButton(
                text = "Submit City Proposal",
                onClick = {
                    feature.submitCityProposal(
                        title = proposalTitle,
                        description = proposalDescription,
                    )
                    if (proposalTitle.isNotBlank()) {
                        proposalTitle = ""
                        proposalDescription = ""
                    }
                },
            )

            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (poll in cityPolls) {
                    CityPollCardItem(
                        poll = poll,
                        onVoteFor = { feature.voteOnCityPoll(poll.pollId, true) },
                        onVoteAgainst = { feature.voteOnCityPoll(poll.pollId, false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchedLocationRow(
    entity: WatchedLocationEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        BrotherhoodColors.AccentBlue
    } else {
        BrotherhoodColors.Border
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrotherhoodColors.CardElevated)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.label,
                    color = BrotherhoodColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${entity.h3Cell} • Country #${entity.countryCode}",
                    color = BrotherhoodColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (entity.isPrimaryResidence) {
                BrotherhoodStatusBadge(
                    text = "Primary",
                    color = BrotherhoodColors.AccentGreen,
                )
            } else if (isSelected) {
                BrotherhoodStatusBadge(
                    text = "Selected",
                    color = BrotherhoodColors.AccentBlue,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Members: ${entity.memberCount}",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = "Treasury: ${BrotherhoodUiFormatters.formatRawStrAmount(entity.treasuryBalanceNano, "TON")}",
                color = BrotherhoodColors.AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CityPollCardItem(
    poll: CityPollItem,
    onVoteFor: () -> Unit,
    onVoteAgainst: () -> Unit,
) {
    val totalVotes = (poll.yesVotes + poll.noVotes).coerceAtLeast(1L)
    val yesFraction = poll.yesVotes.toFloat() / totalVotes.toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrotherhoodColors.CardElevated)
            .border(1.dp, BrotherhoodColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#${poll.pollId} • ${poll.title}",
                color = BrotherhoodColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            val voteBadge = when (poll.userVote) {
                true -> "Voted FOR"
                false -> "Voted AGAINST"
                null -> poll.h3Cell.take(7)
            }
            val badgeColor = when (poll.userVote) {
                true -> BrotherhoodColors.AccentGreen
                false -> BrotherhoodColors.AccentRed
                null -> BrotherhoodColors.AccentBlue
            }
            BrotherhoodStatusBadge(text = voteBadge, color = badgeColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = poll.description,
            color = BrotherhoodColors.TextSecondary,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { yesFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = BrotherhoodColors.AccentGreen,
            trackColor = BrotherhoodColors.AccentRed.copy(alpha = 0.35f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "FOR: ${poll.yesVotes}",
                color = BrotherhoodColors.AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "AGAINST: ${poll.noVotes}",
                color = BrotherhoodColors.AccentRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodPrimaryButton(
                text = "Vote For",
                onClick = onVoteFor,
                color = BrotherhoodColors.AccentGreen,
                modifier = Modifier.weight(1f),
            )
            BrotherhoodPrimaryButton(
                text = "Vote Against",
                onClick = onVoteAgainst,
                color = BrotherhoodColors.AccentRed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun H3VisualizerModal(
    selectedH3Cell: String,
    watchedLocations: List<WatchedLocationEntity>,
    onSelectCell: (String) -> Unit,
    onAutoDetectGps: () -> Unit,
    onDismiss: () -> Unit,
) {
    val allCells = remember { H3Res1Grid.ALL_RES1_CELLS }
    val watchedSet = remember(watchedLocations) {
        watchedLocations.map { item -> item.h3Cell }.toSet()
    }
    val selectedMeta = remember(selectedH3Cell, allCells) {
        allCells.firstOrNull { cell -> cell.h3Cell == selectedH3Cell }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BrotherhoodColors.CardSurface)
                .border(1.dp, BrotherhoodColors.Border, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive H3 Resolution-1 Grid",
                        color = BrotherhoodColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "84 global res-1 cells • Tap any hexagon to select",
                        color = BrotherhoodColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                BrotherhoodStatusBadge(
                    text = "${allCells.size} Cells",
                    color = BrotherhoodColors.AccentBlue,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0B121B))
                    .border(1.dp, BrotherhoodColors.Border, RoundedCornerShape(14.dp)),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(allCells) {
                            detectTapGestures { tapOffset ->
                                val widthPx = size.width.toFloat().coerceAtLeast(1f)
                                val heightPx = size.height.toFloat().coerceAtLeast(1f)
                                val nearest = allCells.minByOrNull { cell ->
                                    val centerX = ((cell.center.longitude + 180.0) / 360.0).toFloat() * widthPx
                                    val centerY = ((90.0 - cell.center.latitude) / 180.0).toFloat() * heightPx
                                    hypot(tapOffset.x - centerX, tapOffset.y - centerY)
                                }
                                if (nearest != null) {
                                    onSelectCell(nearest.h3Cell)
                                }
                            }
                        },
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    for (latLine in listOf(-60, -30, 0, 30, 60)) {
                        val lineY = ((90.0 - latLine) / 180.0).toFloat() * canvasHeight
                        drawLine(
                            color = BrotherhoodColors.Border.copy(alpha = 0.45f),
                            start = Offset(0f, lineY),
                            end = Offset(canvasWidth, lineY),
                            strokeWidth = 1f,
                        )
                    }
                    for (lngLine in listOf(-120, -60, 0, 60, 120)) {
                        val lineX = ((lngLine + 180.0) / 360.0).toFloat() * canvasWidth
                        drawLine(
                            color = BrotherhoodColors.Border.copy(alpha = 0.45f),
                            start = Offset(lineX, 0f),
                            end = Offset(lineX, canvasHeight),
                            strokeWidth = 1f,
                        )
                    }

                    val hexRadiusPx = (canvasWidth / 28f).coerceAtLeast(10f)
                    for (cell in allCells) {
                        val centerX = ((cell.center.longitude + 180.0) / 360.0).toFloat() * canvasWidth
                        val centerY = ((90.0 - cell.center.latitude) / 180.0).toFloat() * canvasHeight
                        val isSelected = cell.h3Cell == selectedH3Cell
                        val isWatched = watchedSet.contains(cell.h3Cell)

                        val hexPath = Path().apply {
                            for (vertexIndex in 0 until 6) {
                                val angleRad = (60.0 * vertexIndex - 30.0) * PI / 180.0
                                val vx = centerX + hexRadiusPx * cos(angleRad).toFloat()
                                val vy = centerY + hexRadiusPx * sin(angleRad).toFloat()
                                if (vertexIndex == 0) {
                                    moveTo(vx, vy)
                                } else {
                                    lineTo(vx, vy)
                                }
                            }
                            close()
                        }

                        val fillColor = when {
                            isSelected -> BrotherhoodColors.AccentGreen.copy(alpha = 0.55f)
                            isWatched -> BrotherhoodColors.AccentAmber.copy(alpha = 0.40f)
                            cell.isPreset -> BrotherhoodColors.AccentBlue.copy(alpha = 0.30f)
                            else -> BrotherhoodColors.CardElevated.copy(alpha = 0.45f)
                        }
                        val strokeColor = when {
                            isSelected -> BrotherhoodColors.AccentGreen
                            isWatched -> BrotherhoodColors.AccentAmber
                            cell.isPreset -> BrotherhoodColors.AccentBlue
                            else -> BrotherhoodColors.Border
                        }

                        drawPath(path = hexPath, color = fillColor)
                        val strokeWidth = if (isSelected) {
                            3f
                        } else {
                            1.2f
                        }
                        drawPath(
                            path = hexPath,
                            color = strokeColor,
                            style = Stroke(width = strokeWidth),
                        )
                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = 3.5f,
                                center = Offset(centerX, centerY),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrotherhoodColors.CardElevated)
                    .padding(10.dp),
            ) {
                Text(
                    text = selectedMeta?.label ?: "Selected H3 Sector",
                    color = BrotherhoodColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "H3: $selectedH3Cell • Lat ${selectedMeta?.center?.latitude ?: 0.0}, Lng ${selectedMeta?.center?.longitude ?: 0.0}",
                    color = BrotherhoodColors.AccentBlue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (preset in H3Helper.PRESET_REGIONS) {
                    val active = preset.h3Cell == selectedH3Cell
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (active) {
                                    BrotherhoodColors.AccentBlue
                                } else {
                                    BrotherhoodColors.CardElevated
                                }
                            )
                            .clickable { onSelectCell(preset.h3Cell) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = preset.name.substringBefore(" /"),
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrotherhoodSecondaryButton(
                    text = "Auto-Detect GPS",
                    onClick = onAutoDetectGps,
                    modifier = Modifier.weight(1f),
                )
                BrotherhoodPrimaryButton(
                    text = "Confirm Selection",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun resolveDeviceOrFallbackLatLng(context: Context): Pair<Double, Double> {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    if (hasFine || hasCoarse) {
        val location = runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()
        if (location != null) {
            return location.latitude to location.longitude
        }
    }
    val fallback = H3Helper.PRESET_REGIONS.first().center
    return fallback.latitude to fallback.longitude
}
