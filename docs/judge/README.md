# Hearth Judge: handoff notes

**Status:** preliminary design (v0.2, 2026-10-23). Nothing is built. The full design is in
[`design.html`](design.html); download it and open it in a browser. It is self-contained and works offline.

The Hearth Judge is a home service that judges videos by **topic and context** before HearthTube serves them to kids.
It sits behind HearthTube's existing filters and doesn't replace any of them.

## Decisions so far

- **Pair, don't replace.** Supervised-account refusals, channel blocks, no Shorts, blocked words and AiSList all stay.
  The judge may only hide more, and it reuses the quiet `KidsRefused.hide` / `refuseForKids` path.
- **Precision first.** Hiding many good videos to stop one bad one isn't acceptable, so the strictness depends on where
  the video appears:
  - autoplay and up-next: strictest, because skipping a video there costs almost nothing;
  - rows and search: in between;
  - videos the kid opened directly: protected unless the hit is serious and likely.
- **Measure before enforcing.** The judge starts in shadow mode, logging what it would do. Each surface is enforced only
  once the false flags per true catch, measured in shadow mode, meet the parent's target.
- **The model is swappable** behind an OpenAI-compatible adapter: a local model at home, or a cloud model.
- **Sponsored segments** are detected and used locally. They are never auto-submitted to SponsorBlock, whose rules warn
  or ban automated submitters. A parent review queue is a later, optional phase.
- **AI-made content** is caught by YouTube's "Made with AI" label, AiSList, and an "AI slop" soft topic.
  Forensic video and audio detectors aren't reliable enough on YouTube media.
- **Licensing:** the design draws ideas from Yosemite Kids (GPL-3.0), but no code or prompt text is copied, so
  HearthTube stays MIT.

## Where it plugs in (checked on master, 2026-10-23)

| Surface | Code | Change |
|---|---|---|
| Rows, grids, search | `VideoGroup.add(idx, video)`, near `KeywordFilter.isHidden` (~L493) | Cache-only judge check after the existing three |
| Autoplay | `Video.java` ~L881, `KeywordFilter.checkNext` | Skip judged items when picking the next video |
| Player | `VideoLoaderController`, `refuseClip` … `refuseForKids` (~L614–675) | Add `refuseJudged` on the same quiet path |
| Paid promotion | `VideoLoaderController` ~L349, `getPaidContentText()` | Feed into the judge as a free signal |

## Separate track: read YouTube's "Made with AI" label (no judge needed)

This is a natural extension of AiSList and can ship on its own:

1. **Spike:** find where the label (`howThisWasMadeSectionViewModel` in browser-extension code) sits in the watch-page
   response HearthTube's client receives. Check against a video known to carry the label. It is not in feed or browse
   data.
2. **Parse it in MediaServiceCore.** The submodule doesn't parse it today. That means a fork, or an upstream pull
   request, which would also help SmartTube.
3. **Kids profiles:** refuse a labelled video via `refuseAiChannel` and skip it in `checkNext`. Other profiles show
   "Likely AI".
4. **Rows:** remember the channels of labelled videos in a local list that `AiSlopList.isHidden` also reads, so their
   cards are hidden in rows, where the label itself isn't available.
5. **Caveat:** a missing label proves nothing. The label covers only creator disclosures and YouTube's own labels, such
   as Veo clips.

## Open decisions for the parent

1. Where the model runs: PC GPU, cloud, or Home Assistant CPU.
2. The acceptable false flags per true catch, for serious topics and for soft ones.
3. Whether clean channels skip the full check (channel reputation) or every video is checked.

## Unverified, to check before building

- Can a device fetch the captions and description of a video it isn't playing, at low volume, without tripping
  YouTube's bot checks? The caption code is in the MediaServiceCore submodule, which hasn't been read.
- Which `VideoGroup` types correspond to rows, search and suggestions.
- Whether the PC's GPU is a Radeon 8060S, as reported, and can run the candidate models.
- All cost and latency figures, and every threshold default.

## Before shipping

`PRIVACY.md` is still SmartTube's F-Droid policy. It says "No Backend" and "does not use algorithms or AI". Both
become false once the judge, or a judge-backed feature, ships.
