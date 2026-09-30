# ADR 0013: Deterministic Address Caching, Normalized Contract Storage, and Targeted Invalidation

## Context
Under high interaction frequency or multi-contract querying, the BrotherHood client experiences Toncenter API rate limit errors (HTTP 429). The root causes identified are:
1. **Redundant Deterministic Lookups**: Repeatedly querying RPC endpoints via `get_wallet_address` to compute deterministic child wallet addresses (`FossFiWallet`, `PersonalWallet`) for connected owners and counterparties.
2. **Denormalized & Duplicate In-Flight Contract Fetches**: `getWalletDataAll` provides the complete on-chain state for a contract, but different queries and views store compound or fragmented keys (`member-profiles:list`, `fi-wallet-state:owner`, `fi-wallet-state-by-contract:contractAddr`), leading to duplicate network requests and fragmented caches.
3. **Overly Broad Invalidation Storms**: On state-changing transactions, `useBrotherhoodTransaction` and `useSendToken` execute `markForceFresh()` (triggering global `forceFreshAll = true`) and refetch all active TanStack Query observers at once (`queryClient.refetchQueries({ type: 'active' })`). This indiscriminately re-queries every active contract and profile on the screen, triggering burst 429 errors.

## Decision

1. **Deterministic Wallet Address Caching (`localStorage`)**:
   - Deterministic address calculations (e.g., `FossFiWallet` from Minter + Owner, `PersonalWallet` from `PersonalMinter` + Owner) are cached synchronously in `localStorage` upon first query using the network-scoped key:
     `deterministic_wallet:${network}:${minterAddress}:${ownerAddress}`.
   - Lookups always check this storage first. On miss, the on-chain `get_wallet_address` getter is queried once and persisted to storage.

2. **Normalized Contract State Storage (IndexedDB `contract_cache`)**:
   - All contract state retrieved via `getWalletDataAll` is normalized and persisted by its canonical on-chain contract address:
     `contract_state:${network}:${contractAddress}`.
   - High-level queries (such as `useFiWalletState(owner)`) first resolve the owner's deterministic contract address and read from the normalized contract cache key.
   - Batch lookups (such as `useMemberProfiles` or `useCircle`) read and write per-contract records, avoiding multi-address mega-blob caching.

3. **Targeted Invalidation by Contract Address**:
   - `useBrotherhoodTransaction` accepts an optional `affectedContracts: (Address | string)[]`. If not provided, it defaults to the message destination contracts (`messages.map(m => m.toAddress)`).
   - Invalidation after a transaction is strictly scoped:
     - Only the affected contract keys are removed or marked stale in IndexedDB (`deleteContractCache(contractKey)`).
     - Global `forceFreshAll` is deprecated; only the specific TanStack Query keys corresponding to the affected contracts are invalidated (`queryClient.invalidateQueries({ queryKey: [...] })`).
     - Non-affected contracts and member profiles retain their existing cached snapshots without network re-queries.

4. **Rate-Limiting and Single Deferred Refresh**:
   - Avoid tight polling loops post-transaction. A single targeted refetch for the affected contract(s) occurs after a standard block propagation window (4 seconds).

## Consequences
- **Elimination of 429 RPC Storms**: By preventing global refetches and reusing deterministic address lookups, RPC traffic during navigation and post-transaction updates drops by >80%.
- **Instant UI Rendering**: Deterministic addresses and contract states hydrate instantly from synchronous and IndexedDB storage.
- **Cache Consistency**: Storing state under canonical contract addresses ensures that updates to a wallet state are immediately visible across both owner-based and contract-based views.
