# Ninomae Home

An Android home screen for **any phone**, in the look of the [Ninomae shell](https://github.com/Doctoena/Ninomae-Shell): rounded squircles, joined pieces, frosted glass, and colours taken from your wallpaper.

It also has **foldable support**. On a foldable like the Galaxy Z Fold, the cover screen gets the phone layout and the big inner screen gets a desktop one, with a bar, a dock and app pairs. Each screen keeps its own grid and settings.

<p align="center">
  <img src="docs/screenshots/home.jpg" width="250" alt="Home screen: a frosted clock, glass folders and a glass dock over the wallpaper">
  <img src="docs/screenshots/folder.jpg" width="250" alt="An open folder: a frosted pane grown out of the folder's icon">
  <img src="docs/screenshots/menu.jpg" width="250" alt="Holding an app on the dock: its shortcuts and actions">
</p>

*Screenshots are from a phone-sized screen (a Galaxy Z Fold 7's cover screen).*

## Frosted glass

Samsung turns off Android's window blur, and Android won't let apps read your wallpaper. So you set the wallpaper from Ninomae (**Settings → Look → Wallpaper**). It keeps a frosted copy for the glass, a small preview, and the wallpaper's main colour for the theme. The drawer, settings, folders, dock, clock and menus are then all frosted from it, lined up with where the wallpaper really is.

If you change the wallpaper in another app, Ninomae notices: the glass goes plain (it would show the old picture), and a note above the dock asks you to pick the new one here.

## The app drawer

<p align="center">
  <img src="docs/screenshots/search.jpg" width="250" alt="Searching for an app">
  <img src="docs/screenshots/group.jpg" width="250" alt="The Media group in the drawer's rail">
</p>

- Swipe up from anywhere. Search is at the top and **focused as it opens**, so you can type straight away. Enter opens the top match.
- A slim rail on the left: **Most used**, **Recent**, **A–Z** (with a letter scrubber), your own **tags** (hold an app → Tags, or **＋** in the rail), then groups made from the categories apps give Android (Social, Media, Games, Maps, Work…).
- While you type, the rail picks what to show: apps, people, calendar events, files (with All files access), settings, or the web.
- Search answers questions too: sums (`12*7`, `15% of 80`), conversions (`5 km to mi`), contacts (call or text), and Android settings pages by name.
- Swipe down on the list to close it. It follows your finger.

## Folders and the hold menu

- A **folder** opens as a pane of frosted glass grown from its icon, four apps to a page. Tap the name to rename it, **＋** to tick apps in or out, and **↗** to see them all at once.
- **Hold an app** for its own shortcuts, then Split screen, Pop-up window, Make a pair, Pin, Rename, Hide, App info and Uninstall. From the dock the menu joins onto it. In the drawer or a folder, it opens without closing them. Rename sits on the keyboard, with the app's own name one tap away.
- **Uninstalling** an app takes it off everywhere: home, folders, the dock, pairs and tags.
- **Notifications** show as a small rose bar under the app's icon, the same everywhere.

## The edge sidebar

- **Slide up along the edge** of the home screen (left or right, your choice) and a slim glass rail of your own apps grows out of it, at your thumb or in the middle.
- Keep your finger down and glide: the app under it swells on a soft rose glow, its neighbours a little, and its name shows beside the rail. **Lift to open it**, or lift off the rail and tap.
- Its apps are its own, separate from the dock. Hold any app → **Add to edge**, or set it all up in **Settings → Edge sidebar** (drag to reorder, pick apps, switch it off).

## Edit mode and widgets

<p align="center">
  <img src="docs/screenshots/edit.jpg" width="250" alt="Edit mode with the clock picked: handles on its edges, its card at the bottom">
  <img src="docs/screenshots/widgets.jpg" width="250" alt="The widget picker with Ninomae's own widgets open">
</p>

- **Hold an empty spot** to edit. Tap anything to pick it: drag its edges to resize it, and the bottom bar turns into its card (Rename, Unstack, Another widget, Remove). Hold and drag to move it, or drop an app on another app to make a folder.
- **Widgets**: every app's Android widgets, plus Ninomae's own:
  - **Clock**: a frosted slab with the time, the date, and an agenda of your next event, next alarm and the weather.
  - **Greeting, Player, Jump back in**, and a **stack** that holds several widgets you swipe through.
- **Widget stacks**: drop one widget on another. **Big folders**: resize a folder to tap its apps straight from the home screen.

## Settings

<p align="center">
  <img src="docs/screenshots/settings.jpg" width="250" alt="Settings tiles">
  <img src="docs/screenshots/look.jpg" width="250" alt="The Look page: wallpaper, colours, app icons">
</p>

- Tiles that show what each part is set to, and a search for any setting.
- **Look**: the wallpaper, colours (from the wallpaper, a swatch, or Samsung's), **themed icons** for every app (apps without a monochrome icon get one made from their logo), and icon packs.
- **Home screen**: grid size per screen, app names, notification bars, lock the layout.
- **Gestures**: swipe down for the deck or Samsung's panel (per screen), double-tap to lock.
- **At a glance**: your next calendar event, and the weather if you turn it on (Open-Meteo, rough location only).
- **Backup and restore** your whole home to a file.

## On big and unfolded screens

On a tablet-sized screen, like a foldable's inner screen, the layout becomes a desktop:


- A **bar** in place of the status bar: app pairs on the left, the player in the middle, status and the clock on the right.
- **App pairs**: two apps opened side by side in split screen, named, one tap from the bar. Made on a full-screen page: tap a side, pick its app, Save.
- A **control deck** pulled down from the top: toggles, flashlight, screenshot, lock, the player with volume and brightness, and notifications.

## Build

Needs a JDK 17 or newer. The release build is the one to use day to day, since it's shrunk and much faster than a debug build:

```
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Access it asks for

| Access | For |
|---|---|
| Home app | Making it your home screen |
| Notification access | The player, notifications and the rose bars |
| Accessibility | Split screen and pairs, lock, screenshot, power menu |
| Change system settings | Brightness and auto-rotate |
| Set wallpaper | Setting the wallpaper it frosts for the glass |

Contacts, calendar and location are only asked for when you turn on the feature that needs them.
