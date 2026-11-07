# Changelog

Versions 0.9.0 and earlier below are the history inherited from the upstream project Vellum (MIT, Max Leiter), from which
Oriel UI was forked. Names in those entries (`VellumPages`, `VellumSession`, `vellum.data`, `/vellum`, `vellum.properties`) are
the upstream's; in this fork they are `OrielPages`, `OrielSession`, `oriel.data`, `/oriel` and `oriel.properties` (the handbook in `docs/zh` is authoritative). Treat
those entries as history, not as documentation of the current API.

## 0.11.0 (in development)

- Kit pages: the home page is rebuilt around the pitch, the component library, the community's picks (a carousel with title and author), the mod's settings, the icons, the mod's information and a "coming soon" tile; the main tabs are now Home, Community, Components, Icons, Settings, About. New pages: mod settings (writes the player-facing client settings back through the game) and about (author links to copy, other mods, the Ore UI and beyond statement). The template centre loses its stacked rows (crumb, hints, tag chips, sub-headline, footer): one toolbar row over the grid, a collapsible sidebar with short categories (`templates/categories.json`), details with the actions pinned at the bottom, author avatars kept with the templates (`templates/avatars`), and the inventory, settings, potion HUD and component editor pages as official templates (54–57).
- Settings: add the local mod-settings host for seven player preferences, typed validation and live state replies. Save/reset uses atomic, comment-preserving properties updates; server sessions, privileged keys, malformed and oversized requests are refused. Existing startup fallback behavior and public client/server APIs remain unchanged.

- Add experimental block-flow/overflow old/new/both verification (default old), immutable diagnostic geometry, optional sizing constraints, explicit font-measurement invalidation, a fixed-row range calculator, and the development-only oriel_layout preview probe. Inline/positioning/incremental layout and full R06 gates remain separate work.

- CSS: add opt-in old/new/both cascade ranking, dependency-path invalidation with full-reference computed-field checks, read-only installed-property metadata, and explicit cascade explanations through --why-property / oriel_inspect. Real nested cascade layers are separately opt-in; defaults remain legacy pending performance acceptance. Typed custom properties and true revert-layer remain unsupported. See docs/zh/d36-r04.md.

- Selectors: opt-in compiled matching and differential rule indexing (old/new/both, default old). The new path tracks conservative attribute and relative :has invalidation, queries attribute buckets from the element's present attributes, directly evaluates primitive compound predicates with exact work accounting, and caches nth(of) sibling ranks per pass. Add :target for the document URL fragment and explicit --why / oriel_inspect traces with declaration winners and work budgets. See docs/zh/selector-debug.md for limitations.

- Development: add tools/bench resource freezing, alternating independent A/A and A/B CPU frame comparisons, per-page p50/p95 noise verdicts, deterministic synthetic stress generators and a strict 15-effect captured-frame comparator. Existing timing/golden baselines remain unchanged; phase timings, hover input transitions and game measurements require separate evidence.

- MCP: add versioned capability discovery, observational build-marker queries, bounded tool-event JSONL and readers, a selector why-tree adapter for the R03 CLI, separate fixed-PNG baseline approval with browser-reference comparison, and complete screenshot-review pagination. Route workspace builds through gws without duplicating console options; preserve existing tool receipts.

- Benchmarks: default to chronological paired A/A and A/B relative differences with 95% Student-t intervals and explicit inconclusive verdicts; record external Java/Minecraft process hints at measurement boundaries without retaining command lines.

- Agents: document task ownership, workflow modes, scheduling and evidence contracts; add copyable task, report, evidence, terminal-state and R10 templates.


- Input: add opt-in old/new/both routing experiments, bounded generation-aware text measurement, grapheme/caret fast paths and a conservative large-block hit grid. Keep old as the default pending all migration gates. Add explicit event/composition JSONL diagnostics, why-no-click/focus queries through preview actions and MCP, inert input exclusion, and opt-in spatial navigation actions; existing IME transactions and undo behavior remain shared.

- Layout: add an opt-in Grid candidate with indexed intrinsic contributions, allocation-free occupancy checks, named-line metadata and first-baseline groups. Add headless preview --why-size/--why-position JSONL diagnostics for both solvers; legacy defaults stay unchanged.

