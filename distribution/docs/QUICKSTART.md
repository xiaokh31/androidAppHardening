# Android App Hardening 0.2.0

This package runs offline and requires a preinstalled Eclipse Temurin `17.0.19+10` runtime. It does not download a JRE and does not sign APKs.

The package produced by V2-M0-02 is a non-release canary. Its SBOM and release-manifest identity fields are schema-valid synthetic placeholders; they are not release or security-review evidence.

The bundled Android apksig `9.3.0` bytecode is modified by the pinned Android Build Tools R8 shrinker: only verification APIs and their required implementation remain in the Host JAR and bootstrap DEX. Signing/private-key APIs are excluded. The Maven acquisition lock identifies the unchanged upstream build input; the component baseline identifies the derived package bytes. See `LICENSE` and `THIRD_PARTY_NOTICES.md` for the retained Apache-2.0 attribution.

Windows:

```text
bin\android-app-hardening.cmd --version
bin\android-app-hardening.cmd protect --input input-signed.apk --output output-unsigned.apk --report report.json
```

Ubuntu:

```text
bin/android-app-hardening --version
bin/android-app-hardening protect --input input-signed.apk --output output-unsigned.apk --report report.json
```

The input must be one signed standalone APK with `minSdk >= 29`. The input is read-only, the output path must be different and initially absent, and the successful output is unsigned. Sign the output only with a separate tool outside this package.

AAB, APKS, split APK, dynamic features, Flutter, Unity, React Native, hotfix, plugin frameworks, and APKs that already contain a protection shell are unsupported.
