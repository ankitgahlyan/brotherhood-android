package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.TolkSliceUtils.loadAddressToUnitDict
import com.tonapps.brotherhood.store.TolkSliceUtils.loadCoinsBigInt
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadUIntBigInt
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import java.math.BigInteger

@Serializable
data class LotteryStorage(
    val owner: String,
    val version: Int,
    val entryAmountRaw: String,
    val participants: List<String>,
    val participantCount: Int,
    val revealDeadline: Long,
    val prizePoolRaw: String,
    val randomSeedHex: String,
) {
    val entryAmount: BigInteger
        get() = entryAmountRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    val prizePool: BigInteger
        get() = prizePoolRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    companion object {
        fun fromSlice(s: CellSlice): LotteryStorage {
            val owner = s.loadStdAddress().toAccountId()
            val version = s.loadUInt(10).toInt()
            val entryAmount = s.loadCoinsBigInt().toString()
            val participants = s.loadAddressToUnitDict().map { it.toAccountId() }
            val participantCount = s.loadInt(32).toInt()
            val revealDeadline = s.loadInt(32).toLong()
            val prizePool = s.loadCoinsBigInt().toString()
            val randomSeed = s.loadUIntBigInt(256).toString(16).padStart(64, '0')
            return LotteryStorage(
                owner = owner,
                version = version,
                entryAmountRaw = entryAmount,
                participants = participants,
                participantCount = participantCount,
                revealDeadline = revealDeadline,
                prizePoolRaw = prizePool,
                randomSeedHex = randomSeed,
            )
        }

        fun fromCell(cell: Cell): LotteryStorage = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): LotteryStorage = fromCell(bocBase64.cellFromBase64())
    }
}
