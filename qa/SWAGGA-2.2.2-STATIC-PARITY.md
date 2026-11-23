# SWAGGA LAB PRO 2.2.2 — Verified code parity (23 Nov 2026)

QA status: **static bytecode parity passed; release readiness NOT approved**.

## Sources compared
- Recovered working Nothing Phone APK: `AlphaBeat DAW SWAGGA LAB-2.2.2.apk`.
- Original device APK SHA-256: `743bbe66314c224cf9644d665ffda14394b20a27bd8e7d90bf9bc2931398c684`.
- Reconstructed Android source: QA branch `qa/swagga-2.2.2-play-20261123`, commit `c6a7fffa10ace2f48255591605edaff85b6ea52d`.
- Compilation: [GitHub Actions run 45193363046](https://github.com/digitalfitnessmp3/AlphaBeat-SWAGGA-LAB-PRO/actions/runs/45193363046).

## Result
- Each APK includes seven DEX files and 24 ZIP entries.
- Comparing class and method signatures and every compiled method instruction sequence across all seven DEX files: **zero changed, added or removed methods**, with matching readable DEX strings.
- Five DEX entries are byte-identical. The other two (`classes2.dex` and `classes6.dex`) still differ at the binary container level, but not at the decoded method instruction level.
- Exactly seven APK entries differ in contents: the two DEX containers above and five `res/mipmap-*-v4/ic_launcher.png` icons.
- Original icon assets have been extracted from the user-supplied device APK in a separate local ZIP archive, pending repository integration.

## Hard release blockers
1. Restore the five original device launcher icon assets in QA source; do not silently substitute the prior 2.2.1 icon.
2. Run device functional tests of playback, 4-channel mixing, BPM, Fitness 32, export, and API responses; static parity alone is not a runtime guarantee.
3. The current `FitnessRemixClient` allows remix target BPMs only 128/140/150/160/180, although the 2.2.2 UI offers 200/220/240/260. Higher targets are not verified and may be rejected by the client/server.
4. Build a non-debuggable **release AAB** and confirm correct versionCode 12, versionName 2.2.2, package `com.alphabeat.daw.swaggalab`.
5. Verify upload-signing key against Play Console before any upload to internal testing. No signing private keys should be committed or pasted to chat.
6. **Do not merge to main, publish to production, or replace the device's working APK** before all gates pass.
