# Chess Dude 1.2.0 — validation, 1 November 2026

## Changes

- Bold game moves; normal-weight indented variations in brackets; comments flow inline with the moves. Moves are clickable, selected moves are highlighted, and the notation scrolls independently. No Mainline/Sideline labels are displayed.
- Next move opens a compact continuation chooser at every branching position. The actual game continuation is bold; alternatives are regular. Cancel retains the position. A choice is revalidated against the current parent before navigation. A sole continuation advances directly.
- The notation pencil edits comments before/after the selected move and annotation symbols. Existing inline comments open their editor. Both comments persist in Room, backup and PGN. Before comments are exported after the move number to preserve their position through reimport. Variations have fold/unfold controls and notation text size is adjustable. Navigating into a folded variation unfolds its path.
- Save game includes optional White/Black Elo, validated when provided. Original/custom tags and all moves are retained.
- PGN share/export filenames use actual player names, event and date; unnamed boards use ChessDude_Analysis.pgn. Separate cache directories prevent overwriting previously shared files. Native Android sharing still supports PGN, full PGN text, notation, board PNG and FEN.
- Original knight-and-sunglasses icon appears in the adaptive launcher icon, app header and navigation drawer. Source artwork and generation prompt are in artwork/ICON.md.
- Schema 3 adds before-move comments with an additive migration. Both schema 1 → 3 and 2 → 3 are tested. Older backups without this field restore with empty before comments. Existing game metadata, comments and ratings are retained.
- Live Stockfish follow-position behavior, left-edge evaluation bar, automatic move persistence and native share sheet from 1.1 are retained and regression tested.

Design references: [ChessBase notation window](https://help.chessbase.com/cbase/17/eng/notation_window.htm), [text commentary](https://help.chessbase.com/CBase/17/Eng/000131.htm), [fold notation](https://help.chessbase.com/cbase/17/eng/fold_notation.htm), and the user's screenshots. The Android interface adapts these conventions to touch controls.

## Executed clean build

```sh
./gradlew clean testDebugUnitTest assembleDebug connectedDebugAndroidTest
```

**BUILD SUCCESSFUL in 1m 3s. 33 JVM tests and 12 Android tests passed**, with no failures or skips. Environment: macOS ARM64, JDK 17, Gradle 8.11.1, SDK 35, NDK 28.2.13676358, CMake 3.22.1. Device: Android 15 / API 35 ARM64 emulator chessapp_test, in airplane mode with Wi-Fi/mobile data disabled.

## APK verification

- Package org.offlinechess; Chess Dude version 1.2.0 / code 3. Package and debug signing certificate retained for upgrades.
- artifacts/ChessDude-1.2.0-debug-arm64.apk: 162,090,622 bytes (154.6 MiB).
- SHA-256: `482e0963d0c4d74da4edc23c6738a7affe054dd879653c55a83809f5d2eee74b`.
- arm64-v8a; min SDK 28; target SDK 35. Debug signed; APK signature verification passed.
- No INTERNET or broad storage permission. File sharing grants temporary URI read access through a non-exported FileProvider restricted to generated share files.
- Complete official Stockfish 19 and full bundled NNUE retained. NNUE hash verified inside the APK: `1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2`.

## JVM tests

| Suite | Passed | Failed |
|---|---:|---:|
| BoardPresentationTest | 6 | 0 |
| ChessTest | 12 | 0 |
| EngineTest | 4 | 0 |
| ExportNamesTest | 3 | 0 |
| PgnTest | 8 | 0 |

Includes chess rules/perft, FEN, PGN nested variations and before/after comments, exact notation traversal and folding, meaningful safe filenames, optional rating validation, UCI parsing and score perspective.

## Android tests

| Test | Duration | Result |
|---|---:|---|
| backupRestoreDuplicateHandlingAndCascades | 0.343 s | Passed |
| twoThousandGameImportAndPagination | 10.335 s | Passed |
| importPersistReloadAnalyzePersistReloadOffline | 2.360 s | Passed |
| searchCancellationMateAndRestart | 1.138 s | Passed |
| variationPromotionDeletionAndAppend | 0.058 s | Passed |
| migrationOneToThreePreservesRows | 0.092 s | Passed |
| migrationTwoToThreePreservesExistingGamesAndComments | 0.053 s | Passed |
| pgnFileAndReadableGrantIntentPreserveTree | 0.060 s | Passed |
| notationAndFenShareAsText | 0.001 s | Passed |
| positionImageIsActualPngWithFenCaption | 0.148 s | Passed |
| navigationFullGameAnalysisAndRotation | 5.361 s | Passed |
| analysisFollowsBoardMovesAfterTimedSearchAndGameCanBeSaved | 13.586 s | Passed |

The live regression test runs the packaged engine offline, waits for a finite search, taps e2/e4, verifies legal PVs for the new position, creates d4, opens/cancels the branch popup, reopens it and selects d4, verifies live results, edits before/after comments through the UI, flips the evaluation bar, checks sharing, saves optional Elo and metadata, reloads both branches/comments/custom tags and finds the game in My Games. Other tests cover actual PGN display filename/read grants and collision avoidance, real PNG decoding, complete-game analysis/recreation, migration with old game data, legacy backups, transaction rollback, 2,000-game import/pagination, mate/stalemate and cancellation/restart.

## Manual review and evidence

Installed the final APK with adb install -r. Inspected phone portrait and landscape notation, the icon inside the app, bold/normal variation formatting, inline comments, the actual move-choice popup and comment editor. Screenshots are in artifacts/screenshots/v1.2. The native sharing workflow is exercised by the automated payload/intent tests; the actual Android image share sheet was visually verified in 1.1 (artifacts/screenshots/v1.1/android-share-sheet.png). No external message was sent.

Evidence: artifacts/build-validation.log, artifacts/test-results, artifacts/apk-verification.txt, artifacts/apk-permissions.txt and artifacts/SHA256SUMS. Complete corresponding source, NNUE, icon artwork/prompt and schemas are included in artifacts/ChessDude-1.2.0-AndroidStudio-source.zip.

## Limits

This is a tested debug delivery. No physical phone was tested. WhatsApp and Teams were not installed on the emulator, so app-specific delivery is unverified; Android lists compatible installed targets for each format. Android 9, OEM variations, memory/thermal pressure and exhaustive tablet/accessibility coverage remain unverified. Standard chess only; Chess960 and an opening-book database are outside this build. Analysis stops when the app is backgrounded, including sharing to another app; tap Analyze to enable it again on return.
