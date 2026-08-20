# Building WaterMelonControl

## Requirements

- Android SDK: `/home/skpc/Android/Sdk` in the maintainer environment
- JDK 17: `/home/skpc/android-env/jdk-17.0.11+9` in the maintainer environment
- Production release signing files are optional for debug builds and required for release builds

Do not use JDK 26 with the current Gradle/Kotlin toolchain.

## Debug build and tests

```bash
export JAVA_HOME=/home/skpc/android-env/jdk-17.0.11+9
export ANDROID_HOME=/home/skpc/Android/Sdk
export ANDROID_SDK_ROOT=/home/skpc/Android/Sdk

./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Production release validation

Production signing configuration is local-only and ignored by Git:

```text
keystore/watermeloncontrol-release.properties
keystore/watermeloncontrol-release.p12
```

Run:

```bash
./gradlew :app:generateManifest :app:verifyProductionRelease --no-daemon
```

`verifyProductionRelease` checks that signing configuration exists and that the APK certificate matches the expected production certificate.

Production APK:

```text
app/build/outputs/apk/release/app-release.apk
```

## Notes

- Build outputs should not be treated as source code.
- `DEVELOPMENT_LOG.md` and signing material are intentionally local-only.
- Current releases are built and published manually; GitHub Actions are not used.
