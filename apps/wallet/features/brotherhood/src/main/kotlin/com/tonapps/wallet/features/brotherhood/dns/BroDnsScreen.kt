package com.tonapps.wallet.features.brotherhood.dns

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.wallet.data.brotherhood.db.WatchedDnsDomainEntity
import com.tonapps.wallet.data.brotherhood.repo.InspectedBroDomain
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodCard
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodColors
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodMetricRow
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodPrimaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodSecondaryButton
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodTextField
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.LocalBrotherhoodTxLauncher
import org.koin.androidx.compose.koinViewModel

@Composable
fun BroDnsScreen() {
    val feature = koinViewModel<BroDnsFeature>()
    val searchQuery by feature.searchQuery.collectAsState()
    val inspectedDomain by feature.inspectedDomain.collectAsState()
    val watchedDomains by feature.watchedDomains.collectAsState()
    val bidAmountInput by feature.bidAmountInput.collectAsState()
    val walletRecordInput by feature.walletRecordInput.collectAsState()
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
            .background(BrotherhoodColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!statusMessage.isNullOrBlank()) {
            BrotherhoodCard(
                title = ".bro DNS Resolver Status",
                subtitle = statusMessage,
                badgeText = "TEP-81 .BRO",
                badgeColor = BrotherhoodColors.AccentBlue,
            ) {
                BrotherhoodSecondaryButton(
                    text = "Re-Sync Inspected Domain",
                    onClick = { feature.searchDomain(forceRefresh = true) },
                )
            }
        }

        BroDnsSearchCard(
            searchQuery = searchQuery,
            onSearchQueryChange = feature::updateSearchQuery,
            onInspect = { feature.searchDomain(forceRefresh = true) },
        )

        BroDnsInspectionCard(inspected = inspectedDomain)

        BroDnsAuctionPanelCard(
            inspected = inspectedDomain,
            bidAmountInput = bidAmountInput,
            onBidAmountChange = feature::updateBidAmountInput,
            onPlaceBidOrDeploy = feature::placeBidOrDeploy,
            onFinalizeAuction = feature::finalizeAuction,
        )

        BroDnsRecordEditorCard(
            inspected = inspectedDomain,
            walletRecordInput = walletRecordInput,
            onWalletRecordChange = feature::updateWalletRecordInput,
            onSaveWalletRecord = feature::saveWalletRecord,
            onRenewDomain = feature::renewDomain,
            onReleaseDomain = feature::releaseDomain,
        )

        WatchedBroDomainsCard(
            watchedDomains = watchedDomains,
            onSelectDomain = { domain ->
                feature.updateSearchQuery(domain)
                feature.searchDomain(forceRefresh = false)
            },
        )

        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
private fun BroDnsSearchCard(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onInspect: () -> Unit,
) {
    BrotherhoodCard(
        title = "Search .bro Decentralized Domains",
        subtitle = "Derive deterministic DnsItem address from SHA-256(domain) & inspect BOC state",
        badgeText = ".BRO",
    ) {
        BrotherhoodTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = "Domain Label (with or without .bro)",
            placeholder = "satoshi.bro",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Inspect .bro Domain",
            enabled = searchQuery.isNotBlank(),
            onClick = onInspect,
        )
    }
}

@Composable
private fun BroDnsInspectionCard(
    inspected: InspectedBroDomain?,
) {
    if (inspected == null) {
        BrotherhoodCard(
            title = "Deterministic DnsItem Inspector",
            subtitle = "Search any .bro name above to inspect ownership, auction state, and wallet resolution",
            badgeText = "IDLE",
            badgeColor = BrotherhoodColors.AccentAmber,
        ) {
            Text(
                text = "Collection Resolver: ${BrotherhoodUiFormatters.shortAddress(BrotherhoodConfig.BRO_COLLECTION_RESOLVER_STR)}",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 13.sp,
            )
        }
        return
    }

    val store = inspected.store
    val badgeText = when {
        inspected.isOwnedByUser -> "OWNED BY YOU"
        inspected.isDeployed -> "DEPLOYED"
        else -> "AVAILABLE"
    }
    val badgeColor = when {
        inspected.isOwnedByUser -> BrotherhoodColors.AccentGreen
        inspected.isDeployed -> BrotherhoodColors.AccentBlue
        else -> BrotherhoodColors.AccentAmber
    }

    BrotherhoodCard(
        title = inspected.fullDomain,
        subtitle = "DnsItem: ${inspected.dnsItemAddress}",
        badgeText = badgeText,
        badgeColor = badgeColor,
    ) {
        BrotherhoodMetricRow(
            label = "Contract Status",
            value = if (inspected.isDeployed) {
                "Initialized on-chain"
            } else {
                "Undeployed (Open for initial bid)"
            },
        )
        BrotherhoodMetricRow(
            label = "Owner Address",
            value = BrotherhoodUiFormatters.shortAddress(store?.ownerAddress),
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Resolved Wallet Record",
            value = BrotherhoodUiFormatters.shortAddress(store?.walletRecordAddress),
            monospaceValue = true,
            valueColor = BrotherhoodColors.AccentGreen,
        )
        BrotherhoodMetricRow(
            label = "Last Fill-Up Timestamp",
            value = if ((store?.lastFillUpTime ?: 0L) > 0L) {
                "${store?.lastFillUpTime}s"
            } else {
                "—"
            },
        )
        BrotherhoodMetricRow(
            label = "SHA-256 Index",
            value = BrotherhoodUiFormatters.shortAddress(store?.indexHex),
            monospaceValue = true,
        )
    }
}

