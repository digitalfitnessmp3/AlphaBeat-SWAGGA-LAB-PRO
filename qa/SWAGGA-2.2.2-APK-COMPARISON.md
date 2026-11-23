# SWAGGA LAB PRO 2.2.2 — APK / GitHub source comparison (23 Nov 2026)

This is **QA evidence only**. It does **not** authorize publishing an AAB or merging to `main`.

## Device APK verified

- File: `AlphaBeat DAW SWAGGA LAB-2.2.2.apk` (user-recovered from Nothing Phone)
- SHA-256: `743bbe66314c224cf9644d665ffda14394b20a27bd8e7d90bf9bc2931398c684`
- Package: `com.alphabeat.daw.swaggalab`; versionName `2.2.2`; versionCode `12`
- minSdk `26`; targetSdk `36`
- APK is **debuggable**, signed with **Android Debug** certificate; **not uploadable to Play as the release AAB**

## Evidence from APK

- Seven `classes*.dex` files; 65 compiled classes (includes generated and synthetic classes).
- Source-file references in DEX identify 17 Java modules, also named in the current repository: MainActivity, SwaggaConsoleView, SwaggaFaderOverlay, SwaggaBpmControl, SwaggaBpmDisplay, SwaggaEcho, WaveformAnalyzer, AudioDecoder, BeatDetector, BeatEnergyAnalyzer, BpmEngine, NativeAudioDecoder, PcmAudio, AudioClip, DawTimeline, WaveformView, FitnessRemixClient.
- Key expected method names exist in DEX (e.g. `createFitnessRemix`, `setSwaggaPlaybackBpm`, `muteChannel`, `soloChannel`, `setDetectedBpm`).
- **This does not establish functional source equivalence**: method bodies and numerical logic may differ.

## Exact graphical-resource comparison

Compared extracted APK PNG bytes to Git blob SHA-1 of corresponding files on `main`.

**Byte-identical, 9/9 mixer and UI assets:**
`alphabeat_daw_reference.png`, `swagga_fader_background.png`,
`swagga_fader_green_qa.png`, `swagga_reference.png`,
`swagga_knob_ch1.png`, `swagga_knob_ch2.png`, `swagga_knob_ch3.png`,
`swagga_knob_ch4.png`, `swagga_knob_master.png`.

**Different:** all five density variants of Android `ic_launcher.png`.

## Repository status and release gate

- `main` and this QA branch still contain `versionName=2.2.1`, `versionCode=11`.
- No source code verified to produce the device's `2.2.2` APK is presently checked in.
- **Do not relabel 2.2.1 as 2.2.2 and pass it off as the user's working release.**
- Next: recover original 2.2.2 source/backup or perform controlled DEX-to-source method comparison and patch missing behavior; rebuild; check non-debuggable status, signing/upload certificate, package; test internally before any publication.
- Preserve installed 2.2.2 and existing app packages. Do not put signing keys/passwords in this repository.
