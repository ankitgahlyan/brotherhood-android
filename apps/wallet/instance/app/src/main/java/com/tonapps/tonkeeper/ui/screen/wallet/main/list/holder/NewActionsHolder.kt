package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.WithdrawFragment
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import org.koin.core.context.GlobalContext
import uikit.widget.ActionIconButtonView

class NewActionsHolder(parent: ViewGroup) : Holder<Item.Actions>(parent, R.layout.view_new_wallet_actions) {

    private val sendView = findViewById<ActionIconButtonView>(R.id.send)
    private val receiveView = findViewById<ActionIconButtonView>(R.id.receive)
    private val swapView = findViewById<ActionIconButtonView>(R.id.swap)
    private val stakeView = findViewById<ActionIconButtonView>(R.id.stake)

    override fun onBind(item: Item.Actions) {
        val isWatchOnly = item.walletType == WalletType.Watch
        val isSwapEnabled = item.walletType != WalletType.Watch
        val isSendEnabled = item.walletType != WalletType.Watch

        receiveView.setOnClickListener {
            navigation?.add(DepositFragment())
        }

        swapView.setOnClickListener {
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isSwapEnabled) {
                return@setOnClickListener
            }

            GlobalContext.getOrNull()?.getOrNull<BrotherhoodWalletSessionHolder>()?.requestMutualCreditSwap()
        }

        sendView.setOnClickListener {
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isSendEnabled) {
                return@setOnClickListener
            }

            navigation?.add(WithdrawFragment.create())
        }

        // Staking is disabled in BrotherHood Wallet per product specification
        stakeView.visibility = View.GONE

        swapView.setEnabledAlpha(isSwapEnabled)
        sendView.setEnabledAlpha(isSendEnabled)
    }

    private fun openWatchInfo(wallet: WalletEntity) {
        navigation?.add(WatchInfoScreen.newInstance(wallet))
    }
}
