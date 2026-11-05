# Notes for Claude

## Git workflow (always)

1. **Pull and merge before working.** Run `git fetch origin`, then merge
   `origin/main` and, if it has new commits, the remote copy of the working
   branch into the working branch. Merge, don't rebase, and never force-push.
2. **Merge into `main` after working.** When a change is finished and pushed,
   open a pull request into `main` and merge it (merge commit), so `main`
   always has the latest app.
3. If CI fails on the pull request, say so and fix it; don't hide it.

## Project notes

- Modules: `core/` (shared, Java 17, no dependencies), `desktop/` (Java 21,
  Swing + FlatLaf, bundled FFmpeg), `android/` (Media3). See `README.md`.
- Tests: `./gradlew -Pshrink.android=false :core:test :desktop:test`. The
  Android tests run on an emulator in CI.
- Every text the user sees goes into both `messages.properties` (English) and
  `messages_sk.properties` (Slovak) in
  `core/src/main/resources/io/github/m1omg/shrink/i18n/`; the Android strings
  are generated from them. No ASCII apostrophes there (MessageFormat).
- Animations and playback are driven by elapsed time, never by counting
  frames, so they run the same on any display refresh rate.
- Keep the bundled FFmpeg at `7.1-1.5.11` (`gradle/libs.versions.toml`):
  newer bytedeco builds don't run on macOS 12 Monterey, which must stay
  supported, as must Windows 10. CI checks the Mac part with
  `tools/check-macos-version.sh`.
