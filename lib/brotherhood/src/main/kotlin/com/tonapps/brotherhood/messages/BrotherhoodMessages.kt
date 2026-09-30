package com.tonapps.brotherhood.messages

import com.tonapps.brotherhood.store.TolkSliceUtils.storeAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.storeCoins
import com.tonapps.brotherhood.store.TolkSliceUtils.storeMaybeAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.storeStringRefTail
import com.tonapps.brotherhood.store.TolkSliceUtils.storeStringTail
import com.tonapps.brotherhood.store.TolkSliceUtils.toBigInt
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.ContractCodeCells
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import org.ton.bigint.toBigInt
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import java.math.BigInteger
import java.security.MessageDigest

object BrotherhoodMessages {

    fun defaultQueryId(): Long = System.currentTimeMillis()

    // =========================================================================
    // Standard Jetton Transfers & Burns (FossFiWallet & PersonalWallet)
    // =========================================================================

    fun buildAskToTransfer(
        jettonAmountRaw: BigInteger,
        transferRecipient: AddrStd,
        sendExcessesTo: AddrStd? = null,
        customPayload: Cell? = null,
        forwardTonAmountNano: BigInteger = BigInteger.ZERO,
        forwardPayload: Cell? = null,
        comment: String? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        val effectiveForwardPayload = when {
            forwardPayload != null -> forwardPayload
            !comment.isNullOrEmpty() -> CellBuilder.createCell {
                storeUInt(0, 32)
                storeStringTail(comment)
            }
            else -> null
        }
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ASK_TO_TRANSFER.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeCoins(Coins(jettonAmountRaw.toBigInt()))
            storeAddress(transferRecipient)
            storeMaybeAddress(sendExcessesTo)
            if (customPayload != null) {
                storeBit(true)
                storeRef(customPayload)
            } else {
                storeBit(false)
            }
            storeCoins(Coins(forwardTonAmountNano.toBigInt()))
            if (effectiveForwardPayload != null) {
                storeBit(true)
                storeRef(effectiveForwardPayload)
            } else {
                storeBit(false)
            }
        }
    }

    fun buildAskToBurn(
        jettonAmountRaw: BigInteger,
        sendExcessesTo: AddrStd? = null,
        customPayload: Cell? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ASK_TO_BURN.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeCoins(Coins(jettonAmountRaw.toBigInt()))
            storeMaybeAddress(sendExcessesTo)
            if (customPayload != null) {
                storeBit(true)
                storeRef(customPayload)
            } else {
                storeBit(false)
            }
        }
    }

    // =========================================================================
    // Account Lifecycle & Circle Invites
    // =========================================================================

    fun buildActInvite(
        transferRecipient: AddrStd,
        username: String,
        h3Cell: String,
        country: Int = 0,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_INVITE.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(transferRecipient)
            storeStringRefTail(username.trim())
            storeStringRefTail(h3Cell.trim())
            storeUInt(country, 16)
        }
    }

    fun buildDeActivateCircleRing(
        transferRecipient: AddrStd,
        fundsReceiver: AddrStd? = null,
        amountRaw: BigInteger = BigInteger.ZERO,
        toggleActive: Boolean = true,
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.DEACTIVATE_CIRCLE_RING.toBigInt(), 32)
            storeAddress(transferRecipient)
            storeMaybeAddress(fundsReceiver)
            storeCoins(Coins(amountRaw.toBigInt()))
            storeBit(toggleActive)
        }
    }

    fun buildActCloseAccount(queryId: Long = defaultQueryId()): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_CLOSE_ACCOUNT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
        }
    }

    fun buildAuthorityCloseAccount(
        target: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.AUTHORITY_CLOSE_ACCOUNT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(target)
        }
    }

    fun buildActDestroyAccount(): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_DESTROY_ACCOUNT.toBigInt(), 32)
        }
    }

    // =========================================================================
    // Profile & Location
    // =========================================================================

    fun buildChangeProfile(
        username: String? = null,
        h3Cell: String? = null,
        country: Int? = null,
        nominee: AddrStd? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.CHANGE_PROFILE.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            if (username != null) {
                storeBit(true)
                storeStringRefTail(username.trim())
            } else {
                storeBit(false)
            }
            if (h3Cell != null) {
                storeBit(true)
                storeStringRefTail(h3Cell.trim())
            } else {
                storeBit(false)
            }
            if (country != null) {
                storeBit(true)
                storeUInt(country, 16)
            } else {
                storeBit(false)
            }
            storeMaybeAddress(nominee)
        }
    }

    // =========================================================================
    // Social Vouching / Voting & Authority
    // =========================================================================

    fun buildActVote(
        transferRecipient: AddrStd,
        count: Int = 1,
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_VOTE.toBigInt(), 32)
            storeAddress(transferRecipient)
            storeUInt(count.coerceIn(1, 15), 4)
        }
    }

    fun buildActUnvote(
        transferRecipient: AddrStd,
        count: Int = 1,
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_UNVOTE.toBigInt(), 32)
            storeAddress(transferRecipient)
            storeUInt(count.coerceIn(1, 15), 4)
        }
    }

    fun buildActDispatchAuthorityAction(
        transferRecipient: AddrStd,
        fundsReceiver: AddrStd? = null,
        amountRaw: BigInteger = BigInteger.ZERO,
        toggleActive: Boolean = true,
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_DISPATCH_AUTHORITY_ACTION.toBigInt(), 32)
            storeAddress(transferRecipient)
            storeMaybeAddress(fundsReceiver)
            storeCoins(Coins(amountRaw.toBigInt()))
            storeBit(toggleActive)
        }
    }

    fun buildSetStatus(
        sender: AddrStd,
        status: Int,
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.SET_STATUS.toBigInt(), 32)
            storeAddress(sender)
            storeUInt(status.coerceIn(0, 3), 2)
        }
    }

    // =========================================================================
    // Weekly Grant, Mutual Credit, Loans, Allowances, Gold & Decay
    // =========================================================================

    fun buildActClaimWeeklyGrant(
        sendExcessesTo: AddrStd? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_CLAIM_WEEKLY_GRANT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeMaybeAddress(sendExcessesTo)
        }
    }

    fun buildBuyCredit(
        jettonAmountRaw: BigInteger,
        transferRecipient: AddrStd,
        sendExcessesTo: AddrStd? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.BUY_CREDIT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeCoins(Coins(jettonAmountRaw.toBigInt()))
            storeAddress(transferRecipient)
            storeMaybeAddress(sendExcessesTo)
        }
    }

    fun buildPayback(
        amountRaw: BigInteger,
        sender: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.PAYBACK.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeCoins(Coins(amountRaw.toBigInt()))
            storeAddress(sender)
        }
    }

    fun buildSetLoanRequirement(
        amountRaw: BigInteger? = null,
        maturityDateSeconds: Long? = null,
        multiplier: Int? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.SET_LOAN_REQUIREMENT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            if (amountRaw != null) {
                storeBit(true)
                storeCoins(Coins(amountRaw.toBigInt()))
            } else {
                storeBit(false)
            }
            if (maturityDateSeconds != null) {
                storeBit(true)
                storeUInt(maturityDateSeconds.toBigInt(), 32)
            } else {
                storeBit(false)
            }
            if (multiplier != null) {
                storeBit(true)
                storeUInt(multiplier, 16)
            } else {
                storeBit(false)
            }
        }
    }

    fun buildActPayEmi(
        sendExcessesTo: AddrStd? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_PAY_EMI.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeMaybeAddress(sendExcessesTo)
        }
    }

    fun buildRepayDebt(
        amountRaw: BigInteger,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.REPAY_DEBT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeCoins(Coins(amountRaw.toBigInt()))
        }
    }

    fun buildTriggerDefaultEmi(
        sender: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.TRIGGER_DEFAULT_EMI.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(sender)
        }
    }

    fun buildTriggerDecay(sender: AddrStd): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.TRIGGER_DECAY.toBigInt(), 32)
            storeAddress(sender)
        }
    }

    fun buildSetAllowance(
        grantee: AddrStd,
        amountRaw: BigInteger,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.SET_ALLOWANCE.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(grantee)
            storeCoins(Coins(amountRaw.toBigInt()))
        }
    }

    fun buildSpendAllowance(
        amountRaw: BigInteger,
        receiver: AddrStd,
        sendExcessesTo: AddrStd? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.SPEND_ALLOWANCE.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeCoins(Coins(amountRaw.toBigInt()))
            storeAddress(receiver)
            storeMaybeAddress(sendExcessesTo)
        }
    }

    fun buildAskGoldCoinsTransfer(
        amount: Long,
        receiver: AddrStd,
        sendExcessesTo: AddrStd? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ASK_GOLD_COINS_TRANSFER.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeUInt(amount.toBigInt(), 32)
            storeAddress(receiver)
            storeMaybeAddress(sendExcessesTo)
        }
    }

    // =========================================================================
    // Social Following & Inheritance
    // =========================================================================

    fun buildFollow(
        followee: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.FOLLOW.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(followee)
        }
    }

    fun buildUnfollow(
        initiator: AddrStd,
        followee: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.UNFOLLOW.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(initiator)
            storeAddress(followee)
        }
    }

    fun buildSettleDeath(
        deceased: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.SETTLE_DEATH.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(deceased)
        }
    }

    // =========================================================================
    // Deferred Payments (Holding Contract)
    // =========================================================================

    fun buildRequestDeferredPayment(
        payer: AddrStd,
        amountRaw: BigInteger,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.REQUEST_DEFERRED_PAYMENT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(payer)
            storeCoins(Coins(amountRaw.toBigInt()))
        }
    }

    fun buildActClaimDeferredPayment(
        holdingAddress: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_CLAIM_DEFERRED_PAYMENT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(holdingAddress)
        }
    }

    fun buildActCancelDeferredPayment(
        holdingAddress: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_CANCEL_DEFERRED_PAYMENT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(holdingAddress)
        }
    }

    fun buildToggleDeferredPayment(
        enabled: Boolean,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.TOGGLE_DEFERRED_PAYMENT.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeBit(enabled)
        }
    }

    // =========================================================================
    // Personal Token & FossFi Minter Governance
    // =========================================================================

    fun buildActSetPersonalJetton(
        personalJettonMinter: AddrStd,
        personalJettonWallet: AddrStd,
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_SET_PERSONAL_JETTON.toBigInt(), 32)
            storeAddress(personalJettonMinter)
            storeAddress(personalJettonWallet)
        }
    }

    fun buildMintPersonal(
        queryId: Long = defaultQueryId(),
        mintRecipient: AddrStd,
        tonAmountNano: BigInteger,
        jettonAmountRaw: BigInteger,
        transferInitiator: AddrStd = mintRecipient,
        sendExcessesTo: AddrStd? = mintRecipient,
    ): Cell {
        val internalTransferCell = CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.INTERNAL_TRANSFER_STEP.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeCoins(Coins(jettonAmountRaw.toBigInt()))
            storeUInt(1, 10) // version = 1
            storeBit(false) // transferredAsCredit = false
            storeAddress(transferInitiator)
            storeMaybeAddress(sendExcessesTo)
            storeCoins(Coins.ofNano(0L))
            storeBit(false) // latestWalletCode = null
            storeBit(false) // PayloadInline empty
        }
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.MINT_NEW_JETTONS.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(mintRecipient)
            storeCoins(Coins(tonAmountNano.toBigInt()))
            storeRef(internalTransferCell)
        }
    }

    fun buildChangeMinterMetadata(
        metadataCell: Cell,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.CHANGE_MINTER_METADATA.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeRef(metadataCell)
        }
    }

    fun buildChangeMinterAdmin(
        newAdminAddress: AddrStd,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.CHANGE_MINTER_ADMIN.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(newAdminAddress)
        }
    }

    fun buildClaimMinterAdmin(queryId: Long = defaultQueryId()): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.CLAIM_MINTER_ADMIN.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
        }
    }

    fun buildDropMinterAdmin(queryId: Long = defaultQueryId()): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.DROP_MINTER_ADMIN.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
        }
    }

    fun buildTopUpTons(latestFiWalletCode: Cell? = null): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.TOP_UP_TONS.toBigInt(), 32)
            if (latestFiWalletCode != null) {
                storeBit(true)
                storeRef(latestFiWalletCode)
            } else {
                storeBit(false)
            }
        }
    }

    // =========================================================================
    // DAO Governance & Polls
    // =========================================================================

    fun buildActSubmitProposal(
        targetMsg: Cell,
        daoProxyAddress: AddrStd = BrotherhoodConfig.DAO_PROXY_ADDRESS,
        pollCode: Cell = ContractCodeCells.pollCode,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_SUBMIT_PROPOSAL.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(daoProxyAddress)
            storeRef(targetMsg)
            storeRef(pollCode)
        }
    }

    fun buildActVoteProposal(
        pollAddress: AddrStd,
        proposalId: Long,
        vote: Boolean,
        oldVote: Boolean? = null,
        queryId: Long = defaultQueryId(),
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ACT_VOTE_PROPOSAL.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(pollAddress)
            storeUInt(proposalId.toBigInt(), 64)
            storeBit(vote)
            if (oldVote != null) {
                storeBit(true)
                storeBit(oldVote)
            } else {
                storeBit(false)
            }
        }
    }

    fun buildCleanupProposalVotes(
        pollAddress: AddrStd,
        voterAddresses: List<AddrStd>,
        queryId: Long = defaultQueryId(),
    ): Cell {
        var listCell: Cell? = null
        for (voter in voterAddresses.asReversed()) {
            val next = listCell
            listCell = CellBuilder.createCell {
                storeAddress(voter)
                if (next != null) {
                    storeBit(true)
                    storeRef(next)
                } else {
                    storeBit(false)
                }
            }
        }
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.CLEANUP_PROPOSAL_VOTES.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeAddress(pollAddress)
            if (listCell != null) {
                storeBit(true)
                storeRef(listCell)
            } else {
                storeBit(false)
            }
        }
    }

    // =========================================================================
    // On-Chain Lottery
    // =========================================================================

    fun buildEnterLottery(
        sender: AddrStd,
        amountRaw: BigInteger,
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.ENTER_LOTTERY.toBigInt(), 32)
            storeAddress(sender)
            storeCoins(Coins(amountRaw.toBigInt()))
        }
    }

    fun buildDrawWinner(queryId: Long = defaultQueryId()): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.DRAW_WINNER.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
        }
    }

    // =========================================================================
    // .bro DNS Auctions & Records
    // =========================================================================

    /**
     * Builds the forwardPayload for `AskToTransfer` on the user's `FiWallet` to bid on or mint a `.bro` domain.
     */
    fun buildBidBroDomainForwardPayload(bareDomain: String): Cell {
        val domainCell = ShardedAddressDerivation.encodeDomainCell(bareDomain)
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.BID_BRO_DOMAIN.toBigInt(), 32)
            storeRef(domainCell)
        }
    }

    /**
     * Builds the forwardPayload for `AskToTransfer` on the user's `FiWallet` to renew a `.bro` domain.
     */
    fun buildRenewBroDomainForwardPayload(bareDomain: String): Cell {
        val domainCell = ShardedAddressDerivation.encodeDomainCell(bareDomain)
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.RENEW_BRO_DOMAIN.toBigInt(), 32)
            storeRef(domainCell)
        }
    }

    fun buildFinalizeAuction(queryId: Long = defaultQueryId()): Cell {
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.FINALIZE_AUCTION.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
        }
    }

    fun buildChangeDnsWalletRecord(
        walletAddress: AddrStd?,
        queryId: Long = defaultQueryId(),
    ): Cell {
        val walletCategoryHash = BigInteger(
            1,
            MessageDigest.getInstance("SHA-256").digest("wallet".toByteArray(Charsets.UTF_8))
        )
        return CellBuilder.createCell {
            storeUInt(BrotherhoodOpcodes.CHANGE_DNS_RECORD.toBigInt(), 32)
            storeUInt(queryId.toBigInt(), 64)
            storeUInt(walletCategoryHash.toBigInt(), 256)
            if (walletAddress != null) {
                val recordCell = CellBuilder.createCell {
                    storeUInt(0x9fd3, 16)
                    storeAddress(walletAddress)
                    storeUInt(0, 8)
                }
                storeRef(recordCell)
            }
        }
    }
}
