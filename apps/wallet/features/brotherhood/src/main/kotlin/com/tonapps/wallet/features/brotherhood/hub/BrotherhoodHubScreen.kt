package com.tonapps.wallet.features.brotherhood.hub

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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.wallet.data.brotherhood.db.AddressBookCacheEntity
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodAccountSnapshot
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

@Composable
fun BrotherhoodHubScreen() {
    val feature = koinViewModel<BrotherhoodHubFeature>()
    val selectedSubTab by feature.selectedSubTab.collectAsState()
    val snapshot by feature.snapshot.collectAsState()
    val addressBook by feature.addressBook.collectAsState()
    val autoFundNotifications by feature.autoFundNotifications.collectAsState()
    val statusMessage by feature.statusMessage.collectAsState()
    val pendingIntent by feature.pendingIntent.collectAsState()
    val txLauncher = LocalBrotherhoodTxLauncher.current

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
            tabs = BrotherhoodHubSubTab.ALL_TABS,
            selectedKey = selectedSubTab,
            onSelect = feature::selectSubTab,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Auto-funding notification banner if any FiWallet was topped up
            for (notif in autoFundNotifications) {
                BrotherhoodCard(
                    title = "Auto-Funded Citizen FiWallet (${notif.reason})",
                    subtitle = notif.targetFiWalletAddress,
                    badgeText = "+2.0 TON",
                    badgeColor = BrotherhoodColors.AccentGreen,
                ) {
                    BrotherhoodSecondaryButton(
                        text = "Dismiss Notification",
                        onClick = { feature.dismissAutoFundNotification(notif.id) },
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                BrotherhoodCard(
                    title = "BrotherHood Hub Status",
                    subtitle = statusMessage,
                    badgeText = if (snapshot?.isFiWalletDeployed == true) {
                        "ONBOARDED"
                    } else {
                        "PENDING"
                    },
                    badgeColor = if (snapshot?.isFiWalletDeployed == true) {
                        BrotherhoodColors.AccentGreen
                    } else {
                        BrotherhoodColors.AccentAmber
                    },
                ) {
                    BrotherhoodSecondaryButton(
                        text = "Refresh Zero-Getter BOC State",
                        onClick = { feature.refresh(forceRefresh = true) },
                    )
                }
            }

            when (selectedSubTab) {
                BrotherhoodHubSubTab.ACCOUNT.key -> AccountSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.NETWORK.key -> NetworkSubTabContent(snapshot, addressBook)
                BrotherhoodHubSubTab.CLAIM.key -> ClaimSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.INVITE.key -> InviteSubTabContent(feature)
                BrotherhoodHubSubTab.VOTE.key -> VoteSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.CREDIT.key -> CreditSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.ALLOWANCE.key -> AllowanceSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.GOLD.key -> GoldSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.PROFILE.key -> ProfileSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.DEFERRED.key -> DeferredSubTabContent(snapshot, feature)
                BrotherhoodHubSubTab.AUTHORITY.key -> AuthoritySubTabContent(snapshot, feature)
            }

            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun AccountSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val store = snapshot?.fiWalletStore
    BrotherhoodCard(
        title = "Citizen FiWallet Overview",
        subtitle = snapshot?.derivedFiWalletAddressRaw ?: "No wallet selected",
        badgeText = if (store?.active == true) {
            "ACTIVE"
        } else {
            "UNINITIALIZED"
        },
        badgeColor = if (store?.active == true) {
            BrotherhoodColors.AccentGreen
        } else {
            BrotherhoodColors.AccentAmber
        },
    ) {
        BrotherhoodMetricRow(
            label = "FI / HD Balance",
            value = BrotherhoodUiFormatters.formatNanoAmount(store?.jettonBalance, BrotherhoodConfig.FI_SYMBOL),
            valueColor = BrotherhoodColors.AccentGreen,
        )
        BrotherhoodMetricRow(
            label = "FiWallet Gas Reserve",
            value = BrotherhoodUiFormatters.formatNanoAmount(snapshot?.fiWalletTonBalanceNano, "TON"),
        )
        BrotherhoodMetricRow(
            label = "Citizen Username",
            value = store?.profile?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Unset",
        )
        BrotherhoodMetricRow(
            label = "H3 Region Cell",
            value = store?.profile?.h3Cell?.ifBlank { "Unset" } ?: "Unset",
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Gold Coins Reserve",
            value = "${store?.goldCoins ?: 0L} GOLD",
            valueColor = BrotherhoodColors.AccentAmber,
        )
        BrotherhoodMetricRow(
            label = "Trust Votes (Available / Received)",
            value = "${store?.votes ?: 0} / ${store?.receivedVotes ?: 0}",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSecondaryButton(
            text = "Close FiWallet Account",
            onClick = feature::closeAccount,
        )
    }
}

@Composable
private fun NetworkSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    addressBook: List<AddressBookCacheEntity>,
) {
    val minter = snapshot?.fiMinterStore
    BrotherhoodCard(
        title = "FossFi Global Network Metrics",
        subtitle = "Minter: ${BrotherhoodUiFormatters.shortAddress(BrotherhoodConfig.FI_ADDRESS_STR)}",
        badgeText = "SHARD DEPTH 8",
    ) {
        BrotherhoodMetricRow(
            label = "Total FI Supply",
            value = BrotherhoodUiFormatters.formatNanoAmount(minter?.totalSupply, BrotherhoodConfig.FI_SYMBOL),
        )
        BrotherhoodMetricRow(
            label = "Active Citizens",
            value = "${minter?.totalAccounts ?: 0L}",
        )
        BrotherhoodMetricRow(
            label = "Indexed @username Directory",
            value = "${addressBook.size} citizens",
        )
        BrotherhoodMetricRow(
            label = "Admin Address",
            value = BrotherhoodUiFormatters.shortAddress(minter?.adminAddress),
            monospaceValue = true,
        )
    }

    BrotherhoodCard(
        title = "Citizen Directory (@username)",
        subtitle = "Zero-getter indexed citizen handles",
    ) {
        if (addressBook.isEmpty()) {
            Text(
                text = "No citizen profiles cached yet. Refresh your FiWallet to hydrate your social circle.",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 13.sp,
            )
        } else {
            for (entry in addressBook.take(15)) {
                BrotherhoodMetricRow(
                    label = "@${entry.username.ifBlank { "citizen" }} (${entry.h3Cell.ifBlank { "global" }})",
                    value = BrotherhoodUiFormatters.shortAddress(entry.ownerAddress),
                    monospaceValue = true,
                )
            }
        }
    }
}

@Composable
private fun ClaimSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val store = snapshot?.fiWalletStore
    BrotherhoodCard(
        title = "Weekly Citizen Grant",
        subtitle = "Periodic Universal Basic Credit in FI / HD",
        badgeText = "0.5 TON GAS",
    ) {
        BrotherhoodMetricRow(
            label = "Current Balance",
            value = BrotherhoodUiFormatters.formatNanoAmount(store?.jettonBalance, BrotherhoodConfig.FI_SYMBOL),
        )
        BrotherhoodMetricRow(
            label = "Last Mint Timestamp",
            value = "${store?.timestamps?.lastClaim ?: 0L}",
        )
        BrotherhoodMetricRow(
            label = "Circle Active Connections",
            value = "${store?.connections ?: 0}",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Claim Weekly Grant (ActClaimWeeklyGrant)",
            onClick = feature::claimWeeklyGrant,
            color = BrotherhoodColors.AccentGreen,
        )
    }
}

@Composable
private fun InviteSubTabContent(feature: BrotherhoodHubFeature) {
    var recipientAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var h3Cell by remember { mutableStateOf("813d3ffffffffff") }
    var countryCode by remember { mutableStateOf("356") }
    var deactivateTarget by remember { mutableStateOf("") }

    BrotherhoodCard(
        title = "Invite New Citizen to Circle",
        subtitle = "Mints 100 FI + deploys sharded FiWallet (1.1 TON gas)",
        badgeText = "CIRCLE RING",
    ) {
        BrotherhoodTextField(
            value = recipientAddress,
            onValueChange = { recipientAddress = it },
            label = "Invitee TON Wallet Address",
            placeholder = "0:... or kQ...",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = username,
            onValueChange = { username = it },
            label = "Initial Citizen @username",
            placeholder = "satoshi",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = h3Cell,
            onValueChange = { h3Cell = it },
            label = "H3 Resolution-1 Cell",
            placeholder = "813d3ffffffffff",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = countryCode,
            onValueChange = { countryCode = it },
            label = "ISO Country Numeric Code",
            keyboardType = KeyboardType.Number,
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = "Send Circle Invitation (ActInvite)",
            enabled = recipientAddress.isNotBlank() && username.isNotBlank(),
            onClick = {
                feature.sendCircleInvite(
                    recipientWalletAddress = recipientAddress,
                    username = username,
                    h3Cell = h3Cell,
                    countryCode = countryCode.toIntOrNull() ?: 356,
                )
            },
        )
    }

    BrotherhoodCard(
        title = "Manage Circle Ring Status",
        subtitle = "Toggle active circle ring status for an invitee FiWallet",
    ) {
        BrotherhoodTextField(
            value = deactivateTarget,
            onValueChange = { deactivateTarget = it },
            label = "Target Citizen FiWallet Address",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodSecondaryButton(
            text = "Toggle Circle Member (DeActivateCircleRing)",
            enabled = deactivateTarget.isNotBlank(),
            onClick = { feature.toggleCircleMember(deactivateTarget) },
        )
    }
}

@Composable
private fun VoteSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val store = snapshot?.fiWalletStore
    var targetFiWallet by remember { mutableStateOf("") }
    var votesCount by remember { mutableStateOf("1") }

    BrotherhoodCard(
        title = "Social Trust & Vouching",
        subtitle = "Endorse trusted citizens to expand their mutual credit multiplier",
        badgeText = "${store?.votes ?: 0} VOTES LEFT",
    ) {
        BrotherhoodMetricRow(
            label = "Votes Received",
            value = "${store?.receivedVotes ?: 0}",
        )
        BrotherhoodMetricRow(
            label = "Citizens Voted For",
            value = "${store?.maps?.social?.votedFor?.size ?: 0}",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = targetFiWallet,
            onValueChange = { targetFiWallet = it },
            label = "Target Citizen FiWallet Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = votesCount,
            onValueChange = { votesCount = it },
            label = "Trust Votes Count (1 - 15)",
            keyboardType = KeyboardType.Number,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodPrimaryButton(
                text = "Cast Vote",
                modifier = Modifier.weight(1f),
                enabled = targetFiWallet.isNotBlank(),
                onClick = {
                    feature.giveTrustVote(targetFiWallet, votesCount.toIntOrNull() ?: 1)
                },
            )
            BrotherhoodSecondaryButton(
                text = "Retract Vote",
                modifier = Modifier.weight(1f),
                enabled = targetFiWallet.isNotBlank(),
                onClick = {
                    feature.retractTrustVote(targetFiWallet, votesCount.toIntOrNull() ?: 1)
                },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodSecondaryButton(
                text = "Follow Citizen",
                modifier = Modifier.weight(1f),
                enabled = targetFiWallet.isNotBlank(),
                onClick = { feature.followOrUnfollow(targetFiWallet, follow = true) },
            )
            BrotherhoodSecondaryButton(
                text = "Unfollow",
                modifier = Modifier.weight(1f),
                enabled = targetFiWallet.isNotBlank(),
                onClick = { feature.followOrUnfollow(targetFiWallet, follow = false) },
            )
        }
    }
}

@Composable
private fun CreditSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val store = snapshot?.fiWalletStore
    val inDebt = (store?.debt?.signum() ?: 0) > 0
    var creditNeedInput by remember { mutableStateOf("500") }
    var maturityDaysInput by remember { mutableStateOf("90") }
    var multiplierInput by remember { mutableStateOf("100") }
    var repayDebtInput by remember { mutableStateOf("50") }

    BrotherhoodCard(
        title = "Mutual Credit & Debt Position",
        subtitle = "Zero-interest community credit backed by social trust",
        badgeText = if (inDebt) {
            "IN DEBT"
        } else {
            "HEALTHY"
        },
        badgeColor = if (inDebt) {
            BrotherhoodColors.AccentRed
        } else {
            BrotherhoodColors.AccentGreen
        },
    ) {
        BrotherhoodMetricRow(
            label = "Configured Credit Need",
            value = BrotherhoodUiFormatters.formatNanoAmount(store?.creditNeed, BrotherhoodConfig.FI_SYMBOL),
        )
        BrotherhoodMetricRow(
            label = "Accumulated Debt",
            value = BrotherhoodUiFormatters.formatNanoAmount(store?.debt, BrotherhoodConfig.FI_SYMBOL),
            valueColor = BrotherhoodColors.AccentAmber,
        )
        BrotherhoodMetricRow(
            label = "Max Allowed Loan",
            value = BrotherhoodUiFormatters.formatNanoAmount(store?.creditNeed, BrotherhoodConfig.FI_SYMBOL),
        )
        BrotherhoodMetricRow(
            label = "Credit Multiplier",
            value = "${store?.multiplier ?: 100}‰",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = creditNeedInput,
            onValueChange = { creditNeedInput = it },
            label = "Credit Requirement (FI)",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodTextField(
                value = maturityDaysInput,
                onValueChange = { maturityDaysInput = it },
                label = "Maturity (Days)",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
            )
            BrotherhoodTextField(
                value = multiplierInput,
                onValueChange = { multiplierInput = it },
                label = "Multiplier (‰)",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Configure Credit Rules (SetLoanRequirement)",
            onClick = {
                feature.setCreditRequirement(
                    creditNeedFiStr = creditNeedInput,
                    maturityDays = maturityDaysInput.toIntOrNull() ?: 90,
                    multiplier = multiplierInput.toIntOrNull() ?: 100,
                )
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSecondaryButton(
            text = "Pay Scheduled EMI (ActPayEmi)",
            onClick = feature::payEmiInstallment,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = repayDebtInput,
            onValueChange = { repayDebtInput = it },
            label = "Direct Debt Repayment Amount (FI)",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSecondaryButton(
            text = "Repay Mutual Debt (RepayDebt)",
            onClick = { feature.repayMutualDebt(repayDebtInput) },
        )
    }
}

@Composable
private fun AllowanceSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val allowances = snapshot?.fiWalletStore?.maps?.allowances.orEmpty()
    var spenderFiWallet by remember { mutableStateOf("") }
    var allowanceAmount by remember { mutableStateOf("100") }
    var granterFiWallet by remember { mutableStateOf("") }
    var recipientFiWallet by remember { mutableStateOf("") }
    var spendAmount by remember { mutableStateOf("25") }

    BrotherhoodCard(
        title = "Authorize Spending Allowance",
        subtitle = "Allow another citizen FiWallet to spend up to a cap",
        badgeText = "${allowances.size} ACTIVE",
    ) {
        for ((spender, rawCap) in allowances) {
            BrotherhoodMetricRow(
                label = BrotherhoodUiFormatters.shortAddress(spender),
                value = BrotherhoodUiFormatters.formatRawStrAmount(rawCap, BrotherhoodConfig.FI_SYMBOL),
                monospaceValue = true,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = spenderFiWallet,
            onValueChange = { spenderFiWallet = it },
            label = "Spender FiWallet Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = allowanceAmount,
            onValueChange = { allowanceAmount = it },
            label = "Allowance Cap (FI)",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Set Allowance (SetAllowance)",
            enabled = spenderFiWallet.isNotBlank(),
            onClick = { feature.setSpendingAllowance(spenderFiWallet, allowanceAmount) },
        )
    }

    BrotherhoodCard(
        title = "Spend Delegated Allowance",
        subtitle = "Transfer FI from a granter's FiWallet to a recipient",
    ) {
        BrotherhoodTextField(
            value = granterFiWallet,
            onValueChange = { granterFiWallet = it },
            label = "Granter FiWallet Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = recipientFiWallet,
            onValueChange = { recipientFiWallet = it },
            label = "Recipient FiWallet Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = spendAmount,
            onValueChange = { spendAmount = it },
            label = "Amount to Spend (FI)",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodSecondaryButton(
            text = "Execute SpendAllowance (0x00001144)",
            enabled = granterFiWallet.isNotBlank() && recipientFiWallet.isNotBlank(),
            onClick = {
                feature.spendDelegatedAllowance(granterFiWallet, recipientFiWallet, spendAmount)
            },
        )
    }
}

@Composable
private fun GoldSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val goldBalance = snapshot?.fiWalletStore?.goldCoins ?: 0L
    var recipientFiWallet by remember { mutableStateOf("") }
    var goldCountInput by remember { mutableStateOf("1") }

    BrotherhoodCard(
        title = "BrotherHood Gold Reserve",
        subtitle = "Scarce community reputation reserve coins",
        badgeText = "$goldBalance GOLD",
        badgeColor = BrotherhoodColors.AccentAmber,
    ) {
        BrotherhoodTextField(
            value = recipientFiWallet,
            onValueChange = { recipientFiWallet = it },
            label = "Recipient Citizen FiWallet Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = goldCountInput,
            onValueChange = { goldCountInput = it },
            label = "Gold Coins Count",
            keyboardType = KeyboardType.Number,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Transfer Gold Coins (AskGoldCoinsTransfer)",
            enabled = recipientFiWallet.isNotBlank(),
            color = BrotherhoodColors.AccentAmber,
            onClick = { feature.transferGoldCoins(recipientFiWallet, goldCountInput) },
        )
    }
}

@Composable
private fun ProfileSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val profile = snapshot?.fiWalletStore?.profile
    var username by remember(profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
    var h3Cell by remember(profile?.h3Cell) { mutableStateOf(profile?.h3Cell.orEmpty()) }
    var countryCode by remember(profile?.country) { mutableStateOf((profile?.country ?: 356).toString()) }
    var nomineeAddress by remember { mutableStateOf("") }

    BrotherhoodCard(
        title = "On-Chain Citizen Profile",
        subtitle = "Update @username, H3 res-1 cell, country, or inheritance nominee",
    ) {
        BrotherhoodTextField(
            value = username,
            onValueChange = { username = it },
            label = "Citizen @username",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = h3Cell,
            onValueChange = { h3Cell = it },
            label = "H3 Resolution-1 Index",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = countryCode,
            onValueChange = { countryCode = it },
            label = "Country Code (ISO Numeric)",
            keyboardType = KeyboardType.Number,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = nomineeAddress,
            onValueChange = { nomineeAddress = it },
            label = "Inheritance Nominee FiWallet (Optional)",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Save Citizen Profile (ChangeProfile)",
            onClick = {
                feature.updateCitizenProfile(
                    username = username,
                    h3Cell = h3Cell,
                    countryCodeStr = countryCode,
                    nomineeAddress = nomineeAddress,
                )
            },
        )
    }
}

@Composable
private fun DeferredSubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    var payerFiWallet by remember { mutableStateOf("") }
    var escrowAmount by remember { mutableStateOf("50") }
    var holdingAddress by remember { mutableStateOf("") }

    BrotherhoodCard(
        title = "Deferred Escrow Payments (Holding Contract)",
        subtitle = "Request or settle time-locked sharded Holding escrows",
        badgeText = "SHARDED ESCROW",
    ) {
        BrotherhoodTextField(
            value = payerFiWallet,
            onValueChange = { payerFiWallet = it },
            label = "Counterparty Payer FiWallet Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = escrowAmount,
            onValueChange = { escrowAmount = it },
            label = "Deferred Escrow Amount (FI)",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Request Deferred Payment (0x6a1bc924)",
            enabled = payerFiWallet.isNotBlank(),
            onClick = { feature.requestDeferredEscrow(payerFiWallet, escrowAmount) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodTextField(
            value = holdingAddress,
            onValueChange = { holdingAddress = it },
            label = "Sharded Holding Contract Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodPrimaryButton(
                text = "Claim Holding",
                modifier = Modifier.weight(1f),
                enabled = holdingAddress.isNotBlank(),
                color = BrotherhoodColors.AccentGreen,
                onClick = { feature.settleDeferredHolding(holdingAddress, claim = true) },
            )
            BrotherhoodSecondaryButton(
                text = "Cancel Holding",
                modifier = Modifier.weight(1f),
                enabled = holdingAddress.isNotBlank(),
                onClick = { feature.settleDeferredHolding(holdingAddress, claim = false) },
            )
        }
        for (item in snapshot?.deferredHoldings.orEmpty()) {
            BrotherhoodMetricRow(
                label = BrotherhoodUiFormatters.shortAddress(item.holdingAddress),
                value = BrotherhoodUiFormatters.formatNanoAmount(item.store.amount, BrotherhoodConfig.FI_SYMBOL),
                monospaceValue = true,
            )
        }
    }
}

@Composable
private fun AuthoritySubTabContent(
    snapshot: BrotherhoodAccountSnapshot?,
    feature: BrotherhoodHubFeature,
) {
    val isAuthority = snapshot?.fiWalletStore?.isAuthorityAccount == true
    var targetFiWallet by remember { mutableStateOf("") }
    var slashAmount by remember { mutableStateOf("0") }
    var toggleActive by remember { mutableStateOf(false) }

    BrotherhoodCard(
        title = "Authority & Community Moderation",
        subtitle = "Dispatch accountability actions against Sybil or reported accounts",
        badgeText = if (isAuthority) {
            "AUTHORITY CITIZEN"
        } else {
            "STANDARD CITIZEN"
        },
        badgeColor = if (isAuthority) {
            BrotherhoodColors.AccentPurple
        } else {
            BrotherhoodColors.AccentBlue
        },
    ) {
        BrotherhoodTextField(
            value = targetFiWallet,
            onValueChange = { targetFiWallet = it },
            label = "Target Citizen FiWallet Address",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = slashAmount,
            onValueChange = { slashAmount = it },
            label = "Slash / Confiscate Amount (FI)",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = toggleActive,
                onCheckedChange = { toggleActive = it },
            )
            Text(
                text = "Toggle target citizen active status",
                color = BrotherhoodColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Dispatch Authority Action (0x000010f1)",
            enabled = targetFiWallet.isNotBlank(),
            color = BrotherhoodColors.AccentPurple,
            onClick = {
                feature.dispatchAuthorityModeration(
                    targetFiWalletAddress = targetFiWallet,
                    toggleActive = toggleActive,
                    slashFiStr = slashAmount,
                )
            },
        )
    }
}
