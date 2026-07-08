# CYL Home Audit

Date: 2026-07-09
Surface: Android Home screen
Evidence: `docs/audits/home/01-home-current.png`

## Audit Scope

This audit covers the Home tab only: profile entry, top tab control, Recents rail, Private page list, page row actions, and bottom command bar.

The audit uses a fresh emulator screenshot captured for this run. It does not claim full accessibility compliance because screen-reader traversal, TalkBack labels, contrast tooling, and runtime frame metrics were not captured.

## User Goal

The user should be able to open recent/private pages, create a new page, search, open AI, and switch to chat/activity with minimal visual noise.

## Step List

1. Home loaded
   Health: Functional, readable, but visually heavy at the top and bottom.

2. Scan Recents
   Health: Discoverable, but cards consume more space than the amount of information shown.

3. Scan Private pages
   Health: Clear and scannable, but row density and bottom overlap need polish.

4. Access primary actions
   Health: Search, AI, and create are visible, but the bottom command bar competes with page rows.

## Strengths

1. The monochrome palette is now much closer to the requested black/white direction. The screen no longer reads as purple or overly branded.

2. Page rows have stable visual rhythm: icon, title, timestamp, and actions are predictable.

3. The Home, AI/chat, and activity tabs are reachable from the top without labels, and the icons are large enough to understand after first use.

4. Compose list rendering already uses stable keys for page rows and recent pages in `HomeRoute.kt`, which is good for scroll performance.

## UX Risks

1. Top chrome is too dominant for a utility home screen.
   Evidence: The profile square and large segmented control take a large vertical band before content starts.
   Code: `HomeHeader` in `androidApp/src/main/java/com/changeyourlife/cyl/presentation/home/HomeRoute.kt:640`, `CylFloatingChromeSurface` in `androidApp/src/main/java/com/changeyourlife/cyl/presentation/components/CylChromeBars.kt:30`.
   Recommendation: Reduce top control height and elevation, use quieter inactive tab backgrounds, and make the profile button feel like part of the same row rather than a separate floating card.

2. Recents cards feel oversized for two fields of information.
   Evidence: Each card is a wide rounded rectangle with mostly empty space.
   Code: `RecentPageCard` in `HomeRoute.kt:1317`.
   Recommendation: Make Recents more compact, closer to a lightweight rail item. Keep height around 56-64dp and reduce radius/elevation feeling.

3. Bottom command bar hides or competes with lower list content.
   Evidence: The screenshot shows page rows visually continuing underneath the bottom bar area.
   Code: `HomeBottomBar` in `HomeRoute.kt:1015`, `CylBottomCommandBar` in `CylChromeBars.kt:79`.
   Recommendation: Increase bottom list padding or add a subtle bottom spacer/fade so the final row and its three-dot action are never trapped behind the bar.

4. Too many surfaces use the same rounded-card language.
   Evidence: Profile, tab bar, recents, row icons, and bottom AI bar all feel like separate floating objects.
   Recommendation: Keep the bottom command bar as the main floating object, flatten the top tabs and recents, and let the page list be the quiet main content.

5. Duplicate/near-duplicate page names make the list hard to disambiguate.
   Evidence: Two `Expense Bulan 7` rows appear with close timestamps.
   Recommendation: Consider showing a small secondary signal for database/page type or last action. Do not add badges everywhere; only help when names collide.

## Accessibility Risks

1. Icon-only top tabs may be unclear for first-time users.
   Code has content descriptions, but screenshot-only audit cannot verify TalkBack order.
   Recommendation: Keep content descriptions, and consider long-press tooltips or a short label in accessibility semantics only.

2. The bottom command bar may interfere with reachability and visibility of row actions.
   Recommendation: Ensure final scroll position lets the last row sit fully above the command bar.

3. The three-dot action is visually small.
   Code uses a 44dp `IconButton`, which is good. The visible dot cluster is still subtle.
   Recommendation: Keep 44dp hit area, but consider slightly stronger contrast or a quiet hover/ripple only on press.

## Performance Notes

1. Good: `LazyColumn` and `LazyRow` are used, with stable keys for page collections.
   Code: `HomeRoute.kt:379`, `HomeRoute.kt:414`, `HomeRoute.kt:1304`.

2. Good: There is no obvious heavy work in row rendering from code review. Formatting timestamps per row is acceptable for the current list size, but if Home grows large, preformatted display strings could reduce render work.

3. Watch: The whole Home route is still a large composable with many modal states. It is functional, but long-term maintainability and recomposition tracing would improve if Home chrome, Home list, and Home sheets had narrower state inputs.

No CPU, frame, or memory metrics were captured in this audit.

## Recommended Fix Order

1. Fix bottom overlap first.
   Increase list bottom padding and confirm the last row can scroll fully above the bottom command bar.

2. Quiet the top chrome.
   Reduce elevation/height, flatten inactive tab backgrounds, and visually connect profile with tabs.

3. Compact Recents.
   Reduce card height/width and empty padding; keep it useful but less dominant.

4. Tune list density.
   Keep page rows readable, but reduce excessive vertical whitespace if page count is high.

5. Add a collision helper for duplicate page names.
   Only when two nearby rows have the same title, show a subtle secondary clue.
