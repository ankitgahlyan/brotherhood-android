# BrotherHood Android — Layer-by-Layer Architecture & Porting Blueprint

This document is the authoritative architectural reference for AI agents converting the BrotherHood web wallet (`/home/zeta/jetton`) into this native Android application (`/home/zeta/connected/ton/android`).

---

## 1. Product Scope & Invariants (Decided via `/grill-me`)

1. **Pure BrotherHood TON Wallet**:
   - Supports **TON only** (`v5r1` / `v4r2` + hardware Ledger V4R2).
   - **Whitelisted Ecosystem Tokens Only**: Native TON (`GRAM`, 9 decimals), BrotherHood `FI` / `HD` (`FI_ADDRESS`, 5 decimals), and verified BrotherHood **Personal Tokens** (`PersonalMinter` / `PersonalWallet`, 5 decimals).
   - **Excluded**: Multichain (ETH, SOL, BTC, Tron), Perps (`:apps:wallet:features:perps`), Trading shelves (`:apps:wallet:features:trading`), and Staking.
2. **7 Top-Level Ecosystem Destinations (Scrollable Bottom Dock)**:
   1. **Wallet** — Multi-wallet carousel, `FiWallet` Upgrade Banner, Quick Actions (Send, Receive, Swap, Scan QR), whitelisted Assets list (`GRAM`, `FI`, Personal Tokens + Burn / Add by Minter), NFTs grid, and Activity History with ecosystem opcode classification.
   2. **BrotherHood** — Horizontal `ScrollableTabRow` + `HorizontalPager` with all **11 sub-tabs**:
      `account`, `network` (Circle max 10 & Ring max 100 + Follow/Unfollow + Report/Back Report), `claim` (Weekly Claim + Monthly EMI + Trigger Default EMI), `invite`, `vote` (Country-scoped 10 votes), `credit` (Buy Credit, Seekers, Loan Requirement Terms, Repay/Payback), `allowance` (Active, Grant, Spend Allowance), `gold` (Gold Coins transfer), `profile` (Username, H3 Cell, Country, Nominee), `deferred` (72h Holding contract pull payments), and `authority` (conditional moderation tab).
   3. **Personal Token** — 7 sub-tabs (`info`, `deploy` two-phase deterministic `metadataUri: null` + `ActSetPersonalJetton` then `ChangeMinterMetadata`, `mint`, `addresses`, `admin`, `topup`, `destroy`).
   4. **City Network** — 2 sub-tabs (`explore`, `tracked`) built on Uber H3 hexagonal cells (`Location` child contracts, `SHARD_DEPTH = 8`), interactive H3 Hex Map visualizer modal + Android GPS auto-detect, and automatic `@username -> ownerAddress` Contact Book syncing.
   5. **DAO** — 3 sub-tabs (`proposals`, `submit`, `vote`) interacting with `DaoProxy`, `Poll`, and `Voter` contracts.
   6. **Lottery** — Commit-reveal `Lottery` contract (Round ID, Prize Pool, 10 FI entry fee, Draw Winner).
   7. **DNS** — 3 sub-tabs (`explore`, `my-domains`, `admin`) scoped exclusively to **`.bro` domains** (`BRO_COLLECTION_RESOLVER`) with 7-day `$FI` auctions, 365-day renewals, target address linking, and Treasury admin controls.
3. **Swap & Mutual Credit Engine (`FI/HD` ↔ `Admin Personal Token`)**:
   - **Default From Token**: `FI` / `HD` (`FI_ADDRESS`, treated like GRAM).
   - **Default To Token**: The **Personal Token of the `FI` Admin** (derived from `FiStore.admin` or `adminFiWallet.personalJettonMinter`, treated as the reference stablecoin like USDT).
   - **Execution Engine**: Since Omniston and DeDust only run on TON Mainnet, Testnet swaps between `FI/HD` and Personal Tokens execute directly via the **BrotherHood Mutual Credit contract flow**:
     - `FI -> Personal Token`: sends `BuyCredit` (`0x00001147`) to the target issuer's `FiWallet`, minting `amount * multiplier` Personal Tokens to the buyer.
     - `Personal Token -> FI`: sends `Payback` (`0x00001148`) / burns Personal Tokens to redeem `FI` from the issuer.
     - On Mainnet (when deployed), optional Omniston/DeDust DEX pool routing can be used alongside Mutual Credit.
