package com.tonapps.wallet.data.brotherhood.repo

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.FiStore
import com.tonapps.brotherhood.store.PollStore
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.VoterStore
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.db.KnownPollEntity
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.Flow
import org.ton.block.AddrStd
import org.ton.cell.Cell
import java.math.BigInteger

data class HydratedDaoPoll(
    val pollAddress: String,
    val pollStore: PollStore,
    val userVoterStore: VoterStore?,
    val source: String,
)

class DaoRepository(
    private val dao: BrotherhoodDao,
    private val hydrator: AccountStateHydrator,
) {
    fun observeKnownPolls(): Flow<List<KnownPollEntity>> = dao.observeKnownPolls()

    suspend fun syncDaoGovernance(
        userFiWalletAddress: String?,
        maxPollIdToScan: Int = 8,
        forceRefresh: Boolean = false,
    ): Pair<FiStore?, List<HydratedDaoPoll>> {
        val fiMinterState = hydrator.hydrateAddress(
            address = BrotherhoodConfig.FI_ADDRESS_RAW,
            explicitType = KnownContractType.FI_MINTER,
            forceRefresh = forceRefresh,
        )
        val fiStore = (fiMinterState?.decodedStore as? DecodedContractStore.FiMinter)?.store

        val derivedPollAddresses = (1..maxPollIdToScan).map { id ->
            ShardedAddressDerivation.derivePoll(id.toLong()).address.toAccountId()
        }
        val existingPolls = dao.getAllKnownPolls().map { BrotherhoodConfig.normalizeAddress(it.pollAddress) }
        val allPollAddresses = (derivedPollAddresses + existingPolls).distinct()

        val voterAddrByPoll = if (!userFiWalletAddress.isNullOrBlank() && !BrotherhoodConfig.isZeroAddress(userFiWalletAddress)) {
            val userFi = AddrStd(userFiWalletAddress)
            allPollAddresses.associateWith { pollRaw ->
                ShardedAddressDerivation.deriveVoter(userFi, AddrStd(pollRaw)).address.toAccountId()
            }
        } else {
            emptyMap()
        }

        val batchAddresses = allPollAddresses + voterAddrByPoll.values
        val explicitTypes = buildMap {
            for (p in allPollAddresses) {
                put(p, KnownContractType.POLL)
            }
            for (v in voterAddrByPoll.values) {
                put(v, KnownContractType.VOTER)
            }
        }

        val hydrated = hydrator.hydrateBatch(
            addresses = batchAddresses,
            explicitTypes = explicitTypes,
            forceRefresh = forceRefresh,
        )

        val now = System.currentTimeMillis()
        val pollEntities = mutableListOf<KnownPollEntity>()
        val results = mutableListOf<HydratedDaoPoll>()

        for (pollRaw in allPollAddresses) {
            val pState = hydrated[pollRaw] ?: continue
            val pStore = (pState.decodedStore as? DecodedContractStore.Poll)?.store ?: continue
            val voterRaw = voterAddrByPoll[pollRaw]
            val vStore = voterRaw?.let { (hydrated[it]?.decodedStore as? DecodedContractStore.Voter)?.store }

            pollEntities.add(
                KnownPollEntity(
                    pollAddress = pollRaw,
                    pollId = pStore.proposalId,
                    proposerAddress = pStore.addresses.proposerOwner,
                    description = "Proposal #${pStore.proposalId}",
                    actionType = (pStore.targetOpcode ?: 0L).toInt(),
                    startTime = pStore.expiresAt,
                    durationSeconds = 0L,
                    yesCount = pStore.yesVotes,
                    noCount = pStore.noVotes,
                    passed = pStore.yesVotes > pStore.noVotes,
                    ended = pStore.executed,
                    source = "governance",
                    locationH3Cell = null,
                    updatedAtMs = now,
                )
            )
            results.add(
                HydratedDaoPoll(
                    pollAddress = pollRaw,
                    pollStore = pStore,
                    userVoterStore = vStore,
                    source = "governance",
                )
            )
        }

        if (pollEntities.isNotEmpty()) {
            dao.upsertKnownPolls(pollEntities)
        }
        return fiStore to results.sortedByDescending { it.pollStore.proposalId }
    }

    fun buildSubmitProposalIntent(
        fiWalletAddress: String,
        targetMsgCell: Cell,
    ): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Submit DAO Proposal",
            subtitle = "Deploy sharded Poll contract via DAO Proxy",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.SUBMIT_PROPOSAL_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActSubmitProposal(targetMsg = targetMsgCell),
                )
            ),
        )
    }

    fun buildCastPollVoteIntent(
        fiWalletAddress: String,
        pollAddress: String,
        proposalId: Long,
        support: Boolean,
        oldVote: Boolean? = null,
    ): BrotherhoodTransferIntent {
        val voteLabel = if (support) {
            "YES"
        } else {
            "NO"
        }
        return BrotherhoodTransferIntent(
            title = "Vote $voteLabel on Proposal #$proposalId",
            subtitle = "Cast sharded DAO ballot via Voter contract",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.VOTE_PROPOSAL_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActVoteProposal(
                        pollAddress = AddrStd(pollAddress),
                        proposalId = proposalId,
                        vote = support,
                        oldVote = oldVote,
                    ),
                )
            ),
        )
    }

    fun buildAdminHandoffIntent(
        newAdminAddress: String? = null,
        claimHandoff: Boolean = false,
    ): BrotherhoodTransferIntent {
        val payload = if (claimHandoff || newAdminAddress == null) {
            BrotherhoodMessages.buildClaimMinterAdmin()
        } else {
            BrotherhoodMessages.buildChangeMinterAdmin(AddrStd(newAdminAddress))
        }
        val title = if (claimHandoff || newAdminAddress == null) {
            "Claim DAO Admin Handoff"
        } else {
            "Propose DAO Admin Handoff"
        }
        return BrotherhoodTransferIntent(
            title = title,
            subtitle = "FossFi Minter governance administration",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = BrotherhoodConfig.FI_ADDRESS,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DAO_ADMIN_GAS_NANO),
                    payloadCell = payload,
                )
            ),
        )
    }
}