@Composable
private fun BroDnsAuctionPanelCard(
    inspected: InspectedBroDomain?,
    bidAmountInput: String,
    onBidAmountChange: (String) -> Unit,
    onPlaceBidOrDeploy: () -> Unit,
    onFinalizeAuction: () -> Unit,
) {
    val auction = inspected?.store?.auction
    val maxBidFormatted = BrotherhoodUiFormatters.formatNanoAmount(
        rawNano = auction?.maxBidAmount,
        symbol = BrotherhoodConfig.FI_SYMBOL,
    )
    val auctionEndTime = auction?.auctionEndTime ?: 0L

    BrotherhoodCard(
        title = "English Auction & Minting",
        subtitle = "Bid in FI/HD via BroTreasury (${BrotherhoodUiFormatters.shortAddress(BrotherhoodConfig.BRO_TREASURY_ADDRESS_STR)})",
        badgeText = if (auction != null && auctionEndTime > 0L) {
            "AUCTION ACTIVE"
        } else {
            "OPEN BID"
        },
        badgeColor = BrotherhoodColors.AccentAmber,
    ) {
        BrotherhoodMetricRow(
            label = "Highest Bid (maxBidAmount)",
            value = maxBidFormatted,
            valueColor = BrotherhoodColors.AccentAmber,
        )
        BrotherhoodMetricRow(
            label = "Highest Bidder (maxBidAddress)",
            value = BrotherhoodUiFormatters.shortAddress(auction?.maxBidAddress),
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Auction End Time",
            value = if (auctionEndTime > 0L) {
                "${auctionEndTime}s"
            } else {
                "Not started"
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = bidAmountInput,
            onValueChange = onBidAmountChange,
            label = "Bid Amount (${BrotherhoodConfig.FI_SYMBOL})",
            placeholder = "100",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodPrimaryButton(
                text = "Place Bid / Mint",
                modifier = Modifier.weight(1f),
                enabled = bidAmountInput.isNotBlank(),
                onClick = onPlaceBidOrDeploy,
            )
            BrotherhoodSecondaryButton(
                text = "Finalize Auction",
                modifier = Modifier.weight(1f),
                onClick = onFinalizeAuction,
            )
        }
    }
}

@Composable
private fun BroDnsRecordEditorCard(
    inspected: InspectedBroDomain?,
    walletRecordInput: String,
    onWalletRecordChange: (String) -> Unit,
    onSaveWalletRecord: () -> Unit,
    onRenewDomain: () -> Unit,
    onReleaseDomain: () -> Unit,
) {
    BrotherhoodCard(
        title = "DNS Record Editor & Lease Management",
        subtitle = "Configure TEP-81 wallet record (0x9fd3), renew lease, or release domain",
        badgeText = if (inspected?.isOwnedByUser == true) {
            "OWNER"
        } else {
            "0.15 TON GAS"
        },
        badgeColor = if (inspected?.isOwnedByUser == true) {
            BrotherhoodColors.AccentGreen
        } else {
            BrotherhoodColors.AccentBlue
        },
    ) {
        BrotherhoodTextField(
            value = walletRecordInput,
            onValueChange = onWalletRecordChange,
            label = "Target TON Wallet Address (TEP-81 wallet record)",
            placeholder = "0:... or kQ... (leave blank to clear)",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BrotherhoodPrimaryButton(
            text = "Save Wallet Record (ChangeDnsRecord)",
            onClick = onSaveWalletRecord,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrotherhoodSecondaryButton(
                text = "Renew / FillUp",
                modifier = Modifier.weight(1f),
                onClick = onRenewDomain,
            )
            BrotherhoodSecondaryButton(
                text = "Release Domain",
                modifier = Modifier.weight(1f),
                onClick = onReleaseDomain,
            )
        }
    }
}

@Composable
private fun WatchedBroDomainsCard(
    watchedDomains: List<WatchedDnsDomainEntity>,
    onSelectDomain: (String) -> Unit,
) {
    BrotherhoodCard(
        title = "Watched .bro Domains",
        subtitle = "Cached in local Room DB for instant Send-screen resolution",
        badgeText = "${watchedDomains.size} CACHED",
    ) {
        if (watchedDomains.isEmpty()) {
            Text(
                text = "No .bro domains inspected yet. Search a domain above to cache it locally.",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 13.sp,
            )
        } else {
            for (item in watchedDomains) {
                val resolvedShort = BrotherhoodUiFormatters.shortAddress(item.resolvedWalletAddress)
                val maxBidStr = BrotherhoodUiFormatters.formatRawStrAmount(
                    rawNanoStr = item.maxBidAmountRaw,
                    symbol = BrotherhoodConfig.FI_SYMBOL,
                )
                BrotherhoodMetricRow(
                    label = "${item.domainName}.bro → $resolvedShort",
                    value = if (item.isOwnedByUser) {
                        "OWNED • $maxBidStr"
                    } else {
                        maxBidStr
                    },
                    monospaceValue = true,
                    valueColor = if (item.isOwnedByUser) {
                        BrotherhoodColors.AccentGreen
                    } else {
                        BrotherhoodColors.TextPrimary
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                BrotherhoodSecondaryButton(
                    text = "Inspect ${item.domainName}.bro",
                    onClick = { onSelectDomain(item.domainName) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