4. **Zero-Getter Batch BOC Hydration & Room Cache**:
   - Never call individual `runGetMethod` getters in loops (causes HTTP 429 on public RPCs).
   - Collect requested contract addresses across a **50ms debounce window**, chunk into **batches of 30**, query Toncenter v3 `GET /api/v3/accountStates?address=...&include_boc=true`, match `code_hash`, deserialize `data_boc` on `Dispatchers.Default`, and persist to Room (`BrotherhoodDatabase`) keyed by `(address, lt)`.
5. **Auto-Funding `FiWallet` with Notification**:
   - When an unlocked wallet's `FiWallet` (or Circle invitees' `FiWallet`) has `< 2.0 TON` and the main wallet has sufficient funds (preserving a `0.5 TON` reserve), automatically send `2.0 TON` and emit an **in-app toast/notification** via `MviRelay` informing the Member of the top-up.
6. **Testnet Default & Floating Developer Bubble**:
   - Defaults to **TON Testnet** with the deployed contract addresses below.
   - Unlocking **Developer Mode** (7 taps on `"brotherhood"` in Settings) activates a draggable **Floating Developer Bubble** overlay across the app giving instant access to API Telemetry (requests, latency, 429 hits), Room Contract Cache Explorer, BOC Payload Disassembler, and Custom RPC / API Key settings.

---

## 2. Target Module Structure

We add 3 focused modules to `settings.gradle.kts` while reusing the existing KMP MVI, Moon UI, security, and wallet infrastructure:

```
repo_root/
├── lib/
│   └── brotherhood/                   # (:lib:brotherhood) Pure Kotlin/TON contract codecs, opcodes, StateInit derivation, H3 helpers
├── apps/wallet/
│   ├── data/
│   │   └── brotherhood/               # (:apps:wallet:data:brotherhood) Room cache, Batch BOC Hydrator, RateLimiter, AutoFunder, Repositories
│   └── features/
│       └── brotherhood/               # (:apps:wallet:features:brotherhood) All BrotherHood Compose screens, GraphViewModels, Nav3 routers, DevBubble
```

### How Existing Modules Connect
- **`:kmp:mvi`** (`com.tonapps.mvi.graph.GraphViewModel`) — Property-graph MVI base class for every new ViewModel (`*Feature`).Read [kmp/mvi/README.md](../../kmp/mvi/README.md) before writing any ViewModel.
- **`:kmp:ui`** (`ui.theme.MoonTheme`, `ui.components.moon.*`) — Compose Multiplatform Moon design system (`MoonScaffold`, `MoonTopAppBar`, `MoonBundleCell`, `MoonBottomButtonCell`, `MoonEditText`, `MoonChips`, `MoonNav`).
- **`:apps:wallet:features:core`** (`com.tonapps.core`) — `ComposableFragment`, `NavigationDelegate`, `ResultStore`, `AmountInput`.
- **`:apps:wallet:features:ramp`** (`com.tonapps.deposit`) — Transaction signing (`SignUseCase`, `SignTransaction`, `ConfirmRequest`).
- **`:apps:wallet:instance:app`** (`com.tonapps.tonkeeperx`) — `RootActivity`, Koin wiring (`viewModelWalletModule.kt`, `KoinModule.kt`).

---

## 3. Layer-by-Layer Implementation Guide

### Layer 1: Smart Contract Codecs, Opcodes & Deterministic Derivation (`:lib:brotherhood`)

Port directly from `/home/zeta/jetton/wrappers-ts/*.ts` and `/home/zeta/jetton/apps/wallet/src/lib/brotherhood/config.ts` using `ton-kotlin` (`org.ton.cell.Cell`, `CellBuilder`, `CellSlice`, `org.ton.block.AddrStd`, `org.ton.hashmap.HashMapE`):

