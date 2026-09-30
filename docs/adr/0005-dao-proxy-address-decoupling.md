# Decoupled DAO Proxy Address Calculation & Deployment Initialization

## Context
In TON TVM, contract addresses are computed as cryptographic hashes of `StateInit(code, initial_data)`.
When deploying the BrotherHood network state contracts:
- `FossFi` root minter stores `daoAddress` in its initial state (`FiStore`).
- Previously, `DaoProxy` stored `fiAddress` in its initial state (`DaoProxyStore`).

This produced a mutual circular dependency in initial storage hashes ($Addr(\text{FossFi}) \leftrightarrow Addr(\text{DaoProxy})$), forcing `FossFi` to deploy with `daoAddress = RAW_ZERO_ADDRESS` and require a secondary admin message `sendChangeDaoAddress` to link the DAO Proxy after deployment.

## Decision
1. **Decouple Initial DaoProxy Storage**:
   - `DaoProxyStore` is defined with `{ adminAddress: address, targetAddress: address = RAW_ZERO_ADDRESS }`.
   - `DaoProxy` address is deterministically calculated using `DaoProxy.fromStorage({ adminAddress, targetAddress: RAW_ZERO_ADDRESS })` *before* `FossFi` is created.

2. **Pass DAO Proxy Address on FossFi Deploy**:
   - `FossFi` receives the pre-calculated `daoAddress = daoProxy.address` directly in `FiStore` at deployment time.

3. **Atomic Initialization & Target Configuration**:
   - `DaoProxy` is deployed and initialized with `InitDaoProxy { queryId, targetAddress: minter.address }` by `adminAddress`.
   - `DaoProxy` enforces that only `adminAddress` can set or change `targetAddress`.

4. **HotUpgradability & Migration**:
   - `DaoProxy` is made `HotUpgradable` by `adminAddress` with an explicit migration hook (`@method_id(2727)`).
   - Migration hooks across all system contracts are tagged with explicit `@method_id` values.
