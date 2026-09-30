# ADR 0017: BaseFiWallet Minimal Proxy and Atomic 1-Message Storage Migration

## Status
Accepted

## Context
In BrotherHood, each Member's on-chain record is an Account (`FossFiWallet`). Under TON smart contract mechanics, child contract addresses (`calcDeployFiWallet`) are derived deterministically off-chain and on-chain from the initial code and initial data cell:
$$\text{address} = \text{hash}(\text{StateInit}(\text{code}, \text{data}))$$

Previously, `getFiWalletCode()` returned the full compiled bytecode of `FossFiWallet` (~17 KB). Embedding this monolithic bytecode cell across the parent Minter (`FossFi`), governance (`Poll`), and peer wallet contracts bloated deploy transactions, increased gas costs, and created tight deployment coupling whenever wallet logic was updated.

To make `FossFiWallet` upgradable while preserving deterministic address derivation, the initial deployment code must be small and fixed. Storing the initial code cell after an upgrade also requires the initial code to be as small as possible.

Furthermore, deploying the initial deployer/admin wallet previously required multiple sequential messages (deploy proxy -> send upgrade code -> initialize admin account). This multi-step process added latency, script complexity, and failure modes during contract bootstrapping.

## Decision

1. **Minimal Proxy Contract (`BaseFiWallet`)**:
   - Introduce `contracts/src/fossFi/BaseFiWallet.tolk` as the minimal deployment proxy.
   - Deployed with a minimal initial seed store (`BaseFiWalletStore`):
     ```tolk
     struct BaseFiWalletStore {
         owner: address
         minterAddr: address
         version: uint10 = 0
     }
     ```
   - Retains exact per-owner address uniqueness while consuming <600 bits and 0 child cell references.

2. **Deterministic Initial Code Cell (`getFiWalletCode`)**:
   - `getFiWalletCode()` in `contracts/src/common/fi-wallet-code.tolk` returns the minimal compiled bytecode of `BaseFiWallet`.
   - All address derivations (`calcDeployFiWallet`, `address.getAddrFiWallet()`) compute addresses using this minimal initial code.

3. **Atomic 1-Message Code & Data Upgrade via `TopUpTons`**:
   - Extend `TopUpTons` in `contracts/src/common/messages.tolk` to optionally carry the latest wallet code:
     ```tolk
     struct (0x00001007) TopUpTons {
         walletCode: cell? = null
     }
     ```
   - When the Minter boots up the deployer wallet via `TopUpTons`, it attaches `others.latestFiWalletCode` inside the message body.
   - Upon receipt in `BaseFiWallet`:
     1. Verifies caller is `minterAddr` and `msg.walletCode != null`.
     2. Postpones code update: `contract.setCodePostponed(msg.walletCode!)`.
     3. Updates TVM continuation register C3: `setTvmRegisterC3(transformSliceToContinuation(msg.walletCode!.beginParse()))`.
     4. Transfers control directly to the new code (`c3()`), executing `TopUpTons` against `FossFiWallet` in the **exact same transaction**.
   - Similarly, for `InternalTransferStep` during first-time minting or transfers, `msg.walletCode` is passed to atomically upgrade `BaseFiWallet` to `FossFiWallet`.

4. **Multi-State Storage Declaration and Migration Pipeline (`storage-migration.tolk`)**:
   - `FossFiWallet` declares both active and deployment storage schemas in its contract declaration:
     ```tolk
     contract FossFiWallet {
         storage: FiWalletStore
         storageAtDeployment: BaseFiWalletStore
         incomingMessages: AllowedMessageToWallet
     }
     ```
   - Migration logic is isolated in `contracts/src/fossFi/storage-migration.tolk`:
     - `migrateFromInit`: Inspects the storage slice. If it matches `BaseFiWalletStore` (version 0), it initializes the full `FiWalletStore` with version 1, sets initial balance, and configures privileges.
     - `migrateFromPreviousVersion`: Migrates `FiWalletStore` from version $N-1$ to version $N$.
     - Explicit fail-fast assertions: any version mismatch or unsupported schema jump immediately aborts the transaction.

## Consequences

- Deploying and activating the admin/deployer wallet collapses from a 2-step async sequence into a single atomic transaction.
- Bytecode footprint for parent and child contract address derivation shrinks dramatically from ~17 KB to a minimal proxy cell.
- On-chain Account address calculations remain 100% deterministic and backward compatible across all contract upgrades.
- Storage schema modifications are decoupled from initial contract address derivation and managed via explicit, testable migration functions.
