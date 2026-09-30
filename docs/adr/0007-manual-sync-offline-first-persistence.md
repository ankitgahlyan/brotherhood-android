# Manual-Sync Offline-First Persistence Architecture

## Context
By default, web3 applications continuously poll RPC nodes and API gateways to ensure data freshness, or refetch on window focus, route changes, and reconnects. In a resource-conscious and privacy-respecting client for BrotherHood, unbounded background polling creates rate-limiting pressure, degrades battery life, and causes UI flickering when network conditions vary. Furthermore, members opening the application should experience instant rendering from local storage rather than waiting on on-chain roundtrips.

## Decision
1. **Offline-First Persistent Storage**:
   - All query feeds (BrotherHood on-chain contract states, member profiles, social graph, governance, city networks, and core wallet states) are persisted to IndexedDB (`brotherhood_contract_db`).
   - Serialization custom replacer and reviver handle TON-specific non-JSON primitive types (`bigint`, `Address`, and `Dictionary`).
   - Cache retention is indefinite: saved snapshots remain stored and served on subsequent app launches without auto-expiring or auto-purging.

2. **No Auto-Fetch Policy**:
   - TanStack Query defaults are configured with `staleTime: Infinity`, `refetchOnWindowFocus: false`, `refetchOnMount: false`, and `refetchOnReconnect: false`.
   - On app startup, if cached data exists in IndexedDB, it hydrates immediately into UI state with zero initial network calls.
   - For fresh installations or newly added accounts where no cache exists (cold cache), queries perform an initial fetch once to populate the database, after which auto-fetching is permanently dormant.
   - Background interval polling (such as the 30-second loop in `useWalletDataUpdater`) is removed.

3. **Dedicated Manual Refresh & Last Fetch UX**:
   - A dedicated Refresh control with a live relative timestamp ticker (*"Updated just now"*, *"Updated 4m ago"*) is integrated into the global header navigation bar (`DashboardHeader`), alongside localized refresh buttons on key feature cards.
   - Clicking Refresh triggers active query invalidation and refetching for the current view, updating the stored timestamp upon completion.
   - Hovering or clicking the timestamp displays the exact absolute localized timestamp.

4. **Mutation-Triggered Invalidation**:
   - When a user signs and confirms a state-changing transaction (FI transfer, weekly claim, vote, allowance spend, or invite), the application refetches the directly affected queries so the UI reflects the result of the explicit user action without requiring an additional manual refresh.
