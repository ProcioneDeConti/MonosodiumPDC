# Desktop → Android feature parity

Tracking doc for bringing **MonosodiumPDC** (this Android app) up to the feature level of
**Monosodium Desktop** (the Tauri/React app at `D:\Documents\Applications\e621 Desktop`).

As of this file's creation the desktop app is well ahead: it started as the Android app's
counterpart but has since had a large Phase 3 / Phase 4 / post-1.14 run of features that were
never ported back. This doc is the checklist for closing that gap.

- **Desktop version at last sync:** 1.14.88
- **Android version at last sync:** 2.9.0 (versionCode 83)
- **Source of truth for desktop behaviour:** `PROGRESS.md` at the desktop repo root (full
  milestone / feature history) and the architecture map in the desktop `CLAUDE.md`. When an item
  below says "desktop: `X`", that's the file/component to read there for the reference behaviour.

## How to use this file

1. Pick an item, read the referenced desktop component + the e621 API notes in the desktop
   `PROGRESS.md` entry for it.
2. Port it adapted to Compose / the Android app's existing architecture (Retrofit +
   repositories + ViewModels + Navigation Compose) — **not** a literal transcription of the
   React code. The Android app's own patterns win.
3. Check the box, add the Android version it landed in, and note any deliberate divergence.
4. Keep the "Already at parity" and "Desktop-only / out of scope" sections honest so we don't
   re-litigate them.

Priority legend: **P1** core browsing/viewing gap · **P2** meaningful feature · **P3**
cosmetic / polish.

---

## Post viewer & detail

- [x] **P1 · Post notes overlay** — view-only translation/annotation boxes drawn over the
  image, numbered, tap-to-reveal DText body. Desktop: `get_post_notes` command
  (`GET notes.json?search[post_id]=`), `ZoomableImage.tsx`'s `NoteOverlay`. `has_notes` is
  already on the Android `Post` model, unused. Positioning: note coords are against the
  *original* image size — scale by rendered/natural ratio, don't recompute the zoom transform.
  *(Android 2.15.0: `PostNote` model + `getNotes`; `PostNotesOverlay` renders numbered boxes inside
  the viewer's zoom/pan transform (so they track the image), scaled from original px to the
  letterboxed `ContentScale.Fit` rect; tapping a box shows its DText body in a caption panel drawn
  outside the transform. Only fetched when `has_notes` is set; images only.)*
- [x] **P2 · Post description as DText** — Android renders `post.description` as plain `Text`
  (`PostDetailScreen.kt` info sheet). Desktop renders it through `DText`. `DTextView` already
  exists in this app; just swap it in. *(Android 2.9.3: swapped in `DTextView` in the info sheet;
  the empty-description fallback stays plain `Text` since `DTextView` no-ops on blank input.)*
- [x] **P2 · Parent/child relationships** — Android shows nothing. Desktop `InfoPanel` has a
  Relationships row: "Parent #X" chip → search `~id:X ~parent:X`, "N children" chip → search
  `parent:<id>`. The `relationships` object
  (`parent_id`/`has_children`/`has_active_children`/`children`) needs adding to the Android
  `Post` model (it's in the JSON already). *(Android 2.10.0: added `PostRelationships` to the model;
  `RelationshipsRow` in the viewer info sheet renders Parent / N-children chips that run the search
  and close the sheet.)*
- [x] **P2 · Pools as interactive chips + pool browser** — Android shows `#123, #456` as plain
  text. Desktop makes them chips that open a `PoolPanel` (see Pools item under Browsing).
  *(Android 2.16.0: the info-sheet pools row is now `#<id>` chips that open the pool grid; opening a
  pool from a post already in a pool stacks on the nav back-stack.)*
