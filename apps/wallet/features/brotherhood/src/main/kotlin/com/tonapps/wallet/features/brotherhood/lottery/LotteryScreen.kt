package com.tonapps.wallet.features.brotherhood.lottery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodCard
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodColors
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodMetricRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodPrimaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSecondaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSubTabRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodTextField
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.LocalBrotherhoodTxLauncher
import org.koin.androidx.compose.koinViewModel
import java.math.BigInteger

private val PRESET_ROUND_TABS: List<Pair<String, String>> = listOf(
    "1" to "Round #1",
    "2" to "Round #2",
    "3" to "Round #3",
)

@Composable
fun LotteryScreen() {
    val feature = koinViewModel<LotteryFeature>()
    val selectedRoundId by feature.selectedRoundId.collectAsState()
    val lotteryState by feature.lotteryState.collectAsState()
    val ticketCountInput by feature.ticketCountInput.collectAsState()
    val statusMessage by feature.statusMessage.collectAsState()
    val pendingIntent by feature.pendingIntent.collectAsState()
    val txLauncher = LocalBrotherhoodTxLauncher.current

    var customRoundInput by remember { mutableStateOf("") }

    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent
        if (intent != null) {
            feature.consumePendingIntent()
            txLauncher.launch(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrotherhoodColors.Background),
    ) {
        BrotherhoodSubTabRow(
            tabs = PRESET_ROUND_TABS,
            selectedKey = selectedRoundId.toString(),
            onSelect = { key ->
                val parsed = key.toLongOrNull() ?: 1L
                feature.selectRound(parsed)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!statusMessage.isNullOrBlank()) {
                BrotherhoodCard(
                    title = "Lottery Status",
                    subtitle = statusMessage,
                    badgeText = "ROUND #$selectedRoundId",
                    badgeColor = BrotherhoodColors.AccentPurple,
                ) {
                    BrotherhoodSecondaryButton(
                        text = "Refresh Lottery State",
                        onClick = { feature.refreshLottery(forceRefresh = true) },
                    )
                }
            }

            BrotherhoodCard(
                title = "Sharded Round Selector",
                subtitle = "Switch between preset sharded rounds or inspect a custom Round ID",
                badgeText = "ACTIVE #$selectedRoundId",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrotherhoodTextField(
                        value = customRoundInput,
                        onValueChange = { customRoundInput = it },
                        label = "Custom Round ID",
                        placeholder = "4",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    BrotherhoodSecondaryButton(
                        text = "Load Round",
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                        enabled = customRoundInput.isNotBlank(),
                        onClick = {
                            val parsed = customRoundInput.trim().toLongOrNull()
                            if (parsed != null && parsed > 0L) {
                                feature.selectRound(parsed)
                            }
                        },
                    )
                }
            }

            LotteryPotTrackerCard(
                roundId = selectedRoundId,
                snapshot = lotteryState,
                onRefresh = { feature.refreshLottery(forceRefresh = true) },
            )

            LotteryTicketPurchaseCard(
                roundId = selectedRoundId,
                ticketCountInput = ticketCountInput,
                onTicketCountChange = feature::updateTicketCountInput,
                onBuyTickets = feature::buyTickets,
            )

            LotteryDrawWinnerCard(
                roundId = selectedRoundId,
                snapshot = lotteryState,
                onTriggerDraw = feature::triggerDrawWinner,
            )

            LotteryWinnerHistoryCard(snapshot = lotteryState)

            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun LotteryPotTrackerCard(
    roundId: Long,
    snapshot: LotteryRoundSnapshot?,
    onRefresh: () -> Unit,
) {
    val storage = snapshot?.storage
    val isDeployed = snapshot?.isDeployed == true
    val prizePoolFormatted = BrotherhoodUiFormatters.formatNanoAmount(
        rawNano = storage?.prizePool,
        symbol = BrotherhoodConfig.FI_SYMBOL,
    )
    val contractTonFormatted = BrotherhoodUiFormatters.formatNanoAmount(
        rawNano = snapshot?.balanceNano,
        symbol = "TON",
    )

    BrotherhoodCard(
        title = "Pot Tracker • Round #$roundId",
        subtitle = snapshot?.lotteryAddress ?: "Sharded closeTo FossFi Minter",
        badgeText = if (isDeployed) {
            "LIVE POT"
        } else {
            "UNDEPLOYED"
        },
        badgeColor = if (isDeployed) {
            BrotherhoodColors.AccentGreen
        } else {
            BrotherhoodColors.AccentAmber
        },
    ) {
        BrotherhoodMetricRow(
            label = "Current Prize Pool",
            value = prizePoolFormatted,
            valueColor = BrotherhoodColors.AccentGreen,
        )
        BrotherhoodMetricRow(
            label = "Ticket Price",
            value = "100 FI + 0.08 TON",
            valueColor = BrotherhoodColors.AccentAmber,
        )
        BrotherhoodMetricRow(
            label = "Participants Count",
            value = "${storage?.participantCount ?: storage?.participants?.size ?: 0}",
        )
        BrotherhoodMetricRow(
            label = "Contract TON Balance",
            value = contractTonFormatted,
        )
        BrotherhoodMetricRow(
            label = "Reveal / Draw Deadline",
            value = if ((storage?.revealDeadline ?: 0L) > 0L) {
                "${storage?.revealDeadline}s"
            } else {
                "Open"
            },
        )
        BrotherhoodMetricRow(
            label = "Random Seed",
            value = storage?.randomSeedHex?.takeIf { it.isNotBlank() }?.let {
                BrotherhoodUiFormatters.shortAddress(it)
            } ?: "Pending VRF",
            monospaceValue = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSecondaryButton(
            text = "Sync On-Chain Pot",
            onClick = onRefresh,
        )
    }
}

@Composable
private fun LotteryTicketPurchaseCard(
    roundId: Long,
    ticketCountInput: String,
    onTicketCountChange: (String) -> Unit,
    onBuyTickets: () -> Unit,
) {
    val parsedCount = ticketCountInput.trim().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
    val totalFiNano = BigInteger.valueOf(BrotherhoodConfig.LOTTERY_TICKET_PRICE_FI_NANO)
        .multiply(BigInteger.valueOf(parsedCount))
    val totalFiDisplay = BrotherhoodUiFormatters.formatNanoAmount(
        rawNano = totalFiNano,
        symbol = BrotherhoodConfig.FI_SYMBOL,
    )

    BrotherhoodCard(
        title = "Buy Lottery Tickets",
        subtitle = "Enter Round #$roundId via AskToTransfer + EnterLottery forward payload",
        badgeText = "100 FI / TICKET",
        badgeColor = BrotherhoodColors.AccentBlue,
    ) {
        BrotherhoodTextField(
            value = ticketCountInput,
            onValueChange = onTicketCountChange,
            label = "Number of Tickets",
            placeholder = "1",
            keyboardType = KeyboardType.Number,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodMetricRow(
            label = "Total Ticket Cost (FI)",
            value = totalFiDisplay,
            valueColor = BrotherhoodColors.AccentAmber,
        )
        BrotherhoodMetricRow(
            label = "Attached Entry Gas",
            value = "0.08 TON",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Buy Tickets ($totalFiDisplay + 0.08 TON)",
            enabled = ticketCountInput.isNotBlank(),
            onClick = onBuyTickets,
        )
    }
}

@Composable
private fun LotteryDrawWinnerCard(
    roundId: Long,
    snapshot: LotteryRoundSnapshot?,
    onTriggerDraw: () -> Unit,
) {
    val participantCount = snapshot?.storage?.participantCount ?: 0
    BrotherhoodCard(
        title = "Draw Round #$roundId Winner",
        subtitle = "Permissionless on-chain draw once round deadline or threshold is reached",
        badgeText = "0.2 TON GAS",
        badgeColor = BrotherhoodColors.AccentPurple,
    ) {
        BrotherhoodMetricRow(
            label = "Eligible Entries",
            value = "$participantCount participant(s)",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodPrimaryButton(
            text = "Trigger Draw Winner (DrawWinner 0x0000119a)",
            color = BrotherhoodColors.AccentPurple,
            onClick = onTriggerDraw,
        )
    }
}

@Composable
private fun LotteryWinnerHistoryCard(
    snapshot: LotteryRoundSnapshot?,
) {
    val winnersMap = snapshot?.storage?.winners.orEmpty()
    BrotherhoodCard(
        title = "Round Entries & Winner History",
        subtitle = "Decoded from LotteryStore on-chain dictionary",
        badgeText = "${winnersMap.size} RECORDED",
    ) {
        if (winnersMap.isEmpty()) {
            Text(
                text = "No entries or winners recorded in this Lottery shard yet.",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 13.sp,
            )
        } else {
            for ((slotId, address) in winnersMap) {
                BrotherhoodMetricRow(
                    label = "Slot #$slotId",
                    value = BrotherhoodUiFormatters.shortAddress(address),
                    monospaceValue = true,
                )
            }
        }
    }
}
