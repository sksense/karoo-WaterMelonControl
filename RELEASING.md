# Releasing WaterMelonControl

Releases are built, signed, verified, and uploaded manually. **Do not use GitHub Actions.**

## 1. Prepare version metadata

1. Increment `versionCode` in `app/build.gradle.kts`.
2. Update `versionName` in `app/build.gradle.kts`.
3. Update README installation version.
4. Update local `DEVELOPMENT_LOG.md`.

`WaterMelonControlService` reads `BuildConfig.VERSION_NAME`; no separate service version edit is needed.

## 2. Build and validate

```bash
export JAVA_HOME=/home/skpc/android-env/jdk-17.0.11+9
export ANDROID_HOME=/home/skpc/Android/Sdk
export ANDROID_SDK_ROOT=/home/skpc/Android/Sdk
export BASE_URL="https://github.com/sksense/karoo-WaterMelonControl/releases/download/vX.Y.Z"
export RELEASE_NOTES='Release notes here'

./gradlew clean \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:lintDebug \
  :app:generateManifest \
  :app:verifyProductionRelease \
  --no-daemon
```

Verify:

- package: `com.watermeloncontrol.widget`
- versionName/versionCode
- production certificate SHA-256:
  `66fab07e67d1965f5131469715c4f1b067fc3ee281197ed0e0ac170d68536c30`
- `app/manifest.json` uses tag-specific APK/icon URLs

## 3. Prepare fixed-name assets

```bash
cp app/build/outputs/apk/release/app-release.apk build_apks/WaterMelonControl.apk
cp app/manifest.json build_apks/manifest.json
```

Release assets:

- `WaterMelonControl.apk`
- `manifest.json`
- `WaterMelonControl-icon.webp`

Do not upload duplicate versioned APK assets.

## 4. Commit, tag, and push

Release from `main`:

```bash
git tag vX.Y.Z
git push origin main
git push origin vX.Y.Z
```

## 5. Create release manually

```bash
gh release create vX.Y.Z \
  build_apks/WaterMelonControl.apk \
  build_apks/manifest.json \
  build_apks/WaterMelonControl-icon.webp \
  --repo sksense/karoo-WaterMelonControl \
  --title vX.Y.Z \
  --notes 'Release notes' \
  --latest
```

## 6. Verify publication

Check:

- stable release, not draft/prerelease
- `/releases/latest/download/WaterMelonControl.apk`
- `/releases/latest/download/manifest.json`
- latest manifest version/versionCode
- downloaded APK metadata and certificate
- no GitHub Actions run was created
