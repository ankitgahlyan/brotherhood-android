package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.TolkSliceUtils.loadCoinsBigInt
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadUIntBigInt
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import java.math.BigInteger

@Serializable
data class HoldingStore(
    val payer: String,
    val payee: String,
    val amountRaw: String,
    val queryId: Long,
    val createdAt: Long,
) {
    val amount: BigInteger
        get() = amountRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    companion object {
        fun fromSlice(s: CellSlice): HoldingStore {
            return HoldingStore(
                payer = s.loadStdAddress().toAccountId(),
                payee = s.loadStdAddress().toAccountId(),
                amountRaw = s.loadCoinsBigInt().toString(),
                queryId = s.loadUIntBigInt(64).toLong(),
                createdAt = s.loadUInt(32).toLong(),
            )
        }

        fun fromCell(cell: Cell): HoldingStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): HoldingStore = fromCell(bocBase64.cellFromBase64())
    }
}

@Serializable
data class FollowingStore(
    val follower: String,
    val followee: String,
    val mintAmountRaw: String,
    val isFollowing: Boolean,
) {
    val mintAmount: BigInteger
        get() = mintAmountRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    companion object {
        fun fromSlice(s: CellSlice): FollowingStore {
            return FollowingStore(
                follower = s.loadStdAddress().toAccountId(),
                followee = s.loadStdAddress().toAccountId(),
                mintAmountRaw = s.loadCoinsBigInt().toString(),
                isFollowing = s.loadBit(),
            )
        }

        fun fromCell(cell: Cell): FollowingStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): FollowingStore = fromCell(bocBase64.cellFromBase64())
    }
}
