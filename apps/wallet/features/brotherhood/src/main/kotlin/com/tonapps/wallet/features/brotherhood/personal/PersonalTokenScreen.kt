package com.tonapps.wallet.features.brotherhood.personal

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.wallet.data.brotherhood.db.TrackedPersonalTokenEntity
import com.tonapps.wallet.data.brotherhood.repo.MutualCreditSwapQuote
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
import org.koin.androidx.compose.koinViewModel

@Composable
fun PersonalTokenScreen(
    modifier: Modifier = Modifier,
    viewModel: PersonalTokenFeature = koinViewModel(),
) {
    val selectedSubTab by viewModel.selectedSubTab.collectAsState()
    val trackedTokens by viewModel.trackedTokens.collectAsState()
    val selectedToken by viewModel.selectedToken.collectAsState()
    val isBuyCredit by viewModel.isBuyCreditDirection.collectAsState()
    val swapAmountInput by viewModel.swapAmountInput.collectAsState()
    val swapQuote by viewModel.swapQuote.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val pendingIntent by viewModel.pendingIntent.collectAsState()
    val txLauncher = LocalBrotherhoodTxLauncher.current

    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent
        if (intent != null) {
            txLauncher.launch(intent)
            viewModel.consumePendingIntent()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrotherhoodColors.Background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Personal Token Studio",
                    color = BrotherhoodColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "PersonalMinter • Mutual Credit Swap",
                    color = BrotherhoodColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            BrotherhoodStatusBadge(
                text = selectedToken?.symbol ?: "FIUSD",
                color = BrotherhoodColors.AccentGreen,
            )
        }

        if (!statusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrotherhoodColors.AccentAmber.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusMessage.orEmpty(),
                    color = BrotherhoodColors.AccentAmber,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Dismiss",
                    color = BrotherhoodColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { viewModel.dismissStatusMessage() },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSubTabRow(
            tabs = viewModel.subTabPairs,
            selectedKey = selectedSubTab.id,
            onSelect = { viewModel.selectSubTab(it) },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PersonalTokenSelectorCard(
                tokens = trackedTokens,
                selectedToken = selectedToken,
                onSelect = { viewModel.selectToken(it) },
                onRefresh = { viewModel.refreshPersonalTokens(forceRefresh = true) },
                onAddCustom = { viewModel.addCustomMinter(it) },
            )

            when (selectedSubTab) {
                PersonalTokenSubTab.OVERVIEW -> OverviewPanel(selectedToken)
                PersonalTokenSubTab.CREATE -> CreatePanel { name, symbol, desc, img ->
                    viewModel.createPersonalToken(name, symbol, desc, img)
                }
                PersonalTokenSubTab.MINT -> MintPanel(selectedToken) { recipient, amount ->
                    viewModel.mintPersonalToken(recipient, amount)
                }
                PersonalTokenSubTab.CREDIT -> MutualCreditSwapPanel(
                    selectedToken = selectedToken,
                    isBuyCredit = isBuyCredit,
                    swapAmountInput = swapAmountInput,
                    quote = swapQuote,
                    onToggleDirection = { viewModel.toggleSwapDirection() },
                    onAmountChanged = { viewModel.updateSwapAmountInput(it) },
                    onExecuteSwap = { viewModel.executeMutualCreditSwap() },
                )
                PersonalTokenSubTab.TRANSFER -> TransferPanel(selectedToken) { recipient, amount, comment ->
                    viewModel.transferPersonalToken(recipient, amount, comment)
                }
                PersonalTokenSubTab.BURN -> BurnPanel(selectedToken) { amount ->
                    viewModel.burnPersonalToken(amount)
                }
                PersonalTokenSubTab.ADMIN -> AdminPanel(selectedToken) { name, symbol, desc, img ->
                    viewModel.updateMetadata(name, symbol, desc, img)
                }
            }
        }
    }
}

/**
 * Standalone Mutual Credit Swap modal launched from the Wallet tab's Swap action button.
 * Defaults From `FI/HD` (treated like GRAM) -> To `FI` Admin's Personal Token (`FIUSD`, treated as reference stablecoin like USDT).
 */
