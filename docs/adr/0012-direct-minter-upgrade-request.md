# 12. Direct Minter Upgrade Request & Opcode Streamlining

Date: 2026-09-07

## Status
Accepted

## Context
Previously, an Account upgrade was initiated through an intermediate message hop:
1. The Member's external wallet (`Owner` / W5) sent `ActRequestUpgrade` (`0x00001057`) to their `FossFiWallet` (Account).
2. The `FossFiWallet` validated the sender and forwarded `RequestUpgradeCode` (`0x00001008`) with `{ sender: address, version: uint10 }` to the `FossFi` minter.
3. The `FossFi` minter verified `sender == getAutoAddress(msg.sender).calculateAddress()`, checked `msg.version < store.walletVersion`, and sent `Upgrade` back to the `FossFiWallet`.
4. The `FossFiWallet` updated its code postponed and returned excess gas back to the Owner.

This intermediate hop introduced extra transaction latency, duplicated gas consumption, and bloated message schemas with redundant fields (`sender`, `version`) when requesting upgrades. Furthermore, peer-to-peer upgrade handling in `FossFiWallet` risked draining contract balance if it paid fixed TON out-of-pocket for unverified requests.

## Decision

1. **Direct Minter Request (`Owner` $\rightarrow$ `Minter`)**:
   - The Owner directly sends `RequestUpgradeCode` (`0x00001008`) to `FossFi` (minter).
   - Eliminates `ActRequestUpgrade` (`0x00001057`) completely across contracts, wrappers, and frontend.

2. **Streamlined Zero-Payload Opcode**:
   - `RequestUpgradeCode` is simplified to an empty payload:
     ```tolk
     struct (0x00001008) RequestUpgradeCode {}
     ```
   - No redundant `sender` or `version` fields are required.

3. **Minter Forwarding Semantics (`FossFi`)**:
   - **No gas assertions or version guards**: Anyone can request an upgrade; the requester is responsible for attaching sufficient gas.
   - The minter unconditionally derives the Account address for the caller:
     ```tolk
     val fiWallet = getAutoAddress(in.senderAddress).calculateAddress();
     ```
   - The minter immediately dispatches `Upgrade` to `fiWallet` with `SEND_MODE_CARRY_ALL_REMAINING_MESSAGE_VALUE | SEND_MODE_IGNORE_ERRORS`.

4. **Account Invariant & Gas Safety (`FossFiWallet`)**:
   - When `FossFiWallet` receives `Upgrade` from the minter, it verifies the sender is `minterAddr`, updates `store.version` and sets code postponed if newer (`store.version < msg.walletVersion`), and returns excess gas to `addrs.owner`.
   - Peer-to-peer `RequestUpgradeCode` handling is retained for decentralized sync, but updated to reply with `SEND_MODE_CARRY_ALL_REMAINING_MESSAGE_VALUE | SEND_MODE_IGNORE_ERRORS` (`value: 0`), ensuring the requester pays all gas and preventing Account balance draining.

5. **Client Configuration**:
   - `apps/wallet` sends `RequestUpgradeCode` directly to `FI_ADDRESS` with `GAS.REQUEST_UPGRADE = 0.08 TON`, providing safe overhead for execution across Minter and Account before refunding remaining TON to the Owner.