- Layout: add an opt-in Flex candidate with pass-local item reuse, intrinsic measurement reuse and independent-tree old/new/both comparison. Keep the legacy implementation as default; expose opt-in why-size/why-position JSONL traces without changing page CSS.
- Scripting: opt-in WIP (-Doriel.ecosystem=new; default old) on-demand HTML namespace and type probes for React 18 / Preact / anime, optional four-point Canvas and flat-square confetti bridges, pinned external ecosystem fixtures and regression scenarios. Unused probes and namespace members allocate no feature slots/accessors; pre-existing and detached input wrappers acquire their type on first probe. No browser networking, SVG/iframe implementation or relaxed sandbox limits; see docs/zh/ecosystem.md.

- Paint: add opt-in decoration command caching with old/new/both modes, immutable DisplayList recording/replay, bounded cache counters, and preview/MCP command export. Preserve backend shape overrides and native-content boundaries; old remains the default. Add the narrow Canvas strokeLine extension and explicit source-over/lighter/screen compositing. See docs/zh/d36-paint.md for limits and validation boundaries.

- MCP: add oriel_scaffold for read-only downstream Fabric/NeoForge/Stonecutter detection and integration snippets. Optional preparation runs strict page checks; chat-approved snapshots can create a hello page and client-only Java helper without replacing existing files or editing Gradle/metadata. Receipts retain skipped files and separate downstream game verification.

- Images: opt-in `img loading=lazy` and `data-oriel-lazy` URL backgrounds share a document queue (three sources per 50ms by default, configurable on html), with half-viewport prefetch, fixed-size placeholders and cancellation of unstarted detached elements. Eager loading stays the default; loaded textures retain host-managed lifetimes. The preview checker warns on more than 24 image/background references without lazy loading.

- Preview: structured, bounded post-layout diagnostics for ellipsized text without a tooltip, clipped nowrap text (including ancestor clips), shrinking flex content, and oversized PNG references. Strict checks now include both viewports; snapshots/actions check their final layout. PNG inspection reads only headers and file sizes. Existing kit/showcase/demo pages retain these findings as INFO pending separate visual fixes; external pages remain strict warnings. See docs/zh/validation.md for coverage limits.

- MCP: a resource-only mod-project integration target for downstream mods, with metadata/resource-directory detection, explicit candidates for ambiguous projects, project-bound path checks and copyable opening/hot-reload guidance. Existing approval, snapshot and transaction checks apply; downstream game verification remains separate.

- Layout: constrain scroll-container flex intrinsic contributions by min/max size, keeping auto-height parents sized to the scrollport while preserving the full scrollable content. Fixed-height workarounds for limited scrollboxes are no longer needed.

- UI sounds: eight kit tokens shared by script calls and Shell controls, declarative `data-oriel-sound` overrides (`off` mutes an element, `hover` opts into pointer-entry feedback), modal open/close cues, and client-wide `client.uiSounds` / `client.uiSoundVolume` settings. Existing namespaced sound ids remain supported; headless previews only record requests. No audio files or vanilla UI changes are added.

- Canvas: reuse bounded blit buffers and horizontal samples, avoid redundant source-over channel clamps, and preserve overlapping self-copy snapshots. The Oriel-specific `fillQuad(x0, y0, x1, y1, x2, y2, x3, y3)` fills convex quadrilaterals in native scanlines, avoiding per-row JavaScript calls for rotated particles. Pixel-oracle and allocation regressions cover both paths. See `docs/zh/performance.md` and `docs/zh/scripting.md`; this does not add general paths, transforms, or GPU canvas rendering.

Breaking (supported API): the item editor is no longer part of the library: `/oriel edit`, `OrielKit.ITEM_EDITOR`, `ItemEditorPreset`, `LocalizedMessage.componentValue` and the `oriel.ui.item_editor.*` / `oriel.server.item_editor.*` strings are gone (an application belongs in a mod built on Oriel). The demo commands are one: `/oriel showcase [page]` replaces `/oriel demo`, `/oriel legacy` and the old `/oriel showcase <page>`; the potion HUD toggle is `/oriel showcase potions` and `/oriel showcase templates` opens the template centre. Depend on `[0.11.0,0.12.0)`.

