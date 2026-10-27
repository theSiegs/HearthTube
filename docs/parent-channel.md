# Parent channel (design draft)

> **Design draft, nothing built.** How a parent reads what a kids profile started, and sets what it may not show,
> without sitting at the kid's TV. Builds on what exists: HearthTube's watch log (`WatchLogData`) and its PIN-gated
> Parent options, Hearth's provider contract (version 7, `docs/provider-contract.md` in Hearth) and Hearth's Home
> Assistant integration (`docs/home-assistant-integration.md` there).

## Why not "a parent HearthTube account"

Each Google TV profile is its own Android user, and an app's data is private to its user. A parent's HearthTube and a
kid's HearthTube can't read each other, and YouTube's own account has no place for HearthTube's lists. The things that
do reach across the users are Hearth (its provider answers in a kid's user through the profile's agent) and Home
Assistant (Hearth already posts to it and reads from it). So the parent's side lives in Home Assistant, and Hearth
carries it both ways. The PIN menu on the TV stays as the backup for a home without Home Assistant.

## Layers: Hearth is the base, Home Assistant is an add-on

Home Assistant is optional for Hearth, so nothing here may need it.

1. **HearthTube** keeps everything it needs itself: its log, and the lists it applies.
2. **Hearth** (contract 8) is the parent's store without Home Assistant: it keeps each kid's log of started videos and
   the parent's blocked lists, and a parent reads and edits them in Hearth's own settings (behind Hearth's parent PIN),
   on the TV or in Hearth Mobile when that exists. Built so far: the store, the provider and the relay (`StartedLog`,
   `ParentBlocks`); its settings screens are still to do.
3. **Home Assistant**, when set up, is a second place to read the log and edit the lists, plus the weekly digest. It
   mirrors Hearth's log and adds its own list entries to Hearth's; it never holds the only copy.

## What moves

| Direction | What | Why |
|---|---|---|
| Up | each video a kids profile starts (time, id, title, channel) | the parent reads it; an AI agent reviews it |
| Up (later) | what the kid hid or blocked | the parent sees it and can undo it |
| Down | the parent's blocked videos, channels and words, per kid | the kid's HearthTube applies them and can't undo them |

## Path

```
HearthTube (kid's user) --report_started--> Hearth agent --> Hearth (owner's user) --event--> Home Assistant
HearthTube (kid's user) <--parent_blocks--- Hearth agent <-- Hearth <--state document------- Home Assistant
```

Both legs reuse what is there: `report_playing` already relays a call from a kid's HearthTube through the agent, and
the state document already comes back in the answer to each status post (per profile, by `profile_id`).

### Up: the log

- **Contract 8 call** `report_started`, extras `time_ms`, `video_id`, `title`, `channel`; same callers and relay rules
  as `report_playing`. Returns `ok` true once Hearth has it, and null when it couldn't be passed on (Hearth not
  connected): HearthTube then keeps the entry as unsent and tries again at the next profile ready or start.
- **HearthTube** keeps its own log as the record (it survives a missing Hearth or Home Assistant), adds a "sent up to"
  mark per profile, and sends the unsent entries oldest first, a few at a time. Sending is off the main thread.
- **Hearth** posts them to Home Assistant's integration on a new device endpoint (`.../device/<id>/events`, same
  secret, local addresses only, size and rate limits like status). Entries it can't post stay queued, a day at most.
- **Home Assistant integration**:
  - keeps a stored log per person (the last 2,000, like the phone side), de-duplicated by `(profile, time, video_id)`
    so a resend is harmless;
  - fires `hearth_video_started` (person, video_id, title, channel, url, time) for automations;
  - exposes a **Last video started** sensor per kid, with the newest 50 as an attribute kept out of history, which a
    dashboard card shows and an AI agent reads through the Home Assistant connection it already has;
  - a service `hearth.get_watch_log` (person, since, limit) returning the entries, for an agent or a script.
