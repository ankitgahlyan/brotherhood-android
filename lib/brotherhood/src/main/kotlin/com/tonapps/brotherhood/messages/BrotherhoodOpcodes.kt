package com.tonapps.brotherhood.messages

import org.ton.cell.Cell
import org.ton.cell.CellSlice

data class BrotherhoodOpcodeInfo(
    val opcode: Long,
    val structName: String,
    val friendlyTitle: String,
    val contextBadge: String?,
)

object BrotherhoodOpcodes {
    // Standard Jetton & NFT Opcodes
    const val ASK_TO_TRANSFER = 0x0f8a7ea5L
    const val TRANSFER_NOTIFICATION_FOR_RECIPIENT = 0x7362d09cL
    const val INTERNAL_TRANSFER_STEP = 0x178d4519L
    const val RETURN_EXCESSES_BACK = 0xd53276dbL
    const val ASK_TO_BURN = 0x595f07bcL
    const val NOTIFY_MINTER = 0x7bdd97deL
    const val REQUEST_WALLET_ADDRESS = 0x2c76b972L
    const val RESPONSE_WALLET_ADDRESS = 0xd1735466L
    const val NFT_TRANSFER = 0x5fcc3d14L
    const val OWNERSHIP_ASSIGNED = 0x05138d91L
    const val GET_STATIC_DATA = 0x2fcb26a2L
    const val REPORT_STATIC_DATA = 0x8b771735L
    const val NFT_DESTROY = 0x1f0453e0L

    // Group 1: Governance & Admin
    const val MINT_NEW_JETTONS = 0x00001001L
    const val CHANGE_MINTER_ADMIN = 0x00001002L
    const val CLAIM_MINTER_ADMIN = 0x00001003L
    const val DROP_MINTER_ADMIN = 0x00001004L
    const val CHANGE_MINTER_METADATA = 0x00001005L
    const val UPGRADE = 0x00001006L
    const val TOP_UP_TONS = 0x00001007L
    const val REQUEST_UPGRADE_CODE = 0x00001008L
    const val APPROVE_UPGRADE = 0x00001009L
    const val REJECT_UPGRADE = 0x0000100aL
    const val HOT_UPGRADE = 0x0000100bL
    const val CHANGE_DAO_ADDRESS = 0x0000100cL
    const val EXECUTE_DAO_PROPOSAL = 0x0000100dL
    const val REQUEST_TOTAL_ACCOUNTS = 0x0000100eL
    const val RESPONSE_TOTAL_ACCOUNTS = 0x0000100fL
    const val INIT_DAO_PROXY = 0x00001010L

    // Group 2: Account Lifecycle & Onboarding
    const val ACT_INVITE = 0x00001051L
    const val INTERNAL_INVITE = 0x00001052L
    const val INTERNAL_INVITE_APPROVAL = 0x00001053L
    const val INFORM_MINTER_INVITE_INTERNAL = 0x00001054L
    const val DEACTIVATE_CIRCLE_RING = 0x00001055L
    const val DEACTIVATE_CIRCLE_RING_INTERNAL = 0x00001056L
    const val ACT_DESTROY_ACCOUNT = 0x00001058L
    const val DESTROY = 0x00001059L
    const val ACT_CLOSE_ACCOUNT = 0x0000105aL
    const val AUTHORITY_CLOSE_ACCOUNT = 0x0000105bL

    // Group 3: Profile & Location Indexing
    const val CHANGE_PROFILE = 0x000010a1L
    const val INFORM_MINTER_CHANGE_LOCATION = 0x000010a3L
    const val LOCATION_ADD_MEMBER = 0x000010a4L
    const val LOCATION_REMOVE_MEMBER = 0x000010a5L

    // Group 4: Social, Voting, & DAO
    const val ACT_VOTE = 0x000010f1L
    const val ACT_UNVOTE = 0x000010f2L
    const val VOTING_ACTION = 0x000010f3L
    const val ACT_DISPATCH_AUTHORITY_ACTION = 0x000010f4L
    const val AUTHORITY_ACTION = 0x000010f5L
    const val SET_STATUS = 0x000010f6L
    const val TRANSFER_BY_AUTHORITY = 0x000010f7L
    const val ACT_SUBMIT_PROPOSAL = 0x000010faL
    const val INIT_POLL = 0x000010fbL
    const val ACT_VOTE_PROPOSAL = 0x000010fcL
    const val VOTE_PROPOSAL_CHILD = 0x000010fdL
    const val VOTE_PROPOSAL = 0x000010feL
    const val CLEANUP_PROPOSAL_VOTES = 0x000010ffL

