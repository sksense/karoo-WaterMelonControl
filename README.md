# karoo-WaterMelonControl

[![downloads](https://img.shields.io/github/downloads/sksense/karoo-WaterMelonControl/total?label=downloads&style=flat-square)](https://github.com/sksense/karoo-WaterMelonControl/releases/latest)
[![license](https://img.shields.io/github/license/sksense/karoo-WaterMelonControl?label=license&style=flat-square)](LICENSE)

A Hammerhead Karoo extension providing optimized media and volume controls, styled to match the native KarooOS interface.

This extension allows the user to add widgets to control the Karoo's device's media control so that you can control the sideloaded music app.

The primary goal here is to allow the user to listen in beeper and music at the same time during riding without disturbing people around the user.

## Features

- **PLAYING Widget:** Displays current track and artist name. Tap the widget to jump directly into the active media app.
- **VOLUME Widget:** Precision volume control with native-styled, high-visibility icons.
- **MEDIA Widget:** Fast access to Play/Pause, Next, and Previous track controls.
- **Native Look & Feel:** Optimized for the Karoo display with rounded grey buttons and high-contrast vector icons.
- **Push to Relocate:** All widgets include headers allowing them to be moved and resized on any data page.

## Installation

1. Download the **[Latest WaterMelonControl APK (v1.3.4)](https://github.com/sksense/karoo-WaterMelonControl/releases/latest/download/WaterMelonControl.apk)**.
2. Install the APK onto your Karoo device:
   - **Karoo 3:** Share the APK file to your Karoo device through the **Karoo Companion App**.
   - **Sideloading:** Alternatively, use `adb install <filename>.apk`.
3. Open the **WaterMelonControl** app from the Karoo **Extension menu**.
4. Click **"Open Notification Settings"** and enable access for WaterMelonControl.
5. Add the widgets to your preferred data page in the Karoo settings.

## Compatibility

- **The New Karoo (Karoo 3):** Fully supported and tested.
- **Karoo 2:** Currently **untested**. Compatibility is not guaranteed.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Credits

- **[karoo-ext](https://github.com/hammerheadnav/karoo-ext):** Uses the official Hammerhead Extension SDK (Apache 2.0).
- **[karoo-spintunes](https://github.com/timklge/karoo-spintunes):** Architecture and UI inspired by the Spintunes project.
- **Android Media APIs:** Leverages standard Android Notification and MediaSession protocols for universal app support.
