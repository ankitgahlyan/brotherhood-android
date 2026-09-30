# 10. Member Nominee Designation & Succession Update

Date: 2026-09-06

## Status
Accepted

## Context
In BrotherHood / FossFi, a **Nominee** is the designated Account that receives a Member's remaining tokens (both FI jettons and Gold coins) upon Account Closure (such as on death or authority closure).

Previously:
1. `nomins.nominee` was set automatically during the initial `InternalInvite` step to the inviter's contract address.
2. There was no on-chain message allowing a member to update their nominee or designate a nominee if the account was initialized without one (such as root/direct deployment).
3. The UI in `apps/wallet` displayed the nominee in the Account dashboard as read-only, displaying `—` when unset, without any mechanism to designate or change it.
4. When voting in the Trust Graph, selecting among existing endorsed candidates (`votedFor`) was not directly accessible via an intuitive dropdown on the address input.

## Decision
1. **On-Chain Message `ChangeNominee`**:
   - Introduce opcode `0x000010a9` with message `ChangeNominee { queryId: uint64 = 0, newNominee: address }`.
   - Authorized strictly by the Account Owner (`checkOwnerAction`).
   - Self-nomination defense: Assert that `newNominee != owner` and `newNominee != self_contract_address`.
   - Update `nomins.nominee` in storage and return excess TON to the owner.
2. **Account Dashboard Nominee CTA**:
   - When `account.data.nominee` is unset (`null`), display an explicit "Not Designated" badge with a prominent `[🛡️ Set Nominee]` action button redirecting to nominee management.
   - When `account.data.nominee` is designated, display the address, copy button, and a `[Change]` shortcut button.
3. **Dedicated Nominee Tab**:
   - Add `'nominee'` to top-level navigation tabs.
   - Show current designation status, explanations of inheritance rules, and an input form (with QR scanner support and member quick-picks) to designate or update nominee anytime.
4. **Trust Graph Endorsed Candidates Dropdown**:
   - In the Vote tab, provide a dropdown picker directly with the target member address input populated with accounts from `account.data.votedFor`.
   - Display candidate usernames, country flags, short addresses, and active vote allocations, allowing one-click selection for both incremental voting and unvoting.

## Invariants & Security
- **Self-Nomination Block**: A member cannot nominate their own owner address or wallet contract address (`Errors.InvalidMessage`).
- **Owner Only**: Only the account owner sending with sufficient gas (>= 0.05 TON) can invoke `ChangeNominee`.
- **Surviving Settlement Integrity**: Unaffected by nominee updates; closure logic cleanly transfers remaining assets to the currently designated nominee address.