1. **Protocol Constants (`BrotherhoodConfig.kt`)**:
   - `FI_ADDRESS = "kQByVk5DwR_q9O0QECxai3CDpE-7Qimbb4OUE9Bt4Qz0deAE"`
   - `BRO_COLLECTION_RESOLVER = "kQCKrNefTDKT8hNkJ-wxxANdn6KXLa_3VqsRKFKEXyFUchTa"`
   - `BRO_TREASURY_ADDRESS = "kQCSUhA50ynSi1hxR9KrTLOCOk89iVGHG9wKS_1Agqi-OQF6"`
   - `DAO_PROXY_ADDRESS = "kQCe-0dlNfCYRw_YWKjunlJmxIDfSRWxvHS6FI-eflPgY1jZ"`
   - `FI_DECIMALS = 5` (`1 FI = 100_000` base units), `PERSONAL_DECIMALS = 5`, `TON_DECIMALS = 9`.
   - `SHARD_DEPTH = 8`.
2. **Known Code Hashes (`KnownCodeHashes.kt`)**:
   - Match base64 `code_hash` from `accountStates` to classify raw BOCs without getters:
     - `fiWallet`, `fiMinter`, `personalMinter`, `personalWallet`, `location`, `lottery`, `poll`, `walletV5R1`.
   - Always keep `BaseFiWallet` and `BasePersonalWallet` proxy code cells synchronized with `/home/zeta/jetton/build/*.json`.
3. **Storage Parsers (`store/*.kt`)**:
   - `FiStore.fromSlice(slice: CellSlice)` (`FossFi.ts`)
   - `FiWalletStore.fromSlice(slice: CellSlice)` (`FossFiWallet.ts`: `ActionPoints`, `Relationship`, `OtherInfo`, `WalletCodes`)
   - `PersonalStore.fromSlice(slice: CellSlice)` & `PersonalWalletStore.fromSlice(slice: CellSlice)` (`PersonalMinter.ts`, `PersonalWallet.ts`)
   - `LocationStore.fromSlice(slice: CellSlice)` (`Location.ts`)
   - `LotteryStorage.fromSlice(slice: CellSlice)` (`Lottery.ts`)
   - `PollStore.fromSlice(slice: CellSlice)` & `VoterStorage.fromSlice(slice: CellSlice)` (`Poll.ts`, `Voter.ts`)
   - `HoldingStorage.fromSlice(slice: CellSlice)` & `FollowingStorage.fromSlice(slice: CellSlice)` (`Holding.ts`, `Following.ts`)
   - `DnsItemStore.fromSlice(slice: CellSlice)` (`DnsItem.ts` — remember ADR/rule: `extra: Cell<ItemExtra>?` is in the 4th reference slot to keep root cell $\le 855$ bits).
4. **Message Builders & 32-Bit Opcodes (`messages/*.kt`)**:
   - Every contract message builder returns a `Cell` starting with its 32-bit opcode (`storeUInt(opcode, 32)`):
     - Membership & Lifecycle: `ActInvite` (`0x00001051`), `ActDeactivateMember` (`0x00001053`), `SelfCloseAccount` (`0x00001054`), `Destroy` (`0x00001059`), `UpgradeWallet` (`0x0000105a`), `AuthorityCloseAccount` (`0x0000105b`).
     - Profile & Social: `ChangeProfile` (`0x000010a1`), `ActFollow` (`0x000010a2`), `ActUnfollow` (`0x000010a3`).
     - Voting & Moderation: `ActVote` (`0x000010f1`), `ActUnvote` (`0x000010f2`), `ActDispatchAuthorityAction` (`0x000010f4`), `SetStatus` (`0x000010f6`), `ReportAction` (`0x000010f7`), `BackReportAction` (`0x000010f8`), `ActSubmitProposal` (`0x000010fa`), `ActVoteProposal` (`0x000010fc`).
     - Economy, Claims & Credit: `ActClaimWeeklyGrant` (`0x00001141`), `ActPayEmi` (`0x00001142`), `SetAllowance` (`0x00001143`), `SpendAllowance` (`0x00001144`), `AskGoldCoinsTransfer` (`0x00001145`), `BuyCredit` (`0x00001147`), `Payback` (`0x00001148`), `ActSetPersonalJetton` (`0x00001149`), `SetLoanRequirement` (`0x0000114a`), `RepayDebt` (`0x0000114b`), `TriggerDefaultEmi` (`0x0000114d`).
     - Deferred Payments: `ToggleDeferredPayment` (`0x576f30a1`), `RequestDeferredPayment` (`0x6a1bc924`), `ActClaimDeferredPayment` (`0x1f84b29c`), `ActCancelDeferredPayment` (`0x7c49e102`).
     - Personal Minter: `MintNewJettons` (`0x00001001`), `ChangeMinterAdmin` (`0x00001002`), `ChangeMinterMetadata` (`0x00001005`), `TopUpTons` (`0x00001007`), `BurnJettons` (`0x595f07bc`).
     - Lottery: `ActJoinLottery` (`0x00001191`), `EnterLottery` (`0x00001198`), `DrawWinner` (`0x0000119a`).
     - `.bro` DNS: `MintDomainFor` (`0x2c159bf4`), `FillUp` (`0x370fec51`), `EditRecord` (`0x4eb1f0f9`), `WithdrawFees` (`0x59a3c821`), `DnsBidRequest` (`0x62696430`), `FinalizeAuction` (`0x66696e61`), `DnsRenewRequest` (`0x72656e30`).
