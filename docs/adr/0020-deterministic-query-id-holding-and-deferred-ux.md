# 20. Deterministic QueryId Holding Contract Derivation and Deferred Payment UX

Date: 2026-09-22

## Status
Accepted (Supersedes aspects of [ADR-0019](0019-deferred-payment-and-disposable-holding-contract.md))

## Context
[ADR-0019](0019-deferred-payment-and-disposable-holding-contract.md) established the Deferred Payment pattern and disposable `Holding` contract to facilitate offline payments between Members. Under that design, the `Holding` contract's initial storage included `createdAt: uint32` set directly to `blockchain.now()` by the Payer's Account contract when deploying the child contract.

However, because TON addresses are derived from the SHA-256 hash of `StateInit` (`Hash(code, data)`), varying `createdAt` meant that the Payee and Payer could not compute the `Holding` contract address off-chain before on-chain execution. Consequently, both counterparties had to manually inspect transaction receipts or manually paste the holding address into the wallet UI to dispute or claim escrowed funds, presenting poor user experience.

Additionally, the 30-day `FallbackReclaim` mechanism introduced unnecessary contract surface and maintenance overhead for what is meant to be a strict 72-hour timelocked escrow.

## Decision

1. **Deterministic Address Derivation via Random `queryId`**:
   - `HoldingStore` initial `StateInit` layout is `{ payer: address, payee: address, amount: coins, queryId: uint64, createdAt: uint32 }`.
   - In `calcDeployHolding`, the initial `createdAt` is explicitly initialized to `0`.
   - The Holding contract address is therefore a 100% deterministic function of `(payer, payee, amount, queryId)`:
     `calculateHoldingAddress(payer, payee, holdingCodeLibRef, amount, queryId)`.
   - The Payee generates a cryptographically random 64-bit integer `queryId` client-side and derives the exact Holding contract address synchronously *before* sending `RequestDeferredPayment`.

2. **On-Chain Activation**:
   - When the Payer's Account forwards `PullDeferredFunds` and deploys the `Holding` contract via an empty message, `Holding`'s `onInternalMessage` initializes `store.createdAt = blockchain.now()` if `store.createdAt == 0` and saves state.
   - Mutating storage *after* deployment preserves the contract's immutable TON address while accurately anchoring the 72-hour timelock to the block timestamp of deployment.
   - `PenalizeDeferredRequester` message replaces `createdAt` with `queryId` for authenticating the child contract sender in `FossFiWallet`.

3. **Complete Removal of Fallback Reclaim**:
   - The `FallbackReclaim` message, opcodes, and `HOLDING_FALLBACK_DURATION` constant are completely removed from `holding.tolk` and `messages.tolk`.

4. **Client State Persistence & Automated Notification Lifecycle**:
   - Store pending deferred payments per wallet address in `bro-store` under `brotherhoodSlice.pendingDeferredByAddress`.
   - **Payee**: Added upon dispatching `RequestDeferredPayment`. When 72 hours elapse, an actionable alert surfaces in `NotificationBell` with a one-click "Claim FI" button. Only purged from storage after successful on-chain claim.
   - **Payer**: Ingested automatically from on-chain transactions/events upon opening the app. Displayed in `NotificationBell` as a strictly non-dismissible review alert for the 72-hour challenge window with a direct "Cancel & Dispute" button, auto-clearing after 72 hours.
   - Provide a dedicated "Pending Payments" sub-tab under "Deferred Payments" showing counterparties' usernames, addresses, amounts, and live countdown timers.

## Consequences

- **Positive**: Zero manual address entry required for either Payee or Payer.
- **Positive**: Address calculation is synchronous, pure, and client-executable before dispatching transactions.
- **Positive**: Reduces smart contract footprint and gas by eliminating `FallbackReclaim`.
- **Positive**: Non-dismissible notification in `NotificationBell` ensures Payers are immediately informed of unauthorized pulls during the 72-hour dispute window.
