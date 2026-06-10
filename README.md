<div align="center">

<img src="docs/logo.svg" alt="Intentions logo" width="104" height="104">

# Intentions

</div>

A tool for **building, inspecting and dispatching Android Intents** — construct an intent
by hand, fire it as an activity / broadcast / service, explore everything installed apps
expose, and capture intents flowing through the system.

The app is largely a reconstruction of the Intentions app from 2012, whose code was lost.

> Security note: this is a tool for **authorized** testing of Android apps
> you own or are permitted to test. Use it responsibly.

---

## Features

### Build an intent
- **Intent editor** with toggleable sections — component (package / class), action, data
  (URI + MIME type), categories, and extras. Each field is retained even when toggled off,
  so nothing is lost while experimenting.
- **Autocomplete** for actions, categories and extra names from a built-in catalogue.
- **Typed extras editor** supporting `String`, `Boolean`, `Integer`, `Long`, `Float`,
  `Double`, `Short`, `Byte`, `Character`, `null`, a nested `Intent`, and array / list types
  (`String[]`, `int[]`, `long[]`, `float[]`, `double[]`, `boolean[]`, `ArrayList<String>`,
  `ArrayList<Integer>`). Array values are entered one per line.
- **Flags** — a checklist of common `Intent` flags (`NEW_TASK`, `CLEAR_TOP`,
  `GRANT_READ_URI_PERMISSION`, …).
- **Resolved content type** is shown for `content://` URIs that have no explicit MIME type.

### Dispatch it
From the main screen's **Execute** row:
- **Activity** — `startActivity()` (and shows the returned result code / result intent).
- **Broadcast** — `sendBroadcast()`.
- **Ordered broadcast** — `sendOrderedBroadcast()` and reports the final result code / data /
  extras receivers set.
- **Start service** / **Stop service**.
- **Bind service** — binds and reports the service component and binder interface descriptor.
- **Show manifest** — pretty-prints the target package's `AndroidManifest.xml`.

### Explore the system
- **Package explorer** — searchable list of installed apps with icons. Open one to see its
  activities, services, receivers, providers and declared intent-filter actions, each with
  its icon and a green badge for components that are **exported** (reachable by other apps).
  The scan includes components that are **disabled by default** or **direct-boot**-scoped,
  which big apps routinely declare — so nothing is silently hidden. Tap a component to load
  it into the intent. If an app is already selected, the explorer jumps straight into its
  components.
- **Data browsers** — every **action**, **category**, **data scheme**, **MIME type** and
  **data authority** declared across all installed apps; tap to apply to the current intent.
- **Content-provider query** — enter (or pick from the discovered list of) a `content://`
  authority and view the returned rows.
- **Resource browser** — browse another app's resources across two tabs: **Images**
  (drawable/mipmap rasters and vector drawables, as thumbnails) and **Text / XML** (`xml`,
  `raw`, `layout`, `menu`, … resources decoded back into readable text — binary XML is
  re-serialised). Pick any entry as an `android.resource://…` data URI, or copy the text.
- **App info / Force-stop** — jump to the system App-Info page for a package.

### Capture intents
- **Broadcast sniffer** — a background **foreground-service** monitor that records system
  broadcasts as they happen (action, time, extras). The watched action list is fully
  editable (add / remove / reset to defaults). Tap a captured entry to load it into the
  editor.
- **Scheme & share interceptor** — Intentions appears in the system "Open with" / share
  sheets for common schemes (`tel:`, `mailto:`, `geo:`, `http(s):`, `content:`, …) and for
  `SEND`. Choosing it captures the incoming intent and loads it into the editor.

### Save & share
- **Bookmarks** — save intents (stored locally) and reload them later.
- **Recent intents** — an automatic history of intents you've executed; tap to reload.
  Re-running an intent moves it to the top, and entries can be deleted individually.
- **Copy as `adb` command** — from the editor's ⋮ menu, generate a ready-to-run
  `adb shell am start …` line (extras map onto `am`'s typed flags). `am` has no syntax for a
  nested-`Intent` extra, so those are omitted with a heads-up. Works on nested intents too.
- **Home-screen shortcut** — also in the editor's ⋮ menu; pins a shortcut that fires the
  intent you're editing (including a nested one).
- **Clipboard** — copy/paste an intent as a portable Base64 string.
- **Home-screen shortcut** — from the editor, pin a shortcut that fires the current intent
  (modern pinned shortcuts, with a legacy fallback).
- **Create-shortcut host** — responds to a launcher's `CREATE_SHORTCUT` request so you can
  build an arbitrary intent shortcut from a launcher.

---

## Permissions & notes
- `QUERY_ALL_PACKAGES` — the app's purpose is to enumerate and inspect other apps.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `POST_NOTIFICATIONS` — the
  background broadcast sniffer and its persistent notification.
- `KILL_BACKGROUND_PROCESSES`, `INSTALL_SHORTCUT` — the force-stop helper and legacy shortcut
  fallback.

Some original capabilities are limited by modern Android: listing/killing *other apps'*
running services is no longer permitted (Android 8+), so the app offers the system
App-Info / force-stop page instead.

## Install
A debug build can be installed with:

```sh
./gradlew :app:installDebug
```

See [DEVELOPMENT.md](DEVELOPMENT.md) for the toolchain and architecture.

---

## License

Copyright (C) 2026 David Matscheko

This program is free software: you can redistribute it and/or modify it under the
terms of the **GNU Affero General Public License** as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along with
this program. If not, see <https://www.gnu.org/licenses/>. The full text is in
[LICENSE](LICENSE).
