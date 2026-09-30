# Country-Scoped Authority Voting & Profile Geographic Representation

## Context
In BrotherHood, governance voting (`ActVote` / `VotingAction`) elevates ordinary Member Accounts to Authority status (`isAuthorityAccount`) upon crossing the `AUTHORITY_THRESHOLD`. As a decentralized network state, moral consensus and governance representation need regional alignment so members endorse local leaders within their jurisdiction, while still allowing globalist or stateless members to participate in a global pool.

## Decision
1. **Profile Country Representation**:
   - `ProfileInfo` stores a numeric ISO 3166-1 country code as `uint16` (e.g. 840 for US, 356 for IN).
   - Code `0` represents a globalist / stateless identifier.
   - Initialized upon account invite/deployment and mutable via `ChangeCountry`.

2. **Country-Matching Invariant in Voting**:
   - When casting a vote via `ActVote`, the voter's Account sends a `VotingAction` containing `country: profile.country`.
   - The Candidate's Account verifies that `msg.country == profile.country` (where `0 == 0` is valid for globalists).
   - If the countries mismatch, the Candidate throws `Errors.CountryMismatch`.

3. **Bounce-Based Voting Power Recovery**:
   - `VotingAction` is dispatched with TVM bounce enabled (`BounceMode.Bounce`).
   - If the candidate contract throws an error (e.g. `CountryMismatch`), the message bounces back to the voter.
   - In `onBouncedMessage`, the voter's Account restores `store.votes += msg.count` and removes the candidate from `social.votedFor`.

4. **Unconditional Unvoting (`ActUnvote`)**:
   - Retracting a vote (`positiveVote: false`) does not re-validate the country match. It decrements `receivedVotes` unconditionally based on the voter's verified address, ensuring members can always recover their voting power.

5. **Safe Country Migration**:
   - Calling `ChangeCountry` requires that the Account has no active outgoing votes (`store.votes == 10`), preventing cross-border ghost votes.

6. **Inspection API**:
   - Expose `get fun get_profile(): (slice, slice, int)` returning `(username, city, country)`.
