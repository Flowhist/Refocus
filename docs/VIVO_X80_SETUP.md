# vivo X80 / Android 16 setup

Target software observed during development:

- Model: V2183A
- Software: PD2183B_A_16.4.14.0.W10.V000L1
- Android security update: May 1, 2026

Menu labels can differ slightly by region and system language.

## Install

1. Build and install the debug APK from Android Studio.
2. If Android blocks the sideloaded app from enabling accessibility:
   - Open Refocus app info.
   - Open the top-right menu.
   - Choose “Allow restricted settings” if that option is present.
3. Open Refocus and select at least one monitored app.

## Required settings

In Refocus, open **权限设置** and complete every item:

1. Enable **Refocus 应用监视** in Accessibility.
2. Allow Refocus notifications.
3. Set Refocus battery behavior to **Unrestricted** or allow background activity.
4. In vivo iManager, allow Refocus to auto-start.
5. Return to Refocus and enable the main monitoring switch.

Refocus intentionally uses an accessibility overlay, so it does not require the general “draw over other apps” permission.

## First device test

Use a non-sensitive app for the first test.

1. Select the target app in Refocus.
2. Open the target app and verify that the purpose prompt blocks interaction.
3. Enter a purpose and set one minute.
4. Lock the phone for 15 seconds, unlock, and verify the timer did not consume those 15 seconds.
5. Leave the target app before the deadline and choose:
   - Completed: expect `+1`
   - Not completed: expect `0`
6. Start another session and stay through the deadline:
   - A five-second grace countdown should appear.
   - Staying past it should apply `-1` once.
   - Choosing to continue should cause another reminder five active seconds later.
7. Repeat once in split screen or picture-in-picture and confirm that the session remains active while the target stays visible.

## Known platform boundaries

- Refocus treats an app as exited when it is no longer visible or interactive.
- It does not kill or inspect another app's cached process.
- Security-sensitive apps may hide third-party overlays.
- Force-stopping Refocus or disabling its accessibility service stops monitoring until it is enabled again.
