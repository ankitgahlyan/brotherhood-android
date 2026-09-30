package com.tonapps.wallet.data.brotherhood.repo

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.DnsItemStore
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.db.WatchedDnsDomainEntity
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.Flow
import org.ton.block.AddrStd
import java.math.BigInteger

data class InspectedBroDomain(
    val domainName: String,
    val fullDomain: String,
    val dnsItemAddress: String,
    val isDeployed: Boolean,
    val store: DnsItemStore?,
    val isOwnedByUser: Boolean,
)

class BroDnsRepository(
    private val dao: BrotherhoodDao,
    private val hydrator: AccountStateHydrator,
) {
    fun observeWatchedDomains(): Flow<List<WatchedDnsDomainEntity>> {
        return dao.observeWatchedDnsDomains()
    }

    fun normalizeDomainLabel(raw: String): String {
        return raw.trim().lowercase().removeSuffix(".bro").trim()
    }

    suspend fun inspectDomain(
        rawDomain: String,
        userWalletAddress: String? = null,
        forceRefresh: Boolean = true,
    ): InspectedBroDomain? {
        val clean = normalizeDomainLabel(rawDomain)
        if (clean.isEmpty()) {
            return null
        }
        val init = ShardedAddressDerivation.deriveDnsItem(clean)
        val dnsRaw = init.address.toAccountId()
        val state = hydrator.hydrateAddress(
            address = dnsRaw,
            explicitType = KnownContractType.DNS_ITEM,
            forceRefresh = forceRefresh,
        )
        val store = (state?.decodedStore as? DecodedContractStore.DnsItem)?.store
        val normalizedUser = userWalletAddress?.let { BrotherhoodConfig.normalizeAddress(it) }
        val isOwned = normalizedUser != null &&
            !store?.ownerAddress.isNullOrBlank() &&
            BrotherhoodConfig.normalizeAddress(store.ownerAddress) == normalizedUser

        val entity = WatchedDnsDomainEntity(
            domainName = clean,
            dnsItemAddress = dnsRaw,
            ownerAddress = store?.ownerAddress,
            resolvedWalletAddress = store?.walletRecordAddress ?: store?.ownerAddress,
            maxBidAmountRaw = store?.auction?.maxBidAmountRaw ?: "0",
            maxBidAddress = store?.auction?.maxBidAddress,
            auctionEndTime = store?.auction?.auctionEndTime ?: 0L,
            lastFillUpTime = store?.lastFillUpTime ?: 0L,
            isOwnedByUser = isOwned,
            updatedAtMs = System.currentTimeMillis(),
        )
        dao.upsertWatchedDnsDomain(entity)

        return InspectedBroDomain(
            domainName = clean,
            fullDomain = "$clean.bro",
            dnsItemAddress = dnsRaw,
            isDeployed = state?.isActive == true && store != null,
            store = store,
            isOwnedByUser = isOwned,
        )
    }

    /**
     * Resolves a `*.bro` domain to its wallet record (or owner address fallback) for the Send flow.
     */
    suspend fun resolveBroDomainToWallet(
        domainInput: String,
        userWalletAddress: String? = null,
    ): String? {
        val trimmed = domainInput.trim().lowercase()
        if (!trimmed.endsWith(".bro")) {
            return null
        }
        val inspected = inspectDomain(trimmed, userWalletAddress, forceRefresh = false) ?: return null
        return inspected.store?.walletRecordAddress ?: inspected.store?.ownerAddress
    }

    fun buildDeployOrBidDomainIntent(
        userFiWalletAddress: String,
        domainName: String,
        bidFiAmountRaw: BigInteger,
    ): BrotherhoodTransferIntent {
        val clean = normalizeDomainLabel(domainName)
        val forwardCell = BrotherhoodMessages.buildBidBroDomainForwardPayload(clean)
        val payload = BrotherhoodMessages.buildAskToTransfer(
            jettonAmountRaw = bidFiAmountRaw,
            transferRecipient = BrotherhoodConfig.BRO_TREASURY_ADDRESS,
            forwardTonAmountNano = BigInteger.valueOf(50_000_000L),
            forwardPayload = forwardCell,
        )
        return BrotherhoodTransferIntent(
            title = "Bid on $clean.bro",
            subtitle = "BrotherHood .bro decentralized domain auction",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(userFiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DNS_ACTION_GAS_NANO),
                    payloadCell = payload,
                )
            ),
        )
    }

    fun buildSetWalletRecordIntent(
        domainName: String,
        targetWalletAddress: String?,
    ): BrotherhoodTransferIntent {
        val clean = normalizeDomainLabel(domainName)
        val init = ShardedAddressDerivation.deriveDnsItem(clean)
        val targetAddr = targetWalletAddress?.takeIf { !BrotherhoodConfig.isZeroAddress(it) }?.let { AddrStd(it) }
        return BrotherhoodTransferIntent(
            title = "Set Wallet Record ($clean.bro)",
            subtitle = targetWalletAddress ?: "Clear wallet record",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = init.address,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DNS_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildChangeDnsWalletRecord(targetAddr),
                )
            ),
        )
    }

    fun buildFinalizeAuctionIntent(domainName: String): BrotherhoodTransferIntent {
        val clean = normalizeDomainLabel(domainName)
        val init = ShardedAddressDerivation.deriveDnsItem(clean)
        return BrotherhoodTransferIntent(
            title = "Finalize $clean.bro Auction",
            subtitle = "Complete domain auction on-chain",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = init.address,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DNS_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildFinalizeAuction(),
                )
            ),
        )
    }
}
