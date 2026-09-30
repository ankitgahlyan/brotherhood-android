# Ephemeral Following Contract & Survivor Settlement Invariant

## Context
Storing unbounded follower and following maps inside a single `FossFiWallet` contract violates TON TVM limits (1024 cells, 65KB storage per contract, and 255 outbound actions per transaction). Furthermore, social minting must maintain supply conservation without burdening deceased estates.

## Decision
1. **Child Contract Architecture**: Every directional follow from Follower $A$ to Followee $B$ deploys an independent child contract `Following(A, B)` whose deterministic address is computed using a masterchain/basechain TON Library reference (`FOLLOWING_LIB_HASH`).
2. **Token Flow**:
   - On `Follow`: `Follower A` deploys `Following(A, B)`, which forwards a notification to `Followee B`'s `FiWallet`. `Followee B` mints 1,000 $FI to its balance.
   - On `Unfollow`: `Followee B` burns 1,000 $FI, and `Following(A, B)` self-destructs (`mode 128 + 32`), returning storage rent to `Follower A`.
3. **Survivor Settlement Invariant ("The Living One Burns")**:
   - If **Follower A dies/closes**: `Followee B` (the living recipient of the initial mint) burns 1,000 $FI.
   - If **Followee B dies/closes**: `Follower A` (the living creator of the link) burns 1,000 $FI.
   - In all cases, the deceased member's estate is spared from settlement burns, and the surviving counterparty settles the token supply.
4. **Bad Debt & Sybil Defense**:
   - If a surviving party has insufficient balance to complete a required death burn, the unpaid shortfall becomes `Debt` on their `FiWallet`.
   - On subsequent Account Closure, uncollected `Debt` cascades up the member's Invite Lineage to their Inviter.
5. **Authorization & Settlement Routing**:
   - `FiWallet` accepts `UnFollow` commands from its owner, peer `FiWallet` accounts (for reciprocal settlement), and `isAuthority` accounts.
   - The child `Following` contract only needs to verify messages from its parent `FiWallet`.
