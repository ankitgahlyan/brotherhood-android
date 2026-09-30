# 17. Unified Loan Requirement Mutation (`SetLoanRequirement`)

Date: 2026-09-12

## Status
Accepted

## Context
Previously, configuring borrowing terms on an Account required two separate message opcodes:
1. `SetCreditNeed` (`0x0000114a`): set borrow amount and maturity date.
2. `SetMultiplier` (`0x0000114c`): set Personal Token mint multiplier.

This separation created several operational issues:
- Members had to pay double transaction gas to set up loan terms.
- Setting credit need without a registered Personal Token led to a broken state where lenders attempting `BuyCredit` would fail on incoming transfer (`trustedAddrs.personalJettonMinter != ZERO_ADDRESS`), wasting lender gas.
- The client UI was fragmented into two disjoint cards/forms rather than presenting a coherent borrowing requirement.

## Decision
1. **Consolidated TL-B Message `SetLoanRequirement`**:
   - Introduce unified opcode `0x0000114a` with optional fields, matching the partial update pattern established in ADR-0011 (`ChangeProfile`):
     ```tolk
     struct (0x0000114a) SetLoanRequirement {
         queryId: uint64 = 0
         amount: coins? = null
         maturityDate: uint32? = null
         multiplier: uint16? = null
     }
     ```
   - Retire legacy message structs `SetCreditNeed` and `SetMultiplier` (`0x0000114c`).

2. **Contract Semantics & Invariants (`FossFiWallet`)**:
   - **Authentication**: `assert (in.senderAddress == addrs.owner) throw Errors.NotOwner;`
   - **Personal Token Registration Guard**: Enforce fail-fast check:
     `assert (trustedAddrs.personalJettonMinter != ZERO_ADDRESS && trustedAddrs.personalJettonMinter != RAW_ZERO_ADDRESS) throw Errors.PersonalJettonNotRegistered;`
     No loan requirement (even canceling with `amount = 0`) can be modified unless an active Personal Token minter is registered.
   - **Non-Empty Entry Guard**: Assert at least one field is provided:
     `assert (msg.amount != null || msg.maturityDate != null || msg.multiplier != null) throw Errors.InvalidMessage;`
   - **Partial Updates & Constraints**:
     - `msg.multiplier != null`: Assert `msg.multiplier >= 1` (throw `Errors.InvalidMessage`), then update `store.multiplier = msg.multiplier`.
     - `msg.amount != null`:
       - If `msg.amount > 0`:
         - Determine effective maturity: `msg.maturityDate != null ? msg.maturityDate : store.creditMaturity`.
         - Assert `effectiveMaturity > blockchain.now()` (throw `Errors.InvalidMessage`).
         - If existing `store.creditMaturity > blockchain.now()`, assert `effectiveMaturity >= store.creditMaturity` (throw `Errors.InvalidMessage`) to prevent shortening active future maturity.
         - Update `store.creditMaturity = effectiveMaturity`.
       - Update `store.creditNeed = msg.amount`. When `msg.amount == 0`, `store.creditMaturity` is preserved to protect existing lenders awaiting `Payback` maturity.
     - `msg.amount == null && msg.maturityDate != null`:
       - If existing `store.creditNeed > 0`: assert `msg.maturityDate > blockchain.now()` and `msg.maturityDate >= store.creditMaturity`, then update `store.creditMaturity = msg.maturityDate`.
   - **Excess Value Refund**: Refund gas excesses back to the owner with `returnExcessesBack(msg.queryId, addrs.owner)`.

3. **Client & UI Alignment (`brotherhood-screen.tsx`)**:
   - Merge the separate forms into a unified "Set Loan Requirement" form.
   - Display current on-chain values (`creditNeed`, `creditMaturity`, `multiplier`) as input placeholders while keeping inputs empty initially.
   - Implement dirty-tracking so only modified fields are sent in `SetLoanRequirement`.
   - Disable the form and display a warning banner if the member's Personal Token is not registered (`!account.data?.personalJettonMinter || isZeroAddress(account.data.personalJettonMinter)`).
