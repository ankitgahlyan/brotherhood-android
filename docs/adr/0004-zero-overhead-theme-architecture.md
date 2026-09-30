# Zero-Overhead OKLCH Theme Engine & OLED Architecture

## Context
A modern crypto wallet requires responsive, high-fidelity theming (Light, Midnight Slate Dark, OLED Pure Black, and System Sync) without introducing React re-render cascades, runtime theme calculation overhead, or Flash of Unstyled Content (FOUC). Modern display devices (AMOLED/OLED mobile screens) benefit significantly in power efficiency and visual contrast from true `#000000` pitch blacks with tailored high-contrast semantic borders.

## Decision

1. **CSS Variable & OKLCH Semantic Color Tokens**:
   - The theme token system is implemented entirely in CSS Custom Properties using the OKLCH color space (`App.css`).
   - Color shifts cascade automatically via the DOM without executing JavaScript layout calculations or re-rendering React component subtrees.
   - Semantic tokens: `--background`, `--foreground`, `--card`, `--card-foreground`, `--popover`, `--popover-foreground`, `--primary`, `--primary-foreground`, `--secondary`, `--secondary-foreground`, `--muted`, `--muted-foreground`, `--accent`, `--accent-foreground`, `--destructive`, `--destructive-foreground`, `--border`, `--input`, `--ring`.

2. **Zero-Overhead FOUC Prevention**:
   - In `index.html`, a synchronous inline `<script>` runs immediately before any stylesheet or body rendering.
   - The script inspects `localStorage.getItem('brotherhood-theme')` or falls back to `window.matchMedia('(prefers-color-scheme: dark)')`, immediately toggling `.dark` and setting `[data-theme="light" | "dark" | "oled"]` on `document.documentElement` alongside `<meta name="color-scheme">`.

3. **Theme Store & System Scheme Synchronization**:
   - `core/theme/use-theme.ts` manages reactive theme state (`'system' | 'light' | 'dark' | 'oled'`) via a lightweight Zustand store.
   - Listens to `window.matchMedia('(prefers-color-scheme: dark)')` to dynamically react when system settings change in `system` mode.
   - Persists user preferences to `localStorage` under the `'brotherhood-theme'` key.

4. **Four Distinct Visual Modes**:
   - **Light**: Crisp daylight OKLCH slate palette with subtle border delimiters.
   - **Midnight Dark**: Deep slate navy tone for reduced eye strain in low-light environments.
   - **OLED (Pure Black)**: `#000000` root canvas, high-contrast borders (`oklch(0.24 0.015 260)`), and deep card surfaces for maximum battery efficiency on AMOLED panels.
   - **System**: Automatic sync with the host operating system color scheme preference.

5. **Dual UI Access**:
   - **Quick Toggle**: Sun / Moon / Sparkles button in the Dashboard Header for instant toggle.
   - **Appearance Selector**: Dedicated multi-option selector in Settings Modal for explicit mode selection.
