<p align="center">
  <img src="docs/images/icon.png" width="112" alt="HearthTube logo">
</p>

<h1 align="center">HearthTube</h1>

<p align="center">YouTube for the family TV, without ads, Shorts or AI-made channels for your kids.</p>

> [!NOTE]
> HearthTube is new and still changing. It's tested on Google TV.

## What it does for your family

**In your kids' profiles**
- **Only what their account allows.** The content level and the channels you block in Family Link always apply.
- **No Shorts**, and no TikTok or Instagram clip compilations either.
- **No AI-made channels**, using a list kept by volunteers.
- **Words you choose are blocked.** Videos with them in the title or channel name don't show up or play.
- **No nagging.** Anything left out simply isn't there. Kids don't get "you can't watch this" messages.
- **Settings stay with you.** A parent PIN protects settings, accounts and blocking. Kids can still subscribe to the channels they like.

**For everyone**
- No ads.
- Shorts are off unless you turn them on.
- A "Likely AI" label on videos from AI-made channels.
- Your own blocked words, if you want them. A kid's list never applies to you.
- A Library with your history, Watch later and playlists, and subscriptions shown as channel circles.
- Videos play at an even volume.

**With the Hearth launcher**
- Each person's own YouTube account. Switch profiles on the TV and HearthTube switches account.
- A daily YouTube time limit for each kid, and an optional bedtime lock with Home Assistant.
- Family Link's screen time still applies.
- Hearth installs HearthTube on every profile and keeps it up to date.

## What it doesn't do

- It doesn't replace Family Link. Set content levels and screen time there; HearthTube makes sure they apply.
- It doesn't watch the videos for you. It filters by Shorts, words and a list of AI channels, not by what's said.
- The kids features need Google TV with Family Link.

## Get HearthTube

**With Hearth:** open Hearth's settings, go to **Updates**, and install HearthTube. That's it: Hearth keeps it up to date.

**Without Hearth:** install the free **Downloader** app on your TV and enter this address:

```
https://github.com/theSiegs/HearthTube/releases/download/latest/hearthtube_universal.apk
```

HearthTube updates itself after that. Other ways to install are in the [technical notes](docs/technical.md).

## Privacy

No ads, no tracking, no HearthTube account. HearthTube talks to YouTube like any YouTube app. Besides that, it only
checks GitHub for updates and the AI-channel list, looks up sponsor segments (SponsorBlock) and dislike counts
(Return YouTube Dislike), both of which you can turn off,
and, with Hearth, shares play time for the daily limit and, if you agree, what's playing with Home Assistant.
The full policy: [PRIVACY.md](PRIVACY.md).

## Help and feedback

Something not working, or an idea? Open an [issue](https://github.com/theSiegs/HearthTube/issues). HearthTube is
built on [SmartTube](https://github.com/yuliskov/SmartTube) but isn't part of it, so please report HearthTube problems
here, not there.

## Thanks

HearthTube is [SmartTube](https://github.com/yuliskov/SmartTube) by Yuriy Liskov and its contributors, with family
features added. It works with the [Hearth](https://github.com/theSiegs/Hearth) launcher, uses the
[AiSList](https://github.com/Override92/AiSList) AI-channel list (CC BY-NC 4.0), and SponsorBlock and DeArrow by Ajay
Ramachandran. Released under the [MIT license](LICENSE). Developed with AI assistance.