5. **Deterministic Off-Chain Address Derivation (`derivation/*.kt`)**:
   - `deriveFiWalletAddress(ownerAddress, fiMinterAddress, baseFiWalletCode)` with `SHARD_DEPTH = 8`.
   - **CRITICAL INVARIANT**: In `FiWalletStore`, `invitees` (`maps.invited`) already stores deployed `FiWallet` addresses, NOT raw owner addresses. Never call `deriveFiWalletAddress()` on an address taken from `invitees`!
   - `derivePersonalMinterAddress(ownerAddress, basePersonalWalletCode, personalWalletCode, personalMinterCode)` with `metadataUri = null` and `SHARD_DEPTH = 8`.
   - `deriveLocationAddress(h3Cell, fiMinterAddress, locationCode)` with `SHARD_DEPTH = 8`.

---

### Layer 2: Data, Room Cache, Batch Hydrator & Repositories (`:apps:wallet:data:brotherhood`)

Follows the modern Room + Koin pattern used in `:apps:wallet:data:multichain:wallet` and `:apps:wallet:data:cache`:

1. **Room Database (`BrotherhoodDatabase.kt`)**:
   - Built with `.setDriver(BundledSQLiteDriver())`, `.setQueryCoroutineContext(Dispatchers.IO)`, `JournalMode.WRITE_AHEAD_LOGGING`.
   - **Entities**:
     - `ContractCacheEntity(address: String, network: String, lt: Long, balance: Long, status: String, codeHash: String?, dataBocBase64: String?, contractType: String, parsedJson: String?, isOutdatedCode: Boolean, updatedAt: Long)`
     - `MetadataCacheEntity(address: String, metadataJson: String, updatedAt: Long)` (24h TTL)
     - `ContactBookEntity(username: String, network: String, ownerAddress: String, fiWalletAddress: String?, updatedAt: Long)`
     - `WatchedLocationEntity(h3Index: String, label: String?, addedAt: Long)`
     - `TrackedDomainEntity(domain: String, itemAddress: String, addedAt: Long)`
   - Exposes reactive `Flow<ContractCacheEntity?>` so ViewModels automatically re-emit when background hydration updates Room.
2. **Rate Limiter & Telemetry Interceptor (`ProviderRateLimiter.kt`, `TelemetryRepository.kt`)**:
   - Per-provider Kotlin `Mutex` + FIFO queue (`toncenter`, `tonapi`): 100ms spacing when API key/custom URL is set, 1000ms spacing on public endpoints, exponential backoff on HTTP 429.
   - Records every request's URL, status code, latency, and 429 count into `TelemetryRepository` (`StateFlow<List<ApiCallLog>>`) for the Developer Bubble.
