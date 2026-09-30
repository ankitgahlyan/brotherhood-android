package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.store.TolkSliceUtils.loadAddressToAddressDict
import com.tonapps.brotherhood.store.TolkSliceUtils.loadAddressToBoolDict
import com.tonapps.brotherhood.store.TolkSliceUtils.loadAddressToCoinsDict
import com.tonapps.brotherhood.store.TolkSliceUtils.loadAddressToUInt4Dict
import com.tonapps.brotherhood.store.TolkSliceUtils.loadCoinsBigInt
import com.tonapps.brotherhood.store.TolkSliceUtils.loadMaybeStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStringRefTail
import kotlinx.serialization.Serializable
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import java.math.BigInteger

enum class AccountStatus(val code: Int, val label: String) {
    GOOD_CITIZEN(0, "Good Citizen"),
    BLOCKED_BY_REPORTS(1, "Blocked by Reports"),
    BLOCKED_BY_AUTHORITY(2, "Blocked by Authority"),
    TERMINATED(3, "Terminated");

    companion object {
        fun fromCode(code: Int): AccountStatus {
            return entries.firstOrNull { it.code == code } ?: GOOD_CITIZEN
        }
    }
}

@Serializable
data class ProfileInfo(
    val username: String,
    val h3Cell: String,
    val country: Int,
) {
    companion object {
        fun fromSlice(s: CellSlice): ProfileInfo {
            return ProfileInfo(
                username = s.loadStringRefTail(),
                h3Cell = s.loadStringRefTail(),
                country = s.loadUInt(16).toInt(),
            )
        }
    }
}

@Serializable
data class TimeStamps(
    val accountInit: Long,
    val lastInvite: Long,
    val lastClaim: Long,
    val lastDecay: Long,
) {
    companion object {
        fun fromSlice(s: CellSlice): TimeStamps {
            return TimeStamps(
                accountInit = s.loadUInt(32).toLong(),
                lastInvite = s.loadUInt(32).toLong(),
                lastClaim = s.loadUInt(32).toLong(),
                lastDecay = s.loadUInt(32).toLong(),
            )
        }
    }
}

@Serializable
data class NomInAddrs(
    val nominee: String?,
    val invitor: String?,
    val invitor0: String?,
) {
    companion object {
        fun fromSlice(s: CellSlice): NomInAddrs {
            return NomInAddrs(
                nominee = s.loadMaybeStdAddress()?.toAccountId(),
                invitor = s.loadMaybeStdAddress()?.toAccountId(),
                invitor0 = s.loadMaybeStdAddress()?.toAccountId(),
            )
        }
    }
}

@Serializable
data class TrustedAddrs(
    val minterAddr: String,
    val personalJettonMinter: String,
    val personalJettonWallet: String,
    val authorisedAccs: Map<String, String>,
) {
    val hasPersonalMinter: Boolean
        get() = !BrotherhoodConfig.isZeroAddress(personalJettonMinter)

    val hasPersonalWallet: Boolean
        get() = !BrotherhoodConfig.isZeroAddress(personalJettonWallet)

    companion object {
        fun fromSlice(s: CellSlice): TrustedAddrs {
            val minterAddr = s.loadStdAddress().toAccountId()
            val personalJettonMinter = s.loadStdAddress().toAccountId()
            val personalJettonWallet = s.loadStdAddress().toAccountId()
            val authorisedAccs = s.loadAddressToAddressDict().entries.associate { (k, v) ->
                k.toAccountId() to v.toAccountId()
            }
            return TrustedAddrs(
                minterAddr = minterAddr,
                personalJettonMinter = personalJettonMinter,
                personalJettonWallet = personalJettonWallet,
                authorisedAccs = authorisedAccs,
            )
        }
    }
}

@Serializable
data class FiWalletAddresses(
    val owner: String,
    val nomInAddrs: NomInAddrs,
    val trustedJettonAddrs: TrustedAddrs,
) {
    companion object {
        fun fromSlice(s: CellSlice): FiWalletAddresses {
            return FiWalletAddresses(
                owner = s.loadStdAddress().toAccountId(),
                nomInAddrs = NomInAddrs.fromSlice(s.loadRef().beginParse()),
                trustedJettonAddrs = TrustedAddrs.fromSlice(s.loadRef().beginParse()),
            )
        }
    }
}

@Serializable
data class SocialMaps(
    val votedFor: Map<String, Int>,
    val followingCount: Long,
    val followersCount: Long,
) {
    companion object {
        fun fromSlice(s: CellSlice): SocialMaps {
            val votedFor = s.loadAddressToUInt4Dict().entries.associate { (k, v) ->
                k.toAccountId() to v
            }
            return SocialMaps(
                votedFor = votedFor,
                followingCount = s.loadUInt(32).toLong(),
                followersCount = s.loadUInt(32).toLong(),
            )
        }
    }
}