- Template centre: a Demos view (the twelve inherited showcase pages, with covers) and a My pages view that lists `config/oriel/my-pages`; a page sets its card title, subtitle and cover in its own `<head>` (`<title>`, `oriel-subtitle`, `oriel-cover`, or `cover.png` beside it), and the card tooltip shows the path, time and size.
- Plain HTML pages opt into the shared behaviour (ripples, spotlights, counters, theme and icon switches, `data-action`) with `data-oriel-shell` on `<html>`, no Java controller needed.
- A key binding, Options > Controls > Oriel UI > Open the Oriel home page (F8 in a development run, unbound for players), and a Demos button on the home page.
- `dev.oriel.mod.client.OrielPages` is now a public entry (`folder(namespace, dir)`, `isFolder(namespace)`) for folder pages; the handbook already described `OrielPages.folder`, but the class was only reachable in `dev.oriel.mod.internal`. The API snapshots list it.
- Escape on a page nothing else handles goes back to the previous page; coming back to the template centre from a Demos or My pages card restores its view, sort, search, tag and scroll position.
- The template centre lists My pages from an index (title, subtitle, cover path) and loads the cover pictures three at a time, only for the cards that show or are about to; a folder of thirty pages no longer loads every picture at once. A page itself is loaded only when opened.
- Docs: `principles.md` (what Oriel is and the rules an agent follows), `upgrading.md`, and `replication.md`, the developer workflow for replicating an existing animation, web page or game screen.
- Breaking: the library no longer changes any vanilla screen. The inventory effects panel (and its settings `client.inventoryEffects`, `client.hideVanillaInventoryEffects`), the HUD that hid vanilla's effect icons, the gallery card that gave the player every effect, the player-effects bridge and the access wideners for them are removed. `/oriel showcase potions` is a preview with sample effects. Oriel registers no mixins; a mod that wants these screens in the game writes that part itself (the presets and `GameEffects.provide` stay for it).
- Dev: a downstream mod's own pages reload on save: automatically in a Gradle `runClient` whose run directory is inside the project, with `-Doriel.sourceRoot=<project dir>` when it is elsewhere, and with a line `namespace=<source assets folder>` in `config/oriel/dev-sources.properties` when the jar just sits in a `mods` folder (any environment; images still come from the jar). See `docs/zh/quickstart.md`.
- MCP: an `oriel-folder` integration target, a screenshots review mode with a quoted chat approval, and `quickref` / `showcase` rule topics; `tools/showcase/query.mjs` finds a showcase effect and its source.
- The previewer carries the library's own assets, so pages outside this repository that link `oriel:ui/ore.css` can be checked; Shell's injected icons use `oriel:` URLs.
- Docs: `page-rules.md` (fonts and sizes, a checked skeleton, the max-height scroll box limit) and `showcase.md` (where each showcase effect lives).

## 0.10.0

Breaking (supported API): the canvas-specific slot callback is now `DocumentDriver.SlotSink`, and everything under
`dev.oriel.mod.internal` is no longer callable API. Depend on `[0.10.0,0.11.0)`. See `docs/api/setup.md`.

- Declarative bindings from a Java view model: `o-text`, `o-for` (with a required `o-key`), `o-if`, `o-class:*`, `o-attr:*` and `o-on:*`, with diagnostics that name the file and position. See `docs/zh/bindings.md`.
- Editing a stylesheet no longer rebuilds the page: DOM, controller, focus, input, selection and scroll are kept. HTML and script changes still reload the page.
- The element inspector and the other development tools are left out of release jars.
- Text input: composition (IME) no longer deletes or commits by mistake, cancel and focus loss behave, the candidate window follows CSS scale, and deleting, moving and selecting step over whole grapheme clusters (emoji, combining marks). Chinese punctuation obeys line-start and line-end rules in `<textarea>` the same as in `<p>`.
- Script sandbox: `JSON.parse` can no longer be used to get past the data depth limit, and pages pushed by a server can no longer read folders through navigation or reload.
- Servers: workstation menus can check dimension, position, block and distance (default: a non-air block underfoot within 8 blocks). The item editor keeps unknown components and `custom_data`, falls back to read-only when a component codec fails, and checks old wear when lowering max damage.
- Tooltip cards of the effects panel are placed by their measured size, so long descriptions stay inside the window.
- Development workflow: a web-first MCP flow (preview, check, review, then game); `-Pautopilot=mcp` runs a page in the game. See `docs/zh/mcp.md`.
- Build: Error Prone runs on JDK 25 over Oriel's own sources; local access-widened Loom outputs are no longer pinned byte for byte (external dependency digests are unchanged); `tools/verify-central.mjs` checks digests against Maven Central.

## 0.9.0

- An element inspector: F12 on a page's screen docks a panel on the right with the page's tree, a picker, and the selected element's box, attributes, inline style, text and computed values. Attributes, style and text can be edited live, and elements deleted. Drag the panel's edge to resize it, and the bar between the tree and the details to share its height. It's on in a development environment, and `client.inspector=true` turns it on elsewhere. See "Inspector" in `docs/api/development.md`.
- The `map` demo is gone.

## 0.8.0

