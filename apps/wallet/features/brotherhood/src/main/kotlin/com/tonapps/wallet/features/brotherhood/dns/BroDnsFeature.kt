package com.tonapps.wallet.features.brotherhood.dns

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.messages.BrotherhoodOpcodes
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.brotherhood.db.WatchedDnsDomainEntity
import com.tonapps.wallet.data.brotherhood.repo.BroDnsRepository
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.repo.InspectedBroDomain
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ton.bigint.toBigInt
import org.ton.cell.CellBuilder
import java.math.BigInteger

fun BroDnsRepository.buildRenewDomainIntent(domainName: String): BrotherhoodTransferIntent {
    val clean = normalizeDomainLabel(domainName)
    val init = ShardedAddressDerivation.deriveDnsItem(clean)
    val payload = CellBuilder.createCell {
        storeUInt(BrotherhoodOpcodes.FILL_UP.toBigInt(), 32)
        storeUInt(BrotherhoodMessages.defaultQueryId().toBigInt(), 64)
    }
    return BrotherhoodTransferIntent(
        title = "Renew $clean.bro (FillUp)",
        subtitle = "Extend .bro domain lease and top up DnsItem gas",
        messages = listOf(
            BrotherhoodOutgoingMessage(
                destination = init.address,
                amountNano = BigInteger.valueOf(BrotherhoodConfig.DNS_ACTION_GAS_NANO),
                payloadCell = payload,
            )
        ),
    )
}

fun BroDnsRepository.buildReleaseDomainIntent(domainName: String): BrotherhoodTransferIntent {
    val clean = normalizeDomainLabel(domainName)
    val init = ShardedAddressDerivation.deriveDnsItem(clean)
    val payload = CellBuilder.createCell {
        storeUInt(BrotherhoodOpcodes.DNS_RECORD_RELEASE.toBigInt(), 32)
        storeUInt(BrotherhoodMessages.defaultQueryId().toBigInt(), 64)
    }
    return BrotherhoodTransferIntent(
        title = "Release $clean.bro",
        subtitle = "Release .bro domain record (DnsRecordRelease)",
        messages = listOf(
            BrotherhoodOutgoingMessage(
                destination = init.address,
                amountNano = BigInteger.valueOf(BrotherhoodConfig.DNS_ACTION_GAS_NANO),
                payloadCell = payload,
            )
        ),
    )
}