    // Group 5: Economy, Allowances, & Credit
    const val ACT_CLAIM_WEEKLY_GRANT = 0x00001141L
    const val ACT_PAY_EMI = 0x00001142L
    const val SET_ALLOWANCE = 0x00001143L
    const val SPEND_ALLOWANCE = 0x00001144L
    const val ASK_GOLD_COINS_TRANSFER = 0x00001145L
    const val INTERNAL_GOLD_COINS_TRANSFER = 0x00001146L
    const val BUY_CREDIT = 0x00001147L
    const val PAYBACK = 0x00001148L
    const val ACT_SET_PERSONAL_JETTON = 0x00001149L
    const val SET_LOAN_REQUIREMENT = 0x0000114aL
    const val REPAY_DEBT = 0x0000114bL
    const val TRIGGER_DEFAULT_EMI = 0x0000114dL
    const val TRIGGER_DECAY = 0x0000114eL

    // Group 6: Mini-Apps, Lottery & Follow
    const val ACT_JOIN_LOTTERY = 0x00001191L
    const val REQUEST_STATE = 0x00001192L
    const val PROVIDE_STATE = 0x00001193L
    const val CUSTOM_PAYLOAD_MSG = 0x00001194L
    const val ENTER_LOTTERY = 0x00001198L
    const val LOTTERY_WIN = 0x00001199L
    const val DRAW_WINNER = 0x0000119aL
    const val UPGRADE_LOTTERY_CODE = 0x0000119bL
    const val UNFOLLOW = 0x00001200L
    const val INIT_FOLLOW = 0x00001201L
    const val SETTLE_DEATH = 0x00001204L
    const val FOLLOW = 0x00001205L
    const val REQUEST_FOLLOW = 0x00001206L
    const val REQUEST_UNFOLLOW = 0x00001207L
    const val FOLLOW_REVERTED_NOTIFICATION = 0x00001208L
    const val UNFOLLOW_REVERTED_NOTIFICATION = 0x00001209L

    // Group 7: Deferred Payments (Holding Contract)
    const val CLAIM_DEFERRED_PAYMENT = 0x716a4d21L
    const val CANCEL_DEFERRED_PAYMENT = 0x38b4c81aL
    const val PENALIZE_DEFERRED_REQUESTER = 0x24d8b9e1L
    const val DEFERRED_PAYMENT_INITIATED = 0x19a4f210L
    const val PULL_DEFERRED_FUNDS = 0x49f2b801L
    const val REQUEST_DEFERRED_PAYMENT = 0x6a1bc924L
    const val ACT_CANCEL_DEFERRED_PAYMENT = 0x7c49e102L
    const val ACCEPT_DEFERRED_TRANSFER = 0x531b70a2L
    const val ACT_CLAIM_DEFERRED_PAYMENT = 0x1f84b29cL
    const val TOGGLE_DEFERRED_PAYMENT = 0x576f30a1L

    // Group 8: Personal Token
    const val MINT_PERSONAL = 0x1674b0a0L

    // Group 9: .bro DNS
    const val EDIT_CONTENT = 0x1a0b9d51L
    const val MINT_DOMAIN_FOR = 0x2c159bf4L
    const val FILL_UP = 0x370fec51L
    const val PROCESS_GOVERNANCE_DECISION = 0x44beae41L
    const val CHANGE_DNS_RECORD = 0x4eb1f0f9L
    const val DNS_RECORD_RELEASE = 0x4ed14b65L
    const val OUTBID_NOTIFICATION = 0x557cea20L
    const val WITHDRAW_FEES = 0x59a3c821L
    const val BID_BRO_DOMAIN = 0x62696431L
    const val DNS_AUCTION_FINALIZED = 0x6275726eL
    const val FINALIZE_AUCTION = 0x66696e61L
    const val DNS_OUTBID_NOTIFICATION = 0x6f757462L
    const val RENEW_BRO_DOMAIN = 0x72656e65L