- `<scene>`: a small 3D world in a box. Blocks come from a structure file (`structure="ns:path/file.nbt"`) and `<block x y z state>` children; `<block-group x y z>` claims a box of them (`x="3..7" y="2"`, an axis left out being all of it) to show, fade (`opacity`), move (`-mc-translate`) and turn (`-mc-yaw`, `-mc-pitch` and the new `-mc-roll`) with ordinary CSS transitions and animations; `<entity x y z>` children stand in it; and `<scene-anchor x y z>` pins HTML captions to points in it. The camera is `-mc-yaw`, `-mc-pitch`, `-mc-model-scale` and the new `-mc-scene-center`, plus dragging with `rotatable`. Hovering a block shows its name and fires `blockhover`, clicking fires `blockclick`. Each group is meshed once, with neighbors culled, fluids, connected blocks and block entities, and a scene that doesn't change is one blit a frame. Scripts change scenes by changing their children. 26.x only: on 1.21.1 a scene lays out and fires its events but draws nothing. The previewer draws scenes from the blocks' models. See "Scenes" in `docs/api/elements.md`, and the Cottage showcase (`/vellum showcase scene`), which builds itself step by step.
- `Host.loadBytes`: hosts give the engine binary resources (structure files). In game it reads resources, dev source trees and page folders as `loadText` does.
- Style changes cost much less when selectors can't see them: a script writing `el.style` or text every frame no longer rematches every selector in the page (about 1 ms a frame down to 0.2 ms on a 500-element page). Selectors are matched again only after tree changes, state changes (`:hover`, `:checked`...), changes to attributes some selector reads, `id` and `class`, and text that empties or fills an element.
- Changing a `<link>`'s `href`, `rel` or `media`, or text inside an element in a `<style>`, restyles the page at once, not with the next change that happens to.
- `DocumentDriver.patch(key, value)` sets one top-level field of `vellum.data` without sending the rest. Only the value is serialized and parsed, the page's other objects stay as they are, and the page gets `vellum.on('patch', ({key, value}) => ...)`, then its `'data'` listeners with the key as a second argument. A null value removes the field. Patches are kept across reloads, as pushes are. `VellumSession.patch` and `VellumHud.patch` do the same from a server and for HUD overlays; a server's patches count toward `client.maxDataChars` and `client.maxDataDepth`.
- `merge` no longer parses and re-serializes the whole data: the driver keeps each top-level field's JSON and only serializes the fields you pass. The page still gets the whole data.

## 0.7.0

- `VellumPages.folder(namespace, dir)` serves a namespace from a folder players can edit, such as one in `config/`. Relative stylesheets, scripts and images resolve inside it, saving any of them reloads the page, and nothing outside the folder can be read. See "Pages players edit" in `docs/api/pages.md`.
- If the shape shader fails to compile, Vellum draws shapes the old way until the next resource reload, instead of retrying the compile every 5 seconds (a hitch each time).
- Less work per frame: animations are only advanced a second time when an event handler changed them, outer box-shadows skip work the shader threw away, and the previewer no longer allocates per pixel.
- Less work per restyle, relayout and event: color transitions no longer copy the whole style twice a frame, grid track sizing measures each item once, flex and grid containers skip the anonymous boxes whitespace between items used to make, and event listeners only build error messages when something fails.
- The build no longer pulls in Mixin, whose annotation processor made every change recompile all of the mod's code.
- `&amp`, `&lt` and the like without a semicolon decode at the end of text and right before another `&` (`a &amp` was `a &&`).
- Deselecting an option that wasn't selected, when that leaves a select with no selection, now updates the page (`:checked`, `v-model`).
- A grid row's minimum counts a scroll container item's percentage margins.

## 0.6.2

- The jar is about 12% smaller (2.8 MB to 2.5 MB): classes keep line numbers for stack traces but not local variable tables, and Rhino's French and Chinese error messages are left out.

## 0.6.1

- On Minecraft 26.2 and 26.3, rounded boxes, rounded borders and box-shadows are drawn by a shader: smooth antialiased edges at any GUI scale or rotation, and a real Gaussian blur. `shape-rendering: crispEdges` keeps a page or element pixel-sharp, and `client.shapeShader=false` in `vellum.properties` turns the shader off for everything. 1.21.1 is unchanged.
- The previewer draws rounded boxes, borders and shadows the way the shader does, so pages look the same there as in game.
- `Canvas.fillBoxShadow`: a canvas backend can draw box-shadows itself.

## 0.6.0

