package com.tonapps.tonkeeper.ui.screen.main

import androidx.core.net.toUri
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity

object BrotherhoodSignRequestMapper {

    private const val BROTHERHOOD_APP_URI = "https://brotherhood.ton"

    fun toSignRequestEntity(
        intent: BrotherhoodTransferIntent,
        wallet: WalletEntity,
    ): SignRequestEntity {
        val rawMessages = intent.messages.map { msg ->
            RawMessageEntity(
                addressValue = msg.destinationAddressRaw,
                amount = msg.amountNano,
                stateInitValue = msg.stateInitBase64,
                payloadValue = msg.payloadBase64,
                withBattery = false,
            )
        }
        return SignRequestEntity.Builder()
            .setFrom(wallet.contract.address)
            .setTestnet(wallet.testnet)
            .setValidUntil(intent.resolvedValidUntilSeconds())
            .addMessages(rawMessages)
            .build(BROTHERHOOD_APP_URI.toUri())
    }
}
