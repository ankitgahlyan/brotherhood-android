# Deterministic Personal Token Minter Deployment and Post-Deploy Metadata Configuration

## Context
Previously, deploying a Member's Personal Token Minter (`PersonalStore`) required providing token metadata (such as name, symbol, description, or metadata URI) at contract creation time. Because `metadataUri` was part of the contract's initial data cell (`StateInit`), any variation or postponement in metadata choice changed the computed contract address.

This caused two problems:
1. The Member's Account (`FossFiWallet`) or client application could not deterministically compute the Member's Personal Token Minter address ahead of time to verify if it had already been deployed on-chain.
2. If a member deployed their Personal Token Minter directly on-chain but had not yet registered the address in their `FossFiWallet` (`personalJettonMinter` / `personalJettonWallet`), the client UI relied solely on whether `personalJettonWallet` was set in the wallet state, erroneously prompting the user to deploy again rather than simply registering the existing contract.

## Decision

1. **Nullable Initial Metadata in `PersonalStore`**:
   - Updated `contracts/src/personalMinter/storage.tolk` so `metadataUri: cell? = null` defaults to null in the initial storage schema.
   - Initial data for a Member's `PersonalStore` is now strictly deterministic:
     - `totalSupply = 0`
     - `fiJettonAddress = memberAccountAddress`
     - `adminAddress = memberOwnerAddress`
     - `metadataUri = null`
   - In `contracts/src/personalMinter/personal.tolk`, `get_jetton_data()` returns an empty cell (`createEmptyCell()`) if `metadataUri` is null.

2. **Deterministic Address Calculation**:
   - The frontend and wrappers calculate the exact `StateInit` and on-chain address for any member using purely their Account address and Owner address (`getDeterministicPersonalMinter`).
   - The UI checks whether this deterministic address has code/state deployed on-chain (`checkIsContractDeployed` via `useIsContractDeployed`).

3. **Decoupled Deployment & Post-Deploy Metadata Nudge**:
   - Members can deploy their Personal Token contract deterministically without upfront metadata input.
   - If the contract is deployed on-chain but not yet linked in `FossFiWallet`, the UI detects this state and prompts the Member to register their personal token addresses (`ActSetPersonalJetton`).
   - After deployment, members are nudged to set or update their token metadata (name, symbol, description, image URI) by sending an on-chain message (`sendChangeMinterMetadata` / `ChangeMinterMetadata`).
