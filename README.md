# STE — Simple Text Editor

Lightweight native Android text and source-code editor.

## Emoji support
Uses AndroidX Emoji2 with the bundled emoji font and replace-all processing. Rendering of combined emoji sequences must be verified on the target Android device; text is saved as its original UTF-8 characters and syntax highlighting adds visual spans only.

## Editor appearance
- Continuous full-spectrum HSV color strip; tap or drag to select a background color.
- Compact single-row editor toolbar.
- Text size setting from 10 sp to 36 sp, saved between launches.
- Basic syntax coloring for common source-code tokens.

## Build
Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, Java 17, compileSdk 35, minSdk 23.