@Composable
fun MutualCreditSwapSheet(
    onDismiss: () -> Unit,
    viewModel: PersonalTokenFeature = koinViewModel(),
) {
    val trackedTokens by viewModel.trackedTokens.collectAsState()
    val selectedToken by viewModel.selectedToken.collectAsState()
    val isBuyCredit by viewModel.isBuyCreditDirection.collectAsState()
    val swapAmountInput by viewModel.swapAmountInput.collectAsState()
    val swapQuote by viewModel.swapQuote.collectAsState()
    val pendingIntent by viewModel.pendingIntent.collectAsState()
    val txLauncher = LocalBrotherhoodTxLauncher.current

    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent
        if (intent != null) {
            txLauncher.launch(intent)
            viewModel.consumePendingIntent()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(BrotherhoodColors.Background)
                .border(1.dp, BrotherhoodColors.Border, RoundedCornerShape(24.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Mutual Credit Swap",
                        color = BrotherhoodColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "FI/HD (Native Credit) ↔ Admin Reference Stablecoin",
                        color = BrotherhoodColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = "Close",
                    color = BrotherhoodColors.AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onDismiss() },
                )
            }

            if (trackedTokens.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    trackedTokens.forEach { token ->
                        val isSelected = selectedToken?.minterAddress == token.minterAddress
                        val bg = if (isSelected) {
                            BrotherhoodColors.AccentBlue
                        } else {
                            BrotherhoodColors.CardElevated
                        }
                        val fg = if (isSelected) {
                            Color.White
                        } else {
                            BrotherhoodColors.TextPrimary
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .clickable { viewModel.selectToken(token.minterAddress) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (token.isAdminReferenceStablecoin) {
                                    "${token.symbol} (Stable)"
                                } else {
                                    token.symbol
                                },
                                color = fg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            MutualCreditSwapPanel(
                selectedToken = selectedToken,
                isBuyCredit = isBuyCredit,
                swapAmountInput = swapAmountInput,
                quote = swapQuote,
                onToggleDirection = { viewModel.toggleSwapDirection() },
                onAmountChanged = { viewModel.updateSwapAmountInput(it) },
                onExecuteSwap = { viewModel.executeMutualCreditSwap() },
            )
        }
    }
}

@Composable
private fun PersonalTokenSelectorCard(
    tokens: List<TrackedPersonalTokenEntity>,
    selectedToken: TrackedPersonalTokenEntity?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddCustom: (String) -> Unit,
) {
    var customMinterInput by remember { mutableStateOf("") }
    var showAddField by remember { mutableStateOf(false) }

    BrotherhoodCard(
        title = "Tracked Personal Tokens",
        subtitle = "Admin Reference Stablecoin (FIUSD) & Citizen Personal Jettons",
        badgeText = "${tokens.size} Tracked",
        badgeColor = BrotherhoodColors.AccentBlue,
    ) {
        if (tokens.isEmpty()) {
            Text(
                text = "Syncing FI Admin Reference Stablecoin & Personal Jetton contracts...",
                color = BrotherhoodColors.TextSecondary,
                fontSize = 13.sp,
            )
        } else {
            tokens.forEach { token ->
                val isSelected = selectedToken?.minterAddress == token.minterAddress
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                BrotherhoodColors.AccentBlue.copy(alpha = 0.16f)
                            } else {
                                BrotherhoodColors.CardElevated
                            }
                        )
                        .clickable { onSelect(token.minterAddress) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${token.name} (${token.symbol})",
                            color = BrotherhoodColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (token.isAdminReferenceStablecoin) {
                                "FI Admin Reference Stablecoin • ${BrotherhoodUiFormatters.shortAddress(token.minterAddress)}"
                            } else {
                                "Minter: ${BrotherhoodUiFormatters.shortAddress(token.minterAddress)}"
                            },
                            color = BrotherhoodColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = BrotherhoodUiFormatters.formatRawStrAmount(token.userBalanceRaw, token.symbol),
                        color = BrotherhoodColors.AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (showAddField) {
            BrotherhoodTextField(
                value = customMinterInput,
                onValueChange = { customMinterInput = it },
                label = "Custom PersonalMinter Address",
                placeholder = "0:... or EQ...",
            )
            Spacer(modifier = Modifier.height(8.dp))
            BrotherhoodSecondaryButton(
                text = "Track PersonalMinter",
                onClick = {
                    onAddCustom(customMinterInput)
                    customMinterInput = ""
                    showAddField = false
                },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrotherhoodSecondaryButton(
                    text = "Refresh Tokens",
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                )
                BrotherhoodSecondaryButton(
                    text = "+ Add Minter",
                    onClick = { showAddField = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OverviewPanel(token: TrackedPersonalTokenEntity?) {
    BrotherhoodCard(
        title = token?.name ?: "No Personal Token Selected",
        subtitle = if (token?.isAdminReferenceStablecoin == true) {
            "Reference Stablecoin Issued by FI Admin"
        } else {
            "PersonalMinter & PersonalWallet State"
        },
    ) {
        BrotherhoodMetricRow(
            label = "Symbol",
            value = token?.symbol ?: "—",
        )
        BrotherhoodMetricRow(
            label = "Your Balance",
            value = BrotherhoodUiFormatters.formatRawStrAmount(token?.userBalanceRaw, token?.symbol ?: "PT"),
            valueColor = BrotherhoodColors.AccentGreen,
        )
        BrotherhoodMetricRow(
            label = "Total Supply",
            value = BrotherhoodUiFormatters.formatRawStrAmount(token?.totalSupplyRaw, token?.symbol ?: "PT"),
        )
        BrotherhoodMetricRow(
            label = "Minter Address",
            value = BrotherhoodUiFormatters.shortAddress(token?.minterAddress),
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Owner FiWallet",
            value = BrotherhoodUiFormatters.shortAddress(token?.ownerFiWalletAddress),
            monospaceValue = true,
        )
        BrotherhoodMetricRow(
            label = "Your PersonalWallet",
            value = BrotherhoodUiFormatters.shortAddress(token?.userPersonalWalletAddress),
            monospaceValue = true,
        )
    }
}

@Composable
private fun CreatePanel(
    onCreate: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    BrotherhoodCard(
        title = "Deploy Your Personal Token",
        subtitle = "Deploys deterministic PersonalMinter and links it to your FiWallet (ActSetPersonalJetton)",
    ) {
        BrotherhoodTextField(
            value = name,
            onValueChange = { name = it },
            label = "Token Name",
            placeholder = "e.g., Citizen Credit",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = symbol,
            onValueChange = { symbol = it },
            label = "Token Symbol",
            placeholder = "e.g., ANKIT",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description",
            placeholder = "Mutual credit backed by my BrotherHood reputation",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = "Icon URL (Optional)",
            placeholder = "https://...",
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = "Deploy & Link Personal Token (0.15 TON)",
            onClick = { onCreate(name, symbol, description, imageUrl) },
        )
    }
}

@Composable
private fun MintPanel(
    token: TrackedPersonalTokenEntity?,
    onMint: (String, String) -> Unit,
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("100") }

    BrotherhoodCard(
        title = "Mint ${token?.symbol ?: "Personal Token"}",
        subtitle = "Dispatches MintPersonal (0x00000015) to your PersonalMinter",
    ) {
        BrotherhoodTextField(
            value = recipient,
            onValueChange = { recipient = it },
            label = "Recipient Wallet (Blank = Yourself)",
            placeholder = "0:... or EQ...",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount to Mint (${token?.symbol ?: "PT"})",
            placeholder = "100",
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = "Mint ${token?.symbol ?: "Tokens"}",
            onClick = { onMint(recipient, amount) },
        )
    }
}

@Composable
private fun MutualCreditSwapPanel(
    selectedToken: TrackedPersonalTokenEntity?,
    isBuyCredit: Boolean,
    swapAmountInput: String,
    quote: MutualCreditSwapQuote?,
    onToggleDirection: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onExecuteSwap: () -> Unit,
) {
    val fromLabel = if (isBuyCredit) {
        "${BrotherhoodConfig.FI_SYMBOL} (Treated like GRAM)"
    } else {
        "${selectedToken?.symbol ?: "FIUSD"} (Personal Credit / Stablecoin)"
    }
    val toLabel = if (isBuyCredit) {
        "${selectedToken?.symbol ?: "FIUSD"} (Personal Credit / Stablecoin)"
    } else {
        "${BrotherhoodConfig.FI_SYMBOL} (Treated like GRAM)"
    }

    val badgeText = if (isBuyCredit) {
        "Buy Credit"
    } else {
        "Payback / Redeem"
    }
    val badgeColor = if (isBuyCredit) {
        BrotherhoodColors.AccentGreen
    } else {
        BrotherhoodColors.AccentAmber
    }
    val flipLabel = if (isBuyCredit) {
        "FI → ${selectedToken?.symbol ?: "FIUSD"}"
    } else {
        "${selectedToken?.symbol ?: "FIUSD"} → FI"
    }
    val inputSymbol = if (isBuyCredit) {
        BrotherhoodConfig.FI_SYMBOL
    } else {
        selectedToken?.symbol ?: "FIUSD"
    }

    BrotherhoodCard(
        title = "Mutual Credit Swap",
        subtitle = "BuyCredit (0x00001147) & Payback/AskToBurn (0x595f07bc)",
        badgeText = badgeText,
        badgeColor = badgeColor,
    ) {
        BrotherhoodMetricRow(label = "From Asset", value = fromLabel)
        BrotherhoodMetricRow(label = "To Asset", value = toLabel)

        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodSecondaryButton(
            text = "⇅ Flip Direction ($flipLabel)",
            onClick = onToggleDirection,
        )

        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = swapAmountInput,
            onValueChange = onAmountChanged,
            label = "Input Amount ($inputSymbol)",
            placeholder = "10",
        )

        if (quote != null) {
            Spacer(modifier = Modifier.height(8.dp))
            BrotherhoodMetricRow(
                label = "Estimated Output",
                value = BrotherhoodUiFormatters.formatNanoAmount(quote.estimatedOutputAmountRaw, quote.toSymbol),
                valueColor = BrotherhoodColors.AccentGreen,
            )
            BrotherhoodMetricRow(
                label = "Credit Multiplier",
                value = "${quote.exchangeMultiplierPermille / 10.0}%",
            )
            BrotherhoodMetricRow(
                label = "Network Gas",
                value = BrotherhoodUiFormatters.formatNanoAmount(quote.gasFeeNano, "TON"),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = if (isBuyCredit) {
                "Swap ${BrotherhoodConfig.FI_SYMBOL} → ${selectedToken?.symbol ?: "FIUSD"}"
            } else {
                "Redeem ${selectedToken?.symbol ?: "FIUSD"} → ${BrotherhoodConfig.FI_SYMBOL}"
            },
            onClick = onExecuteSwap,
        )
    }
}

@Composable
private fun TransferPanel(
    token: TrackedPersonalTokenEntity?,
    onTransfer: (String, String, String) -> Unit,
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("10") }
    var comment by remember { mutableStateOf("") }

    BrotherhoodCard(
        title = "Transfer ${token?.symbol ?: "Personal Token"}",
        subtitle = "Supports @username, .bro domain, or TON address",
    ) {
        BrotherhoodTextField(
            value = recipient,
            onValueChange = { recipient = it },
            label = "Recipient (@username, domain.bro, or address)",
            placeholder = "@alice or alice.bro",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount (${token?.symbol ?: "PT"})",
            placeholder = "10",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = comment,
            onValueChange = { comment = it },
            label = "Comment (Optional)",
            placeholder = "Mutual credit settlement",
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = "Send ${token?.symbol ?: "Personal Token"}",
            onClick = { onTransfer(recipient, amount, comment) },
        )
    }
}

@Composable
private fun BurnPanel(
    token: TrackedPersonalTokenEntity?,
    onBurn: (String) -> Unit,
) {
    var amount by remember { mutableStateOf("10") }

    BrotherhoodCard(
        title = "Burn ${token?.symbol ?: "Personal Token"}",
        subtitle = "Sends AskToBurn (0x595f07bc) to your PersonalWallet",
    ) {
        BrotherhoodTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount to Burn (${token?.symbol ?: "PT"})",
            placeholder = "10",
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = "Burn ${token?.symbol ?: "Tokens"}",
            onClick = { onBurn(amount) },
        )
    }
}

@Composable
private fun AdminPanel(
    token: TrackedPersonalTokenEntity?,
    onUpdateMetadata: (String, String, String, String) -> Unit,
) {
    var name by remember(token?.name) { mutableStateOf(token?.name.orEmpty()) }
    var symbol by remember(token?.symbol) { mutableStateOf(token?.symbol.orEmpty()) }
    var description by remember { mutableStateOf("Updated Personal Token Metadata") }
    var imageUrl by remember(token?.imageUrl) { mutableStateOf(token?.imageUrl.orEmpty()) }

    BrotherhoodCard(
        title = "PersonalMinter Admin",
        subtitle = "Update on-chain metadata for ${BrotherhoodUiFormatters.shortAddress(token?.minterAddress)}",
    ) {
        BrotherhoodTextField(
            value = name,
            onValueChange = { name = it },
            label = "Token Name",
            placeholder = "Citizen Credit",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = symbol,
            onValueChange = { symbol = it },
            label = "Symbol",
            placeholder = "PT",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description",
            placeholder = "Updated description",
        )
        Spacer(modifier = Modifier.height(8.dp))
        BrotherhoodTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = "Image URL",
            placeholder = "https://...",
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrotherhoodPrimaryButton(
            text = "Update On-Chain Metadata",
            onClick = { onUpdateMetadata(name, symbol, description, imageUrl) },
        )
    }
}