3. **Zero-Getter Batch Hydrator (`AccountStateHydrator.kt`)**:
   - Collects `hydrate(address)` requests into a `Channel` / `MutableSharedFlow`, debounces for **50ms**, deduplicates in-flight addresses, and chunks into **30 addresses per batch**.
   - Calls Toncenter v3 `GET /api/v3/accountStates?address=...&include_boc=true`.
   - On `Dispatchers.Default`, compares returned `lt` against `ContractCacheDao`: if `lt` is unchanged, skips BOC parsing; otherwise matches `code_hash`, runs `*.fromSlice()`, and upserts `ContractCacheEntity`.
   - **Zero-Getter Fallback Rule**: If `code_hash` mismatches and `fromSlice` fails, never fall back to `runGetMethod` — mark `isOutdatedCode = true` so the UI displays the `UpgradeBanner`.
4. **Background Sync & Auto-Funding (`BrotherhoodSyncManager.kt`, `AutoFiWalletFunder.kt`)**:
   - Triggered when the active wallet changes or on pull-to-refresh (guarded by a 60s TTL unless `force = true`).
   - Pre-computes off-chain addresses (`fiWalletAddress`, `personalMinterAddress`, `adminFiWalletAddress`) and hydrates them in the first batch, followed by Circle (10), Ring (100), and Location contracts in the second batch.
   - `AutoFiWalletFunder`: Checks if the user's `FiWallet` or Circle invitees' `FiWallet`s have `< 2.0 TON` (`2_000_000_000L` nanoTON). While `(activeWalletBalance - 0.5 TON) >= 2.05 TON`, builds and signs a multi-recipient transfer of `2.0 TON` per underfunded `FiWallet` (tracking funded addresses in a session `Set<String>` to prevent duplicate top-ups) and emits a notification event.
5. **Koin Wiring (`Module.kt`)**:
   - Export `val brotherhoodDataModule = module { ... }` and register it in `AppDelegate.kt` (`apps/wallet/instance/main/src/main/java/com/tonapps/tonkeeper/AppDelegate.kt`).

---

### Layer 3: Presentation / MVI `GraphViewModel` & Compose UI (`:apps:wallet:features:brotherhood`)

All screens MUST follow the **Property Graph MVI** architecture (`kmp/mvi/README.md`) and **Moon Design System** (`:kmp:ui`):

1. **`GraphViewModel` Rules (Mandatory)**:
   - Extend `GraphViewModel<MyState, MyAction>(), MyState` where `MyState : MviViewState` declares `val prop: StateFlow<T>` nodes and `MyAction : MviAction` is a `sealed interface`.
   - Never use `MviFeature` (deprecated) or a single monolithic state data class.
   - Cache every node with `.cacheState(initialValue = ...)` or `.cacheStateWithLoading()` on the default `mainScope` (`Dispatchers.Main.immediate`). Never use `flowOn` or `cacheState(scope = stateScope)`.
   - Wrap async work in `.transformStateLatest { ... }` (or `.transformStateFirst { ... }` for submit/continue actions). Always catch exceptions inside loaders (`runCatching { ... }`, calling `verifyError(t)` so `CancellationException` rethrows) and return a fallback/`KResult.Err` so a node never stays stuck in `KState.Loading`.
   - Emit navigation and toast events through `private val relay = MviRelay<MyEvent>()`.
2. **Navigation & Routing**:
   - Define `@Serializable sealed interface BrotherhoodRoutes : NavKey` and render with `MoonNav(backStack = backStack)` (`:kmp:ui`).
   - Host inside `ComposableFragment` subclasses (`BrotherhoodFragment`, `PersonalJettonFragment`, `CityNetworkFragment`, `DaoFragment`, `LotteryFragment`, `BroDnsFragment`, `DeveloperSheetFragment`).
   - For transaction signing, construct a `ConfirmRequest` (or invoke `SignUseCase` in `:apps:wallet:features:ramp`) so all transactions pass through `PasscodeManager` / biometric confirmation and Ledger signing when applicable.
