package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.base64
import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadUIntBigInt
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellSlice

@Serializable
data class PollAddresses(
    val proposerOwner: String,
    val daoProxyAddress: String,
    val fiAddress: String,
) {
    companion object {
        fun fromSlice(s: CellSlice): PollAddresses {
            return PollAddresses(
                proposerOwner = s.loadStdAddress().toAccountId(),
                daoProxyAddress = s.loadStdAddress().toAccountId(),
                fiAddress = s.loadStdAddress().toAccountId(),
            )
        }
    }
}

@Serializable
data class PollStore(
    val proposalId: Long,
    val addresses: PollAddresses,
    val targetMsgBocBase64: String,
    val targetOpcode: Long?,
    val yesVotes: Long,
    val noVotes: Long,
    val totalAccounts: Long,
    val expiresAt: Long,
    val executed: Boolean,
) {
    companion object {
        fun fromSlice(s: CellSlice): PollStore {
            val proposalId = s.loadUIntBigInt(64).toLong()
            val addresses = PollAddresses.fromSlice(s.loadRef().beginParse())
            val targetMsgCell = s.loadRef()
            val targetOpcode = runCatching {
                val targetSlice = targetMsgCell.beginParse()
                if (targetSlice.bits.size >= 32) {
                    targetSlice.loadUInt(32).toLong() and 0xFFFFFFFFL
                } else {
                    null
                }
            }.getOrNull()
            val yesVotes = s.loadUInt(33).toLong()
            val noVotes = s.loadUInt(33).toLong()
            val totalAccounts = s.loadUInt(33).toLong()
            val expiresAt = s.loadUInt(32).toLong()
            val executed = s.loadBit()

            return PollStore(
                proposalId = proposalId,
                addresses = addresses,
                targetMsgBocBase64 = targetMsgCell.base64(),
                targetOpcode = targetOpcode,
                yesVotes = yesVotes,
                noVotes = noVotes,
                totalAccounts = totalAccounts,
                expiresAt = expiresAt,
                executed = executed,
            )
        }

        fun fromCell(cell: Cell): PollStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): PollStore = fromCell(bocBase64.cellFromBase64())
    }
}

@Serializable
data class VoterStore(
    val voterOwner: String,
    val pollAddress: String,
    val voted: Boolean,
    val vote: Boolean,
) {
    companion object {
        fun fromSlice(s: CellSlice): VoterStore {
            return VoterStore(
                voterOwner = s.loadStdAddress().toAccountId(),
                pollAddress = s.loadStdAddress().toAccountId(),
                voted = s.loadBit(),
                vote = s.loadBit(),
            )
        }

        fun fromCell(cell: Cell): VoterStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): VoterStore = fromCell(bocBase64.cellFromBase64())
    }
}