class BroDnsFeature(
    private val broDnsRepository: BroDnsRepository,
    private val brotherhoodRepository: BrotherhoodRepository,
    private val sessionHolder: BrotherhoodWalletSessionHolder,
) : AsyncViewModel() {

    private val _searchQuery = MutableStateFlow(DEFAULT_DOMAIN_QUERY)
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _inspectedDomain = MutableStateFlow<InspectedBroDomain?>(null)
    val inspectedDomain: StateFlow<InspectedBroDomain?> = _inspectedDomain.asStateFlow()

    private val _watchedDomains = MutableStateFlow<List<WatchedDnsDomainEntity>>(emptyList())
    val watchedDomains: StateFlow<List<WatchedDnsDomainEntity>> = _watchedDomains.asStateFlow()

    private val _bidAmountInput = MutableStateFlow(DEFAULT_BID_FI)
    val bidAmountInput: StateFlow<String> = _bidAmountInput.asStateFlow()

    private val _walletRecordInput = MutableStateFlow("")
    val walletRecordInput: StateFlow<String> = _walletRecordInput.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingIntent = MutableStateFlow<BrotherhoodTransferIntent?>(null)
    val pendingIntent: StateFlow<BrotherhoodTransferIntent?> = _pendingIntent.asStateFlow()

    init {
        bgScope.launch {
            broDnsRepository.observeWatchedDomains().collectLatest { list ->
                _watchedDomains.value = list
            }
        }
        bgScope.launch {
            sessionHolder.walletAddressFlow.collectLatest { walletAddr ->
                if (!walletAddr.isNullOrBlank() && _walletRecordInput.value.isBlank()) {
                    _walletRecordInput.value = walletAddr
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateBidAmountInput(input: String) {
        _bidAmountInput.value = input
    }

    fun updateWalletRecordInput(input: String) {
        _walletRecordInput.value = input
    }

    fun consumePendingIntent() {
        _pendingIntent.value = null
    }

    fun searchDomain(forceRefresh: Boolean = true) {
        val rawQuery = _searchQuery.value.trim()
        if (rawQuery.isEmpty()) {
            _statusMessage.value = "Enter a .bro domain name to inspect"
            return
        }
        bgScope.launch {
            try {
                val userWallet = sessionHolder.walletAddressFlow.value
                val inspected = broDnsRepository.inspectDomain(
                    rawDomain = rawQuery,
                    userWalletAddress = userWallet,
                    forceRefresh = forceRefresh,
                )
                _inspectedDomain.value = inspected
                if (inspected == null) {
                    _statusMessage.value = "Invalid .bro domain label"
                } else {
                    val existingRecord = inspected.store?.walletRecordAddress
                    if (!existingRecord.isNullOrBlank()) {
                        _walletRecordInput.value = existingRecord
                    } else if (!userWallet.isNullOrBlank() && _walletRecordInput.value.isBlank()) {
                        _walletRecordInput.value = userWallet
                    }
                    _statusMessage.value = if (inspected.isDeployed) {
                        "Inspected ${inspected.fullDomain} • Active DnsItem (${BrotherhoodUiFormatters.shortAddress(inspected.dnsItemAddress)})"
                    } else {
                        "${inspected.fullDomain} is available to mint / bid (${BrotherhoodUiFormatters.shortAddress(inspected.dnsItemAddress)})"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to inspect .bro domain"
            }
        }
    }

    fun placeBidOrDeploy() {
        bgScope.launch {
            try {
                val domain = resolveActiveDomainLabel()
                val bidNano = BrotherhoodUiFormatters.parseDecimalToNano(
                    input = _bidAmountInput.value,
                    decimals = BrotherhoodConfig.FI_RAW_DECIMALS,
                ) ?: throw IllegalArgumentException("Enter a valid FI bid amount")
                val fiWalletAddress = resolveUserFiWalletAddress()
                val intent = broDnsRepository.buildDeployOrBidDomainIntent(
                    userFiWalletAddress = fiWalletAddress,
                    domainName = domain,
                    bidFiAmountRaw = bidNano,
                )
                _pendingIntent.value = intent
                _statusMessage.value = "Prepared: ${intent.title}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to build .bro bid intent"
            }
        }
    }

    fun finalizeAuction() {
        bgScope.launch {
            try {
                val domain = resolveActiveDomainLabel()
                val intent = broDnsRepository.buildFinalizeAuctionIntent(domain)
                _pendingIntent.value = intent
                _statusMessage.value = "Prepared: ${intent.title}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to build finalize auction intent"
            }
        }
    }

    fun renewDomain() {
        bgScope.launch {
            try {
                val domain = resolveActiveDomainLabel()
                val intent = broDnsRepository.buildRenewDomainIntent(domain)
                _pendingIntent.value = intent
                _statusMessage.value = "Prepared: ${intent.title}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to build renew domain intent"
            }
        }
    }

    fun releaseDomain() {
        bgScope.launch {
            try {
                val domain = resolveActiveDomainLabel()
                val intent = broDnsRepository.buildReleaseDomainIntent(domain)
                _pendingIntent.value = intent
                _statusMessage.value = "Prepared: ${intent.title}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to build release domain intent"
            }
        }
    }

    fun saveWalletRecord() {
        bgScope.launch {
            try {
                val domain = resolveActiveDomainLabel()
                val targetWallet = _walletRecordInput.value.trim().ifBlank { null }
                val intent = broDnsRepository.buildSetWalletRecordIntent(
                    domainName = domain,
                    targetWalletAddress = targetWallet,
                )
                _pendingIntent.value = intent
                _statusMessage.value = "Prepared: ${intent.title}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to build DNS wallet record intent"
            }
        }
    }

    private fun resolveActiveDomainLabel(): String {
        val current = _inspectedDomain.value?.domainName
            ?: broDnsRepository.normalizeDomainLabel(_searchQuery.value)
        if (current.isBlank()) {
            throw IllegalArgumentException("Enter or inspect a .bro domain name first")
        }
        return current
    }

    private fun resolveUserFiWalletAddress(): String {
        val cachedFiWallet = brotherhoodRepository.accountSnapshot.value?.derivedFiWalletAddressRaw
        if (!cachedFiWallet.isNullOrBlank()) {
            return cachedFiWallet
        }
        val ownerAddress = sessionHolder.walletAddressFlow.value
            ?: throw IllegalStateException("Connect or select a TON wallet first")
        return brotherhoodRepository.deriveFiWalletAddress(ownerAddress).toAccountId()
    }

    companion object {
        private const val DEFAULT_DOMAIN_QUERY = "satoshi"
        private const val DEFAULT_BID_FI = "100"
    }
}
