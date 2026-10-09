# HearthTube

> **Draft documentation.** This README is a first draft and will change.
>
> **Early development.** HearthTube is young and changes often. It's built and tested on one Google TV setup
> (Android 14), so expect rough edges elsewhere.

HearthTube is a YouTube client for Google TV and Android TV, made for families. It's a fork of
[SmartTube](https://github.com/yuliskov/SmartTube) that adds safety features for Google TV **kids profiles** and works
hand in hand with the [Hearth](https://github.com/theSiegs/Hearth) launcher.

It installs as its own app (`com.thesiegs.hearthtube`), next to SmartTube if you have it.

## What it adds

### Kids profiles
HearthTube recognises a Google TV kids profile (supervised by Family Link) and, in one:
- **No Shorts, anywhere.** YouTube's Shorts rows are emptied, Shorts that YouTube doesn't label are recognised too,
  and the player refuses one however it was opened (search, link, cast or autoplay).
- **No TikTok or Instagram clips,** including compilations of them, matched by title and channel.
- **Settings and accounts locked** behind a parent PIN, entered on a shuffled pad so a PIN can be typed in front of
  the kids. With Hearth installed, Hearth's own parent PIN is used.
- **A kids profile never lands on a grown-up's YouTube account.**
- **Screen time:** when Google TV's daily limit or bedtime is up, HearthTube stops and steps aside (with Hearth).

### Coming next
- **Blocked words** (in the next release): hide videos whose title or channel name contains a word or phrase you
  choose, per profile. Add words in Settings, or pick them off a video's title with the remote. In kids profiles the
  player refuses such a video, and changing the list needs the parent PIN.
- **AI-made channels** (in development): channels on the community [AiSList](https://github.com/Override92/AiSList)
  are hidden in kids profiles and labelled "Likely AI" elsewhere.

### For everyone
- **Shorts are off by default** (Settings → Look and layout → Show Shorts turns them on).
- **No signed-out mode:** every profile watches with a YouTube account.
- **A look that matches Hearth:** Roboto, accent-coloured focus, Hearth's search box, a calmer player, and icons in
  the long-press menu.

### With the Hearth launcher
- Each Google TV profile gets its own YouTube account, following Hearth's active profile.
- HearthTube shows Hearth's wallpaper and uses its accent colour and clock format.
- Hearth can keep HearthTube up to date and install it in kids profiles.

Everything SmartTube offers is still there: SponsorBlock, adjustable speed, high resolutions and HDR, live chat and
more.

## Install

Download the APK for your TV from the
[latest release](https://github.com/theSiegs/HearthTube/releases/latest):

| TV | File |
|---|---|
| Most current Google TV and Android TV devices | `hearthtube_arm64-v8a.apk` |
| TVs with a 32-bit system | `hearthtube_armeabi-v7a.apk` |
| Not sure | `hearthtube_universal.apk` (works everywhere, larger) |

With the Downloader app, enter
`https://github.com/theSiegs/HearthTube/releases/download/latest/hearthtube_arm64-v8a.apk`
(or the file for your TV). HearthTube checks this release for updates.

## Status and security

- **Early development:** features and settings may change between releases.
- **Built from source.** HearthTube is built from SmartTube's public source code and signed with its own key, so it
  isn't affected by the 2025 SmartTube signing-key incident described in SmartTube's README.
- **Release signing** uses a development key for now; this will be finalised before a stable release.

## Issues and contributions

Bug reports and ideas are welcome in [Issues](https://github.com/theSiegs/HearthTube/issues). Problems that also
happen in SmartTube are best reported [there](https://github.com/yuliskov/SmartTube/issues).

## Credits and license

- [SmartTube](https://github.com/yuliskov/SmartTube) by Yuriy Liskov and contributors, which HearthTube is built on.
- [Hearth](https://github.com/theSiegs/Hearth), the launcher HearthTube is designed alongside.
- [AiSList](https://github.com/Override92/AiSList) by its contributors (CC BY-NC 4.0), for the list of AI-made
  channels.

HearthTube is released under the MIT license, like SmartTube. See [LICENSE](LICENSE).
