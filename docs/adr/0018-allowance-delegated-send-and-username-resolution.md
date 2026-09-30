# 18. Allowance-Delegated Send, On-Chain Getter, and Username-Address Local Resolution

Date: 2026-09-14

## Status
Accepted

## Context
Members of BrotherHood can grant spending permissions (**Allowance**) to friends from their Account (`FossFiWallet`). The on-chain contract supports `SpendAllowance` (`0x00001144`), which lets a designated grantee spend up to an authorized amount of the granter's FI balance.

Previously:
1. The Send flow (`/send`) only supported transfers initiated by the connected Member ("Self") directly from their own wallet balance.
2. In-memory reading of an external granter's allowance required inspecting the full `maps.allowances` dictionary without a dedicated contract getter.
3. Entering recipient identities required knowing their exact 48-character TON Owner Address. Telegram handles (**Username**) stored in the Member's Account profile (`ProfileInfo`) were not resolved or cached locally during address entry.
4. Recent transacted recipients were not surfaced, requiring repetitive manual entry for frequent transfers.

## Decision
1. **Contract Getter `get_allowance` (`FossFiWallet.tolk`)**:
   - Expose a dedicated getter function on `FossFiWallet`:
     ```tolk
     get fun get_allowance(grantee: address): coins {
         val store = lazy FiWalletStore.load();
         val maps = lazy store.maps.load();
         val granted = maps.allowances.get(grantee);
         if (!granted.isFound) {
             return 0;
         }
         return granted.loadValue();
     }
     ```
   - Regenerate Tolk and TypeScript contract wrappers (`FossFiWallet.gen.ts`).

2. **Bidirectional Username-to-Address Local Caching**:
   - Maintain network-scoped localStorage mappings:
     - `brotherhood_usernames_${network}`: maps normalized username to Owner Address.
     - `brotherhood_addresses_${network}`: maps Owner Address to display username.
   - On recipient Owner Address entry:
     - Query `localStorage` for a cached username.
     - If not cached, query the on-chain Account profile (`getFiWalletState`).
     - When an on-chain username is present, store the bidirectional mapping in `localStorage`.
     - When no username exists on-chain, do not cache negative results (querying fresh on subsequent inputs).
   - On recipient `@username` entry:
     - Resolve immediately to the linked Owner Address from `localStorage` for transaction dispatch.
     - If not found in `localStorage`, prompt the user to input the Owner Address once to link.

3. **Sender Field (Delegated Allowance Send)**:
   - Scoped strictly to BrotherHood FI transfers (`isFiToken`).
   - Defaults to "Self (My Account)".
   - When switched to "Spend Allowance":
     - Prompts for granter Owner Address or saved `@username`.
     - Automatically queries remaining Allowance granted to the connected Member (`useAllowanceBalance`).
     - Enforces fail-fast client validation that `amount <= remainingAllowance`.
     - Dispatches transaction via `SpendAllowance` (`0x00001144`) sent to the granter's `FossFiWallet` address.

4. **Vertical Mobile-Friendly Recent Transacted Members List**:
   - Positioned beneath the send action buttons.
   - Displays Member Username (`@username`) and full Owner Address with copy-to-clipboard.
   - Tapping an entry autofills the Recipient field.
   - Unbounded history with per-entry removal (trash action) and a "Clear All" action.
   - Records recipient immediately upon transaction submission.

## Consequences
- Members can easily exercise granted pocket-money permissions directly from the main Send screen.
- Peer transactions are simplified through Telegram username resolution and persistent local suggestions.
- Unnecessary on-chain queries are minimized while ensuring unlinked usernames can be discovered and cached.
