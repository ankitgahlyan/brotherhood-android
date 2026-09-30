# 11. Unified Profile & Nominee Mutation (`ChangeProfile`)

Date: 2026-09-06

## Status
Accepted

## Context
Previously, mutating a Member's Account metadata required four distinct opcodes and messages:
1. `ChangeUsername` (`0x000010a1`)
2. `ChangeLocation` (`0x000010a2`)
3. `ChangeCountry` (`0x000010a8`)
4. `ChangeNominee` (`0x000010a9`)

This led to fragmented contract handlers, separate transaction flows, and sub-optimal client UX where members had to navigate separate tabs and pay individual transaction fees to update basic account information. Furthermore, updating H3 spatial location triggers an on-chain cascade (`FiWallet` -> `Minter` -> `LocationRemoveMember` + `LocationAddMember` child contracts) which requires sufficient gas reserves as the spatial tree grows.

## Decision
1. **Consolidated TL-B Message `ChangeProfile`**:
   - Introduce unified opcode `0x000010a1`:
     ```tolk
     struct (0x000010a1) ChangeProfile {
         queryId: uint64 = 0
         username: string? = null
         h3Cell: string? = null
         country: uint16? = null
         nominee: address? = null
     }
     ```
   - Retire legacy messages `ChangeUsername`, `ChangeLocation`, `ChangeCountry`, and `ChangeNominee`.
2. **Contract Semantics & Invariants (`FossFiWallet`)**:
   - **Authentication**: `assert (in.senderAddress == addrs.owner) throw Errors.IncorrectSender;`
   - **Global 1 TON Gas Requirement**: `assert (in.valueCoins >= ton("1")) throw Errors.NotEnoughGas;`
   - **Non-Empty Payload Entry Guard**: Assert at least one field is non-null (`msg.username != null || msg.h3Cell != null || msg.country != null || msg.nominee != null`), throwing `Errors.InvalidMessage` otherwise.
   - **Partial Updates**:
     - `username != null`: Assert `isNonEmptyString(msg.username)` and set `profile.username`.
     - `country != null`: Assert `store.votes == 10` (`Errors.HasActiveVotes`, per ADR-0002) and set `profile.country`.
     - `nominee != null`: Set `nomins.nominee = msg.nominee` (unrestricted address assignment).
     - `h3Cell != null`: Assert `isNonEmptyString(msg.h3Cell)`, set `profile.h3Cell`, and send `InformMinterChangeLocation` carrying remaining message value.
   - **Excess Value Refund**:
     - When `h3Cell == null`, invoke `returnExcessesBack(msg.queryId, addrs.owner)` carrying all remaining message value back to the owner (`SEND_MODE_CARRY_ALL_REMAINING_MESSAGE_VALUE | SEND_MODE_IGNORE_ERRORS`).
3. **Frontend Unified Profile UX & Tab Consolidation**:
   - Retire the dedicated top-level `nominee` navigation tab in the Brotherhood screen and direct nominee editing into the unified `profile` tab.
   - Do not autofill existing profile values into input fields; inputs start empty with current on-chain values displayed as placeholders. Only fields explicitly filled by the user are marked dirty and sent as non-null in `ChangeProfile`.
   - Country code is presented as read-only by default with an explicit `[Edit]` / `[Cancel]` toggle to prevent accidental country changes (which are blocked if the user has active votes).
   - In the Personal Jetton module, remove the duplicate `register` sub-tab under `admin` in favor of the dedicated `addresses` tab, setting `metadata` as the default admin sub-tab.
   - Submit button is disabled unless at least one field is modified and all modified fields are valid.

