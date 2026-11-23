# SWAGGA LAB PRO 2.2.2 — Google Play QA (23 Nov 2026)

This branch is **preparation only** and MUST NOT be merged or deployed without validation.
The production app is `com.alphabeat.daw.swaggalab`.

## Ground truth

- User-recovered device APK: `AlphaBeat DAW SWAGGA LAB-2.2.2.apk`
- APK SHA256: `743bbe66314c224cf9644d665ffda14394b20a27bd8e7d90bf9bc2931398c684`
- APK manifest: versionName `2.2.2`; versionCode `12`; min SDK 26; target SDK 36.
- APK is debuggable and signed with Android Debug certificate; **do not upload**.
- Debug certificate SHA256: `8B:93:77:77:4F:EC:00:A0:79:98:6E:82:57:C4:8A:9D:1F:89:FB:CD:A7:15:81:BD:A4:A7:29:71:F6:B0:96:65`.
- Repository `main` still declares `2.2.1` / versionCode `11`; equivalence with installed 2.2.2 has **not** been established.
- Play Console internal track shows 2.2.1 (11) per user screenshot.

## Non-negotiable release checks

1. Compare the 2.2.2 APK DEX/resources to source/compiled 2.2.1 and identify functional differences.
2. Build an equivalent release using source and reproducible Android Gradle build. Never pass off renamed 2.2.1 as the same 2.2.2.
3. Verify that the release is **not debuggable**, has the correct package, permissions and target SDK 36.
4. Check **Google Play Console -> App integrity -> Upload key certificate**, and match it against the certificate of the build being uploaded.
5. If Codemagic contains an existing Android keystore under Team -> Code signing identities, use it only if its certificate matches the registered upload certificate. Never commit or paste passwords/private keys in GitHub or chat.
6. Sign, test, and only then upload the AAB to **internal testing**, not production. Keep existing 2.2.1 and device 2.2.2 safe until successful QA.

No private keys or signing secrets are included in this branch.
