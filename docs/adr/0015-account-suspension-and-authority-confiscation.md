# ADR 0015: Account Suspension and Authority Sanction with Fund Confiscation

## Status
Accepted

## Context
BrotherHood relies on social accountability and invite lineages for Sybil-resistance and network security. When bad actors behave maliciously or members become inactive or compromised, two distinct tiers of enforcement are required:

1. **Lineage Accountability (Circle & Ring Management)**: Inviters (`invitor`) and upstream inviters (`invitor0`) vouch for their invitees and bear reputational and financial responsibility for them. If an invitee turns out to be abusive, spamming, or compromised, their direct inviter or upstream inviter needs a reversible mechanism to suspend or reactivate the member's account without destroying their funds or permanently closing the account.
2. **Protocol Authority Enforcement (Malicious Account Confiscation)**: When an account engages in systemic attacks, fraudulent activities, or malicious exploits, elevated Authority accounts (`isAuthorityAccount` or `isPrevilegedAccount`) require a punitive, high-severity action to revoke active status and immediately confiscate all FI tokens held in the offending wallet back to the authority's wallet for restitution or protocol safeguarding.

Previously, `FossFiWallet` only had `AuthorityCloseAccount` (which closes a deceased member's account and passes assets to their nominated successor) and a generic `SetStatus` call. It lacked dedicated opcodes for direct lineage suspension and authority fund confiscation.

## Decision

### 1. Circle/Ring Member Active State Toggle & Fund Transfer (`DeActivateCircleRing`)
- **Opcode**: `0x00001055` (`DeActivateCircleRing`), with internal message `0x00001056` (`DeActivateCircleRingInternal`).
- **Initiator**: Account owner of an active member wallet (`store.active == true`).
- **Target**: Specified recipient address (`transferRecipient.getAddrFiWallet()`).
- **Fields**:
  - `transferRecipient: address`: Target member account being managed.
  - `fundsReceiver: address? = null`: Destination for transferred funds. Defaults to caller's `FiWallet` when omitted.
  - `amount: coins = 0`: Amount of FI tokens to transfer. If greater than available `store.jettonBalance`, all available balance is transferred and the remaining deficit is added to `store.debt` (`store.debts = true`).
  - `toggleActive: bool = true`: Whether to toggle the member's `store.active` flag. When set to `false`, performs the fund transfer/debt creation without altering the member's active status.
- **Verification at Target**:
  The target `FossFiWallet` checks `in.senderAddress == nomins.invitor0 || in.senderAddress == nomins.invitor`.
- **Properties**:
  - Reversible: Inviter or upstream inviter can toggle active back on if the dispute is resolved.
  - Granular: Supports pure suspension, fund transfer/restitution, or punitive debt assignment without deactivation.
  - Gas: Minimal forward fee covered via standard `GAS.AUTHORITY` / `GAS.INVITE` allocation (`0.1 TON`).

### 2. Authority Sanction with Flexible Fund Confiscation (`ActDispatchAuthorityAction`)
- **Opcode**: `0x000010f4` (`ActDispatchAuthorityAction`), triggering internal message `0x000010f5` (`AuthorityAction`).
- **Initiator**: Account owner of an authorized Authority account (`store.isAuthorityAccount || store.isPrevilegedAccount`).
- **Target**: Specified recipient address (`transferRecipient.getAddrFiWallet()`).
- **Fields**:
  - `transferRecipient: address`: Malicious member account being sanctioned.
  - `fundsReceiver: address? = null`: Destination for confiscated funds (Authority self, Treasury, or victim).
  - `amount: coins = 0`: Amount of FI tokens to confiscate (defaults to 100% of balance if 0). If the specified amount exceeds `store.jettonBalance`, the shortfall is added as on-chain `store.debt` (`store.debts = true`).
  - `toggleActive: bool = true`: Optional toggle for `store.active`.
- **Verification at Target**:
  - Target must not be an authority account (`assert (!store.isAuthorityAccount)`).
  - Toggles target's active state if `msg.toggleActive` is true.
  - Transfers requested/available funds to the specified `fundsReceiver` via `InternalTransferStep` with forwardPayload `"authorityFreeze"`.
- **Properties**:
  - Punitive & Restitutive: Confiscates malicious assets to authority or designated recipient for remediation.
  - Gas: `GAS.AUTHORITY` (`0.1 TON`) with remaining message value forwarded to complete the internal transfer.

### 3. Frontend & Architecture Integration
- **`useDeactivateMember`**: Extended with options `{ fundsReceiver?, amount?, toggleActive? }`.
- **`useAuthorityActions`**: Extended `dispatchAuthorityAction` with options `{ fundsReceiver?, amount?, toggleActive? }`.
- **User Interface**:
  - `MemberDetailView`: Both Circle/Ring Management and Authority Sanction sections include an "Advanced" mode exposing optional beneficiary recipient address, custom transfer amount (with debt creation notice), and an active state toggle checkbox.
  - `BrotherhoodScreen`: Authority "Sanction" sub-tab includes target address, optional funds receiver, amount input, and toggle active checkbox.

## Consequences
- Lineage inviters can enforce local discipline or recover funds from their invitees with deficit recorded as debt.
- Authorities can redirect confiscated funds directly to victims, treasury, or custom beneficiaries rather than only self.
- Account deactivation is completely optional across both lineage and authority actions.
- All wrapper interfaces (`FossFiWallet.gen.tolk`, `FossFiWallet.gen.ts`) and test suites maintain strict TL-B alignment.
