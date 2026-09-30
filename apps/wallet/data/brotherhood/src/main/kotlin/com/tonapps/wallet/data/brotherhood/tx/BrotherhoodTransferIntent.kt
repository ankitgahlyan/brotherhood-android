package com.tonapps.wallet.data.brotherhood.tx

import com.tonapps.brotherhood.store.TolkSliceUtils.base64
import org.ton.block.AddrStd
import org.ton.cell.Cell
import java.math.BigInteger

data class BrotherhoodOutgoingMessage(
    val destination: AddrStd,
    val amountNano: BigInteger,
    val payloadCell: Cell? = null,
    val stateInitCell: Cell? = null,
) {
    val destinationRaw: String
        get() = destination.toString(userFriendly = false).lowercase()

    val payloadBocBase64: String?
        get() = payloadCell?.base64()

    val stateInitBocBase64: String?
        get() = stateInitCell?.base64()
}

data class BrotherhoodTransferIntent(
    val title: String,
    val subtitle: String,
    val messages: List<BrotherhoodOutgoingMessage>,
    val isAutoFund: Boolean = false,
) {
    val totalAttachedTonNano: BigInteger
        get() = messages.fold(BigInteger.ZERO) { acc, msg -> acc + msg.amountNano }
}
