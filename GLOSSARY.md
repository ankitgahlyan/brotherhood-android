# BrotherHood Glossary

BrotherHood is an invite-only online community on TON that uses cryptocurrency to seek moral and economic consensus, and ultimately to crowdsource physical territory for diplomatic recognition (a "network state"). Its jetton (FI) is the economic and governance instrument; supply is minted through social growth (invites, following) and recurring claims, and decays over a member's lifetime, so value tracks human trust rather than capital.

## Identity

**Member** — a human who belongs to BrotherHood. A member can invite, be invited, vote, be reported, and, on death, have their account closed.
_Avoid_: User, person, account holder

**Country** — the numeric ISO 3166-1 country code recorded in a Member's Account profile, scoping governance voting and regional representation.
_Avoid_: Nationality, citizenship, region

**Location** — an on-chain child contract indexing Members residing within a specific Uber H3 spatial area. Deployed on-demand by the Minter.
_Avoid_: City, CityMap, Region, Geo-index

**H3 Cell** — the string representation of an Uber H3 hexagonal spatial index recorded in a Member's Account profile to position them geographically.
_Avoid_: Coordinate, GPS, City name, Address

**Account** — a member's on-chain record, implemented as a `FossFiWallet` contract. Holds the member's balance, votes, connections, and status. One member owns exactly one account.
_Avoid_: Wallet, jetton wallet (reserved for the TON standard view)

**Base Account** — the minimal initial proxy contract deployed on-chain (`BaseFiWallet`) to establish deterministic Account address calculation with negligible bytecode footprint before upgrading to full Account logic.
_Avoid_: Proxy wallet, stub account

**Username** — the Telegram handle registered in a Member's Account profile (`ProfileInfo`), enabling peer communication and network coordination via Telegram deep-links.
_Avoid_: Handle, nick, alias

**Owner** — the external TON address that signs for and controls an Account's wallet contract.
_Avoid_: Wallet, signer

**Authority** — an Account elevated through received votes to take admin actions (freezing, burning, closing) on ordinary Accounts.
_Avoid_: Admin, moderator, validator

## Economy

**FI** (also referred to as **HD** in swap contexts) — the BrotherHood jetton. Minted to reward social growth (an accepted invite, a follow) and recurring claims (weekly); burned through monthly dues (EMI), unfollows, and closure. Its supply is a measure of human trust, not a fixed issuance. Treated as the primary ecosystem currency (like GRAM/TON).
_Avoid_: Token (when the distinction matters), the coin

**Admin Personal Token** — the Personal Token deployed by the FI Minter Admin (`FiStore.admin`). Treated as the ecosystem's reference stablecoin counterpart (like USDT) in default swap/mutual-credit pairs (`FI` ↔ `Admin Personal Token`).

**Invite** — the act of bringing a new Member into BrotherHood. Only Members can invite; an accepted invite mints FI to reward the network growth it causes.
_Avoid_: Referral, signup, voucher

**Following** — a directional social link from Follower Account to Followee Account, rewarding the Followee with 1,000 minted FI. Implemented as an ephemeral child `Following` contract.
_Avoid_: Friendship, connection, subscription

**Unfollow** — removing a Following; burns the 1,000 FI from the Followee and self-destructs the child `Following` contract to recover TON storage rent.
_Avoid_: Unfriend, remove

**Settlement** — the required burning of minted FI when either party of an active Following dies/closes. Under the survivor-pays invariant, the surviving counterparty burns 1,000 FI to settle the trust supply. Unpaid shortfalls become Debt on the surviving Account and cascade up its Invite Lineage.
_Avoid_: Liquidation, default, clearing

**Monthly Due (EMI)** — a recurring monthly burn of 2,500 FI an Account pays to remain in good standing. Bounded by a 30-day cycle and a 24-hour grace period; failure to pay within grace adds the unpaid balance to Debt and triggers a 5% penalty across all Debt.
_Avoid_: Inflation, demurrage, fees, loan repayment

**Weekly Claim** — a recurring mint of FI a Member may claim every 7 days after an initial 1-day activation wait. Composed of a 2-year Fixed Grant (11,111 FI/week) followed by a lifetime baseline floor (500 FI/week), combined with a lifetime Reputation Grant (10 FI per received vote per week). Automatically offsets any outstanding Debt before crediting net balance.
_Avoid_: Stipend, allowance

**Gold Coin** — a transferrable store-of-value unit held by an Account, used for token-like transfers alongside FI.
_Avoid_: Points, credits

**Allowance** — a spending permission an Account grants to a friend, letting them spend a limited amount of the Account's balance (pocket money).
_Avoid_: Spending limit, delegation

**Deferred Payment** — a timelocked pull payment allowing a Member to pay a counterparty without carrying their wallet device. Disabled by default via the `allowDeferred` account flag, and enabled through an explicit owner toggle. Funds are escrowed in a temporary child contract for a 72-hour challenge window before release.
_Avoid_: Offline payment, pull transfer, async payment

**Holding Contract** — an ephemeral child contract deployed per Deferred Payment, locking the transferred FI until the 72-hour challenge window expires, or self-destructing early if cancelled by the Payer.
_Avoid_: Holding wallet, escrow wallet

**Loan Requirement** — an Account's recorded terms for borrowing FI credit, combining the needed FI amount (Credit Need), loan maturity timestamp, and Personal Token mint ratio (Credit Multiplier). Can only be configured when the Account has a registered Personal Token.
_Avoid_: Borrow terms, credit profile, loan application

