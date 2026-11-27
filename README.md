# STE — Simple Text Editor

Lightweight native Android text and source-code editor.

## Emoji rendering

The editor uses AndroidX Emoji2 with the bundled Noto Color Emoji font and explicitly initializes `EmojiCompat` before creating the editable field. This avoids depending on a device-installed downloadable emoji font provider. The bundled font increases APK size (the upstream font is over 10 MB). Actual display should still be verified on the target device, especially for newest emoji sequences.

## Editor appearance

- Horizontal editor-background color palette.
- Text size setting from 10 sp to 36 sp, saved between launches.
- Background color and text contrast are saved between launches.

## Build

Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, Java 17, compileSdk 35, minSdk 23.
