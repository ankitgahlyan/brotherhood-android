package com.tonapps.wallet.features.brotherhood.personal

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.brotherhood.db.TrackedPersonalTokenEntity
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.repo.MutualCreditSwapQuote
import com.tonapps.wallet.data.brotherhood.repo.PersonalJettonRepository
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ton.block.AddrStd
import java.math.BigInteger

enum class PersonalTokenSubTab(val id: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    CREATE("create", "Create"),
    MINT("mint", "Mint"),
    CREDIT("credit", "Mutual Credit Swap"),
    TRANSFER("transfer", "Transfer"),
    BURN("burn", "Burn"),
    ADMIN("admin", "Admin"),
}

class PersonalTokenFeature(
    private val personalJettonRepository: PersonalJettonRepository,
    private val brotherhoodRepository: BrotherhoodRepository,
    private val sessionHolder: BrotherhoodWalletSessionHolder,
) : AsyncViewModel() {

    private val _selectedSubTab = MutableStateFlow(PersonalTokenSubTab.OVERVIEW)
    val selectedSubTab: StateFlow<PersonalTokenSubTab> = _selectedSubTab.asStateFlow()

    private val _trackedTokens = MutableStateFlow<List<TrackedPersonalTokenEntity>>(emptyList())
    val trackedTokens: StateFlow<List<TrackedPersonalTokenEntity>> = _trackedTokens.asStateFlow()

    private val _selectedToken = MutableStateFlow<TrackedPersonalTokenEntity?>(null)
    val selectedToken: StateFlow<TrackedPersonalTokenEntity?> = _selectedToken.asStateFlow()

    private val _isBuyCreditDirection = MutableStateFlow(true)
    val isBuyCreditDirection: StateFlow<Boolean> = _isBuyCreditDirection.asStateFlow()

    private val _swapAmountInput = MutableStateFlow("10")
    val swapAmountInput: StateFlow<String> = _swapAmountInput.asStateFlow()

    private val _swapQuote = MutableStateFlow<MutualCreditSwapQuote?>(null)
    val swapQuote: StateFlow<MutualCreditSwapQuote?> = _swapQuote.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingIntent = MutableStateFlow<BrotherhoodTransferIntent?>(null)
    val pendingIntent: StateFlow<BrotherhoodTransferIntent?> = _pendingIntent.asStateFlow()

    val subTabPairs: List<Pair<String, String>> = PersonalTokenSubTab.entries.map {
        it.id to it.label
    }

    init {
        bgScope.launch {
            personalJettonRepository.observeTrackedPersonalTokens().collectLatest { list ->
                _trackedTokens.value = list
                val current = _selectedToken.value
                if (current == null || list.none { it.minterAddress == current.minterAddress }) {
                    val defaultToken = list.firstOrNull { it.isAdminReferenceStablecoin }
                        ?: list.firstOrNull()
                    _selectedToken.value = defaultToken
                } else {
                    _selectedToken.value = list.firstOrNull { it.minterAddress == current.minterAddress }
                }
                recomputeSwapQuote()
            }
        }
        bgScope.launch {
            sessionHolder.walletAddressFlow.collectLatest { walletAddr ->
                if (!walletAddr.isNullOrBlank()) {
                    refreshPersonalTokens(forceRefresh = false)
                }
            }
        }
    }

    fun selectSubTab(subTabId: String) {
        val target = PersonalTokenSubTab.entries.firstOrNull { it.id == subTabId } ?: return
        _selectedSubTab.value = target
    }

    fun selectToken(minterAddress: String) {
        val found = _trackedTokens.value.firstOrNull { it.minterAddress == minterAddress } ?: return
        _selectedToken.value = found
        recomputeSwapQuote()
    }

    fun toggleSwapDirection() {
        _isBuyCreditDirection.value = !_isBuyCreditDirection.value
        recomputeSwapQuote()
    }

    fun updateSwapAmountInput(input: String) {
        _swapAmountInput.value = input
        recomputeSwapQuote()
    }

    fun refreshPersonalTokens(forceRefresh: Boolean = true) {
        val walletAddress = sessionHolder.walletAddressFlow.value
        if (walletAddress.isNullOrBlank()) {
            return
        }
        bgScope.launch {
            runCatching {
                personalJettonRepository.syncPersonalTokens(
                    userWalletAddress = walletAddress,
                    forceRefresh = forceRefresh,
                )
            }.onSuccess { list ->
                if (_selectedToken.value == null) {
                    _selectedToken.value = list.firstOrNull { it.isAdminReferenceStablecoin }
                        ?: list.firstOrNull()
                }
                recomputeSwapQuote()
            }.onFailure { err ->
                _statusMessage.value = "Sync failed: ${err.message}"
            }
        }
    }

    fun addCustomMinter(minterAddressInput: String) {
        val walletAddress = sessionHolder.walletAddressFlow.value
        if (walletAddress.isNullOrBlank() || minterAddressInput.isBlank()) {
            return
        }
        bgScope.launch {
            val added = personalJettonRepository.addCustomPersonalTokenByMinter(
                userWalletAddress = walletAddress,
                minterAddress = minterAddressInput.trim(),
            )
            if (added != null) {
                _selectedToken.value = added
                _statusMessage.value = "Added ${added.name} (${added.symbol})"
                recomputeSwapQuote()
            } else {
                _statusMessage.value = "Invalid PersonalMinter address"
            }
        }
    }

    fun executeMutualCreditSwap() {
        recomputeSwapQuote()
        val quote = _swapQuote.value
        if (quote == null || quote.inputAmountRaw <= BigInteger.ZERO) {
            _statusMessage.value = "Enter a valid swap amount"
            return
        }
        _pendingIntent.value = personalJettonRepository.buildMutualCreditSwapIntent(quote)
    }

    fun createPersonalToken(
        name: String,
        symbol: String,
        description: String,
        imageUrl: String,
    ) {
        val walletAddress = sessionHolder.walletAddressFlow.value
        if (walletAddress.isNullOrBlank() || name.isBlank() || symbol.isBlank()) {
            _statusMessage.value = "Name and Symbol are required"
            return
        }
        _pendingIntent.value = personalJettonRepository.buildDeployPersonalTokenIntent(
            ownerWalletAddress = walletAddress,
            name = name,
            symbol = symbol,
            description = description,
            imageUrl = imageUrl,
        )
    }

    fun mintPersonalToken(recipientAddress: String, amountInput: String) {
        val token = _selectedToken.value
        val walletAddress = sessionHolder.walletAddressFlow.value
        if (token == null || walletAddress.isNullOrBlank()) {
            _statusMessage.value = "Select a Personal Token first"
            return
        }
        val targetRecipient = recipientAddress.trim().ifBlank { walletAddress }
        val amountRaw = BrotherhoodUiFormatters.parseDecimalToNano(
            input = amountInput,
            decimals = BrotherhoodConfig.PERSONAL_TOKEN_DECIMALS,
        )
        if (amountRaw == null || amountRaw <= BigInteger.ZERO) {
            _statusMessage.value = "Enter a valid mint amount"
            return
        }
        _pendingIntent.value = personalJettonRepository.buildMintPersonalTokenIntent(
            personalMinterAddress = token.minterAddress,
            recipientWalletAddress = targetRecipient,
            amountRaw = amountRaw,
        )
    }

    fun transferPersonalToken(recipientInput: String, amountInput: String, comment: String) {
        val token = _selectedToken.value
        val walletAddress = sessionHolder.walletAddressFlow.value
        if (token == null || walletAddress.isNullOrBlank() || recipientInput.isBlank()) {
            _statusMessage.value = "Recipient and token are required"
            return
        }
        val amountRaw = BrotherhoodUiFormatters.parseDecimalToNano(
            input = amountInput,
            decimals = BrotherhoodConfig.PERSONAL_TOKEN_DECIMALS,
        )
        if (amountRaw == null || amountRaw <= BigInteger.ZERO) {
            _statusMessage.value = "Enter a valid transfer amount"
            return
        }
        bgScope.launch {
            val resolvedRecipient = brotherhoodRepository
                .resolveUsernameToOwnerOrFiWallet(recipientInput.trim())
                ?.ownerAddress
                ?: recipientInput.trim()
            _pendingIntent.value = personalJettonRepository.buildTransferPersonalTokenIntent(
                senderPersonalWalletAddress = token.userPersonalWalletAddress,
                recipientOwnerAddress = resolvedRecipient,
                senderOwnerAddress = walletAddress,
                amountRaw = amountRaw,
                comment = comment.trim().ifBlank { null },
            )
        }
    }

    fun burnPersonalToken(amountInput: String) {
        val token = _selectedToken.value
        val walletAddress = sessionHolder.walletAddressFlow.value
        if (token == null || walletAddress.isNullOrBlank()) {
            _statusMessage.value = "Select a Personal Token first"
            return
        }
        val amountRaw = BrotherhoodUiFormatters.parseDecimalToNano(
            input = amountInput,
            decimals = BrotherhoodConfig.PERSONAL_TOKEN_DECIMALS,
        )
        if (amountRaw == null || amountRaw <= BigInteger.ZERO) {
            _statusMessage.value = "Enter a valid burn amount"
            return
        }
        val userFiWallet = ShardedAddressDerivation.deriveFiWallet(AddrStd(walletAddress)).address
        _pendingIntent.value = BrotherhoodTransferIntent(
            title = "Burn ${token.symbol}",
            subtitle = "Redeem Personal Token credit back to FI/HD",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(token.userPersonalWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.CREDIT_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildAskToBurn(
                        jettonAmountRaw = amountRaw,
                        sendExcessesTo = userFiWallet,
                    ),
                )
            ),
        )
    }

    fun updateMetadata(
        name: String,
        symbol: String,
        description: String,
        imageUrl: String,
    ) {
        val token = _selectedToken.value ?: return
        if (name.isBlank() || symbol.isBlank()) {
            _statusMessage.value = "Name and Symbol are required"
            return
        }
        _pendingIntent.value = personalJettonRepository.buildUpdatePersonalMetadataIntent(
            personalMinterAddress = token.minterAddress,
            name = name,
            symbol = symbol,
            description = description,
            imageUrl = imageUrl,
        )
    }

    fun consumePendingIntent() {
        _pendingIntent.value = null
    }

    fun dismissStatusMessage() {
        _statusMessage.value = null
    }

    private fun recomputeSwapQuote() {
        val walletAddress = sessionHolder.walletAddressFlow.value
        val token = _selectedToken.value ?: _trackedTokens.value.firstOrNull { it.isAdminReferenceStablecoin }
            ?: _trackedTokens.value.firstOrNull()
        if (walletAddress.isNullOrBlank() || token == null) {
            _swapQuote.value = null
            return
        }
        val isBuy = _isBuyCreditDirection.value
        val decimals = if (isBuy) {
            BrotherhoodConfig.FI_RAW_DECIMALS
        } else {
            BrotherhoodConfig.PERSONAL_TOKEN_DECIMALS
        }
        val inputRaw = BrotherhoodUiFormatters.parseDecimalToNano(_swapAmountInput.value, decimals)
            ?: BigInteger.ZERO
        val userFiWallet = runCatching {
            ShardedAddressDerivation.deriveFiWallet(AddrStd(walletAddress)).address.toAccountId()
        }.getOrDefault("")
        if (userFiWallet.isBlank()) {
            _swapQuote.value = null
            return
        }
        _swapQuote.value = personalJettonRepository.computeMutualCreditQuote(
            isBuyCredit = isBuy,
            inputAmountRaw = inputRaw,
            personalToken = token,
            userFiWalletAddress = userFiWallet,
        )
    }
}
