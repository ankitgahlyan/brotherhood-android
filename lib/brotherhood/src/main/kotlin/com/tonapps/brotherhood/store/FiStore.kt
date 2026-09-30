package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.base64
import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.TolkSliceUtils.loadCoinsBigInt
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStringRefTail
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import java.math.BigInteger

@Serializable
data class AdminHandoff(
    val newAdminAddress: String,
    val timestamp: Long,
) {
    companion object {
        fun fromSlice(s: CellSlice): AdminHandoff {
            return AdminHandoff(
                newAdminAddress = s.loadStdAddress().toAccountId(),
                timestamp = s.loadUInt(32).toLong(),
            )
        }
    }
}

@Serializable
data class CurrentRequest(
    val requester: String,
    val codeBocBase64: String?,
    val dataBocBase64: String?,
    val storeVersion: Int,
    val walletVersion: Int,
) {
    companion object {
        fun fromSlice(s: CellSlice): CurrentRequest {
            val requester = s.loadStdAddress().toAccountId()
            val codeBocBase64 = if (s.loadBit()) {
                s.loadRef().base64()
            } else {
                null
            }
            val dataBocBase64 = if (s.loadBit()) {
                s.loadRef().base64()
            } else {
                null
            }
            return CurrentRequest(
                requester = requester,
                codeBocBase64 = codeBocBase64,
                dataBocBase64 = dataBocBase64,
                storeVersion = s.loadUInt(10).toInt(),
                walletVersion = s.loadUInt(10).toInt(),
            )
        }
    }
}

@Serializable
data class FiCodes(
    val totalAccounts: Long,
    val lotteryCodeBocBase64: String,
    val latestFiWalletCodeBocBase64: String,
    val currentRequest: CurrentRequest?,
) {
    companion object {
        fun fromSlice(s: CellSlice): FiCodes {
            val totalAccounts = s.loadUInt(33).toLong()
            val lotteryCode = s.loadRef().base64()
            val latestFiWalletCode = s.loadRef().base64()
            val currentRequest = if (s.loadBit()) {
                CurrentRequest.fromSlice(s.loadRef().beginParse())
            } else {
                null
            }
            return FiCodes(
                totalAccounts = totalAccounts,
                lotteryCodeBocBase64 = lotteryCode,
                latestFiWalletCodeBocBase64 = latestFiWalletCode,
                currentRequest = currentRequest,
            )
        }
    }
}

@Serializable
data class FiStore(
    val totalSupplyRaw: String,
    val offChainRulesHash: String,
    val walletVersion: Int,
    val adminAddress: String,
    val daoAddress: String,
    val adminHandoff: AdminHandoff?,
    val metadataBocBase64: String,
    val parsedMetadata: JettonMetadataContent?,
    val others: FiCodes,
) {
    val totalSupply: BigInteger
        get() = totalSupplyRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    val totalAccounts: Long
        get() = others.totalAccounts

    companion object {
        fun fromSlice(s: CellSlice): FiStore {
            val totalSupply = s.loadCoinsBigInt().toString()
            val offChainRulesHash = s.loadStringRefTail()
            val walletVersion = s.loadUInt(10).toInt()
            val adminAddress = s.loadStdAddress().toAccountId()
            val daoAddress = s.loadStdAddress().toAccountId()
            val adminHandoff = if (s.loadBit()) {
                AdminHandoff.fromSlice(s.loadRef().beginParse())
            } else {
                null
            }
            val metadataCell = s.loadRef()
            val others = FiCodes.fromSlice(s.loadRef().beginParse())

            return FiStore(
                totalSupplyRaw = totalSupply,
                offChainRulesHash = offChainRulesHash,
                walletVersion = walletVersion,
                adminAddress = adminAddress,
                daoAddress = daoAddress,
                adminHandoff = adminHandoff,
                metadataBocBase64 = metadataCell.base64(),
                parsedMetadata = runCatching { JettonContentCodec.parseJettonContent(metadataCell) }.getOrNull(),
                others = others,
            )
        }

        fun fromCell(cell: Cell): FiStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): FiStore = fromCell(bocBase64.cellFromBase64())
    }
}