@Serializable
data class ReportInfo(
    val reports: Map<String, Boolean>,
    val tosBreach: Boolean,
    val reporterCount: Int,
    val disputerCount: Int,
    val reportResolutionTime: Long,
) {
    companion object {
        fun fromSlice(s: CellSlice): ReportInfo {
            val reports = s.loadAddressToBoolDict().entries.associate { (k, v) ->
                k.toAccountId() to v
            }
            return ReportInfo(
                reports = reports,
                tosBreach = s.loadBit(),
                reporterCount = s.loadUInt(10).toInt(),
                disputerCount = s.loadUInt(10).toInt(),
                reportResolutionTime = s.loadUInt(32).toLong(),
            )
        }
    }
}

@Serializable
data class FiWalletMaps(
    val invited: Map<String, String>,
    val allowances: Map<String, String>,
    val social: SocialMaps,
    val reportInfo: ReportInfo,
) {
    val invitedAddresses: List<String>
        get() = invited.keys.toList()

    companion object {
        fun fromSlice(s: CellSlice): FiWalletMaps {
            val invited = s.loadAddressToCoinsDict().entries.associate { (k, v) ->
                k.toAccountId() to v.toString()
            }
            val allowances = s.loadAddressToCoinsDict().entries.associate { (k, v) ->
                k.toAccountId() to v.toString()
            }
            return FiWalletMaps(
                invited = invited,
                allowances = allowances,
                social = SocialMaps.fromSlice(s.loadRef().beginParse()),
                reportInfo = ReportInfo.fromSlice(s.loadRef().beginParse()),
            )
        }
    }
}

@Serializable
data class FiWalletStore(
    val jettonBalanceRaw: String,
    val goldCoins: Long,
    val txnCount: Int,
    val status: Int,
    val isAuthorityAccount: Boolean,
    val isPrevilegedAccount: Boolean,
    val creditNeedRaw: String,
    val creditMaturity: Long,
    val multiplier: Int,
    val accumulatedFeesRaw: String,
    val debtRaw: String,
    val allowDeferred: Boolean,
    val votes: Int,
    val receivedVotes: Int,
    val connections: Int,
    val active: Boolean,
    val mintable: Boolean,
    val version: Int,
    val storeVersion: Int,
    val profile: ProfileInfo,
    val timestamps: TimeStamps,
    val addresses: FiWalletAddresses,
    val maps: FiWalletMaps,
) {
    val jettonBalance: BigInteger
        get() = jettonBalanceRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    val creditNeed: BigInteger
        get() = creditNeedRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    val accumulatedFees: BigInteger
        get() = accumulatedFeesRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    val debt: BigInteger
        get() = debtRaw.toBigIntegerOrNull() ?: BigInteger.ZERO

    val accountStatus: AccountStatus
        get() = AccountStatus.fromCode(status)

    val ownerAddrStd: AddrStd
        get() = AddrStd(addresses.owner)

    companion object {
        fun fromSlice(s: CellSlice): FiWalletStore {
            val jettonBalance = s.loadCoinsBigInt().toString()
            val goldCoins = s.loadUInt(32).toLong()
            val txnCount = s.loadUInt(8).toInt()
            val status = s.loadUInt(2).toInt()
            val isAuthorityAccount = s.loadBit()
            val isPrevilegedAccount = s.loadBit()
            val creditNeed = s.loadCoinsBigInt().toString()
            val creditMaturity = s.loadUInt(32).toLong()
            val multiplier = s.loadUInt(16).toInt()
            val accumulatedFees = s.loadCoinsBigInt().toString()
            val debt = s.loadCoinsBigInt().toString()
            val allowDeferred = s.loadBit()
            val votes = s.loadUInt(4).toInt()
            val receivedVotes = s.loadUInt(20).toInt()
            val connections = s.loadUInt(8).toInt()
            val active = s.loadBit()
            val mintable = s.loadBit()
            val version = s.loadUInt(10).toInt()
            val storeVersion = s.loadUInt(10).toInt()

            val profile = ProfileInfo.fromSlice(s.loadRef().beginParse())
            val timestamps = TimeStamps.fromSlice(s.loadRef().beginParse())
            val addresses = FiWalletAddresses.fromSlice(s.loadRef().beginParse())
            val maps = FiWalletMaps.fromSlice(s.loadRef().beginParse())

            return FiWalletStore(
                jettonBalanceRaw = jettonBalance,
                goldCoins = goldCoins,
                txnCount = txnCount,
                status = status,
                isAuthorityAccount = isAuthorityAccount,
                isPrevilegedAccount = isPrevilegedAccount,
                creditNeedRaw = creditNeed,
                creditMaturity = creditMaturity,
                multiplier = multiplier,
                accumulatedFeesRaw = accumulatedFees,
                debtRaw = debt,
                allowDeferred = allowDeferred,
                votes = votes,
                receivedVotes = receivedVotes,
                connections = connections,
                active = active,
                mintable = mintable,
                version = version,
                storeVersion = storeVersion,
                profile = profile,
                timestamps = timestamps,
                addresses = addresses,
                maps = maps,
            )
        }

        fun fromCell(cell: Cell): FiWalletStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): FiWalletStore = fromCell(bocBase64.cellFromBase64())
    }
}