**Credit Need** — an Account's recorded amount of FI requested as a loan under its Loan Requirement, checked before an incoming credit transfer is processed. Setting amount to zero cancels active borrowing while preserving maturity for existing loans.
_Avoid_: Credit score, risk rating

**Credit Multiplier** — the ratio of Personal Tokens minted to a lender per unit of FI credit extended under an Account's Loan Requirement (defaults to 1; e.g. a multiplier of 2 mints 2 Personal Tokens per 1 FI borrowed).
_Avoid_: Interest rate, token bonus, leverage

**Nominee** — the Account designated to receive a Member's remaining tokens when that Member's Account closes on death.
_Avoid_: Heir, beneficiary, successor

**Personal Token** — a jetton minted by a Member against their own trust. Its minter address is derived deterministically at initialization with empty metadata (`metadataUri: null`), allowing on-chain verification before registration and post-deploy metadata configuration. Buying it is how another Member extends that Member a loan: the buyer's FI flows to the issuer, and the issuer repays by buying back and burning the token.
_Avoid_: Credit token, IOU, social token

**Loan** — FI owed between Members, collateralized by a borrower's Personal Token.
_Avoid_: Debt

**Debt** — an Account's outstanding liability. Arises when a followed Account closes on death and the follower lacks enough tokens to burn, or when an Account defaults on its Monthly Due (EMI) past the 24-hour grace period. Debt blocks transfers, invites, and following, and is automatically garnished by incoming Weekly Claims.
_Avoid_: Loan, balance owed

**Lottery** — a side game within BrotherHood: Accounts pay an entry amount (10 FI) to join a pool; a winner is selected cryptographically (commit-reveal) and receives the pool.
_Avoid_: Gambling, raffle

**Report** — a complaint by a Member against another. A report is only actionable once a minimum number of Members back it (sybil-resistance), after which the Authority adjudicates; substantiated reports can lead to Closure.
_Avoid_: Flag, complaint, dispute

**Report Backing** — a Member's endorsement of someone else's Report, counted toward the minimum needed before the Report reaches the Authority.
_Avoid_: Second, agreement, co-sign

## Invites

**Inviter** — the Member whose Account brought this Member into BrotherHood. Vouches for the Invitee, is held accountable for it, and can deactivate it.
_Avoid_: Referrer, sponsor

**Invitee** — the Member being brought in by an Inviter.

**Invite Lineage** — the ordered chain of Inviters above an Account, making accountability traceable to its root and enabling closure propagation.
_Avoid_: Referral chain, network

**Circle** — the collection of Accounts directly invited by a Member (1st-degree invitees, max 10).
_Avoid_: Direct referrals, friends, level 1

**Ring** — the collection of Accounts invited by Members of one's Circle (2nd-degree invitees: invitees of invitees, max 100).
_Avoid_: Indirect referrals, level 2, extended network

## Governance

**Vote** — a unit of reputational endorsement cast by one Account for another within the same Country. Voting power is a fixed endowment of 10 votes per Account that can be allocated granularly across multiple Candidates.
_Avoid_: Stake, token-weight

**Candidate** — an active Member Account receiving a Vote endorsement from another Member of the same Country.
_Avoid_: Nominee, delegate

**Received Votes** — the tally of votes an Account has collected; crossing the Authority Threshold (1,000,000) elevates it to Authority.

**Authority Threshold** — the received-vote count at which an Account becomes Authority.
_Avoid_: Quorum, supermajority

**Authority Action** — a privileged operation an Authority may perform on an ordinary Account: freeze, burn, or close.
_Avoid_: Admin action, moderation

**Poll** — an on-chain contract deployed per governance proposal, tallying Member votes and forwarding successful proposals to the DAO Proxy upon reaching the supermajority threshold.
_Avoid_: DAO contract, Proposal contract, ballot

**Voter** — a child contract of a Poll tracking whether a specific Member Account has cast a vote on that Poll and preventing duplicate voting.
_Avoid_: DaoVoter, Ballot receipt

**DAO Proxy** — the network's canonical governance proxy contract registered in the minter, authenticating and forwarding approved actions from verified Poll contracts to the minter.
_Avoid_: DAO, governance controller

## Account Lifecycle

**Active** — the operational state of an Account that can take actions.
_Avoid_: Verified, live

**Suspension** — a temporary, reversible state of an Account, set by its Inviter, Upstream Inviter (`inviter0`), or an Authority.
_Avoid_: Freeze, ban, block

**Under Review** — an Account's state while an active Report with insufficient backing prevents it from acting.
_Avoid_: Locked, flagged

**Closure** — the permanent end of an Account, on a Member's death or as an Authority sanction. Unfollows all, burns the remaining minted FI, propagates up the Invite Lineage, and transfers the Member's remaining tokens to their Nominee.
_Avoid_: Deletion, destruction

**Storage Migration** — the version-gated schema transformation of on-chain Account state, converting a Base Account's minimal initial seed into a full Account record (`migrateFromInit`) or migrating an Account across schema revisions (`migrateFromPreviousVersion`).
_Avoid_: Schema patch, state update

## Organization & Client

**Treasury** — the network's canonical Account, which governs the minter and anchors the Address of every wallet contract.
_Avoid_: Admin wallet, operator

**Developer Mode** — a device-local diagnostic state unlocked on the client via Easter egg (tapping "brotherhood" 7 times in Settings), exposing a floating Developer Bubble with real-time API telemetry, BOC payload disassembler, and Room contract cache explorer.
_Avoid_: Developer user, debug account, admin user
