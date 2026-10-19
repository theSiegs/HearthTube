# HearthTube Mobile

The phone and tablet app. **Work in progress: not released, and not yet tried on a real supervised phone.** It is a
separate app (`com.thesiegs.hearthtube.mobile`) built from the same code as the TV app, so the kids' protections work
the same way.

## What it is

- One Android app for phones and tablets (Android 7.0 and up). It has its own touch screens; everything behind them is
  the TV app's: the YouTube service, the presenters, the video player engine, the parent PIN, blocked words, the
  AI-channel list, the no-Shorts rule and the daily allowance.
- Works as an ordinary YouTube client on anyone's phone or tablet, and as a locked-down one on a kid's. For a kid it
  is meant for a supervised Google account in Family Link: the kid is signed in to YouTube with that account, so
  YouTube's own content level and channel blocks apply, and HearthTube adds its own on top.
- It lives in the `hearthtubemobile` module. `common` holds everything it shares with the TV app.
- **It looks like HearthTube on the TV.** The same wallpaper behind every screen (Bing's picture of the day, or
  Hearth's own when Hearth is on the device), under a shade that is darker at the top and bottom; dark glass pills for
  the sections and buttons, the open one in white; rounded cards with the "Likely AI" label; and Hearth's accent color on
  the Search and Settings buttons, switches and the player's seek bar. Settings has **Look and layout** (accent color
  and wallpaper) and **About**, which names the day's picture and its photographer. The video itself stays on black.

## Adults and kids

- **Anyone else's device works as a normal YouTube client.** Sign in from the Sign in button wherever a screen needs an
  account (it can open the sign-in page right on the phone). Settings holds accounts, blocked words and a switch for
  kids mode.
- **A Family Link device starts in kids mode.** The app looks for the restrictions Family Link puts on a supervised
  user, and treats the device as a kid's when it finds them. Whoever sets the device up can also switch kids mode on
  by hand (Settings → Kids mode on this device). Switching it off on a device that Family Link supervises doesn't
  work: supervision wins. **Not yet confirmed on a real phone:** if the check misses, the device acts as an adult's
  until kids mode is switched on by hand. `adb shell dumpsys user | grep -i restrictions` on a supervised phone shows
  what Family Link sets.
- **In kids mode, nobody signed in means nothing to see.** The home screen, search results and channel pages stay
  empty ("Ask a parent to sign in"), and nothing plays, including a video opened from a link. What YouTube lists
  signed out isn't supervised.
- **The parent PIN.** In kids mode the first parent to open the Parent menu chooses it. It protects that menu,
  signing in, the PIN itself, and a video's long-press menu (subscribing, playlists, blocking channels and words).
  Outside kids mode none of this asks for a PIN.
- **Daily YouTube time.** A parent can set it on a kid's device (15 minutes to 2 hours). It counts what played and
  starts again at midnight. When Hearth is on the device, Hearth's limit takes its place. (The counting is unit
  tested; stopping a video at the limit hasn't been run on a device yet.)
- **Links.** Links to youtube.com and youtu.be can open in this app, so they go through the same filters. Android
  doesn't hand them over by itself: see "Installing" below.
- **Left out on purpose:** picture-in-picture, background play (not offered), voice search, the TV's play queue and notifications
  tabs, boot receivers, overlay and package-delete permissions, the zip restore screen, and any update feed.

## Installing on a supervised phone

Family Link has no way to push an app that isn't from Google Play. What works (reported by others; confirm on your
device):

1. In the Family Link parent app: the child → Signed-in devices → the device → **Apps from unknown sources** on.
2. On the phone, allow the app you install from (Files, a browser) to install unknown apps, and install the APK.
3. Turn both switches off again.
4. Block the YouTube app and the browsers' youtube.com in Family Link, so a kid can't go around HearthTube.

Things to check on a real device, because nothing documents them: that the app stays installed after the switch goes
off; whether Family Link lists it and can block it or limit its time (the TV shows sideloaded apps without a block
control); and whether the kid can change the default home app.

To make a YouTube link open here after the official YouTube app is blocked, the app must be approved for the
domains: `adb shell pm set-app-links-user-selection --user 0 --package <package> true youtu.be www.youtube.com m.youtube.com youtube.com`
(Android 12 and up; the filter alone isn't enough, since Chrome and the YouTube app are verified for those domains).

## Updates

There is no update feed yet: the app is set to find none (it must never offer SmartTube's). Until there is one, a
new version is installed the way the first one was. Android's developer verification (from 2026 in some countries,
worldwide in 2027) will need the package registered; the limited-distribution account (up to 20 devices) fits a
family.

## Signing

Release builds are signed with the same key as Hearth and the TV app (certificate SHA-256 in the
[technical notes](technical.md)), so Hearth can trust the app by its package and certificate. The key stays in the
git-ignored local file `keystore.properties` points at; nothing here needs it except a release build. A debug build
has its own package (`.debug` added) and the debug key, which Hearth doesn't trust.

## For developers

```sh
.\gradlew.bat :hearthtubemobile:assembleDebug          # one APK per ABI in hearthtubemobile/build/outputs/apk/debug
.\gradlew.bat :hearthtubemobile:assembleRelease        # signed when keystore.properties points at the key
.\gradlew.bat :hearthtubemobile:testDebugUnitTest      # unit tests
.\gradlew.bat :hearthtubemobile:lintDebug
```

JDK 17, as for the TV app. CI builds, lints and tests it next to the TV app.

How it is put together:

| Part | What it does |
|---|---|
| `MobileApplication` | Registers which screen shows each shared view (`ViewManager`) |
| `MobileSplashActivity` | The launcher. The shared splash presenter starts the kids filters and reads the link or search the app was opened with. Nothing else may be the launcher |
| `browse/`, `search/`, `channel/` | Touch screens on one shared `ui/VideoGrid` (one column on a phone, more on a tablet) |
| `playback/MobilePlaybackActivity` | ExoPlayer's controls over the TV app's player engine and presenter |
| `dialogs/MobileDialogActivity` | Draws every settings screen and menu the shared presenters build |
| `parent/MobileParentMenu`, `signin/` | The parent's menu and the sign-in code screen |
| `ui/KidGate` | "Kids mode and nobody signed in": every video screen asks it |

Shared pieces added for it in `common`: `KidsDecision` and `KidsMode` (is this a kids device), `LocalAllowance` (the
daily limit without Hearth), the signed-out rule in `KidsRefused`, and `MotherActivity.initDpi()` made overridable
(the TV's fixed-width scaling is wrong on a phone).

## Open

- Tablet and phone layouts are checked on emulators only. Hearth's own wallpaper and accent reach the app only once
  Hearth accepts its package; until then it shows Bing's picture and the default accent.
- Suggestions while typing in search, the player's suggestion rows, comments and chat, subtitles menus.
- A mobile update feed and release script.
- With Hearth on the phone (a launcher), reading its profile and allowance like the TV does: needs a package
  and signing agreement on Hearth's side.
