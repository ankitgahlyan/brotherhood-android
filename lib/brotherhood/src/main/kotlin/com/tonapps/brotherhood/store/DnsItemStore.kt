package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.TolkSliceUtils.loadCoinsBigInt
import com.tonapps.brotherhood.store.TolkSliceUtils.loadMaybeStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStringTail
import com.tonapps.brotherhood.store.TolkSliceUtils.loadTonDict
import com.tonapps.brotherhood.store.TolkSliceUtils.loadUIntBigInt
import com.tonapps.brotherhood.store.TolkSliceUtils.parseUInt256Key
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import java.math.BigInteger
import java.security.MessageDigest

@Serializable
data class AuctionState(
    val maxBidAddress: String?,
    val maxBidAmountRaw: String,
    val auctionEndTime: Long,
) {
    val maxBidAmount: BigInteger
        get() = maxBidAmountRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    companion object {
        fun fromSlice(s: CellSlice): AuctionState {
            return AuctionState(
                maxBidAddress = s.loadMaybeStdAddress()?.toAccountId(),
                maxBidAmountRaw = s.loadCoinsBigInt().toString(),
                auctionEndTime = s.loadUIntBigInt(64).toLong(),
            )
        }
    }
}

@Serializable
data class DnsItemStore(
    val isInitialized: Boolean,
    val indexHex: String,
    val collectionAddress: String,
    val ownerAddress: String? = null,
    val domain: String? = null,
    val walletRecordAddress: String? = null,
    val auction: AuctionState? = null,
    val lastFillUpTime: Long = 0L,
    val fiMinterAddress: String? = null,
) {
    companion object {
        private val WALLET_CATEGORY_KEY: BigInteger by lazy {
            val digest = MessageDigest.getInstance("SHA-256").digest("wallet".toByteArray(Charsets.UTF_8))
            BigInteger(1, digest)
        }

        fun fromSlice(s: CellSlice): DnsItemStore {
            val indexHex = s.loadUIntBigInt(256).toString(16).padStart(64, '0')
            val collectionAddress = s.loadStdAddress().toAccountId()

            if (s.bits.size - s.bitsPosition == 0 && s.refs.size - s.refsPosition == 0) {
                return DnsItemStore(
                    isInitialized = false,
                    indexHex = indexHex,
                    collectionAddress = collectionAddress,
                )
            }

            val ownerAddress = s.loadMaybeStdAddress()?.toAccountId()
            val contentCell = s.loadRef()
            val domainCell = s.loadRef()
            val domain = runCatching { domainCell.beginParse().loadStringTail() }.getOrNull()
            val walletRecordAddress = runCatching { parseWalletDnsRecord(contentCell) }.getOrNull()

            val auction = if (s.loadBit()) {
                AuctionState.fromSlice(s.loadRef().beginParse())
            } else {
                null
            }
            val lastFillUpTime = s.loadUIntBigInt(64).toLong()
            val fiMinterAddress = if (s.bitsPosition < s.bits.size && s.loadBit()) {
                s.loadRef().beginParse().loadMaybeStdAddress()?.toAccountId()
            } else {
                null
            }

            return DnsItemStore(
                isInitialized = true,
                indexHex = indexHex,
                collectionAddress = collectionAddress,
                ownerAddress = ownerAddress,
                domain = domain,
                walletRecordAddress = walletRecordAddress,
                auction = auction,
                lastFillUpTime = lastFillUpTime,
                fiMinterAddress = fiMinterAddress,
            )
        }

        private fun parseWalletDnsRecord(contentCell: Cell): String? {
            val contentSlice = contentCell.beginParse()
            val records = contentSlice.loadTonDict(256, ::parseUInt256Key) { valueSlice ->
                if (valueSlice.refsPosition < valueSlice.refs.size) {
                    valueSlice.loadRef()
                } else {
                    null
                }
            }
            val walletCell = records[WALLET_CATEGORY_KEY] ?: return null
            val rs = walletCell.beginParse()
            if (rs.bits.size >= 16) {
                val prefix = rs.loadUInt(16).toInt()
                // 0x9fd3 is standard TEP-81 dns_smc_address prefix
                if (prefix == 0x9fd3) {
                    return rs.loadStdAddress().toAccountId()
                }
            }
            return null
        }

        fun fromCell(cell: Cell): DnsItemStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): DnsItemStore = fromCell(bocBase64.cellFromBase64())
    }
}