- Timers a page sets while it loads wait their full delay. In game, the first frame jumped the clock from 0 to the game's own time, so a load-time `setTimeout` fired at once and `performance.now()` jumped. `performance.now()` and `requestAnimationFrame` now count from the page's first frame, as in browsers.
- The jar is about 150 KB smaller: Rhino's bytecode compiler, serialization and CommonJS loader, which Vellum never runs, are left out.
- Showing a HUD overlay again from its own `onClose` handler works (it was undone right after), so a queue of toasts is a few lines; `docs/api/hud.md` has the recipe.
- `-mc-entity-focus: center` fits an `<entity>` like `body` but zooms around its middle with `-mc-model-scale`, so zooming in no longer shows only the feet.
- `:key` works on any element, not only in a `v-for`: when the key changes the element is re-created, so its entrance animation replays. A keyed `v-if` branch does the same. The journal and Mobdex use it instead of one-item loops.
- What an animation's `finished` promise or event changes applies before the next frame is drawn, so the journal no longer flickers after a page turn.
- The templates and the Vellum build start from any JDK 17 or newer: Gradle downloads Java 25 for itself, which Fabric Loom needs.
- The showcase and demo pages are about 12% smaller and share `showcase/showcase.css`, with the same look.

## 0.5.1

- `list-style` and `list-style-type` work: `none` removes a list's bullets, and `disc`, `circle` and `square` pick them.
- Enter in a text field submits its form, as in browsers.
- NeoForge 26.3 no longer warns about Vellum's deprecated `logoFile` at startup.
- `template/` has example mods for NeoForge and Fabric that depend on Vellum from the maven.

## 0.5.0

- Vellum is on maven.maxleiter.com. Mods should make it a required dependency instead of bundling it.
- `.mc-dim` and `.mc-center` give a page vanilla's dimmed background and centered layout.
- Vellum now runs on Minecraft 26.2 as well as 26.3 and 1.21.1, on NeoForge (26.2.0.88) and Fabric (Fabric API 0.161.0+26.2). Everything works on 26.2 except the dev tour (`-Ptour`), which stays 26.3 only; docs/versions.md lists the differences.

## 0.4.1

- First release on CurseForge and Modrinth.
- Vellum has a mod icon.
- The jars include Rhino's license and notice, and say where the patched Rhino source is.

## 0.4.0

- Vellum now runs on Minecraft 1.21.1 as well as 26.3, on NeoForge and Fabric. Everything works on 1.21.1; the README lists the few differences.
- Scripts can use classes, async functions and `await`, spread arguments in calls, and `let` and `const` with a fresh binding in each loop iteration.
- A client `onMessage` handler that throws is logged and the other handlers still run.
- `/vellum demo chest` is the vanilla-looking chest. The old dark chest demo is gone.

## 0.3.0

- Pages from servers are treated as untrusted. Scripts get budgets for CPU time, memory, timers and frames, and the engine caps DOM size, CSS size and nesting depth.
- Every cap is a setting in `config/vellum.properties`, including the engine limits (`limits.*` keys).
- Messages from servers are parsed strictly, with size and depth limits and a cap on open sessions per player.
- Script errors caused by a bug in Vellum read "Internal error in <name>" on the page and put the details in the log.
- The demo chest looks like vanilla's, and `.mc-panel` has vanilla's black outline.
- Mobdex mobs call out when picked, and the trader chimes on a purchase.

## 0.2.0 to 0.2.6

- Narration: a page's `<title>` is the screen title, focused and hovered elements read like vanilla widgets, and `aria-live` regions speak.
- `-mc-tooltip-delay` sets how long a `title` tooltip waits.
- Entity portraits: `object-position`, `-mc-entity-focus`, `-mc-gaze-reach` and `-mc-gaze-limit`, and mods can supply render states.
- `VellumAutomation`, a client API for driving pages in tests (hover, click, drag, type, wait for a page to settle).
- `VellumScreen.pauses(boolean)` lets a screen pause a singleplayer world.
- `DocumentDriver.onKey` gives a mod the keys its page doesn't use.
- Pages are hovered from their first frame, under a resting pointer.
- The previewer can run a script of input and screenshots (`--actions`) and print what the narrator would read (`--narrate`).
- CSS: radial-gradient sizes, animated pseudo-elements, and `min()`, `max()` and `clamp()` mixing px and %.

## 0.1.x

- The first releases: the engine (HTML, CSS with block, inline, flex and grid layout, transitions and animations, sandboxed scripting and templates), screens, container screens with `<slot>`, HUD overlays, server pages, the previewer, and 3D `<model>` and `<entity>` content.
