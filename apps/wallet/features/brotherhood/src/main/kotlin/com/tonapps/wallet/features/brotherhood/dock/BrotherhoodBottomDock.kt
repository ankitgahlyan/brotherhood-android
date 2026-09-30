package com.tonapps.wallet.features.brotherhood.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodColors

enum class BrotherhoodMainTab(
    val index: Int,
    val id: String,
    val shortTitle: String,
    val badgeSymbol: String,
) {
    WALLET(0, "wallet", "Wallet", "◈"),
    BROTHERHOOD(1, "brotherhood", "BrotherHood", "⚡"),
    PERSONAL_TOKEN(2, "personal", "Personal Token", "◎"),
    CITY_NETWORK(3, "city", "City Network", "⬢"),
    DAO(4, "dao", "DAO", "⚖"),
    LOTTERY(5, "lottery", "Lottery", "✦"),
    DNS(6, "dns", ".bro DNS", "🌐"),
}

/**
 * 7-tab horizontally scrollable bottom navigation dock replacing the legacy 3-tab bottom bar.
 */
@Composable
fun BrotherhoodBottomDock(
    selectedTab: BrotherhoodMainTab,
    onTabSelected: (BrotherhoodMainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BrotherhoodColors.Background.copy(alpha = 0.96f))
            .border(1.dp, BrotherhoodColors.Border)
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrotherhoodMainTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val containerColor = if (isSelected) {
                BrotherhoodColors.AccentBlue
            } else {
                BrotherhoodColors.CardSurface
            }
            val contentColor = if (isSelected) {
                Color.White
            } else {
                BrotherhoodColors.TextSecondary
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) {
                            BrotherhoodColors.AccentBlue
                        } else {
                            BrotherhoodColors.Border
                        },
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${tab.badgeSymbol} ${tab.shortTitle}",
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                )
            }
        }
    }
}
