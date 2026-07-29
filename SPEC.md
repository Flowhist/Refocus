# Refocus v0.1 Product Specification

## Target device

- vivo X80
- Model: V2183A
- Software: PD2183B_A_16.4.14.0.W10.V000L1
- Android: 16
- Personal sideloaded APK; no app-store release requirements for v0.1

## Session lifecycle

1. When a selected app becomes visible, Refocus shows a blocking purpose prompt.
2. Purpose is required.
3. Planned duration is required and cannot be extended after the session starts.
4. The timer starts after the purpose prompt is confirmed.
5. Locking the phone pauses the timer. Unlocking resumes it.
6. Switching to another app ends the current session after a short technical debounce.
7. A selected app in split screen, floating window, or picture-in-picture remains in use.
8. Leaving before the deadline, or during the five-second grace period, opens the completion prompt.
9. Completion scoring:
   - Completed: `+1`
   - Not completed: `0`
10. If the selected app remains visible after the five-second grace period:
    - The session receives `-1` once.
    - Refocus reminds again every five active seconds.
    - Completion is still recorded after exit, but does not change the `-1`.

## Exit definition

“Exit” means that the monitored app is no longer visible or interactive. Refocus does not attempt to kill another app's cached process.

## Privacy

- Store all purposes and records locally.
- Do not request network access.
- Observe package names only.
- Do not read accessibility node text, passwords, messages, or user input from other apps.
- Disable Android backup for the application.

## Recovery

- Active sessions are persisted across Refocus process recreation within the same boot.
- A lock pause is persisted.
- Completion prompts are persisted until answered.
- A device reboot closes an unfinished session as interrupted; an already-applied overdue penalty remains `-1`.