    val KNOWN_OPCODE_STRUCT_NAMES: Map<Long, String> = mapOf(
        ASK_TO_TRANSFER to "AskToTransfer",
        TRANSFER_NOTIFICATION_FOR_RECIPIENT to "TransferNotificationForRecipient",
        INTERNAL_TRANSFER_STEP to "InternalTransferStep",
        RETURN_EXCESSES_BACK to "ReturnExcessesBack",
        ASK_TO_BURN to "AskToBurn",
        NOTIFY_MINTER to "NotifyMinter",
        REQUEST_WALLET_ADDRESS to "RequestWalletAddress",
        RESPONSE_WALLET_ADDRESS to "ResponseWalletAddress",
        NFT_TRANSFER to "NftTransfer",
        OWNERSHIP_ASSIGNED to "OwnershipAssigned",
        GET_STATIC_DATA to "GetStaticData",
        REPORT_STATIC_DATA to "ReportStaticData",
        NFT_DESTROY to "NftDestroy",
        MINT_NEW_JETTONS to "MintNewJettons",
        CHANGE_MINTER_ADMIN to "ChangeMinterAdmin",
        CLAIM_MINTER_ADMIN to "ClaimMinterAdmin",
        DROP_MINTER_ADMIN to "DropMinterAdmin",
        CHANGE_MINTER_METADATA to "ChangeMinterMetadata",
        UPGRADE to "Upgrade",
        TOP_UP_TONS to "TopUpTons",
        REQUEST_UPGRADE_CODE to "RequestUpgradeCode",
        APPROVE_UPGRADE to "ApproveUpgrade",
        REJECT_UPGRADE to "RejectUpgrade",
        HOT_UPGRADE to "HotUpgrade",
        CHANGE_DAO_ADDRESS to "ChangeDaoAddress",
        EXECUTE_DAO_PROPOSAL to "ExecuteDaoProposal",
        REQUEST_TOTAL_ACCOUNTS to "RequestTotalAccounts",
        RESPONSE_TOTAL_ACCOUNTS to "ResponseTotalAccounts",
        INIT_DAO_PROXY to "InitDaoProxy",
        ACT_INVITE to "ActInvite",
        INTERNAL_INVITE to "InternalInvite",
        INTERNAL_INVITE_APPROVAL to "InternalInviteApproval",
        INFORM_MINTER_INVITE_INTERNAL to "InformMinterInviteInternal",
        DEACTIVATE_CIRCLE_RING to "DeActivateCircleRing",
        DEACTIVATE_CIRCLE_RING_INTERNAL to "DeActivateCircleRingInternal",
        ACT_DESTROY_ACCOUNT to "ActDestroyAccount",
        DESTROY to "Destroy",
        ACT_CLOSE_ACCOUNT to "ActCloseAccount",
        AUTHORITY_CLOSE_ACCOUNT to "AuthorityCloseAccount",
        CHANGE_PROFILE to "ChangeProfile",
        INFORM_MINTER_CHANGE_LOCATION to "InformMinterChangeLocation",
        LOCATION_ADD_MEMBER to "LocationAddMember",
        LOCATION_REMOVE_MEMBER to "LocationRemoveMember",
        ACT_VOTE to "ActVote",
        ACT_UNVOTE to "ActUnvote",
        VOTING_ACTION to "VotingAction",
        ACT_DISPATCH_AUTHORITY_ACTION to "ActDispatchAuthorityAction",
        AUTHORITY_ACTION to "AuthorityAction",
        SET_STATUS to "SetStatus",
        TRANSFER_BY_AUTHORITY to "TransferByAuthority",
        ACT_SUBMIT_PROPOSAL to "ActSubmitProposal",
        INIT_POLL to "InitPoll",
        ACT_VOTE_PROPOSAL to "ActVoteProposal",
        VOTE_PROPOSAL_CHILD to "VoteProposalChild",
        VOTE_PROPOSAL to "VoteProposal",
        CLEANUP_PROPOSAL_VOTES to "CleanupProposalVotes",
        ACT_CLAIM_WEEKLY_GRANT to "ActClaimWeeklyGrant",
        ACT_PAY_EMI to "ActPayEmi",
        SET_ALLOWANCE to "SetAllowance",
        SPEND_ALLOWANCE to "SpendAllowance",
        ASK_GOLD_COINS_TRANSFER to "AskGoldCoinsTransfer",
        INTERNAL_GOLD_COINS_TRANSFER to "InternalGoldCoinsTransfer",
        BUY_CREDIT to "BuyCredit",
        PAYBACK to "Payback",
        ACT_SET_PERSONAL_JETTON to "ActSetPersonalJetton",
        SET_LOAN_REQUIREMENT to "SetLoanRequirement",
        REPAY_DEBT to "RepayDebt",
        TRIGGER_DEFAULT_EMI to "TriggerDefaultEmi",
        TRIGGER_DECAY to "TriggerDecay",
        ACT_JOIN_LOTTERY to "ActJoinLottery",
        REQUEST_STATE to "RequestState",
        PROVIDE_STATE to "ProvideState",
        CUSTOM_PAYLOAD_MSG to "CustomPayloadMsg",
        ENTER_LOTTERY to "EnterLottery",
        LOTTERY_WIN to "LotteryWin",
        DRAW_WINNER to "DrawWinner",
        UPGRADE_LOTTERY_CODE to "UpgradeLotteryCode",
        UNFOLLOW to "Unfollow",
        INIT_FOLLOW to "InitFollow",
        SETTLE_DEATH to "SettleDeath",
        FOLLOW to "Follow",
        REQUEST_FOLLOW to "RequestFollow",
        REQUEST_UNFOLLOW to "RequestUnfollow",
        FOLLOW_REVERTED_NOTIFICATION to "FollowRevertedNotification",
        UNFOLLOW_REVERTED_NOTIFICATION to "UnfollowRevertedNotification",
        CLAIM_DEFERRED_PAYMENT to "ClaimDeferredPayment",
        CANCEL_DEFERRED_PAYMENT to "CancelDeferredPayment",
        PENALIZE_DEFERRED_REQUESTER to "PenalizeDeferredRequester",
        DEFERRED_PAYMENT_INITIATED to "DeferredPaymentInitiated",
        PULL_DEFERRED_FUNDS to "PullDeferredFunds",
        REQUEST_DEFERRED_PAYMENT to "RequestDeferredPayment",
        ACT_CANCEL_DEFERRED_PAYMENT to "ActCancelDeferredPayment",
        ACCEPT_DEFERRED_TRANSFER to "AcceptDeferredTransfer",
        ACT_CLAIM_DEFERRED_PAYMENT to "ActClaimDeferredPayment",
        TOGGLE_DEFERRED_PAYMENT to "ToggleDeferredPayment",
        MINT_PERSONAL to "MintPersonal",
        EDIT_CONTENT to "EditContent",
        MINT_DOMAIN_FOR to "MintDomainFor",
        FILL_UP to "FillUp",
        PROCESS_GOVERNANCE_DECISION to "ProcessGovernanceDecision",
        CHANGE_DNS_RECORD to "ChangeDnsRecord",
        DNS_RECORD_RELEASE to "DnsRecordRelease",
        OUTBID_NOTIFICATION to "OutbidNotification",
        WITHDRAW_FEES to "WithdrawFees",
        BID_BRO_DOMAIN to "BidBroDomain",
        DNS_AUCTION_FINALIZED to "DnsAuctionFinalized",
        FINALIZE_AUCTION to "FinalizeAuction",
        DNS_OUTBID_NOTIFICATION to "DnsOutbidNotification",
        RENEW_BRO_DOMAIN to "RenewBroDomain",
    )

