package com.tonapps.wallet.features.brotherhood.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Provides the active TON wallet context (`ownerWalletAddress`, `testnet`, and transaction launcher)
 * to all BrotherHood feature tabs without coupling `:apps:wallet:features:brotherhood` to legacy app classes.
 */
class BrotherhoodWalletSessionHolder {
    private val _walletAddressFlow = MutableStateFlow<String?>(null)
    val walletAddressFlow: StateFlow<String?> = _walletAddressFlow.asStateFlow()

    private val _isTestnetFlow = MutableStateFlow(BrotherhoodConfig.DEFAULT_TESTNET)
    val isTestnetFlow: StateFlow<Boolean> = _isTestnetFlow.asStateFlow()

    private val _swapRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val swapRequests: SharedFlow<Unit> = _swapRequests.asSharedFlow()

    fun updateActiveWallet(addressRawOrFriendly: String?, testnet: Boolean = true) {
        _walletAddressFlow.value = addressRawOrFriendly
        _isTestnetFlow.value = testnet
    }

    fun requestMutualCreditSwap() {
        _swapRequests.tryEmit(Unit)
    }
}

fun interface BrotherhoodTxLauncher {
    fun launch(intent: BrotherhoodTransferIntent)
}

val LocalBrotherhoodTxLauncher = staticCompositionLocalOf<BrotherhoodTxLauncher> {
    BrotherhoodTxLauncher { }
}

object BrotherhoodUiFormatters {
    private val NINE_DECIMALS = BigDecimal.TEN.pow(9)

    fun formatNanoAmount(
        rawNano: BigInteger?,
        symbol: String = BrotherhoodConfig.FI_SYMBOL,
        displayDecimals: Int = 4,
    ): String {
        val value = rawNano ?: BigInteger.ZERO
        val decimal = BigDecimal(value).divide(NINE_DECIMALS, displayDecimals, RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
        return "$decimal $symbol"
    }

    fun formatRawStrAmount(
        rawNanoStr: String?,
        symbol: String = BrotherhoodConfig.FI_SYMBOL,
        displayDecimals: Int = 4,
    ): String {
        val parsed = rawNanoStr?.toBigIntegerOrNull() ?: BigInteger.ZERO
        return formatNanoAmount(parsed, symbol, displayDecimals)
    }

    fun parseDecimalToNano(input: String, decimals: Int = 9): BigInteger? {
        val trimmed = input.trim().replace(",", ".")
        if (trimmed.isEmpty()) {
            return null
        }
        return runCatching {
            val bd = BigDecimal(trimmed)
            if (bd <= BigDecimal.ZERO) {
                null
            } else {
                bd.multiply(BigDecimal.TEN.pow(decimals))
                    .setScale(0, RoundingMode.DOWN)
                    .toBigInteger()
            }
        }.getOrNull()
    }

    fun shortAddress(address: String?): String {
        if (address.isNullOrBlank()) {
            return "—"
        }
        val trimmed = address.trim()
        return if (trimmed.length <= 14) {
            trimmed
        } else {
            "${trimmed.take(6)}…${trimmed.takeLast(6)}"
        }
    }
}

object BrotherhoodColors {
    val Background = Color(0xFF10161F)
    val CardSurface = Color(0xFF182230)
    val CardElevated = Color(0xFF202E40)
    val Border = Color(0xFF2B3B52)
    val AccentBlue = Color(0xFF45AEF5)
    val AccentGreen = Color(0xFF34C759)
    val AccentAmber = Color(0xFFFF9F0A)
    val AccentPurple = Color(0xFFAF52DE)
    val AccentRed = Color(0xFFFF453A)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E9BAE)
    val TextMuted = Color(0xFF5E6C80)
}

@Composable
fun BrotherhoodSubTabRow(
    tabs: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((key, title) in tabs) {
            val isSelected = key == selectedKey
            val bgColor = if (isSelected) {
                BrotherhoodColors.AccentBlue
            } else {
                BrotherhoodColors.CardSurface
            }
            val textColor = if (isSelected) {
                Color.White
            } else {
                BrotherhoodColors.TextSecondary
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) {
                            BrotherhoodColors.AccentBlue
                        } else {
                            BrotherhoodColors.Border
                        },
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                )
            }
        }
    }
}

@Composable
fun BrotherhoodCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeColor: Color = BrotherhoodColors.AccentBlue,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BrotherhoodColors.CardSurface)
            .border(1.dp, BrotherhoodColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = BrotherhoodColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = BrotherhoodColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            if (!badgeText.isNullOrBlank()) {
                BrotherhoodStatusBadge(text = badgeText, color = badgeColor)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun BrotherhoodStatusBadge(
    text: String,
    color: Color = BrotherhoodColors.AccentBlue,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun BrotherhoodMetricRow(
    label: String,
    value: String,
    monospaceValue: Boolean = false,
    valueColor: Color = BrotherhoodColors.TextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = BrotherhoodColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospaceValue) {
                FontFamily.Monospace
            } else {
                FontFamily.Default
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BrotherhoodTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, fontSize = 12.sp) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(text = placeholder, fontSize = 12.sp, color = BrotherhoodColors.TextMuted) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BrotherhoodColors.TextPrimary,
            unfocusedTextColor = BrotherhoodColors.TextPrimary,
            focusedContainerColor = BrotherhoodColors.CardElevated,
            unfocusedContainerColor = BrotherhoodColors.CardElevated,
            focusedBorderColor = BrotherhoodColors.AccentBlue,
            unfocusedBorderColor = BrotherhoodColors.Border,
            focusedLabelColor = BrotherhoodColors.AccentBlue,
            unfocusedLabelColor = BrotherhoodColors.TextSecondary,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun BrotherhoodPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = BrotherhoodColors.AccentBlue,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = BrotherhoodColors.Border,
            disabledContentColor = BrotherhoodColors.TextMuted,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun BrotherhoodSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = BrotherhoodColors.AccentBlue,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
