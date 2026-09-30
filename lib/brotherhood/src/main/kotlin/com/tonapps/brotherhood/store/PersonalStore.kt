package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.base64
import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.TolkSliceUtils.loadCoinsBigInt
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import java.math.BigInteger

@Serializable
data class PersonalStore(
    val totalSupplyRaw: String,
    val fiJettonAddress: String,
    val adminAddress: String,
    val metadataUriBocBase64: String?,
    val parsedMetadata: JettonMetadataContent?,
    val version: Int,
    val latestPersonalWalletCodeBocBase64: String,
) {
    val totalSupply: BigInteger
        get() = totalSupplyRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    companion object {
        fun fromSlice(s: CellSlice): PersonalStore {
            val totalSupply = s.loadCoinsBigInt().toString()
            val fiJettonAddress = s.loadStdAddress().toAccountId()
            val adminAddress = s.loadStdAddress().toAccountId()
            val metadataCell = if (s.loadBit()) {
                s.loadRef()
            } else {
                null
            }
            val version = s.loadUInt(10).toInt()
            val latestPersonalWalletCode = s.loadRef().base64()

            return PersonalStore(
                totalSupplyRaw = totalSupply,
                fiJettonAddress = fiJettonAddress,
                adminAddress = adminAddress,
                metadataUriBocBase64 = metadataCell?.base64(),
                parsedMetadata = metadataCell?.let {
                    runCatching { JettonContentCodec.parseJettonContent(it) }.getOrNull()
                },
                version = version,
                latestPersonalWalletCodeBocBase64 = latestPersonalWalletCode,
            )
        }

        fun fromCell(cell: Cell): PersonalStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): PersonalStore = fromCell(bocBase64.cellFromBase64())
    }
}

@Serializable
data class PersonalWalletStore(
    val jettonBalanceRaw: String,
    val owner: String,
    val deployer: String,
    val minterAddress: String,
    val version: Int,
) {
    val jettonBalance: BigInteger
        get() = jettonBalanceRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    companion object {
        fun fromSlice(s: CellSlice): PersonalWalletStore {
            return PersonalWalletStore(
                jettonBalanceRaw = s.loadCoinsBigInt().toString(),
                owner = s.loadStdAddress().toAccountId(),
                deployer = s.loadStdAddress().toAccountId(),
                minterAddress = s.loadStdAddress().toAccountId(),
                version = s.loadUInt(10).toInt(),
            )
        }

        fun fromCell(cell: Cell): PersonalWalletStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): PersonalWalletStore = fromCell(bocBase64.cellFromBase64())
    }
}