    val FRIENDLY_OPCODE_TITLES: Map<String, String> = mapOf(
        "RequestDeferredPayment" to "Request Deferred Payment",
        "ActClaimDeferredPayment" to "Claim Deferred Payment",
        "ClaimDeferredPayment" to "Claim Deferred Payment",
        "ActCancelDeferredPayment" to "Cancel Deferred Payment",
        "CancelDeferredPayment" to "Cancel Deferred Payment",
        "ToggleDeferredPayment" to "Toggle Deferred Payments",
        "PullDeferredFunds" to "Pull Deferred Funds",
        "AcceptDeferredTransfer" to "Accept Deferred Transfer",
        "PenalizeDeferredRequester" to "Penalize Deferred Requester",
        "DeferredPaymentInitiated" to "Deferred Payment Initiated",
        "MintNewJettons" to "Mint Community Tokens",
        "ChangeMinterAdmin" to "Change Community Admin",
        "ClaimMinterAdmin" to "Claim Community Admin",
        "DropMinterAdmin" to "Drop Community Admin",
        "ChangeMinterMetadata" to "Update Community Info",
        "Upgrade" to "Upgrade Contract",
        "HotUpgrade" to "Hot Upgrade Contract",
        "TopUpTons" to "Top Up Gas",
        "RequestUpgradeCode" to "Request Upgrade Code",
        "ApproveUpgrade" to "Approve Upgrade",
        "RejectUpgrade" to "Reject Upgrade",
        "ChangeDaoAddress" to "Change DAO Address",
        "ExecuteDaoProposal" to "Execute DAO Proposal",
        "RequestTotalAccounts" to "Request Total Accounts",
        "ResponseTotalAccounts" to "Response Total Accounts",
        "InitDaoProxy" to "Initialize DAO Proxy",
        "ActInvite" to "Invite Member",
        "InternalInvite" to "Process Member Invite",
        "InternalInviteApproval" to "Approve Member Invite",
        "InformMinterInviteInternal" to "Inform Minter Invite",
        "DeActivateCircleRing" to "Toggle Circle Ring",
        "DeActivateCircleRingInternal" to "Process Toggle Circle Ring",
        "ActDestroyAccount" to "Destroy Account",
        "Destroy" to "Process Destroy Account",
        "ActCloseAccount" to "Close Account",
        "AuthorityCloseAccount" to "Authority Close Account",
        "ChangeProfile" to "Update Profile",
        "InformMinterChangeLocation" to "Update Location",
        "LocationAddMember" to "Add Member to Location",
        "LocationRemoveMember" to "Remove Member from Location",
        "ActVote" to "Cast Vouch / Vote",
        "ActUnvote" to "Retract Vouch / Vote",
        "VotingAction" to "Process Vote",
        "ActDispatchAuthorityAction" to "Dispatch Authority Action",
        "AuthorityAction" to "Process Authority Action",
        "SetStatus" to "Set Member Status",
        "TransferByAuthority" to "Transfer By Authority",
        "ActSubmitProposal" to "Submit DAO Proposal",
        "InitPoll" to "Initialize Poll",
        "ActVoteProposal" to "Vote on DAO Proposal",
        "VoteProposalChild" to "Relay Proposal Vote",
        "VoteProposal" to "Record Proposal Vote",
        "CleanupProposalVotes" to "Cleanup Proposal Votes",
        "ActClaimWeeklyGrant" to "Claim Weekly Grant",
        "ActPayEmi" to "Pay Loan EMI",
        "SetAllowance" to "Set Spending Allowance",
        "SpendAllowance" to "Spend Allowance",
        "AskGoldCoinsTransfer" to "Transfer Community Credit",
        "InternalGoldCoinsTransfer" to "Process Community Credit",
        "BuyCredit" to "Buy Credit",
        "Payback" to "Repay Loan",
        "RepayDebt" to "Repay Debt",
        "ActSetPersonalJetton" to "Link Personal Token",
        "SetLoanRequirement" to "Configure Loan Rules",
        "TriggerDefaultEmi" to "Trigger Default EMI",
        "TriggerDecay" to "Trigger Token Decay",
        "MintPersonal" to "Mint Personal Token",
        "ActJoinLottery" to "Join Lottery",
        "RequestState" to "Request State",
        "ProvideState" to "Provide State",
        "CustomPayloadMsg" to "Custom Payload",
        "EnterLottery" to "Enter Lottery",
        "LotteryWin" to "Lottery Win",
        "DrawWinner" to "Draw Lottery Winner",
        "UpgradeLotteryCode" to "Upgrade Lottery Code",
        "Follow" to "Follow Member",
        "Unfollow" to "Unfollow Member",
        "InitFollow" to "Initialize Follow",
        "SettleDeath" to "Settle Member Inheritance",
        "RequestFollow" to "Request Follow",
        "RequestUnfollow" to "Request Unfollow",
        "FollowRevertedNotification" to "Follow Reverted",
        "UnfollowRevertedNotification" to "Unfollow Reverted",
        "AskToTransfer" to "Send Token",
        "TransferNotificationForRecipient" to "Received Token",
        "InternalTransferStep" to "Transfer Step",
        "ReturnExcessesBack" to "Excess Return",
        "AskToBurn" to "Burn Token",
        "NotifyMinter" to "Notify Token Minter",
        "RequestWalletAddress" to "Request Wallet Address",
        "ResponseWalletAddress" to "Response Wallet Address",
        "NftTransfer" to "Transfer NFT",
        "OwnershipAssigned" to "Ownership Assigned",
        "GetStaticData" to "Get Static Data",
        "ReportStaticData" to "Report Static Data",
        "NftDestroy" to "Destroy NFT",
        "EditContent" to "Edit Domain Records",
        "MintDomainFor" to "Mint .bro Domain",
        "FillUp" to "Fill Up Domain Gas",
        "ProcessGovernanceDecision" to "Process Domain Governance",
        "ChangeDnsRecord" to "Update .bro DNS Record",
        "DnsRecordRelease" to "Release .bro Domain",
        "OutbidNotification" to "Domain Outbid Notification",
        "WithdrawFees" to "Withdraw Domain Fees",
        "BidBroDomain" to "Bid on .bro Domain",
        "DnsAuctionFinalized" to "Domain Auction Finalized",
        "FinalizeAuction" to "Finalize .bro Auction",
        "DnsOutbidNotification" to "Domain Outbid Refund",
        "RenewBroDomain" to "Renew .bro Domain",
    )