- [x] **P2 · Comment voting** — Android comments are view/post only. Desktop votes on comments
  (e621's voting controller is shared between posts and comments — same `VoteRequest`/
  `VoteResponse` shape). Desktop: `vote_comment` command, `useCommentMutations.ts`.
  *(Android 2.11.0: `voteComment` in `PostActionsRepository`; up/down + score in each `CommentRow`,
  `voteBy`/`score` patched from the response since the comment index doesn't reliably serialize them.)*
- [x] **P2 · Comment edit / delete (own comments only)** — `PATCH`/`DELETE comments/:id.json`,
  `{comment: {body}}` wrapper. Gate on `creator_id == users/me.json id`. Desktop: Comments
  round 2 in `PROGRESS.md`. (Caveat carried from desktop: unconfirmed whether PATCH returns
  the updated comment JSON or an empty body.) *(Android 2.11.0: `editComment`/`deleteComment`;
  PATCH treated as no-body, local copy patched with the sent text. `currentUserId()` fetched
  per sheet-open — not cached, since accounts differ per site.)*
- [ ] **P3 · Comment reply** — client-side DText `[quote]` insertion into the compose box
  (`{username} said:` + original body); e621 comments have no server threading.
- [x] **P2 · Comment reporting** — `POST tickets.json`, `{ticket: {disp_id, qtype: "comment",
  reason}}`. Desktop: `report_comment`. (Low-confidence shape — inferred from the
  Danbooru-family ticket convention, not verified live. Same caveat applies to post reporting.)
  *(Android 2.11.0: `reportComment` → `createTicket`; a Flag action on other users' comments opens
  an inline reason field.)*
- [x] **P2 · Post reporting / flag** — a flag button in the viewer toolbar filing a mod ticket
  against the post (`qtype: "post"`, same `tickets.json` machinery). Android has a `PostFlag.kt`
  model — **verify** whether it's wired to anything; desktop has `ReportPostButton.tsx` +
  `report_post`. *(Android 2.11.1: `PostFlag` was read-only (flag-history box); added `reportPost`
  → `createTicket(qtype:"post")` and a "Report post" action in the info sheet.)*
- [x] **P2 · Related tags** — e621's `related_tag.json` (`search[query]=<tag>`, member-only).
  Desktop: `TagChip` menu → "Related tags" → `RelatedTagsPanel` (category-coloured chips with
  search/add/exclude). Parse defensively — the response shape has drifted across e621ng
  versions (current: bare top-level array of `{name, category_id}`). *(Android 2.19.0: `getRelatedTags`
  (raw body) + `fetchRelatedTags` parses bare-array / `{related_tags:[]}` / `[name,cat]` pairs /
  query-keyed shapes; a "Related tags" item in the tag-chip menu opens a dialog of category-coloured
  chips, tap a chip for search / + / − actions.)*
- [ ] **P2 · Post history / versions tab** — `get_post_versions` (`post_versions.json?
  search[post_id]=`). Desktop: a third viewer sidebar tab (Tags / Comments / History).
- [ ] **P2 · Inline wiki previews** — `[[wiki]]` links currently open the browser. Desktop
  turns them into a tap-to-preview popover rendering the target page's own DText inline
  (`get_wiki_page` → `wiki_pages.json?search[title]=&limit=1`, returns null for a missing
  page). Needs `[[wiki]]` to become its own DText node type carrying the raw title, distinct
  from a plain named link.
- [x] **P2 · Add current post to a set** — a viewer-toolbar popover (add to an existing set or
  create one inline). Depends on Post sets (Browsing). *(Android 2.17.0: "Add to set" pill in the
  info sheet opens a dialog listing your sets (already-in marked) + an inline "new set from this
  post" field.)*

## Browsing & discovery

- [x] **P1 · Pools browser** — `PoolPanel`: fetch a pool (`get_pool` → `pools/<id>.json`,
  public), then assemble the sequence client-side (`id:1,2,3,...` search re-sorted against the
  pool's authoritative `post_ids`), feed it to the grid/viewer as a fixed non-paginated list.
  320-post-per-request cap is a known limit. Opening a pool from a post that's itself in a pool
  should stack. *(Android 2.16.0: `Pool` model + `getPool`, `PoolRepository.fetchPoolContent`
  (fetch by `id:` + re-sort against `post_ids`, 320 cap with a "first 320" banner), `PoolViewModel`
  (fixed list, blacklist-aware like the grid), `PoolScreen` reusing `PostGridBody`, `pool/{id}` route
  + a `SOURCE_POOL` branch in the detail viewer. Rating filter deliberately not applied to a pool.)*
- [x] **P2 · Popular posts browser** — `popular.json?date=&scale=` (public). Day/Week/Month
  segmented control + prev/next period steppers + "Now" shortcut. Desktop: `PopularPanel`,
  `get_popular_posts`, `lib/popular.ts` for the date math. Fixed non-paginated list like pools.
  *(Android 2.18.0: `getPopular` + `PopularRepository` (`PopularScale` owns the date math),
  `PopularViewModel`/`PopularScreen` (Day/Week/Month segmented + `<`/`>` steppers clamped at today
  + Now), `popular` route + drawer entry + `SOURCE_POPULAR` detail branch.)*
- [x] **P2 · Random post / shuffle** — a button that re-runs the *current* search with
  `order:random` mixed in (drop any existing `order:*` first). Re-submitting must actually
  re-roll (e621 re-randomises per request — don't no-op on the unchanged query). Optional:
  a shuffle mode for the slideshow. *(Android 2.14.0: a shuffle icon in the search bar's trailing
  row runs `withRandomOrder(activeQuery)` through `onSearchSubmit`; an unchanged query hits
  `refresh()` which re-fetches, and e621 re-randomises per request, so each tap re-rolls.)*
- [x] **P2 · Post sets** — `post_sets.json`. List your sets, create (name → shortname:
  3–50 `[a-z0-9_]`, ≥1 letter/underscore), open a set into a grid/viewer, add/remove posts.
  Endpoints verified against e621ng source in the desktop `PROGRESS.md` entry
  (`POST /post_sets/:id/{add,remove}_posts` with a top-level `post_ids` array; `create` permits
  `post_set[name/shortname/description/is_public]`). *(Android 2.17.0: `PostSet` model + full API
  (`getPostSets`/`getPostSet`/`createPostSet`/`add`/`remove`/`delete`), `PostSetRepository`,
  `PostSetsViewModel` (hoisted, shared with the add-to-set picker), `PostSetsScreen` (drawer entry,
  list + create dialog with shortname validation + delete), `PostSetContentViewModel`/`Screen`
  (fixed list like pools) + `post_set/{id}` route + `SOURCE_POST_SET` detail branch. Remove-from-set
  wired in the content VM.)*
- [ ] **P2 · Local collections** — purely client-side post collections (no e621 account, no
  API). Desktop stores them in their own local store. Distinct from post sets and favorites.
- [ ] **P2 · Artist pages** — `ArtistPanel`: an artist's wiki/DText + their posts, opened from
  an artist tag. Desktop: `components/Artist/ArtistPanel.tsx`.
- [ ] **P2 · Wiki browser** — `WikiPanel`: search + browse wiki pages standalone (not just the
  inline preview). `wiki_pages.json`.
- [ ] **P2 · Advanced search builder** — `SearchBuilder.tsx`: a form for composing a query
  (rating, order, score/date comparisons, include/exclude tag fields) without hand-typing
  metatags.
- [ ] **P3 · ID list import** — a dialog to paste a list of post IDs and open them as a search
  (`id:1,2,3,...`). Desktop: `IdListImportDialog.tsx`.
- [x] **P2 · Metatag value autocomplete** — currently anything with `:` suppresses
  autocomplete. Desktop: `lib/metatags.ts` — static enums for `rating:`/`order:`/`type:`/
  `filetype:`/`status:`/`locked:`, live-fetched `user:`/`fav:`/`pool:` (via `autocomplete_users`
  → `users.json?search[name_matches]=<prefix>*` and `autocomplete_pools`), syntax hints for
  `score:`/`date:`/`filesize:`. *(Android 2.21.0: `TagSuggestionRepository.suggestMetatagValues` -
  static enums for the six, `autocompleteUsers`/`autocompletePools` for `user:`/`fav:`/`pool:`;
  picking one drops in a `metatag:value` chip. Syntax-hint rows for score:/date:/filesize: skipped.)*
- [ ] **P3 · Recent search history** — auto-recorded, most-recent-first, deduped, capped ~25,
  shown as a dropdown when the search box is focused and empty. Separate from Saved Searches
  (named, explicit). Deliberately not in the backup snapshot.
- [ ] **P3 · Slideshow** — auto-advance through the current search's posts, configurable
  interval + transition, optional shuffle, pause/resume, countdown bar. Desktop-originated
  (no reference app equivalent) — `lib/slideshow.ts`, `PostViewer` slideshow control bar.
- [ ] **P3 · Search tabs** — browser-style parallel searches (session-only, not in the nav
  back-stack). Lower priority on mobile; may not fit the interaction model.

## Grid

- [x] **P2 · Grid quick-actions** — favourite / upvote / download from the grid without
  opening the viewer. Desktop uses hover; Android equivalent is a long-press menu or a
  small action row. Reuse the vote/favorite mutation + local post-cache patch so the
  thumbnail updates instantly. *(Android 2.22.0: long-press a thumbnail on the search grid or
  favorites for Upvote / Favorite / Download. Vote+favorite route through the grid VM's
  `quickUpvote`/`quickToggleFavorite` (+ `updatePost` patch); on the favorites grid an unfavorite
  prunes the post immediately. Pool/popular/set grids omitted.)*
- [x] **P2 · Multi-select + bulk actions** — select mode with checkboxes; bulk favorite,
  bulk unfavorite (two-tap confirm — bulk-destructive), add-to-set, download. Sequential
  `mutateAsync`-style calls so the rate limiter paces them, with N/total progress. When
  viewing your own favorites, pruned posts should leave the grid immediately. *(Android 2.23.0:
  "Select" in the thumbnail long-press menu enters select mode (gold-ringed tiles, tap toggles);
  a bottom bar does bulk Favorite / Unfavorite (two-tap Confirm) / Download with an N/total
  progress bar. Favorite/unfavorite run one request at a time via the grid VM's `bulkSetFavorite`;
  on the favorites grid an unfavorited post leaves immediately. Bulk add-to-set skipped.)*

## Downloads

- [ ] **P2 · Download queue** — a queue panel with per-job state (queued/active/done/error),
  retry, remove, "show in folder", clear-finished/clear-all. All downloads route through it
  (grid button, viewer button, bulk). Desktop: `DownloadsPanel` + `state/downloadsStore.ts`
  (session-only queue, concurrency 2). Android already downloads single files — this is the
  queue/visibility layer on top.

## Account, profile & analytics

- [x] **P2 · Delete dmails** — `DELETE /dmails/:id.json`. Desktop: `delete_dmail` (1.14.37).
  Android `MessagesRepository` has no delete. *(Android 2.12.0: `deleteDmail` (401/403/404 = real
  failure, everything else = success, matching e621ng's template-less response); two-tap Delete in
  the message detail top bar, drops the row from the inbox on return. List multi-select deliberately
  deferred to the general multi-select item.)*
- [x] **P2 · Upload level / upload karma on profile** — e621ng's newer `method_attributes`
  expose upload karma. Desktop shows it on the profile with a progress indicator (1.14.34).
  Verify the Android profile doesn't already have it. *(Android 2.14.0: added the method_attributes
  fields to `UserProfile` (all optional - absent on older e621ng); a Contribution section shows the
  recomputed 0-10 upload level + karma progress bar + uploads/edits tiles + approver/verified pills,
  rendered only when `upload_karma` is present.)*
- [ ] **P2 · User Dashboard** — local-only usage analytics (posts viewed, searches,
  favorites ±, votes, downloads + bytes, time in app, per-site splits, daily buckets,
  top viewed artists/characters). All local, nothing sent, opt-out toggle. Desktop:
  `components/Dashboard/`, `state/statsStore.ts`. Charts are hand-rolled — no chart lib.
- [ ] **P2 · Favorites analysis** — a progressive page-by-page fetch of `fav:<user>` (yours or
  any user's public favorites), rolled up into rating / filetype / score-bucket / year /
  top-artist / top-character breakdowns, with a progress bar + cancel. Structurally
  rate-limit-safe (one page at a time through the normal path). Desktop: `lib/favoritesAnalysis.ts`,
  `queries/useFavoritesAnalysis.ts`. 30-min result cache + 30-sec start gap for API courtesy.
- [ ] **P3 · Favorites "wrapped" share card** — a generated shareable image (score tiles,
  top-artist bars, ratings bar, character chips, a rating-based one-liner), export as
  PNG/PDF. Desktop: `lib/favoritesCard.ts` + `lib/exportCard.ts`. Heavy; do last.
- [ ] **P3 · About page** — app name + live version, tagline, developer links, "built with"
  stack list.
- [ ] **P3 · Profile visual redesign** — hero banner with punched-out avatar, big name +
  level pill, BigAction cards for Posts/Favorites, per-tile stat icons. Cosmetic.

## Settings

- [x] **P2 · Theme override (System / Light / Dark)** — Android follows the system theme only.
  Desktop: `Settings > Appearance` segmented control (1.14.47), persisted + in the backup
  snapshot. *(Android 2.13.0: `ThemePreference` enum, `theme_preference` DataStore key,
  `SettingsBackup.themePreference`, a System/Light/Dark segmented selector in Settings > Appearance;
  `MainActivity` derives `darkTheme` from it.)*
- [x] **P2 · Blacklist tester** — paste/pick a post and see which blacklist entries match it,
  live. Desktop: `BlacklistTester` in the blacklist settings section (1.14.48). *(Android 2.20.0:
  a tester in Settings > Blacklist - enter a post id/link, it fetches the post and reports whether
  it's hidden + which blacklist lines matched, testing the text as-typed (unsaved edits included)
  via `UserSettings.matchingBlacklistLines`.)*
- [ ] **P3 · Backup coverage audit** — desktop found several times that its backup wasn't
  actually covering everything it should (`PROGRESS.md` 1.14 "wasn't actually backing up
  everything"). Cross-check the Android `SettingsBackup` field list against current
  `UserSettings` / stores and note deliberate exclusions (saved searches, search history,
  per-day stats are all intentionally out).

## Fixes worth checking on Android

- [x] **P1 · Pagination for non-default `order:` searches** — desktop had a bug where keyset
  pagination broke for any `order:` other than the default (1.14.36). Check the Android
  `CursorPager` / `BlacklistAwarePostPaging` for the same issue. *(Android 2.9.1: `PostGridViewModel`
  already switched to numbered pages for any `order:` query and de-dupes by id via
  `accumulatePostsUntilVisibleOrEnd`'s `seenIds`; added the missing page-750 hard cap. `CursorPager`
  consumers — forum/messages/feedback/comments — are all default id-desc order, so keyset is correct
  there. `fav:` favorites are also default order.)*
- [x] **P2 · Meta operators silently replaced by a tag suggestion** — typing `score:>1500` etc.
  triggered a live autocomplete fetch and Enter auto-selected a garbage tag. Desktop now skips
  autocomplete whenever the prefix contains `:` and double-checks on the Enter handler. Verify
  the Android `TagSuggestionRepository` / search bar behaviour. *(Android 2.9.2: `TagSuggestionRepository.suggest()`
  now returns empty for any prefix containing `:`. The search bar's `submit()` / space-finalize
  already inserted the literal typed token, never a suggestion, so no Enter-handler change was
  needed.)*
- [ ] **P3 · Forum: oldest-first threads + post search** — desktop 1.14.39.

---

## Already at parity (no action needed)

Search + tag-chip autocomplete + masonry/staggered grid + thumbnail resize · keyset pagination
with blacklist-aware page skipping · client-side blacklist + disable toggle + caution stripe +
import/push against the account · post viewer (zoom/pan, video with loop/speed/mute) ·
vote / favorite · favorites (as a `fav:<user>` search) · saved searches (local-only,
create/delete) · user profiles + feedback records + user comment history · messages / dmail
(inbox, detail, compose, reply, read receipts, unread badge) · forum (topics, topic detail,
reply; browsing public) · comments (view + post; DText body; avatars) · DText rendering
(comments / forum / profile / feedback / messages) · avatars with a shared cache · EULA
first-launch gate · "What's New" dialog (desktop deliberately skipped this — Android-only,
fine) · update checker (manual, GitHub releases, rate-limit count) · on-disk image cache limit
+ clear · accent colour theming · connection health check · encrypted backup/restore
(AES-256-GCM, PBKDF2) · site toggle (e621 / e6AI) with per-site login · app links /
`/posts/{id}` deep links · notifications · "hello" greetings · the "cooter" easter egg.

## Desktop-only / out of scope for Android

These exist on desktop but don't map to a phone (different interaction model or platform
capability). Listed so we don't keep rediscovering them.

- System tray icon, global hotkey (`Ctrl+Shift+E`), close-to-tray — mobile has the task
  switcher.
- Pop a post into its own OS window / multi-window.
- True OS fullscreen (`F11`) — the viewer is already full-screen on mobile.
- Keyboard cheatsheet, full grid keyboard navigation, resizable viewer sidebar, viewer chevron
  positioning — pointer/keyboard affordances.
- Drag-and-drop reverse image search (SauceNAO) — *the drag-drop entry point* is desktop-only,
  but reverse image search itself could be added to Android via a share-target / image picker
  if wanted. Not currently planned. (Desktop: `saucenao.rs`, needs the user's own API key.)
- Portable-exe / local-only-storage mode, password-based local vault encryption — Android uses
  the system keystore / scoped storage; no equivalent needed.
- "Browser-opened index.html" notice, opaque-window / WebView2 compositing perf passes, the
  bundle/crate rename, the raccoon app icon — desktop packaging/perf specifics.

---

## Change log for this file

- _(add an entry each time an item is checked off: date · Android version · item · notes)_
- 2026-09-01 · 2.9.1 · P1 Pagination for non-default `order:` searches · already handled in
  `PostGridViewModel` (numbered pages + id de-dupe); added the page-750 hard cap to match desktop.
- 2026-09-01 · 2.9.2 · P2 Meta operators silently replaced · `TagSuggestionRepository.suggest()`
  now short-circuits any prefix with a `:`; submit already used the literal token.
- 2026-09-01 · 2.9.3 · P2 Post description as DText · info sheet now renders `post.description`
  through `DTextView`.
- 2026-09-01 · 2.10.0 · P2 Parent/child relationships · `PostRelationships` model + Relationships
  row (Parent #X / N children chips) in the viewer info sheet.
- 2026-09-01 · 2.11.0 · P2 Comment voting + edit/delete + reporting · one CommentRow rework:
  vote up/down, own-comment edit/delete (gated on users/me.json id), report others' comments.
- 2026-09-01 · 2.11.1 · P2 Post reporting/flag · reportPost → tickets.json (qtype:post) + a
  "Report post" action with a reason dialog in the viewer info sheet.
- 2026-09-01 · 2.12.0 · P2 Delete dmails · deleteDmail + two-tap delete in message detail.
- 2026-09-01 · 2.13.0 · P2 Theme override · System/Light/Dark selector, persisted + backed up.
- 2026-09-01 · 2.14.0 · P2 Random shuffle + P2 upload karma on profile · shuffle button in the
  search bar; Contribution section on the profile (bundled - both small).
- 2026-09-01 · 2.15.0 · P1 Post notes overlay · PostNote model + numbered tap-to-reveal note
  boxes in the image viewer.
- 2026-09-01 · 2.16.0 · P1 Pools browser + P2 pool chips · Pool model/repo/VM/screen, pool/{id}
  route, SOURCE_POOL detail branch; info-sheet pool chips.
- 2026-09-01 · 2.17.0 · P2 Post sets + P2 add-to-set · full post_sets API + list/create/delete
  screen (drawer) + set content grid + "Add to set" picker in the viewer.
- 2026-09-01 · 2.18.0 · P2 Popular posts browser · popular.json + Day/Week/Month screen with
  period steppers, drawer entry.
- 2026-09-01 · 2.19.0 · P2 Related tags · defensive related_tag.json parse + a Related tags dialog
  from the tag-chip menu.
- 2026-09-01 · 2.20.0 · P2 Blacklist tester · Settings > Blacklist tester (post id -> matched lines).
- 2026-09-01 · 2.21.0 · P2 Metatag value autocomplete · static enums + live user:/fav:/pool:
  completion in the search bar (was fully suppressed for any `:`).
- 2026-09-01 · 2.22.0 · P2 Grid quick-actions · long-press thumbnail menu (upvote/favorite/download)
  on the search + favorites grids.
- 2026-09-01 · 2.23.0 · P2 Multi-select + bulk actions · select mode + bottom bar (bulk
  favorite/unfavorite/download, sequential, N/total progress) on the search + favorites grids.
