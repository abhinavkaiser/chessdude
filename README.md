# Chess Dude for Android

A native Kotlin / Jetpack Compose Android application with a private Room database and the complete official **Stockfish 19** C++ engine. Android 9 (API 28) or newer, **arm64-v8a**. No Internet permission, account, server, JavaScript, WebView, telemetry, runtime asset download, or reduced-strength engine.

## Version 1.2.0 — Chess Dude

The launcher name, app header and delivery files are **Chess Dude**, with an original knight-and-sunglasses icon in the launcher, app header and navigation drawer. Artwork and generation prompt are in [artwork/ICON.md](artwork/ICON.md). The Android package ID, signing certificate and database are unchanged for upgrades from the first Offline Chess APK.

Live analysis remains enabled after a timed/depth-limited search finishes and automatically starts again after each played move, navigation step, or sideline selection. The Stop button explicitly disables following moves. Generation checks and serialized cancellation prevent results from an old position appearing on a new board.

The board now has a left-edge black/white evaluation bar, with the White segment at the bottom in normal orientation and at the top when flipped. Scores and the advantage label use White’s perspective; terminal mate/stalemate UCI scores also display correctly. A dedicated notation window stays below the board on phones and beside it in landscape/tablet layouts. It follows the [ChessBase notation-window conventions](https://help.chessbase.com/cbase/17/eng/notation_window.htm): bold game moves, regular-weight indented variations in brackets, current-move highlighting and comments flowing inline. Tap a move to jump to it. The forward arrow opens **Choose move** whenever the position has multiple continuations. No line-name labels are shown. Each variation can be folded with its minus button; **Notation options** includes fold/unfold all and text-size controls. Entering a folded branch unfolds its path. The notation scrolls independently. Engine PVs, export and full-game analysis are under the **Game and engine details** button (slider icon).

Use **Save game** above the board to enter player names, optional White/Black Elo, game name/event, date and result, then choose **Save to My Games**. This updates the existing game while retaining its full move tree and unknown/custom PGN tags. Moves and variations are also saved automatically as they are played, so explicitly saving is for naming and finding the game rather than avoiding lost moves.

Use the pencil beside **Notation** to add or edit comments before or after the selected move, including paragraphs and annotation symbols. Tap an existing inline comment to edit it. Both comment fields are persisted, backed up and exported in PGN; comments before a move are written after its move number so that their placement survives a round trip. Schema 3 migrates existing games without deleting data; older v1 backups remain readable.

Use the **Share** icon in the top bar of Chessboard, Analysis or Game Review to share a full PGN file, full PGN text, notation with all sidelines, the current board as a PNG diagram, or the current FEN. Android’s native share sheet lists compatible installed apps (such as WhatsApp and Teams); the user chooses the recipient app and sends there. PGN files retain original tags, annotations and variations. Filenames use the actual player names, event and date when provided, for example `Carlsen_vs_Gukesh_Tata_Steel_2026-11-01.pgn`; an unnamed analysis uses `ChessDude_Analysis.pgn`. Separate share directories prevent one export from overwriting another. Images follow the current board orientation/theme and include a FEN caption. FileProvider grants temporary read access only to generated exports in a dedicated cache subdirectory; the database and private files are not exposed. Sharing works without a network connection in Chess Dude; a receiving messaging app may need its own connection to send.

## Build and install

Required: JDK 17; Android SDK platform 35; build-tools 35.0.0; NDK 28.2.13676358; CMake 3.22.1. Gradle 8.11.1 is provided through the wrapper. Dependencies must be resolved at build time; the installed application is offline.

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1'
# Set ANDROID_HOME, or put sdk.dir=/absolute/path/to/sdk in local.properties.
./gradlew clean
./gradlew testDebugUnitTest
./gradlew assembleDebug
bash scripts/verify-apk.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The native build runs automatically before Android compilation. The complete NNUE is already in `app/src/main/assets`; building never downloads an engine or network. This project uses AGP 8.9.1, Kotlin 2.1.20, Compose BOM 2025.04.01, and Room 2.7.1.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It is signed with a development debug key, not a production signing key. Complete source and NNUE accompany this project. See `VALIDATION.md` for actual executed checks and device limits.

To recreate the delivery archive after building, run `python3 scripts/package-delivery.py`. The archive includes all source, bundled NNUE, licenses, schemas and test evidence, excluding local SDK paths and generated build caches.

## Using the application

- **Home:** device-local library counts, recent games, new board and import actions. No sample records are injected.
- **My Games:** paginated library, player/event/site/opening/tag search, result/date/ECO/control/rating/analysis/favorite filters; sort by import/date/event/player/rating/opening. Select games for export, deletion, collection membership (including removal) or tags.
- **Chessboard:** tap or drag legal moves; promotion chooser; castling/en passant; move navigation; branching variations with promotion/deletion; annotations/NAGs; persistent arrows/marks; board flip; check and terminal-state notices; a persistent notation window showing mainline and full nested sidelines; a vertical edge evaluation bar; a visible Save game action; three board themes; animated moves; optional sound. Metadata preserves every original PGN tag, including unrecognized/custom tags. Missing tags are not invented.
- **Analysis:** the same interactive board with live Stockfish MultiPV, SAN continuations, White-normalized vertical evaluation bar, mate scores, depth, nodes, nps, hash usage, search time and best-move arrow. Save a position analysis or run Analyze Complete Game. Change positions while analysis is enabled to replace the search, including after a finite search has completed.
- **Game Review:** choose a saved analysis session; interactive evaluation graph, per-move classifications, alternatives, errors and an explicitly app-defined accuracy estimate. Sessions at different settings are preserved separately. Interrupted full-game analysis can resume without repeating stored positions.
- **Import / Export:** native Android document picker, multi-game PGN, progress and cancellation, exact duplicate skipping or keeping, local versioned backup/restore. Files shared as PGN or text can be received from other Android apps. FEN and annotated PGN can also be copied from the board.
- **Collections:** create, browse, export and delete collections. Deleting a collection retains its games.
- **Settings:** threads, hash, MultiPV, depth and/or time limit, unlimited live search, battery/performance mode, board theme, dark appearance, sounds, classification thresholds and notices.

The document picker can also expose cloud providers installed on a device, but this application needs only local documents. Select on-device storage when testing offline.

## Architecture

`ui/AppModel.kt` is an Android ViewModel with StateFlow; manual constructor injection keeps the dependency graph explicit. Compose screens collect state through lifecycle-aware collectors. Room queries, import/export, backups and analysis run on IO coroutines. The ViewModel survives configuration changes. The selected game/node and preferences are persisted for later launches; the game tree and completed analysis are always stored in SQLite.

`core/Chess.kt` validates standard-chess FEN and wraps the vendored, tested JVM Chesslib library. The Java chess rules execute natively on Android's runtime; there is no browser or scripting engine. Both start-position and Kiwipete perft tests cover move generation. `core/Pgn.kt` is a streaming lexer and variation-tree parser/serializer; each game is validated before insertion. Comments preserve standard embedded clock/eval/arrow/square directives as PGN text. Comments and NAGs remain editable independently of engine analysis.

`data/Database.kt` defines games, moves, collections, memberships, sessions and analysis records with indexes and cascading foreign keys. Move roots have a null parent; all other moves have a parent, ordinal, SAN, UCI, ply and resulting FEN. Analysis belongs to a separate session and node. All original metadata lives in a JSON tag map; frequently searched fields also have dedicated columns. Import transactions are per game, so cancellation or a malformed later game cannot discard earlier imports. Duplicate fingerprints include canonical metadata and the annotated variation tree; identical move sequences from distinct tournament records remain distinct.

`app/schemas` contains Room versions 1 and 2, with a tested 1→2 migration that adds preferred-session selection. There is no destructive migration fallback. Schema 1 is the development baseline, not a previously published app release. Library queries page in 40 games, and all-game PGN export reads game IDs in bounded pages. Each open game loads its own tree, rather than the whole library. Single extremely large games are bounded by parser safety limits.

### Stockfish hosting

`vendor/stockfish` is the complete official `sf_19` source at the commit in `VENDOR_LOCK.txt`, without search/evaluation changes. `native/CMakeLists.txt` compiles the upstream standalone program for Android API 28 with baseline ARM64 NEON (mandatory for arm64-v8a), **without optional dot-product/SVE extensions**. The full search, NNUE and UCI implementation are retained. It uses the static C++ runtime and 16 KB-compatible linker page alignment.

The resulting executable is named `libstockfish.so` solely so Android's package installer extracts it into the **read-only native library directory**. It is an ELF executable, not a JNI library. `ProcessBuilder` launches that installed file and exchanges UCI on pipes from dedicated IO coroutines. This deliberately chooses process isolation over adapting Stockfish's global iostreams into JNI: a native engine failure can be detected and restarted without taking down the UI, and the upstream UCI loop remains intact. No file in writable app data is executed.

The packaged NNUE is copied as data into private files on first engine startup and SHA-256 checked before use. The app sets EvalFile to its absolute private path. No connection is needed. Stockfish 19 uses a single full network; its release retired the secondary network used in earlier versions.

`engine/Engine.kt` owns one serialized process/search. It waits for `uciok` and `readyok`, sends UCI settings and positions, parses streamed info and drains `bestmove` on cancellation before allowing another search. ViewModel generation checks also suppress output from a superseded selection. The engine is shut down in the background; interrupted full-game sessions retain completed rows and can resume. Low-memory and severe thermal states stop or reject work; hash and CPU allocations are capped conservatively. Defaults are one thread, 32 MB hash, three PVs, depth 18 with 1500 ms/position. Full-game analysis always uses a finite limit.

### Analysis semantics

Stored centipawn and mate scores retain Stockfish's **side-to-move** perspective for that FEN; display/graph convert to White's perspective. Classifications compare the engine's best value before a move against the position after the played move, with sign inversion. The chance proxy is `100 / (1 + exp(-cp / 300))`; mate maps to a forced win/loss. Default chance-loss thresholds are 5 / 10 / 20 percentage points for Inaccuracy / Mistake / Blunder. A single legal move is Forced; matching the preferred move is Best; other moves within one percentage point are Excellent, then Good. Losing an engine-reported forced mate receives special handling. Thresholds are configurable.

Accuracy is `mean(100 * exp(-loss / 20))`. These are application heuristics, **not Stockfish outputs, a FIDE rating, or an external service's accuracy metric**. They depend on search depth and time. No opening book is bundled, so Book move is not claimed. Imported opening tags are shown without guessing missing metadata.

### Backup format

Chess Dude Backup v1 is UTF-8 JSON lines. Its `offline-chess-backup` format identifier is unchanged so existing backups remain compatible. The header identifies format/version. Typed records contain preferences, collections, games, moves, sessions, analysis and memberships, followed by a mandatory completion record with a record count. A missing footer or count mismatch is rejected. Foreign keys, standard FEN validation and parent/child move replay validate restoration inside a single transaction; failure rolls back the old library. Preferences are applied after a successful database commit. Restore **replaces** the library and requires a clear confirmation. Export a backup first to keep the old library. Files are not encrypted.

## Tests

```sh
./gradlew testDebugUnitTest
# Start an ARM64 emulator, then put it in airplane mode:
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell svc data disable
./gradlew connectedDebugAndroidTest
```

Tests cover chess perft and special moves, draw rules, FEN, nested PGN round trips, malformed game isolation, UCI info parsing, move classifications, Room persistence/cascade/queries/duplicates/migrations, variation editing, 2,000-game import/pagination, backup restoration and rollback, real packaged-engine initialization/MultiPV/mate/cancellation/restart, and an offline import→database→reload→real engine→saved-analysis→reload flow. Compose instrumentation covers navigation, full-game analysis, recreation, real board taps after a completed finite search, analysis following sidelines, evaluation-bar orientation, branch-choice cancellation/selection, inline comment editing, and the Save game dialog with persisted Elo, metadata and both branches.

## Scope and known limits

This is a functioning build, not a claim of production certification. See `VALIDATION.md` for executed evidence. Physical-device compatibility, long-running thermal behavior, arbitrary OEM document providers, very large real-world PGN corpora and all tablet/landscape combinations require broader QA. Standard chess only; Chess960 and other variants are explicitly rejected. There is no bundled opening book, Syzygy tablebase, or ECO inference database. Fonts use Android's local system typography; piece artwork is packaged locally. Fifty-move/threefold claims and automatic 75-move/fivefold outcomes are distinguished in the board status. Engine FEN analysis does not include earlier-game repetition history, so its evaluation may differ in repetition-sensitive positions.

This application stops analysis on backgrounding instead of running a foreground notification service. Backup restoration has transactional database integrity, but filesystem failures while replacing preferences after commit cannot be made atomic with SQLite. PGN round trips preserve supported semantic data; whitespace, tag order, comment brace formatting and original move-number formatting are not byte-for-byte preserved. Resource limits are used for pathological inputs.

## Licensing and source distribution

**Source code:** https://github.com/abhinavkaiser/chessdude — the complete corresponding source of every released version (tags `v*`), including build scripts, vendored Stockfish and the NNUE network. The Google Play version is the same code; the Play price pays for the convenience of installing and updating it, not for the code, which anyone may build, modify and share under GPL-3.0-or-later.

The application is distributed under **GPL-3.0-or-later**. The APK and complete corresponding source must travel together when redistributed; preserve Stockfish copyright notices, AUTHORS, COPYING and bundled network identity. Both static and dynamic integration can create GPL obligations; this project deliberately uses a GPL-compatible application distribution model rather than relying on the process boundary to avoid them. No proprietary distribution exception is claimed.

See `LICENSE`, `app/src/main/assets/licenses`, `vendor/stockfish/Copying.txt`, `vendor/chesslib/LICENSE`, `vendor/pieces/NOTICE.txt` and `VENDOR_LOCK.txt`. Cburnett's GPL-2.0-or-later artwork is distributed here under GPLv3. Apache-licensed components retain notices and license texts.

Primary references: [Stockfish 19 release](https://github.com/official-stockfish/Stockfish/releases/tag/sf_19), [Stockfish source and license](https://github.com/official-stockfish/Stockfish), [Lichess artwork license attribution](https://github.com/lichess-org/lila/blob/master/COPYING.md), [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html).