- **Weekly digest** (chosen: weekly, to the parents' phones, and fed to a reviewer agent in Home Assistant). A service
  `hearth.watch_digest` (person, days, default 7) returns one document for the week: per kid, the entries (time, title,
  channel, url, video id, channel id), counts per channel and per day, and what was blocked in that time. A blueprint
  calls it weekly and sends the readable summary to the parents' phones; the same document, as JSON, is what the
  reviewer agent takes as input (the blueprint can hand it to a conversation agent or a script, or an agent can call
  the service itself). Nothing is sent per video.
- Without Home Assistant there is no push: the log is read in Hearth's settings, and can be saved as the same text and
  JSON files the PIN menu writes.

### Down: the lists

- **Home Assistant** holds, per kid: blocked video ids, blocked channel ids, blocked words. Set from a dashboard card
  or by services `hearth.block_video`, `hearth.block_channel`, `hearth.block_word` and their `unblock` twins. A
  started-video row in the dashboard has a **Block video** and **Block channel** button, which is how a parent acts on
  what the log shows. Phone notifications can carry the same buttons.
- **State document**: per profile, `youtube_blocks` `{"videos": [...], "channels": [...], "words": [...],
  "version": n}`.
- **Hearth** keeps its own lists too (set in its settings, which is all there is without Home Assistant) and hands
  HearthTube the union. It passes it on in the provider (and in a profile's agent) as the `parent_blocks` column, JSON text, null
  when the document has none, and `parent_blocks_version` (integer). Observers on `/active` are told when it changes.
- **HearthTube** reads it when it starts, at each profile ready and when the observer fires, and stores it per
  profile as the **parent's** entries.

### Marking entries: kid or parent

HearthTube's blocked lists (hidden videos, blocked creators, blocked words) get a source on each entry: `kid` (what a
kid hid with Not interested or Don't show this creator) or `parent` (from the contract, or from the PIN menu).

- A kid can add `kid` entries with no PIN, and remove only their own.
- A `parent` entry can't be removed in a kids profile except through the PIN menu (and it is then removed in Home
  Assistant's list too, where it would otherwise come straight back with the next document).
- When Home Assistant's document drops an entry, its `parent` copy goes; the kid's own stay.
- A parent entry for a creator or video the kid also hid is one entry, shown as the parent's.
- The Hidden and blocked screen shows the parent's entries as locked, with no remove button.

## Without Home Assistant

Hearth's own settings are the path (step 2 under Layers): the log and the lists, on the TV behind Hearth's parent PIN.
That is still a trip to a TV, so a parent who wants to do it from the couch wants Home Assistant, or later Hearth
Mobile. And the PIN menu stays: Parent options in a kids profile's card menu shows the started log and saves it as files (done),
and will list and remove `parent` entries and add blocks to the kid's lists. It needs the parent at the TV, so it is the
backup. Without Hearth either (a phone or tablet), none of this applies: the phone app has no kid mode, and gets no
Not interested, creator blocking or Hidden and blocked screen.

## Contract 8, for Hearth's page

Written and committed on branch `parent-channel-contract-8` in the Hearth repo (not pushed).

- Columns: `parent_blocks` (text JSON or null), `parent_blocks_version` (integer, 0 when none).
- Call: `report_started` (extras `time_ms`, `video_id`, `title`, `channel`, `channel_id`).
- `contract_version` 8. History line: "added `parent_blocks`, `parent_blocks_version` and the `report_started` call (a kids
  profile's started videos up, the parent's blocked videos, channels and words down). Also in a profile's agent."
- HearthTube's `HearthProfile.KNOWN_CONTRACT` goes to 8 with its reader; an older Hearth simply has neither, and
  HearthTube keeps its log local and its lists as they are.

## Build order

1. **Hearth contract 8** (Hearth repo): **done, committed on its branch**: provider column and call, the agent relay,
   the log and list stores, 9 unit tests.
2. **HearthTube**: **done, committed (not yet tried on a device)**: the channel id in the watch log; the contract reader
   and sender (`ParentChannel`, with an unsent mark); the parent's blocks kept apart from the kid's (`ParentBlockData`,
   sources Hearth and PIN, plus what the kid blocked themselves); applying them to lists, search, suggestions and the
   player; locked rows in Hidden and blocked; a parent blocks a video or channel from a log row. The phone keeps its
   menu items: a kid's phone now gets Not interested, Don't show this creator and Hidden and blocked with no PIN, as on
   the TV (it was behind the PIN), and adult phones are unchanged.
3. **Hearth's settings screens** (no Home Assistant needed): per kid, Videos started (read, save as files, block a
   video or channel from a row) and Blocked on YouTube (the lists), behind Hearth's parent PIN.
4. **Hearth to Home Assistant**: events endpoint and its queue (Hearth), then the integration's stored log, event,
   sensor, `hearth.watch_digest`, the lists and their services, the state document field, the weekly digest blueprint,
   dashboard card and tests (`ha_tests`).
5. Kid's hides sent up (the second "Up" row).

## Open questions

- **Words:** does the parent's word list apply to titles only, as HearthTube's own does, or to channel names too?
- **Retention:** 2,000 entries per kid in Home Assistant matches the app; a longer record would need its own file.
- **A kid on two TVs:** each TV's HearthTube has its own log; Home Assistant's is the merge, keyed by the kid's
  `profile_id` mapped to a person (the integration's People step).
