# HearthTube privacy policy

**Last updated:** 2026-10-14. Applies to HearthTube (`com.thesiegs.hearthtube`), from version 2026.10.14.

HearthTube is new, and this policy will change as its features do. Each change is listed at the end.

## In short

- HearthTube has **no ads, no analytics, no crash reporting, no HearthTube account and no HearthTube server.** Nobody
  working on HearthTube receives anything about you or your family.
- It talks to **YouTube**, as any YouTube app does, with the YouTube account signed in on that profile.
- Otherwise it connects only for the features below, and you can turn off each one that isn't essential.
- Your settings, PIN, blocked words and sign-in tokens stay on the TV.

## What HearthTube connects to

| Feature | Connects to | Sends | When |
|---|---|---|---|
| YouTube | YouTube and Google (`youtube.com`, `googleapis.com`, `ytimg.com`) | What any YouTube app sends: your sign-in, what you browse, search and watch. YouTube's own privacy policy and your account's settings (for kids, Family Link's) apply | Always |
| Sign-in | Google (`oauth2.googleapis.com`) | Google's standard TV sign-in: you approve on a phone or computer, and HearthTube gets a token. Your password never reaches HearthTube | When you sign in |
| Updates | GitHub (`github.com/theSiegs/HearthTube` releases) | A request for the update list, then the new version | At start and when you check. Only in the TV owner's profile, and not while Hearth updates HearthTube |
| AI-channel list | GitHub (`raw.githubusercontent.com/theSiegs/aislist-channel-ids`) | A request for the list | Once a day at most; after a failed try, again after an hour. A copy ships with the app |
| SponsorBlock | `sponsor.ajay.app` | The ID of the video playing | On by default; Settings → SponsorBlock |
| DeArrow | `sponsor.ajay.app`, `dearrow-thumb.ajay.app` | The IDs of the videos shown | Off by default |
| Return YouTube Dislike | `returnyoutubedislikeapi.com` | The ID of the video playing | While the like and dislike counter is on (on by default) |
| Bing photo of the day | `www.bing.com` | A request for the photo | Only if you choose it as the background (without Hearth). The phone app (not released yet) will show it by default, until you choose another background |
| Links in About | `api.qrserver.com` | The link being shown, to draw it as a QR code | When you open a link there |
| Country flags | `flagcdn.com` | A country code | When a list of countries is shown |

These services see the TV's IP address, like any website you visit. None of them gets your Google account, your
sign-in token or your profile's name from HearthTube.

## Backup

HearthTube makes a backup only when you ask for one in **Settings → Backup and restore** (the daily automatic copy is
off by default). The backup is a folder and a `.zip` file of HearthTube's settings, accounts (and their PINs) and sign-in
tokens, saved in the TV's storage under `Android/media/com.thesiegs.hearthtube` (and `Documents`) where file managers
and other apps with storage access can read it. It is not encrypted and goes nowhere else. Delete it when you no
longer need it. The parent PIN and blocked words are not in it. Opening a `.zip` from another app does not restore it:
restoring is only done from the Backup screen.

## On the TV, with Hearth

If the [Hearth](https://github.com/theSiegs/Hearth) launcher is installed, HearthTube and Hearth talk to each other
on the TV only:

- **HearthTube reads from Hearth:**
  - which Google TV profile is watching, and whether it's a kids profile;
  - whether Family Link's screen time is up;
  - the minutes of YouTube left today;
  - Hearth's wallpaper, accent color and clock.
- **HearthTube tells Hearth how long videos played** (just the time, no titles) so Hearth can count each kid's YouTube
  time per day. It does this in every profile.
- **Parent PIN checks:** when Hearth has a parent PIN, HearthTube asks Hearth to check the PIN you type. Neither app
  shows or sends the PIN anywhere else.
- Hearth only shares this with a HearthTube signed with Hearth's own key.

**What's playing, to Home Assistant.** Like most video apps, HearthTube tells Android what's playing (title, channel
and video ID). Android shows that to apps you've given notification access, such as Hearth, which can pass it on to
**your own** Home Assistant if you set that up. Hearth asks during its Home Assistant setup whether to share this; if it
never asked, sharing is on. To share only "HearthTube" instead of the title, turn off **Settings → Works with Hearth →
Tell Home Assistant what's playing**, or the matching switch in Hearth's Home Assistant settings.

**Continue Watching.** The video you're watching goes on the TV's Continue Watching list (Android TV's Watch Next),
on the TV, so the home screen can show it. Live streams and Shorts aren't added.

## Kids profiles

HearthTube doesn't collect anything about children. In a kids profile it:
- plays only what the kid's own supervised YouTube account allows;
- hides Shorts, videos with blocked words, and channels on the AI-channel list, using the title, the channel name and
  the channel ID, all on the TV;
- keeps no log of what was hidden or watched. YouTube keeps the kid's watch history in their Google account, under
  Family Link's settings.

## What HearthTube does with content

HearthTube changes what you see: it removes Shorts (by default), videos with your blocked words, and (in kids
profiles) channels on the AI-channel list. It adds a "Likely AI" label for adults. All of this runs on the TV, from
fixed rules and published lists. It uses no AI model and builds no profile of anyone.

## Your choices

- Turn off SponsorBlock, DeArrow, the "Likely AI" label, Home Assistant titles and automatic update checks in
  Settings.
- Clearing HearthTube's data, or uninstalling it, removes everything it kept on the TV, including sign-in tokens.
  Your YouTube history and subscriptions stay in your Google account.
- There is no HearthTube account. We hold no data about you, so there is nothing to request or delete from us.

## Contact

Open an issue at [github.com/theSiegs/HearthTube/issues](https://github.com/theSiegs/HearthTube/issues).

## Changes

- 2026-10-20: the backup and the AI-channel list's retry are described; no cleartext (`http`) traffic; the phone app's background.
- 2026-10-14: first HearthTube policy, replacing SmartTube's F-Droid policy.
