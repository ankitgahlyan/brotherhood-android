# Agent Guide — BrotherHood Android (`brotherhood-android`)

This file is the primary routing catalog for AI agents working on the BrotherHood Android application. Keep this file small and stable; consult the disclosed reference docs linked below based on your current task.

## Agent skills

### Issue tracker

Issues and specs for this repo live as GitHub issues on `ankitgahlyan/brotherhood-android` (uses the `gh` CLI). See [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md).

### Triage labels

Five canonical triage roles: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See [docs/agents/triage-labels.md](docs/agents/triage-labels.md).

### Domain docs

Single-context layout: canonical terminology lives in [GLOSSARY.md](GLOSSARY.md) at the repo root and architecture decisions live in [docs/adr/](docs/adr/). See [docs/agents/domain.md](docs/agents/domain.md).

---

## Task-Based Navigation & Context Pointers

### BrotherHood Porting & Layer-by-Layer Architecture
- [docs/agents/architecture.md](docs/agents/architecture.md) — **Read first** when building or porting any BrotherHood feature from `/home/zeta/jetton` (`:lib:brotherhood` contract codecs/opcodes, `:apps:wallet:data:brotherhood` Room cache & Zero-Getter 30-address Batch BOC Hydrator, `:apps:wallet:features:brotherhood` 7-tab dock, 11 BrotherHood sub-tabs, Personal Token, City Network H3, DAO, Lottery, `.bro` DNS, Mutual Credit Swap, Auto-Funding, and Floating Developer Bubble).
- [GLOSSARY.md](GLOSSARY.md) — Canonical BrotherHood domain vocabulary (`Member`, `Account`, `Base Account`, `FI`/`HD`, `Admin Personal Token`, `Personal Token`, `Circle`, `Ring`, `Weekly Claim`, `Monthly Due (EMI)`, `Allowance`, `Deferred Payment`, `Holding Contract`, `Loan Requirement`, `Poll`, `Voter`, `DAO Proxy`).
- [docs/adr/](docs/adr/) — 20 Architecture Decision Records governing BrotherHood smart contracts, deterministic deployment, caching, and UX invariants.
- `/home/zeta/jetton` — Source web repository (`wrappers-ts/*.ts` for exact binary TL-B/Cell codecs and 32-bit opcodes; `apps/wallet/src/lib/brotherhood/config.ts` for deployed Testnet addresses).

### MVI, Threading & UI Layer (`GraphViewModel` + Moon Compose + Nav3)
- [kmp/mvi/README.md](kmp/mvi/README.md) — **Mandatory reading** before creating or editing any ViewModel (`*Feature`). Explains `GraphViewModel` property-graph nodes, `cacheState` on `mainScope`, `transformStateLatest`/`transformStateFirst`, `MviRelay`, and threading invariants.
- [kmp/ui/](kmp/ui/) — Compose Multiplatform **Moon** design system (`MoonTheme`, `MoonScaffold`, `MoonTopAppBar`, `MoonBundleCell`, `MoonBottomButtonCell`, `MoonEditText`, `MoonChips`, `MoonNav`).
- [apps/wallet/features/core/](apps/wallet/features/core/) — `ComposableFragment`, `NavigationDelegate`, `ResultStore`, `AmountInput`.
- [apps/wallet/instance/app/src/main/java/com/tonapps/tonkeeper/koin/viewModelWalletModule.kt](apps/wallet/instance/app/src/main/java/com/tonapps/tonkeeper/koin/viewModelWalletModule.kt) — Koin registration for all `*Feature` ViewModels.

### Data, Persistence, Security & Blockchain
- [lib/blockchain/](lib/blockchain/) — TON `Cell`/`CellSlice`/`AddrStd` utilities, wallet contracts (`V4R2`, `V5R1`), `ConfirmRequest`.
- [lib/security/](lib/security/) — Encryption, Android KeyStore `Vault`, `PasscodeBiometric`.
- [apps/wallet/data/cache/](apps/wallet/data/cache/) — Room + `BundledSQLiteDriver` reference pattern (`CacheDatabase`, `JsonCacheRepository`).
- [apps/wallet/features/ramp/](apps/wallet/features/ramp/) — Transaction signing (`SignUseCase`, `SignTransaction`, Ledger signing).

### Build, Android CLI & Code Quality
- [README.md](README.md) — Build flavors, Gradle commands (`./gradlew :apps:wallet:instance:main:assembleDefaultDebug`), and module overview.
- [settings.gradle.kts](settings.gradle.kts) & [gradle/libs.versions.toml](gradle/libs.versions.toml) — Module definitions and centralized dependency catalog (never add unapproved libraries).
- [detekt/detekt.yml](detekt/detekt.yml) & [rules/CHECK.MD](rules/CHECK.MD) — Static analysis rules (`MaxIssues: 0`) and pre-PR review protocol.
- **Android CLI (`android`)**: Use `android docs search "<keywords>"` and `android docs fetch <kb-url>` (see the `android-cli`, `navigation-3`, `navigation-event`, and `edge-to-edge` skills) for authoritative Android/Jetpack documentation and device/emulator inspection (`android layout`, `android screen`).
