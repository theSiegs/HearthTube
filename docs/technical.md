# HearthTube technical notes

For people sideloading HearthTube, or curious how it works. Parents don't need any of this: see the
[README](../README.md).

## Install files

Every release has four files. Hearth picks the right one by itself.

| File | For |
|---|---|
| `hearthtube_universal.apk` | Any device. Use this if you're not sure |
| `hearthtube_armeabi-v7a.apk` | 32-bit systems, which includes most Google TV sticks |
| `hearthtube_arm64-v8a.apk` | 64-bit systems |
| `hearthtube_x86.apk` | x86 devices and emulators |

`adb shell getprop ro.product.cpu.abilist` shows which system you have (`arm64-v8a` first means 64-bit).

```sh
adb install hearthtube_universal.apk
```

All files: [releases](https://github.com/theSiegs/HearthTube/releases).

## Versions and updates

- Package: `com.thesiegs.hearthtube`. It installs next to YouTube and SmartTube.
- Versions are release dates: 2026.10.14, then 2026.10.14.2 for a second release that day. Settings → About shows the
  SmartTube version each one is built on.
- There are two channels: pre-releases (GitHub tag `latest`) and releases (tag `stable`). Every release is a
  pre-release for now.
- **Settings → About → Include pre-releases** follows the build you run: on in a pre-release, off in a release. You
  can change it; your choice stands until you install a build of the other kind.
- Only the copy in the TV owner's profile checks for updates. When Hearth updates HearthTube, HearthTube leaves updating
  to Hearth, and Hearth's own "Include pre-releases" switch decides.

## Signing

Releases are signed with the same key as Hearth, rotated from the earlier development key, so existing installs update
in place. Certificate SHA-256: `243bf074648ab3bfebb260d129e7c8203b407ca7677507ce93dbe72b02396d4c`. Hearth only
trusts a HearthTube signed with it.

## How it works with Hearth

HearthTube reads Hearth's profile provider (`content://com.thesiegs.hearth.profile`), described in Hearth's
[provider contract](https://github.com/theSiegs/Hearth/blob/master/docs/provider-contract.md). It uses contract
versions up to 7:

| What | Since Hearth |
|---|---|
| Profile following, wallpaper, accent, clock and language | earlier versions |
| YouTube time per day and the Home Assistant schedule lock (contract 6) | 2026.10.28 (kids only from 2026.10.49) |
| "Share what's playing with Home Assistant", asked in Hearth's Home Assistant setup (contract 7) | 2026.10.58 |

HearthTube tells Hearth how long videos played (milliseconds only), for the daily limit.

**Opening HearthTube's settings from another app:** start the action
`com.thesiegs.hearthtube.action.OPEN_SETTINGS`, optionally with a String extra `section`: `general`, `player`,
`accounts`, `blocked_words` or `about`. Without one, or with an unknown one, the main settings menu opens. Kids
profiles ask for the parent PIN first.

```sh
adb shell am start -a com.thesiegs.hearthtube.action.OPEN_SETTINGS --es section blocked_words
```

## Kids profiles

- A Google TV kids profile is its own Android user, with its own copy of HearthTube and its own settings and
  blocked-word list. Adults share the TV owner's copy.
- A kid signed in to their own supervised account only plays what YouTube allows that account. When YouTube refuses a
  video, HearthTube doesn't fetch it another way.
- A kids profile with nobody signed in shows and plays nothing, however HearthTube was opened (launcher, link, cast,
  voice or Google TV search, Watch Next, a shortcut): Home says "Ask a parent to sign in", search, channels and the
  player refuse, and the welcome asks for the parent PIN (`LaunchGate`). Adult profiles are unchanged.
- Shorts and TikTok-style clips are blocked in kids profiles even when the process starts without the splash screen
  (`KidsShorts` asks `ParentGate` itself).
- Refused videos close quietly and leave the rows. Only a video opened from a link or a cast shows a short reason.

## Building

Build with JDK 17: `./gradlew :smarttubetv:assembleHearthtubeDebug`. `release.ps1` builds, signs and publishes
releases; it needs the signing key, which isn't in the repository.
