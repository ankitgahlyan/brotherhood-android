@file:Suppress("MagicNumber")

package com.tonapps.wallet.features.brotherhood.dao

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.messages.BrotherhoodOpcodes
import com.tonapps.brotherhood.store.FiStore
import com.tonapps.wallet.data.brotherhood.repo.HydratedDaoPoll
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodCard
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodColors
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodMetricRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodPrimaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSecondaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodStatusBadge
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSubTabRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodTextField
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.LocalBrotherhoodTxLauncher
import org.koin.compose.koinInject

private val DAO_SUB_TABS = listOf(
    DaoFeature.SUB_TAB_PROPOSALS to "Proposals",
    DaoFeature.SUB_TAB_TREASURY to "Treasury & Grants",
    DaoFeature.SUB_TAB_ADMIN to "Admin & Upgrades",
)

@Composable
fun DaoScreen(
    modifier: Modifier = Modifier,
    feature: DaoFeature = koinInject(),
) {
    val subTab by feature.subTab.collectAsState()
    val polls by feature.polls.collectAsState()
    val inspectedPoll by feature.inspectedPoll.collectAsState()
    val fiMinterStore by feature.fiMinterStore.collectAsState()
    val statusMessage by feature.statusMessage.collectAsState()
    val pendingIntent by feature.pendingIntent.collectAsState()

    val txLauncher = LocalBrotherhoodTxLauncher.current

    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent
        if (intent != null) {
            txLauncher.launch(intent)
            feature.consumePendingIntent()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrotherhoodColors.Background),
    ) {
        BrotherhoodSubTabRow(
            tabs = DAO_SUB_TABS,
            selectedKey = subTab,
            onSelect = { key -> feature.selectSubTab(key) },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

            when (subTab) {
                DaoFeature.SUB_TAB_PROPOSALS -> DaoProposalsSection(
                    polls = polls,
                    inspectedPoll = inspectedPoll,
                    onRefresh = { feature.refreshDao(forceRefresh = true) },
                    onInspectPoll = { pollId -> feature.inspectPoll(pollId) },
                    onCreateProposal = { pollId, desc, recipient, amount ->
                        feature.createProposal(pollId, desc, recipient, amount)
                    },
                    onVote = { pollId, support -> feature.castVote(pollId, support) },
                    onExecute = { pollId -> feature.executeProposal(pollId) },
                )

                DaoFeature.SUB_TAB_TREASURY -> DaoTreasurySection(
                    fiMinterStore = fiMinterStore,
                    polls = polls,
                    onCreateGrantProposal = { pollId, desc, recipient, amount ->
                        feature.createProposal(pollId, desc, recipient, amount)
                    },
                )

                DaoFeature.SUB_TAB_ADMIN -> DaoAdminSection(
                    fiMinterStore = fiMinterStore,
                    onChangeAdmin = { newAdmin -> feature.changeMinterAdmin(newAdmin) },
                    onClaimAdmin = { feature.claimMinterAdmin() },
                    onDropAdmin = { feature.dropMinterAdmin() },
                    onRefresh = { feature.refreshDao(forceRefresh = true) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DaoProposalsSection(
    polls: List<HydratedDaoPoll>,
    inspectedPoll: HydratedDaoPoll?,
    onRefresh: () -> Unit,
    onInspectPoll: (Long) -> Unit,
    onCreateProposal: (Long, String, String, String) -> Unit,
    onVote: (Long, Boolean) -> Unit,
    onExecute: (Long) -> Unit,
) {
    var pollIdInput by remember(polls.size) {
        val nextId = (polls.maxOfOrNull { item -> item.pollStore.proposalId } ?: 0L) + 1L
        mutableStateOf(nextId.toString())
    }
    var descriptionInput by remember { mutableStateOf("") }
    var recipientInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("100") }

    BrotherhoodCard(
        title = "Submit Sharded DAO Proposal",
        subtitle = "Deploy Poll contract via DAO Proxy (${BrotherhoodUiFormatters.shortAddress(BrotherhoodConfig.DAO_PROXY_ADDRESS_STR)})",
        badgeText = "1.5 TON Gas",
        badgeColor = BrotherhoodColors.AccentBlue,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodTextField(
                value = pollIdInput,
                onValueChange = { text -> pollIdInput = text },
                label = "Proposal ID",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            BrotherhoodTextField(
                value = amountInput,
                onValueChange = { text -> amountInput = text },
                label = "Amount (${BrotherhoodConfig.FI_SYMBOL})",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = recipientInput,
            onValueChange = { text -> recipientInput = text },
            label = "Target Recipient TON Address (optional)",
            placeholder = BrotherhoodConfig.DAO_PROXY_ADDRESS_STR,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = descriptionInput,
            onValueChange = { text -> descriptionInput = text },
            label = "Proposal Rationale & Action Description",
            placeholder = "Describe the on-chain governance proposal",
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodPrimaryButton(
                text = "Submit Proposal",
                onClick = {
                    val parsedId = pollIdInput.trim().toLongOrNull() ?: 1L
                    onCreateProposal(parsedId, descriptionInput, recipientInput, amountInput)
                    if (descriptionInput.isNotBlank()) {
                        descriptionInput = ""
                    }
                },
                modifier = Modifier.weight(1f),
            )
            BrotherhoodSecondaryButton(
                text = "Sync Polls",
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (inspectedPoll != null) {
        val store = inspectedPoll.pollStore
        val opcodeTitle = store.targetOpcode?.let { op ->
            BrotherhoodOpcodes.getOpcodeInfo(op)?.friendlyTitle
        } ?: "Governance Payload"

        BrotherhoodCard(
            title = "Inspected Proposal #${store.proposalId}",
            subtitle = opcodeTitle,
            badgeText = if (store.executed) {
                "Executed"
            } else {
                "Active"
            },
            badgeColor = if (store.executed) {
                BrotherhoodColors.AccentPurple
            } else {
                BrotherhoodColors.AccentGreen
            },
        ) {
            BrotherhoodMetricRow(
                label = "Poll Contract",
                value = BrotherhoodUiFormatters.shortAddress(inspectedPoll.pollAddress),
                monospaceValue = true,
            )
            BrotherhoodMetricRow(
                label = "Proposer Owner",
                value = BrotherhoodUiFormatters.shortAddress(store.addresses.proposerOwner),
                monospaceValue = true,
            )
            BrotherhoodMetricRow(
                label = "Target Opcode",
                value = store.targetOpcode?.let { op -> "0x${op.toString(16)}" } ?: "—",
                monospaceValue = true,
            )
            BrotherhoodMetricRow(
                label = "Snapshot Total Citizens",
                value = "${store.totalAccounts} accounts",
            )
        }
    }

    BrotherhoodCard(
        title = "DAO Governance Polls (${polls.size})",
        subtitle = "Active & completed sharded PollStore contracts",
    ) {
        if (polls.isEmpty()) {
            Text(
                text = "No active or cached Poll contracts found on chain yet. Tap 'Sync Polls' or submit a new proposal.",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 12.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (poll in polls) {
                    DaoPollCard(
                        hydratedPoll = poll,
                        isInspected = inspectedPoll?.pollStore?.proposalId == poll.pollStore.proposalId,
                        onSelect = { onInspectPoll(poll.pollStore.proposalId) },
                        onVoteFor = { onVote(poll.pollStore.proposalId, true) },
                        onVoteAgainst = { onVote(poll.pollStore.proposalId, false) },
                        onExecute = { onExecute(poll.pollStore.proposalId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DaoPollCard(
    hydratedPoll: HydratedDaoPoll,
    isInspected: Boolean,
    onSelect: () -> Unit,
    onVoteFor: () -> Unit,
    onVoteAgainst: () -> Unit,
    onExecute: () -> Unit,
) {
    val store = hydratedPoll.pollStore
    val voter = hydratedPoll.userVoterStore
    val totalVotes = (store.yesVotes + store.noVotes).coerceAtLeast(1L)
    val yesFraction = store.yesVotes.toFloat() / totalVotes.toFloat()

    val voterBadgeText = when {
        voter?.voted == true && voter.vote -> "Voter: YES"
        voter?.voted == true && !voter.vote -> "Voter: NO"
        store.executed -> "Executed"
        else -> "Not Voted"
    }
    val voterBadgeColor = when {
        voter?.voted == true && voter.vote -> BrotherhoodColors.AccentGreen
        voter?.voted == true && !voter.vote -> BrotherhoodColors.AccentRed
        store.executed -> BrotherhoodColors.AccentPurple
        else -> BrotherhoodColors.AccentBlue
    }
    val borderColor = if (isInspected) {
        BrotherhoodColors.AccentBlue
    } else {
        BrotherhoodColors.Border
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BrotherhoodColors.CardElevated)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Proposal #${store.proposalId}",
                    color = BrotherhoodColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Poll ${BrotherhoodUiFormatters.shortAddress(hydratedPoll.pollAddress)} • Proposer ${BrotherhoodUiFormatters.shortAddress(store.addresses.proposerOwner)}",
                    color = BrotherhoodColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            BrotherhoodStatusBadge(
                text = voterBadgeText,
                color = voterBadgeColor,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
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
                text = "FOR: ${store.yesVotes}",
                color = BrotherhoodColors.AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Quorum Base: ${store.totalAccounts}",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 11.sp,
            )
            Text(
                text = "AGAINST: ${store.noVotes}",
                color = BrotherhoodColors.AccentRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodPrimaryButton(
                text = "Vote For",
                onClick = onVoteFor,
                enabled = !store.executed,
                color = BrotherhoodColors.AccentGreen,
                modifier = Modifier.weight(1f),
            )
            BrotherhoodPrimaryButton(
                text = "Vote Against",
                onClick = onVoteAgainst,
                enabled = !store.executed,
                color = BrotherhoodColors.AccentRed,
                modifier = Modifier.weight(1f),
            )
        }
        if (!store.executed && store.yesVotes > store.noVotes) {
            Spacer(modifier = Modifier.height(8.dp))
            BrotherhoodSecondaryButton(
                text = "Execute Proposal #${store.proposalId}",
                onClick = onExecute,
            )
        }
    }
}

@Composable
private fun DaoTreasurySection(
    fiMinterStore: FiStore?,
    polls: List<HydratedDaoPoll>,
    onCreateGrantProposal: (Long, String, String, String) -> Unit,
) {
    var grantRecipient by remember { mutableStateOf("") }
    var grantAmount by remember { mutableStateOf("500") }
    var grantMilestone by remember { mutableStateOf("") }

    BrotherhoodCard(
        title = "BrotherHood Ecosystem Treasury",
        subtitle = "FossFi Minter & .bro Treasury Reserves",
        badgeText = BrotherhoodConfig.FI_SYMBOL,
        badgeColor = BrotherhoodColors.AccentGreen,
    ) {
        BrotherhoodMetricRow(
            label = "Total ${BrotherhoodConfig.FI_SYMBOL} Supply",
            value = BrotherhoodUiFormatters.formatNanoAmount(
                rawNano = fiMinterStore?.totalSupply,
                symbol = BrotherhoodConfig.FI_SYMBOL,
            ),
            valueColor = BrotherhoodColors.AccentGreen,
        )
        BrotherhoodMetricRow(
            label = "Registered Citizen Accounts",
            value = "${fiMinterStore?.totalAccounts ?: 0L}",
        )
        BrotherhoodMetricRow(
            label = "DAO Proxy Contract",
            value = BrotherhoodUiFormatters.shortAddress(
                fiMinterStore?.daoAddress ?: BrotherhoodConfig.DAO_PROXY_ADDRESS_STR
            ),
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = ".bro Treasury Address",
            value = BrotherhoodUiFormatters.shortAddress(BrotherhoodConfig.BRO_TREASURY_ADDRESS_STR),
            monospaceValue = true,
        )
    }

    BrotherhoodCard(
        title = "Request Ecosystem Grant",
        subtitle = "Submit a treasury disbursement proposal to the DAO",
        badgeText = "Grant",
        badgeColor = BrotherhoodColors.AccentAmber,
    ) {
        BrotherhoodTextField(
            value = grantRecipient,
            onValueChange = { text -> grantRecipient = text },
            label = "Grant Recipient TON Wallet / FiWallet",
            placeholder = BrotherhoodConfig.FI_ADDRESS_STR,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = grantAmount,
            onValueChange = { text -> grantAmount = text },
            label = "Requested Grant Amount (${BrotherhoodConfig.FI_SYMBOL})",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = grantMilestone,
            onValueChange = { text -> grantMilestone = text },
            label = "Grant Deliverable & Milestone Summary",
            placeholder = "e.g. Open-source Tolk contract audit & tooling",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Submit Grant Proposal",
            onClick = {
                val nextId = (polls.maxOfOrNull { item -> item.pollStore.proposalId } ?: 0L) + 1L
                onCreateGrantProposal(nextId, grantMilestone, grantRecipient, grantAmount)
                if (grantMilestone.isNotBlank()) {
                    grantMilestone = ""
                }
            },
        )
    }
}

@Composable
private fun DaoAdminSection(
    fiMinterStore: FiStore?,
    onChangeAdmin: (String) -> Unit,
    onClaimAdmin: () -> Unit,
    onDropAdmin: () -> Unit,
    onRefresh: () -> Unit,
) {
    var newAdminInput by remember { mutableStateOf("") }
    val handoff = fiMinterStore?.adminHandoff
    val currentRequest = fiMinterStore?.others?.currentRequest

    BrotherhoodCard(
        title = "FossFi Minter Governance State",
        subtitle = "On-chain FiStore parameters & upgrade queue",
        badgeText = "v${fiMinterStore?.walletVersion ?: 1}",
        badgeColor = BrotherhoodColors.AccentPurple,
    ) {
        BrotherhoodMetricRow(
            label = "FossFi Minter Contract",
            value = BrotherhoodUiFormatters.shortAddress(BrotherhoodConfig.FI_ADDRESS_STR),
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Current Admin Address",
            value = BrotherhoodUiFormatters.shortAddress(fiMinterStore?.adminAddress),
            monospaceValue = true,
            valueColor = BrotherhoodColors.AccentBlue,
        )
        BrotherhoodMetricRow(
            label = "Active DAO Address",
            value = BrotherhoodUiFormatters.shortAddress(
                fiMinterStore?.daoAddress ?: BrotherhoodConfig.DAO_PROXY_ADDRESS_STR
            ),
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Off-Chain Rules Hash",
            value = fiMinterStore?.offChainRulesHash?.ifBlank { "—" } ?: "—",
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Pending Admin Handoff",
            value = if (handoff != null) {
                BrotherhoodUiFormatters.shortAddress(handoff.newAdminAddress)
            } else {
                "None"
            },
            monospaceValue = true,
            valueColor = if (handoff != null) {
                BrotherhoodColors.AccentAmber
            } else {
                BrotherhoodColors.TextSecondary
            },
        )
        if (currentRequest != null) {
            BrotherhoodMetricRow(
                label = "Pending Code Upgrade",
                value = "Store v${currentRequest.storeVersion} / Wallet v${currentRequest.walletVersion}",
                valueColor = BrotherhoodColors.AccentAmber,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSecondaryButton(
            text = "Refresh Minter State",
            onClick = onRefresh,
        )
    }

    BrotherhoodCard(
        title = "Minter Admin Handoff Controls",
        subtitle = "Two-step ChangeMinterAdmin / ClaimMinterAdmin / DropMinterAdmin",
        badgeText = "0.3 TON Gas",
        badgeColor = BrotherhoodColors.AccentAmber,
    ) {
        BrotherhoodTextField(
            value = newAdminInput,
            onValueChange = { text -> newAdminInput = text },
            label = "Proposed New Admin Address",
            placeholder = BrotherhoodConfig.DAO_PROXY_ADDRESS_STR,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Propose Admin Handoff",
            onClick = { onChangeAdmin(newAdminInput) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodSecondaryButton(
                text = "Claim Admin Role",
                onClick = onClaimAdmin,
                modifier = Modifier.weight(1f),
            )
            BrotherhoodPrimaryButton(
                text = "Drop Admin",
                onClick = onDropAdmin,
                color = BrotherhoodColors.AccentRed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