    fun getContractContextBadge(opcode: Long): String? {
        return when {
            opcode in setOf(
                CLAIM_DEFERRED_PAYMENT,
                CANCEL_DEFERRED_PAYMENT,
                PENALIZE_DEFERRED_REQUESTER,
                DEFERRED_PAYMENT_INITIATED,
                PULL_DEFERRED_FUNDS,
                REQUEST_DEFERRED_PAYMENT,
                ACT_CANCEL_DEFERRED_PAYMENT,
                ACCEPT_DEFERRED_TRANSFER,
                ACT_CLAIM_DEFERRED_PAYMENT,
                TOGGLE_DEFERRED_PAYMENT,
            ) -> "Holding"

            opcode in 0x00001051L..0x0000105bL ||
                opcode in 0x000010a1L..0x000010a8L ||
                opcode in 0x000010f1L..0x000010f7L ||
                opcode in 0x00001141L..0x0000114eL -> "Brotherhood Member"

            opcode in 0x0000100cL..0x00001011L ||
                opcode in 0x000010faL..0x000010ffL -> "DAO"

            opcode in 0x00001001L..0x0000100bL -> "FossFi Minter"
            opcode in 0x00001191L..0x0000119bL -> "Lottery"
            opcode in 0x00001200L..0x00001209L -> "Followers"
            opcode == MINT_PERSONAL || opcode == ACT_SET_PERSONAL_JETTON -> "Personal Token"
            opcode in setOf(
                EDIT_CONTENT,
                MINT_DOMAIN_FOR,
                FILL_UP,
                PROCESS_GOVERNANCE_DECISION,
                CHANGE_DNS_RECORD,
                DNS_RECORD_RELEASE,
                OUTBID_NOTIFICATION,
                WITHDRAW_FEES,
                BID_BRO_DOMAIN,
                DNS_AUCTION_FINALIZED,
                FINALIZE_AUCTION,
                DNS_OUTBID_NOTIFICATION,
                RENEW_BRO_DOMAIN,
            ) -> ".bro DNS"

            else -> null
        }
    }

