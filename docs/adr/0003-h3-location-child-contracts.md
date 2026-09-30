# On-Demand H3 Location Child Contracts

## Context
Previously, location was tracked via 27 static letter aggregator contracts (`Location`) that deployed per-city child contracts (`CityMap`). This required deploying 27 upfront contracts on initial setup, used brittle letter indexing (`cityLetter`), and relied on arbitrary city strings that did not map directly to geographic coordinates or neighborhood clusters.

## Decision
1. **Direct H3 Cell Location Contracts**: Each geographic cluster is identified by an Uber H3 hexagonal spatial index string (e.g. `"8828308281fffff"`). Each H3 index has its own independent `Location` contract storing `members: map<address, bool>` and `memberCount: uint32`.
2. **Elimination of CityMap**: The intermediate `CityMap` contract and the 27 static letter routers are removed.
3. **On-Demand Library Deployment**: The Minter (`FossFi`) deploys `Location` child contracts on-demand using TON Library references (`locationCodeLibRef`) via `AutoDeployAddress` whenever a member joins an H3 cell.
4. **Member Lifecycle Sync**:
   - On invite/join: Minter sends `LocationAddMember` (with stateInit) to the `Location` child contract for that H3 cell.
   - On location change: Minter sends `LocationRemoveMember` to the old H3 `Location` contract and `LocationAddMember` (with deploy stateInit) to the new H3 `Location` contract.

