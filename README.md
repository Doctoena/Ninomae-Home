# Ninomae Home

An Android home screen for the Galaxy Z Fold 7 in the Ninomae shell's look: rounded squircles, joined pieces, colours from the wallpaper.

- **Home screen grid** with pages (8×5 on the inner screen, 4×6 on the cover): hold and drag apps anywhere, drop one app on another for a folder, drop on the dock to pin, drag to the top to remove.
- **Widgets**: every app's Android widgets plus Ninomae's own (stack, greeting, player, jump back in, clock). Resize and remove in edit mode.
- **Edit mode**: hold an empty spot. Widgets, Wallpaper, Settings, Done.
- **Shortcuts** in the hold menu, and other apps can add shortcuts to the home screen.
- **Notification dots**, **hide** and **rename** apps, web and Play Store search from the drawer.
- **Gestures**: swipe up for all apps, swipe down for the sidebar or Samsung's panel, double-tap to lock.
- **Bar** in place of the status bar: app pairs on the left, the player in the middle, status and clock on the right.
- **Joined dock**: pinned apps, recent apps, all apps. Hold an app for Open, Split, Window, Pair, Pin, App info and Uninstall.
- **App drawer**: search, A–Z / Most used / Games, pairs along the top.
- **App pairs**: two apps opened together in split screen, named, from the bar.
- **Sidebar**: toggles, flashlight, screenshot, lock, wallpaper, Samsung's panel, the player with volume and brightness, and notifications.

The inner screen gets the desktop layout. The cover screen gets a compact one.

## Build

```
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Access it asks for

| Access | For |
|---|---|
| Home app | Making it your home screen |
| Notification access | The player and notifications |
| Accessibility | Split screen and pairs, lock, screenshot, power menu |
| Change system settings | Brightness and auto-rotate |
