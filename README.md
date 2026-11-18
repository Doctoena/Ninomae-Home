# Ninomae Home

An Android home screen for the Galaxy Z Fold 7 in the Ninomae shell's look: rounded squircles, joined pieces, frosted glass, colours from the wallpaper.

- **Home screen grid** with pages (8×5 on the inner screen, 4×6 on the cover): hold and drag apps anywhere, drop one app on another for a folder, drop on the dock to pin, drag to the top to remove.
- **Widgets**: every app's Android widgets plus Ninomae's own (stack, greeting, player, jump back in, clock). The clock is a frosted slab with the time, the date and a small agenda: next event, next alarm, weather.
- **Edit mode**: hold an empty spot. Tap anything to pick it: drag its edges to resize, and the bottom bar turns into its card (rename, unstack, remove).
- **Shortcuts** in the hold menu, and other apps can add shortcuts to the home screen.
- **Notification dots**, **hide** and **rename** apps, web and Play Store search from the drawer.
- **Gestures**: swipe up for all apps, swipe down for the sidebar or Samsung's panel, double-tap to lock.
- **Search that answers**: sums (12*7, 15% of 80), conversions (5 km to mi, 70 f in c), contacts (call or text), Android settings pages, the web and the Play Store.
- **At a glance**: your next calendar event, and the weather if you turn it on (Open-Meteo, rough location only), under the clock.
- **Look**: set the wallpaper from Ninomae and it frosts it for the drawer, settings, dock, folders and clock (Samsung turns off Android's window blur), and takes its colours. Or a swatch. Themed icons cover every app (ones without a monochrome icon get one made from their logo), plus icon packs.
- **Widget stacks** (drop a widget on a widget) and **big folders** (resize a folder to tap its apps directly).
- **Grid sizes** per screen, **lock the layout**, **backup and restore** to a file.
- **Bar** in place of the status bar: app pairs on the left, the player in the middle, status and clock on the right.
- **Dock**: one frosted capsule: pinned apps, recent apps, all apps. A rose bar marks apps with notifications. Hold an app for Open, Split, Window, Pair, Pin, App info and Uninstall.
- **App drawer**: full screen on frosted glass, search at the top and focused as it opens. A slim rail of Most used, Recent, A–Z and groups (from Android's app categories). While you type, the top match opens with Enter. Swipe down to close.
- **Folders**: a pane of frosted glass grown from the folder, four apps a page, add apps from a picker, rename in place.
- **Settings**: full screen on glass, tiles that show what each part is set to, and a search for any setting.
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
