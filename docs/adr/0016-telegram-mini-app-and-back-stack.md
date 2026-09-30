# ADR 0016: Telegram Mini App Architecture and Unified Back Stack

## Status
Accepted

## Context
The BrotherHood wallet application is designed as a standalone Progressive Web App (PWA) on mobile and desktop browsers. To expand accessibility and user acquisition on The Open Network (TON), the wallet must also operate seamlessly as a Telegram Mini App (TWA / TMA) inside Telegram clients on iOS, Android, and Desktop.

In mobile environments and Telegram Mini Apps, back navigation poses critical challenges:
1. Pressing the Telegram native `BackButton` or using the Android hardware/gesture back should close open dialogs, drawers, and modal sheets before triggering full page transitions.
2. In mobile web and standalone PWAs, the browser `popstate` event must be intercepted to pop modals rather than bouncing the user back to previous pages or out of the app.
3. UI elements, sticky headers, and bottom action sheets must properly respect Telegram safe area insets (`--tg-safe-area-top`, `--tg-safe-area-bottom`) on notched and curved devices.
4. Dragging bottom sheets must not accidentally dismiss the Telegram Mini App via vertical swipe-to-close gestures.

## Decision

1. **Dual-Mode Auto-Detection**:
   - The application dynamically detects if it is executed within a Telegram WebApp container (using `@telegram-apps/sdk` and `window.Telegram.WebApp`).
   - If running inside Telegram, it initializes Mini App features (viewport expand, safe areas, theme sync, back button, haptics, swipe lock).
   - If running in standard web/PWA mode, it gracefully operates without error and provides standard browser history coordination.

2. **3-Tier Unified Back Stack**:
   - **Tier 1 (Modals/Drawers/Sheets)**: When a modal opens, it registers a dismiss callback to the Back Stack. Any back press (Telegram `BackButton` or HTML5 `popstate`) pops and executes the top-most modal callback first.
   - **Tier 2 (Router Navigation)**: When no modals are open and the current router path is not at the root dashboard (`/wallet`), the Back Stack triggers router history navigation (`router.history.back()`).
   - **Tier 3 (Root Exit)**: When at the root dashboard with no open modals, the Back Stack hides the Telegram `BackButton`, permitting default Telegram close/minimize behavior.

3. **Safe Area Inset Injection**:
   - Dynamic safe area insets reported by the Telegram WebApp are mapped to CSS custom properties (`--tg-safe-area-top`, `--tg-safe-area-bottom`, `--tg-content-safe-area-top`, `--tg-content-safe-area-bottom`) with standard CSS `env(safe-area-inset-*)` fallbacks.
   - Layout containers apply these properties to ensure status bars and home indicators never obscure wallet UI.

4. **Vertical Swipe-to-Close Lock**:
   - While interactive modals, drawers, or gesture-driven UI components are open, `disableVerticalSwipes()` is invoked to prevent accidental Mini App dismissal.

## Consequences

- Seamless user experience both inside Telegram Mini App and across mobile/desktop browsers without needing separate codebases or separate deployment builds.
- Clean separation between modal lifecycle management and routing history.
- Consistent haptic feedback and safe-area rendering on mobile devices.
