@file:Suppress("MagicNumber")

package com.tonapps.wallet.features.brotherhood.dao

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.messages.BrotherhoodOpcodes
import com.tonapps.brotherhood.store.FiStore
import com.tonapps.brotherhood.store.PollAddresses
import com.tonapps.brotherhood.store.PollStore
import com.tonapps.brotherhood.store.TolkSliceUtils.base64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.VoterStore
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.repo.DaoRepository
import com.tonapps.wallet.data.brotherhood.repo.HydratedDaoPoll
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ton.block.AddrStd
import org.ton.cell.CellBuilder
import java.math.BigInteger

class DaoFeature(
    private val daoRepository: DaoRepository,
    private val brotherhoodRepository: BrotherhoodRepository,
    private val sessionHolder: BrotherhoodWalletSessionHolder,
) : AsyncViewModel() {

    private val _subTab = MutableStateFlow(SUB_TAB_PROPOSALS)
    val subTab: StateFlow<String> = _subTab.asStateFlow()

    private val _polls = MutableStateFlow<List<HydratedDaoPoll>>(emptyList())
    val polls: StateFlow<List<HydratedDaoPoll>> = _polls.asStateFlow()

    private val _inspectedPoll = MutableStateFlow<HydratedDaoPoll?>(null)
    val inspectedPoll: StateFlow<HydratedDaoPoll?> = _inspectedPoll.asStateFlow()

    private val _fiMinterStore = MutableStateFlow<FiStore?>(null)
    val fiMinterStore: StateFlow<FiStore?> = _fiMinterStore.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingIntent = MutableStateFlow<BrotherhoodTransferIntent?>(null)
    val pendingIntent: StateFlow<BrotherhoodTransferIntent?> = _pendingIntent.asStateFlow()

    init {
        bgScope.launch {
            brotherhoodRepository.accountSnapshot.collectLatest { snapshot ->
                if (snapshot?.fiMinterStore != null) {
                    _fiMinterStore.value = snapshot.fiMinterStore
                }
            }
        }
        refreshDao(forceRefresh = false)
    }

    fun selectSubTab(tab: String) {
        _subTab.value = tab
    }

    fun inspectPoll(pollId: Long) {
        val found = _polls.value.firstOrNull { item -> item.pollStore.proposalId == pollId }
        if (found != null) {
            _inspectedPoll.value = found
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun consumePendingIntent() {
        _pendingIntent.value = null
    }

    fun refreshDao(forceRefresh: Boolean = false) {
        bgScope.launch {
            runCatching {
                val userFiWallet = resolveActiveFiWalletAddress()
                val (minterStore, hydratedPolls) = daoRepository.syncDaoGovernance(
                    userFiWalletAddress = userFiWallet,
                    forceRefresh = forceRefresh,
                )
                if (minterStore != null) {
                    _fiMinterStore.value = minterStore
                }
                _polls.value = hydratedPolls
                val currentInspectedId = _inspectedPoll.value?.pollStore?.proposalId
                _inspectedPoll.value = if (currentInspectedId != null) {
                    hydratedPolls.firstOrNull { item -> item.pollStore.proposalId == currentInspectedId }
                        ?: hydratedPolls.firstOrNull()
                } else {
                    hydratedPolls.firstOrNull()
                }
                if (forceRefresh) {
                    _statusMessage.value = "Synchronized ${hydratedPolls.size} sharded DAO Poll contracts."
                }
            }.onFailure { error ->
                verifyError(error)
                _statusMessage.value = "Failed to sync DAO governance: ${error.message ?: "Network error"}"
            }
        }
    }

    fun createProposal(
        pollId: Long,
        description: String,
        recipientAddress: String,
        amountTonStr: String,
    ) {
        val cleanDesc = description.trim()
        if (cleanDesc.isEmpty()) {
            _statusMessage.value = "Proposal description is required."
            return
        }
        val fiWalletAddress = resolveActiveFiWalletAddress()
        if (fiWalletAddress == null) {
            _statusMessage.value = "Connect an active TON wallet before submitting a DAO proposal."
            return
        }
        val recipientAddr = runCatching {
            if (recipientAddress.isNotBlank()) {
                AddrStd(recipientAddress.trim())
            } else {
                AddrStd(fiWalletAddress)
            }
        }.getOrNull()
        if (recipientAddr == null) {
            _statusMessage.value = "Invalid recipient TON address."
            return
        }

        val amountNano = BrotherhoodUiFormatters.parseDecimalToNano(amountTonStr)
            ?: BigInteger.valueOf(BrotherhoodConfig.ONE_TON_NANO)
        val targetMsgCell = BrotherhoodMessages.buildAskToTransfer(
            jettonAmountRaw = amountNano,
            transferRecipient = recipientAddr,
            comment = cleanDesc,
        )
        val intent = daoRepository.buildSubmitProposalIntent(
            fiWalletAddress = fiWalletAddress,
            targetMsgCell = targetMsgCell,
        )

        val effectivePollId = if (pollId > 0L) {
            pollId
        } else {
            (_polls.value.maxOfOrNull { item -> item.pollStore.proposalId } ?: 0L) + 1L
        }
        val derivedPollAddr = ShardedAddressDerivation.derivePoll(effectivePollId).address.toAccountId()
        val optimisticPoll = HydratedDaoPoll(
            pollAddress = derivedPollAddr,
            pollStore = PollStore(
                proposalId = effectivePollId,
                addresses = PollAddresses(
                    proposerOwner = fiWalletAddress,
                    daoProxyAddress = BrotherhoodConfig.DAO_PROXY_ADDRESS.toAccountId(),
                    fiAddress = BrotherhoodConfig.FI_ADDRESS_RAW,
                ),
                targetMsgBocBase64 = targetMsgCell.base64(),
                targetOpcode = BrotherhoodOpcodes.ASK_TO_TRANSFER,
                yesVotes = 1L,
                noVotes = 0L,
                totalAccounts = _fiMinterStore.value?.totalAccounts ?: 1L,
                expiresAt = (System.currentTimeMillis() / 1000L) + DEFAULT_POLL_DURATION_SEC,
                executed = false,
            ),
            userVoterStore = VoterStore(
                voterOwner = fiWalletAddress,
                pollAddress = derivedPollAddr,
                voted = true,
                vote = true,
            ),
            source = "governance",
        )
        _polls.update { existing ->
            listOf(optimisticPoll) + existing.filterNot { item -> item.pollStore.proposalId == effectivePollId }
        }
        _inspectedPoll.value = optimisticPoll
        _pendingIntent.value = intent
        _statusMessage.value = "Prepared DAO Proposal #$effectivePollId ($cleanDesc)"
    }

    fun castVote(pollId: Long, support: Boolean) {
        val fiWalletAddress = resolveActiveFiWalletAddress()
        if (fiWalletAddress == null) {
            _statusMessage.value = "Connect an active TON wallet to cast a sharded DAO ballot."
            return
        }
        val existingPoll = _polls.value.firstOrNull { item -> item.pollStore.proposalId == pollId }
        val pollAddress = existingPoll?.pollAddress
            ?: ShardedAddressDerivation.derivePoll(pollId).address.toAccountId()
        val voterStore = existingPoll?.userVoterStore
        val oldVote = if (voterStore?.voted == true) {
            voterStore.vote
        } else {
            null
        }
        val intent = daoRepository.buildCastPollVoteIntent(
            fiWalletAddress = fiWalletAddress,
            pollAddress = pollAddress,
            proposalId = pollId,
            support = support,
            oldVote = oldVote,
        )
        _pendingIntent.value = intent
        val voteLabel = if (support) {
            "FOR"
        } else {
            "AGAINST"
        }
        _statusMessage.value = "Prepared $voteLabel ballot on Proposal #$pollId"
    }

    fun executeProposal(pollId: Long) {
        val existingPoll = _polls.value.firstOrNull { item -> item.pollStore.proposalId == pollId }
        val pollAddress = existingPoll?.pollAddress
            ?: ShardedAddressDerivation.derivePoll(pollId).address.toAccountId()
        val payloadCell = CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.EXECUTE_DAO_PROPOSAL, 32)
            storeUInt(BrotherhoodMessages.defaultQueryId(), 64)
            storeUInt(pollId, 64)
        }
        _pendingIntent.value = BrotherhoodTransferIntent(
            title = "Execute DAO Proposal #$pollId",
            subtitle = "Trigger passed proposal execution on Poll contract",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(pollAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.VOTE_PROPOSAL_GAS_NANO),
                    payloadCell = payloadCell,
                )
            ),
        )
        _statusMessage.value = "Prepared execution transaction for Proposal #$pollId"
    }

    fun changeMinterAdmin(newAdminAddress: String) {
        val trimmed = newAdminAddress.trim()
        val parsed = runCatching { AddrStd(trimmed) }.getOrNull()
        if (parsed == null) {
            _statusMessage.value = "Enter a valid TON address for the new FossFi Minter admin."
            return
        }
        val intent = daoRepository.buildAdminHandoffIntent(
            newAdminAddress = parsed.toAccountId(),
            claimHandoff = false,
        )
        _pendingIntent.value = intent
        _statusMessage.value = "Prepared ChangeMinterAdmin handoff to ${BrotherhoodUiFormatters.shortAddress(trimmed)}"
    }

    fun claimMinterAdmin() {
        val intent = daoRepository.buildAdminHandoffIntent(
            newAdminAddress = null,
            claimHandoff = true,
        )
        _pendingIntent.value = intent
        _statusMessage.value = "Prepared ClaimMinterAdmin transaction for FossFi Minter"
    }

    fun dropMinterAdmin() {
        _pendingIntent.value = BrotherhoodTransferIntent(
            title = "Drop FossFi Minter Admin",
            subtitle = "Permanently renounce FossFi Minter admin privileges",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = BrotherhoodConfig.FI_ADDRESS,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DAO_ADMIN_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildDropMinterAdmin(),
                )
            ),
        )
        _statusMessage.value = "Prepared DropMinterAdmin transaction"
    }

    private fun resolveActiveFiWalletAddress(): String? {
        val fromSnapshot = brotherhoodRepository.accountSnapshot.value?.derivedFiWalletAddressRaw
        if (!fromSnapshot.isNullOrBlank() && !BrotherhoodConfig.isZeroAddress(fromSnapshot)) {
            return fromSnapshot
        }
        val ownerWallet = sessionHolder.walletAddressFlow.value
        if (ownerWallet.isNullOrBlank()) {
            return null
        }
        return runCatching {
            brotherhoodRepository.deriveFiWalletAddress(ownerWallet).toAccountId()
        }.getOrNull()
    }

    companion object {
        const val SUB_TAB_PROPOSALS = "proposals"
        const val SUB_TAB_TREASURY = "treasury"
        const val SUB_TAB_ADMIN = "admin"
        private const val DEFAULT_POLL_DURATION_SEC = 604_800L
    }
}
