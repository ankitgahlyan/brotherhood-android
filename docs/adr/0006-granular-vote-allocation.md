# Granular Vote Allocation and Partial Unvoting

## Context
In BrotherHood, each Member Account receives a fixed endowment of 10 voting power units (`uint4`). Previously, `ActVote` and `ActUnvote` operated on a monolithic all-or-nothing basis (allocating all remaining votes to a single candidate and unvoting all at once). To support nuanced moral and governance representation, members need the ability to split their 10 votes across multiple Candidates, top up existing endorsements additively, and reclaim endorsements partially or fully.

## Decision
1. **Granular Message Schemas**:
   - `ActVote` includes `count: uint4 = 1` indicating how many votes to cast for `transferRecipient`.
   - `ActUnvote` includes `count: uint4 = 1` indicating how many votes to reclaim from `transferRecipient`.
   - `VotingAction` continues to carry `count: uint4`, representing the exact delta transferred in positive or negative votes.

2. **Additive Endorsements**:
   - Multiple `ActVote` messages to the same candidate add to that candidate's endorsement in `social.votedFor` (`votedFor[candidate] = current + msg.count`).
   - The voter's available votes decrease by `msg.count` (`store.votes -= msg.count`), asserting `msg.count > 0` and `store.votes >= msg.count`.

3. **Partial and Full Unvoting (`ActUnvote`)**:
   - Voters can specify `count` where `1 <= count <= votedFor[candidate]`.
   - If `count == votedFor[candidate]`, the candidate is deleted from `social.votedFor`.
   - If `count < votedFor[candidate]`, the entry is updated to `votedFor[candidate] - count`.
   - If `count > votedFor[candidate]` or candidate is not endorsed, transaction throws `Errors.NoVotesAvailable` or `Errors.NotVotedYet`.
   - Voter's available votes are restored (`store.votes += msg.count`), and a `VotingAction { positiveVote: false, count: msg.count }` is sent to the candidate.

4. **Accurate Bounce Recovery**:
   - When a positive `VotingAction` bounces back to the voter (e.g., candidate country mismatch):
     - Voter recovers `store.votes += msg.count`.
     - `social.votedFor[candidate]` is decreased by `msg.count` (and deleted if remaining count drops to 0), preserving any pre-existing endorsements for that candidate.

5. **Authority Threshold Transitions**:
   - On receiving `VotingAction`:
     - If `positiveVote`: `store.receivedVotes += msg.count`.
     - If `!positiveVote`: `store.receivedVotes = store.receivedVotes >= msg.count ? store.receivedVotes - msg.count : 0`.
     - Unless privileged, `isAuthorityAccount` is dynamically updated: `receivedVotes > AUTHORITY_THRESHOLD`.
