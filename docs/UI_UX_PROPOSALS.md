# Refocus UI/UX optimization proposals

## Current experience

The app already has a clear three-part structure:

- **应用** controls the monitoring switch and guarded app list.
- **记录** shows today's score, usage, completion count, and recent sessions.
- **设置** explains privacy and guides the user through required permissions.

The system overlay asks for a purpose and duration, then presents completion,
grace, and overdue decisions. The visual language is consistent, but the main
screen emphasizes configuration more than the user's current focus state. The
permission setup also mixes mandatory, optional, and vendor-specific actions at
the same visual level.

## Proposal A: Quiet control center

Best for the next release because it preserves the current architecture and has
the lowest implementation risk.

### Main screen

- Replace the large static header with one status card: **守门中 / 已暂停 /
  还需完成设置**.
- Put the primary action inside that card. When setup is incomplete, the action
  should open the next required permission instead of showing a disabled switch.
- Show selected apps directly below as compact icon chips. Move the full app
  picker behind **管理应用**.
- Keep search only inside the picker so the normal screen stays short.
- Add a small current-session strip when a timer is running: app, purpose,
  elapsed/planned time, and whether the session is paused.

### Navigation

- Use Material 3 bottom navigation for **守门 / 记录 / 设置**.
- Add icons and preserve labels. The current text-only segmented tabs are easy
  to miss and consume useful vertical space.

### Setup

- Convert permissions into a checklist with three groups:
  **必须** (accessibility), **推荐** (notifications and battery), and
  **仅 vivo** (autostart).
- Hide the vivo item on other manufacturers.
- After returning from settings, briefly highlight the row whose state changed.

### Expected result

A calmer home screen, less scrolling, and a clearer answer to “is Refocus
working right now?”

## Proposal B: Today-first focus dashboard

Best if the product should feel like a daily focus companion rather than an app
blocker.

### Main screen

- Make **今天** the default destination.
- Lead with today's score and a compact ring showing focused time versus
  overrun time.
- Show the current or last session as the main card with one contextual action:
  **继续专注**, **完成回顾**, or **选择守护应用**.
- Move the monitoring switch into the app bar as a persistent status control.
- Place the guarded app list in a secondary **规则** screen.

### History

- Group sessions by day instead of one continuous list.
- Add filters for app, completed/unfinished, and overdue.
- Replace the current “planned → actual” text with a small visual bar and keep
  exact values available in the detail view.
- Explain score changes inline: `完成 +1`, `未完成 0`, `超时 -1`.

### Expected result

The app communicates progress and intent first; configuration becomes
supporting infrastructure.

## Proposal C: Minimal intervention mode

Best for users who want almost no app UI and interact mainly through overlays
and the quick-settings tile.

### Main screen

- Use one screen with monitoring status, current session, and guarded apps.
- Move history and setup into top-right actions or sheets.
- Make the quick-settings tile the primary on/off control and show an optional
  persistent notification action for pause/resume.
- Add an optional home-screen widget with status, current purpose, and remaining
  time.

### Expected result

Fewer navigation decisions and the lowest day-to-day interaction cost.

## Overlay UX improvements shared by all proposals

### Purpose prompt

- Remember the last three purposes per app and show them as one-tap suggestions.
- Keep duration presets, but label them consistently in Chinese rather than
  mixing `min` and `分钟`.
- Put the custom-duration field behind **自定义** until selected.
- Preserve unfinished input if the overlay temporarily disappears behind a
  system permission screen.

### Grace and overdue states

- Show both the countdown and the original purpose so the interruption remains
  meaningful.
- Change **继续 5 秒** to **再给我 5 秒** and state that the score has already
  changed when applicable.
- Prevent accidental taps by separating destructive **退出应用** from the
  continuation action.
- Add haptic feedback only at the start of grace and first overdue state, not
  on every reminder.

### Completion

- Use three outcomes when useful: **完成**, **部分完成**, **没有完成**.
  If scoring must remain binary, retain two buttons but let the user add an
  optional short note after the choice.
- Confirm the result with a lightweight toast/banner and include the score
  change.

## Common visual and accessibility improvements

- Move all user-facing strings to resources and support system font scaling.
- Keep touch targets at least 48 dp and add semantic descriptions to app icons,
  switches, status dots, and score indicators.
- Do not rely on green/red alone; pair color with icons and text.
- Use one spacing scale and one corner-radius scale across Compose and the
  accessibility overlay.
- Respect reduced-motion settings and avoid blur on devices where it is costly
  or unsupported.
- Add empty states with a next action, not only explanatory text.

## Recommended sequence

1. Implement Proposal A's status card, bottom navigation, and grouped setup.
2. Improve purpose suggestions and overdue wording in the overlay.
3. Add current-session visibility and richer history explanations.
4. Decide from usage feedback whether to evolve toward Proposal B or C.
