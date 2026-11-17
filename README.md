# Ninomae Home

An Android home screen for the Galaxy Z Fold 7 in the Ninomae shell's look: rounded squircles, joined pieces, colours from the wallpaper.

- **Bar** in place of the status bar: app pairs on the left, the player in the middle, status and clock on the right.
- **Desktop widgets**: greeting, player, and recent apps and pairs (left, right or off).
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
