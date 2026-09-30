# Reputation Grant, Monthly Due (EMI), and Debt Penalties

## Context
Under the original economic model, weekly claims were strictly capped at 2 years of fixed 11,111 FI issuance, after which claims ceased completely. Meanwhile, passive lifetime demurrage was enforced via arbitrary keeper triggers with no personal accountability or penalty for non-payment.

To align recurring issuance with ongoing social trust and community reputation, we need:
1. An ongoing lifetime grant stream rewarding members who have accumulated community trust (`receivedVotes`).
2. A modest baseline lifetime floor after the initial 2-year universal basic incubation period ends.
3. Replacing passive decay with an active monthly commitment (**Monthly Due / EMI**) of 2,500 FI, placing responsibility on the account owner, enforced by keeper defaults with a 5% debt penalty if delinquent past a 24-hour grace window.
4. Automatic garnishment of incoming weekly claims against outstanding debt so indebted members can restore good standing through protocol claims.

## Decision

### 1. Hybrid Weekly Claim Issuance
Weekly claims continue past the initial 2-year window for the member's lifetime:
- **First 2 Years (`now < accountInit + 2 years`)**:
  `Total Claim = 11,111 FI (Fixed Base Grant) + (receivedVotes * 10 FI) (Reputation Grant)`.
- **Post-2-Years Lifetime (`now >= accountInit + 2 years`)**:
  `Total Claim = 500 FI (Baseline Lifetime Floor) + (receivedVotes * 10 FI) (Reputation Grant)`.
- **Reputation Grant Rate**: 10 FI per received vote per week, uncapped, reflecting moral trust directly into protocol patronage.
- **Interval & Gating**: Requires 1-day activation wait after `accountInit`, followed by a 7-day cooldown between claims.

### 2. Automatic Debt Repayment on Weekly Claim
If an account carries `store.debt > 0`:
- Any claimed weekly grant is automatically applied first to reduce `store.debt`.
- If `store.debt` is cleared to 0, `store.debts` is reset to `false`.
- Only the remaining net surplus (if any) is added to `store.jettonBalance`.
- The full minted amount is reported to `FossFi` minter (`NotifyMinter`, `queryId = 1`) to preserve total supply accounting.

### 3. Monthly Due (EMI)
- **Amount**: Fixed 2,500 FI per 30-day period (`DECAY_MONTH_SECONDS = 2592000`).
- **Owner Payment (`ActPayEmi`)**:
  - The wallet owner can initiate payment once the 30-day period has elapsed (`now >= time.lastDecay + DECAY_MONTH_SECONDS`).
  - Requires `store.jettonBalance >= 2,500 FI`.
  - Burns 2,500 FI, notifies the minter (`queryId = 0`), and advances `time.lastDecay`.

### 4. Overdue Default & 5% Penalty (`TriggerDefaultEmi`)
- **Grace Period**: 24 hours (`EMI_GRACE_PERIOD = 86400`) from the 30-day due date (`time.lastDecay + DECAY_MONTH_SECONDS`).
- **Default Trigger**:
  - If unpaid after `30 days + 24 hours`, any keeper or outsider may send `TriggerDefaultEmi`.
  - Any available `jettonBalance` up to 2,500 FI is burned toward the due.
  - The unpaid shortfall is added to `store.debt`.
  - `store.debts` is set to `true`.
  - A penalty of 5% of total debt is added: `store.debt += (store.debt * 5 / 100)`.
  - Advances `time.lastDecay += DECAY_MONTH_SECONDS`.
