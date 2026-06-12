# Development

## Toolchain

| Tool | Version |
|------|---------|
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.21 (with the Compose compiler plugin) |
| Gradle | 8.13 (wrapper) |
| Jetpack Compose | BOM 2026.05.00, Material 3 |
| Room | 2.8.4 (KSP) |
| compileSdk / targetSdk | 36 · minSdk 24 |
| Java bytecode | 17 |

The build compiles on JDK 17–21. The pinned bytecode target is 17 but **no Gradle
toolchain is configured**, so it uses whatever JVM runs Gradle — point `JAVA_HOME` at a
JDK 17–21 (the Android Studio JBR works well):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug      # build
./gradlew installDebug       # build + install on a connected device/emulator
```

If multiple devices are attached, target one with `ANDROID_SERIAL=<serial>` (e.g.
`emulator-5554`).

## Architecture

Single-activity, single-module, **Jetpack Compose + Navigation**, with a
`ViewModel`-held state model. There are no Fragments and no XML layouts (only resources).

```
at.matscheko.intentions
├── MainActivity                 launcher entry; CREATE_SHORTCUT host; loads captured intents
├── InterceptorActivity          scheme/share filter target → records + hands intent to Main
├── model/
│   ├── IntentSpec               editable description of an Intent (the source of truth)
│   └── Extras                    ExtraEntry + ExtraType (Bundle (de)serialisation)
├── core/                        framework-facing logic, no Compose
│   ├── ManifestScanner          installed apps, components, action/category/data scans, providers
│   ├── ResourceBrowser          enumerate + render another app's resources (zip + Resources)
│   ├── IntentActions            broadcast / ordered broadcast / service / bind / manifest
│   ├── IntentCodec              Intent ⇄ Base64 (Parcel marshalling)
│   ├── IntentClipboard          clipboard get/put for intents and text
│   ├── Shortcuts                pin a home-screen shortcut (ShortcutManagerCompat)
│   ├── SnifferRepository        broadcast sniffer: action list, receiver, persistence, state
│   ├── BroadcastActions         curated default action list + schemes
│   ├── ManifestReader / IntentSuggestions / Toasts
├── data/                        Room
│   ├── Bookmarks                Bookmark entity + DAO + DB
│   └── SnifferLog               SniffedBroadcast entity + DAO + DB
├── service/SnifferService       foreground service that owns the sniffer registration
└── ui/
    ├── AppViewModel             shared state: working IntentSpec, result, caches, bookmarks
    ├── IntentionsApp            NavHost + Routes
    ├── theme/                   Material 3 (dynamic colour)
    ├── components/              IntentCard, AutoCompleteField, ListOverflowMenu
    └── screens/                 one composable per destination
```

### Key design points

- **`IntentSpec` is the source of truth.** The original Java app passed real `Intent`
  objects between activities plus a "shadow intent" so toggling a field off wouldn't lose
  its value. Here every field is always retained and the `has*` flags decide what
  `toIntent()` includes — no shadow copy needed. `IntentSpec.from(intent)` imports an
  existing intent (clipboard, bookmark, result, captured share).

- **`AppViewModel` is activity-scoped** (obtained via `viewModel()` in `IntentionsApp`), so
  every screen edits the same working intent. It owns:
  - the working `spec`, last `resultText` / `resultSpec`, and `viewSpec` (read-only view);
  - cached installed-apps list (prefetched + icon-warmed at startup), per-package component
    lists, and `LruCache`s for app / component / resource icons;
  - the content-provider list and the shared content-query URI;
  - bookmark CRUD via Room `Flow`.

- **Icons load lazily per visible row** via `produceState`, backed by the ViewModel
  `LruCache`s, so lists scroll without jank. The app/icon list is warmed in the background
  on launch.

- **Manifest scanning** (`ManifestScanner`) reads other packages' compiled manifests with
  `AssetManager.openXmlResourceParser("AndroidManifest.xml")` and enumerates components via
  `PackageManager` GET_* flags; everything heavy runs on `Dispatchers.Default`.

- **Resource browsing** (`ResourceBrowser`) discovers resource *names* by reading the target
  APK(s) (`applicationInfo.sourceDir` + splits) as a zip, then resolves & renders each via
  `getResourcesForApplication(pkg)` + `getIdentifier` (so vector drawables and density
  variants render correctly). Resource-shrunk/obfuscated names can't be resolved and are
  skipped.

- **Broadcast sniffer.** `SnifferService` (foreground, `specialUse` type) holds the
  registration; `SnifferRepository` (object) builds the `IntentFilter`s, registers the
  runtime receiver with `ContextCompat.registerReceiver(..., RECEIVER_EXPORTED)`, persists
  the editable action list in `SharedPreferences`, and writes captures to the
  `SnifferDatabase`. Three filters are registered (actions-only, actions+schemes,
  actions+`*/*` type) because a single filter with data specs would drop no-data broadcasts.

## Testing

Run the unit suite with:

```sh
./gradlew testDebugUnitTest --rerun-tasks
```

- **Pure JVM** tests cover the framework-free logic: `AmCommandTest` (the `adb am` generator)
  and `ExtraTypeDescribeTest` (value → `ExtraType` classification).
- **Robolectric** tests (`@Config(sdk = [34])`) cover the framework-dependent core:
  `IntentSpecRoundTripTest` (spec ⇄ `Intent`), `ExtraTypePutTest` (typed extras → `Bundle`),
  and `IntentCodecTest` (Base64 `Parcel` round-trip).

`am`-command building and extra classification are pure functions specifically so they can be
unit-tested without a device. For features that need real installed apps / the live framework
(scanning, the sniffer, resource browsing), manual verification is done on a device/emulator
via `adb` (install, drive, screenshot).
