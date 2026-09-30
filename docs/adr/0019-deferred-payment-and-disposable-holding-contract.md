# 19. Deferred Payment and Disposable Holding Contract

Date: 2026-09-18

## Status
Accepted

## Context
Members of BrotherHood may find themselves needing to pay peers or merchants in-person without having access to their signing device containing their Account (`FossFiWallet`). 

Standard TON transfers and Jetton transfer specifications require the sender's cryptographic signature to initiate an on-chain debit. Pre-authorized spending limits (`Allowance`) exist for pre-configured friends, but cannot facilitate impromptu payments to new merchants or arbitrary members when a device is unavailable.

Conversely, enabling unrestricted pull payments on-chain without safeguards creates an immediate existential attack vector: any attacker could drain liquid balances of offline members.

Furthermore, `FiWalletStore` in [`contracts/src/fossFi/storage.tolk`](../../contracts/src/fossFi/storage.tolk) has already saturated the TVM limit of 4 references in its root storage cell (`profile`, `timestamps`, `addresses`, `maps`). Storing persistent pending payment queues directly in `FossFiWallet` storage would exceed cell reference limits and burden long-term contract storage rent.

## Decision

1. **Pure Optimistic Pull Flow**:
   - The Payee (merchant or peer) dispatches `RequestDeferredPayment` to their own Account, which forwards `PullDeferredFunds` to the Payer's Account.
   - The Payer's Account validates fail-fast preconditions:
     - `store.active == true`
     - `store.allowDeferred == true` (explicit opt-in toggle repurposing redundant `debts: bool`, disabled by default, toggled via `ToggleDeferredPayment`)
     - `store.debt == 0` (unified debt checking across all operations)
     - `store.jettonBalance >= amount`
   - Upon validation, the Payer's Account deducts `amount`, records `createdAt = now()`, deploys a disposable `Holding` contract carrying the escrowed tokens, and notifies the Payee's Account with `DeferredPaymentInitiated`.

2. **Disposable `Holding` Contract**:
   - Implemented as an ephemeral child contract in `contracts/src/holding/holding.tolk`.
   - Initial storage: `{ payer: address, payee: address, amount: coins, createdAt: uint32 }`.
   - Contract code is embedded directly via `Acton.toml` (`holdingCompiledCodeLibraryRef`) and addressed deterministically via `calculateHoldingAddress(holdingCode, payer, payee, amount, createdAt)`.
   - Holds the principal tokens for a 72-hour challenge window (`createdAt + 72 hours`).
   - Every terminal path (`ClaimDeferredPayment`, `CancelDeferredPayment`, `FallbackReclaim`) reclaims all remaining TON balance and self-destructs (`SEND_MODE_CARRY_ALL_BALANCE | SEND_MODE_DESTROY_ACCOUNT`) to recover storage rent.

3. **Settlement & Dispute Pathways**:
   - **Happy Path (Claim)**: After 72 hours (`now() >= createdAt + 72 * 3600`), the Payee dispatches `ClaimDeferredPayment`. `Holding` transfers the `amount` of FI to the Payee's Account and destroys itself.
   - **Payer Veto (Fraud Cancellation & Penalty)**: Within the 72-hour window (`now() < createdAt + 72 * 3600`), the Payer can cancel the payment from their Account:
     - `Holding` refunds the 1x `amount` of principal back to the Payer's Account.
     - `Holding` dispatches `PenalizeDeferredRequester { payer, amount, createdAt }` to the Payee's Account.
     - The Payee's Account verifies that the sender is the deterministically derived `Holding` contract.
     - The Payee's Account deducts 1x `amount` from its liquid balance and burns it via `NotifyMinter`. If the balance is insufficient, the deficit converts to `Debt` (`store.debt += deficit;`), blocking account activities and garnishing Weekly Claims.
   - **Counter-Dispute (Authority Adjudication)**: If a dishonest Payer consumes goods and fraudulently cancels to inflict the penalty on an honest merchant, the merchant petitions an **Authority** off-chain with proof. The Authority utilizes existing `AuthorityAction` (`ActDispatchAuthorityAction`) to confiscate 3x `amount` from the abusive Payer and award it to the Payee.
   - **Abandoned Fallback**: If the Payee fails to claim within 30 days (`now() >= createdAt + 30 * 86400`), the Payer can execute `FallbackReclaim` to recover their tokens and gas without penalty.

4. **Frontend & UX Alignment**:
   - Extend the BrotherHood wallet interface (`apps/wallet`) with a "Deferred Payment" action under Send / Receive.
   - Surface pending deferred payments in a dedicated panel showing the remaining 72-hour countdown timer, "Cancel & Dispute" action for Payers, and "Claim" action for Payees once unlocked.

## Consequences

- **Positive**: Members can make payments in real life even if their smartphone is dead, lost, or absent, with zero risk of irreversible immediate fund loss.
- **Positive**: No modification of `FiWalletStore`'s 4-cell reference root layout is required; `Holding` contracts are isolated and disposable.
- **Positive**: Malicious spam pull attempts are economically deterred by automated 1x burn penalties on dispute and Authority 3x punitive confiscation.
- **Negative / Trade-off**: Payees must wait 72 hours before claimed funds become liquid in their Account.
- **Negative / Trade-off**: Requires Payer to review their Account within 72 hours if an uninvited pull is initiated against them.