3. **Register ViewModels**:
   - Add `viewModelOf(::BrotherhoodFeature)`, `viewModelOf(::PersonalJettonFeature)`, `viewModelOf(::CityNetworkFeature)`, `viewModelOf(::DaoFeature)`, `viewModelOf(::LotteryFeature)`, `viewModelOf(::BroDnsFeature)`, `viewModelOf(::DeveloperFeature)` in `apps/wallet/instance/app/src/main/java/com/tonapps/tonkeeper/koin/viewModelWalletModule.kt`.

---

## 4. Ordered Implementation Roadmap (Step-by-Step for Agents)

Execute these phases in order so each layer is tested and verified before the layer above it is built:

- **Phase 1 — `:lib:brotherhood` (Contract Codecs, Opcodes & Derivation)**:
  - Create `:lib:brotherhood` module in `settings.gradle.kts`.
  - Port `BrotherhoodConfig`, `KnownCodeHashes`, all Tolk store `fromSlice` parsers (`FiStore`, `FiWalletStore`, `PersonalStore`, `PersonalWalletStore`, `LocationStore`, `LotteryStorage`, `PollStore`, `HoldingStorage`, `FollowingStorage`, `DnsItemStore`), all 32-bit opcode message builders, and deterministic `SHARD_DEPTH = 8` address calculators.
  - Write unit tests verifying `fromSlice` and address derivation against known Testnet BOC vectors from `/home/zeta/jetton`.
- **Phase 2 — `:apps:wallet:data:brotherhood` (Room Cache, Rate Limiter, Batch BOC Hydrator & Repositories)**:
  - Create `:apps:wallet:data:brotherhood` with `BrotherhoodDatabase`, `ProviderRateLimiter`, `TelemetryRepository`, and `AccountStateHydrator` (50ms window, 30-address Toncenter v3 `accountStates?include_boc=true` batcher).
  - Implement `BrotherhoodRepository`, `PersonalJettonRepository`, `CityNetworkRepository`, `DaoRepository`, `LotteryRepository`, `BroDnsRepository`, and `AutoFiWalletFunder` (with notification relay).
- **Phase 3 — Base Wallet Scoping, Ecosystem Assets & Mutual Credit Swap**:
  - Scope `:apps:wallet:features:portfolio` and `RootActivity` to TON-only (`v5r1`/`v4r2`/Ledger) and whitelisted ecosystem tokens (`GRAM`, `FI`, verified Personal Tokens).
  - Add Ecosystem Opcode & Tolk struct classification (`KNOWN_OPCODES`, `FRIENDLY_OPCODE_TITLES`) to `:apps:wallet:features:events`.
  - Update `:apps:wallet:features:swap` (`GraphSwapFeature`) to default to `FI/HD` ↔ `Admin Personal Token` using the Mutual Credit (`BuyCredit` / `Payback`) flow on Testnet.
  - Update Send flow to support `@username` Contact Book resolution, `.bro` DNS resolution (`BRO_COLLECTION_RESOLVER`), and `SpendAllowance` delegated sending.
- **Phase 4 — `:apps:wallet:features:brotherhood` (Core 11-Tab BrotherHood Screen)**:
  - Implement `BrotherhoodFeature` (`GraphViewModel`) and `BrotherhoodScreen` with the 11 sub-tabs (`account`, `network`, `claim`, `invite`, `vote`, `credit`, `allowance`, `gold`, `profile`, `deferred`, `authority`) and `MemberDetailSheet`.
- **Phase 5 — Personal Token, City Network (H3), DAO, Lottery & `.bro` DNS Screens**:
  - Implement `PersonalJettonFeature` & 7 sub-tabs (including 2-phase deterministic deployment).
  - Implement `CityNetworkFeature`, GPS → H3 index converter, interactive `H3VisualizerModal`, and Contact Book auto-sync.
  - Implement `DaoFeature` (3 sub-tabs), `LotteryFeature`, and `BroDnsFeature` (3 sub-tabs).
- **Phase 6 — 7-Tab Scrollable Bottom Dock & Floating Developer Bubble**:
  - Wire the 7-tab scrollable bottom navigation dock in `RootActivity` / main shell.
  - Add 7-tap `"brotherhood"` unlock in Settings and the draggable `DeveloperBubble` overlay (API Telemetry, Room State Explorer, BOC Payload Disassembler, Network/RPC Config).
