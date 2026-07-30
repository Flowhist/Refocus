# Refocus background performance review

## Scope

The review covered the accessibility service lifecycle, foreground app
detection, coroutine scheduling, screen state handling, preferences, SQLite
session storage, system overlays, Compose state loading, app catalog loading,
the quick-settings tile, and the Android service declaration.

## High-impact findings and changes

### Permanent 250 ms polling

Previously the accessibility service called `tick()` every 250 ms in every
state. An idle installation could therefore wake the main thread about 345,600
times per day. Each tick also read monitoring preferences and could traverse
interactive accessibility windows.

The service now uses a conflated event signal and an adaptive fallback:

- Accessibility window events wake evaluation immediately.
- Multiple events arriving during one evaluation collapse into one pending
  evaluation.
- Idle, locked, disabled, or completion-pending states use a 60-second health
  check.
- Active sessions use a maximum five-second fallback and wake exactly at a
  nearer deadline.
- The five-second grace countdown updates once per second.
- Overdue reminders wake at their next scheduled reminder.

Theoretical fallback wakeups fall by about 99.6% while idle and 95% during the
normal part of an active session. Real devices also generate accessibility
events, so battery improvement must still be measured on-device rather than
inferred solely from these limits.

### Repeated preference reads and set allocation

`monitoringEnabled` previously read `SharedPreferences` on each tick.
`monitoredPackages()` also copied its stored string set on each call, including
during foreground evaluation and notification state construction.

Both values are now cached in the application-scoped repository. Mutations
update the cache, persist asynchronously, and notify the accessibility service
so it reacts immediately without polling.

### Accessibility event bursts

Window state and window list events previously called foreground evaluation
directly. The service's `notificationTimeout` already coalesces some system
events, but bursts could still trigger repeated window-root scans.

Events now send to a conflated channel. The latest package remains available,
while redundant evaluation requests collapse.

### Quick-settings compatibility

Android lint found the pre-Android-14 compatibility branch for
`startActivityAndCollapse(Intent)`. The branch is required below API 34, so the
method now carries the narrow lint suppression while API 34 and newer continue
to use `PendingIntent`.

## Areas reviewed without code changes

- **SQLite:** writes occur on session state transitions rather than on the
  periodic tick. Recent-history and daily-summary queries run from the activity
  on `Dispatchers.IO`, so they do not contribute to background wakeups.
- **Compose UI:** installed apps are loaded on `Dispatchers.IO` and retained
  while the activity is alive. Compose work happens only while the activity is
  visible.
- **App icons:** icon rasterization is limited to catalog loading, not the
  background monitor.
- **Foreground notification:** retained because it communicates continuous
  monitoring and supports process resilience. Its content update is already
  state-deduplicated.
- **Accessibility window scan:** still required for multi-window and
  picture-in-picture correctness, but it is no longer performed four times per
  second while idle.
- **Overlay blur and grain:** these add GPU work only while a decision overlay
  is visible. An adaptive reduced-effects mode is a suitable future UI setting
  for low-end devices.
- **Dependencies:** lint reports newer library versions, but broad dependency
  upgrades were intentionally excluded from this focused performance release.

## Verification

- `testDebugUnitTest`: passed.
- `lintDebug`: passed with no errors.
- `assembleRelease`: passed.
- Scheduling tests cover idle, normal active, near-deadline, and grace states.
- `aapt2` confirms package `com.flowhist.refocus`, version code `15`, version
  name `0.2.13`, and target SDK `36`.

## Recommended device validation

Before comparing battery percentages, use the same phone, monitored apps, and
screen-on workload for both versions.

1. Reset battery statistics and run each version for an equal eight-hour idle
   window.
2. Run a scripted session with repeated app switches, screen lock/unlock, PIP,
   grace, and overdue states.
3. Compare process CPU time, wakeups, accessibility event count, and foreground
   service residency through Battery Historian or Perfetto.
4. Confirm the purpose overlay appears immediately after a monitored app opens
   on vivo's normal and restricted background modes.

The 60-second idle heartbeat is intentionally a recovery fallback. If a target
OEM proves that window events are consistently reliable, it can be increased
further; if an OEM drops events, shorten it only for that device family.