    fun getOpcodeInfo(opcode: Long): BrotherhoodOpcodeInfo? {
        val masked = opcode and 0xFFFFFFFFL
        val structName = KNOWN_OPCODE_STRUCT_NAMES[masked] ?: return null
        val friendlyTitle = FRIENDLY_OPCODE_TITLES[structName] ?: structName
        return BrotherhoodOpcodeInfo(
            opcode = masked,
            structName = structName,
            friendlyTitle = friendlyTitle,
            contextBadge = getContractContextBadge(masked),
        )
    }

    fun parseOpcodeNumber(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val trimmed = raw.trim()
        return runCatching {
            if (trimmed.startsWith("0x", ignoreCase = true)) {
                trimmed.substring(2).toLong(16) and 0xFFFFFFFFL
            } else {
                trimmed.toLong() and 0xFFFFFFFFL
            }
        }.getOrNull()
    }

    fun decodeOpcodeFromCell(cell: Cell?): BrotherhoodOpcodeInfo? {
        if (cell == null || cell.isEmpty()) {
            return null
        }
        val slice: CellSlice = cell.beginParse()
        if (slice.bits.size < 32) {
            return null
        }
        val op = slice.loadUInt(32).toLong() and 0xFFFFFFFFL
        return getOpcodeInfo(op)
    }
}
