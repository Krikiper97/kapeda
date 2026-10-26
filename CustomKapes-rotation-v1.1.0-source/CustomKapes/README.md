# Custom Kapes (Minecraft 1.21.11 / Fabric)

Client-side custom cape with a Skin Customization toggle and automatic PNG rotation.

## Installing

1. Build with Java 21 and a suitable Gradle 9.x install: `gradle build` (or `./gradlew build` if you have generated a wrapper).
2. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
3. Put `build/libs/custom-kapes-1.1.0.jar` in your instance's `mods` folder. Do not use the `-sources.jar` file.
4. Launch the game; `KapeLoader` will be created in your active instance folder.
5. Put any number of `.png` files in `KapeLoader`.

## Features

* A toggle in Options > Skin Customization labeled **Kape Toggle: ON/OFF**.
* Alphabetically cycles PNGs every **25 minutes of real elapsed time while the game is running**.
* Scans for added/removed/updated images approximately every 2 seconds.
* Skips unreadable PNG files.
* Loads only for the local player; the server and other players do not see it.
* Saves toggle state to `config/custom_kapes.json`.

## Important: image formats

**Default `image_mode` is `artwork`.** Every PNG, including 64×32 and 128×64 PNGs, is treated as ordinary artwork, center-cropped to the 10×16 visible cape panel, mirrored onto its lining and surrounded by filled edge strips. This prevents a 2:1 ordinary picture from being incorrectly interpreted as a Minecraft UV atlas.

If your PNG files are **already genuine Minecraft cape UV atlases**, edit `config/custom_kapes.json` and change `"image_mode": "artwork"` to `"image_mode": "atlas"`, then restart Minecraft. Atlas mode needs a 2:1 image and keeps the normal Minecraft cape UV layout.

The *converted* 64×32 image is saved at `config/custom_kapes_preview.png`. Open it and check the visible cape exterior at pixels x=1..10 and y=1..16. If that region is black, the input PNG (or chosen mode) is the problem. If that region is correct yet the in-game cape is black, share that preview and the `latest.log` entries containing `Custom Kapes`.

Note: Wide artworks get **center-cropped** to fit a tall cape; some content at the sides will be intentionally removed.

## Build verification

A Gradle build was attempted in the assistant environment, but it does not have Gradle installed and cannot resolve the external Gradle download server. The source is not compile-verified here.
