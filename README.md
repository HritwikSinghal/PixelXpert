### For Pixel Stock Android 16 QPR1 and newer:  
[![Download Latest Build](https://img.shields.io/badge/Download-Latest%20Build-blue)](https://github.com/HritwikSinghal/PixelXpert/actions/workflows/forkBuild.yml)

Builds are downloadable as GitHub Actions artifacts named `PixelXpert-<branch>-<short7hash>.{zip,apk}`.



[![Telegram URL](https://img.shields.io/badge/Telegram-Join-2CA5E?style=social&logo=telegram)](https://t.me/PixelXpert_Github)

![Header Image](https://github.com/HritwikSinghal/PixelXpert/blob/patch/.github/PixelXpert_Banner_1280.jpg?raw=true)

This is a mixed Xposed+Magisk module, which is made to allow customizations that are not originally designed in AOSP (Android Open Source Project). Please read thorough below before reaching to download links
<hr>

> **Note (Fork):** This is a maintained fork of [siavash79/PixelXpert](https://github.com/siavash79/PixelXpert)
> that carries a small set of custom features on top of upstream. The `canary` branch mirrors upstream;
> custom work lives on the `patch` branch (the default branch here). See **Fork Features** below.
<hr>

### **Features:**
Currently, PixelXpert offers customizations on different aspects of system framework and SystemUI, including:
- Status bar
- Quick Settings panel
- Lock screen
- Notifications
- Gesture Navigations
- Phone & Dialer
- Hotspot
- Package Manager
- Screen properties
<hr>

### **Fork Features:**
Custom features this fork adds on top of upstream (each opt-in, default-off):
- **Force close in Recents menu** -- adds a "Force close" entry to the app menu that appears when tapping
  an app's title in the Recents/Overview screen (alongside App info, Split screen, Pin, Pause app, etc.),
  so a running app can be force-stopped directly from Recents without going through App info > Force stop.
  Enable it via the launcher settings toggle ("Add a Force close button to the recents task menu").
<hr>

### **Compatibility:**
PixelXpert is ONLY compatible with pixel stock firmware on Google Pixel devices. Any custom ROM (including PE, PE plus, pixel plus ui and etc) or stock ROM outside stock pixel firmware on Google pixel devices (e.g. OneUI on Samsung, MIUI on Xiaomi and etc) is not supported and may not be fully (or even at all) compatible.

Supported versions:

- Android 16 QPR1 and newer
<hr>

### **Prerequisites:**
- Compatible ROM (see Compatibility text above)
- Device Rooted with Magisk 24.2+ or KSU
- LSPosed (Zygisk Version preferred); on Android 16+ use the [LSPosed fork by JingMatrix](https://github.com/JingMatrix/LSPosed/releases)
<hr>

### **How to install:**
- Download the stable magisk module according to your firmware as mentioned above 
- Install in magisk/KSU
- Reboot (no bootloops are expected)
- Open PixelXpert app and apply changes

P.S. For KSU, there is an extra step of granting root access to PixelXpert as it doesn't request automatically as in Magisk
<hr>

### **Release Variants:**  
The module is also released in 2 flavors with different manual download and update procedures. But both can utilize automated updates through magisk manager, or through in-app updater (for canary, updates will not count against the module's download count).

<ins>Stable release:</ins> 
- Manual Install/Update: through the in-app updater, plus GitHub Actions artifacts from the fork's [Actions page](https://github.com/HritwikSinghal/PixelXpert/actions/workflows/forkBuild.yml)

<ins>Canary release:</ins>
- Manual Install/Update: through the in-app updater and the fork's [Actions page](https://github.com/HritwikSinghal/PixelXpert/actions/workflows/forkBuild.yml)

*No matter which flavor you're on, you can always switch to the other one with in-app updater
<hr>

### **Translations:**  
[![Crowdin](https://badges.crowdin.net/aospmods/localized.svg)](https://crowdin.com/project/aospmods)  
Want to help translate PixelXpert to your language? Visit [Crowdin](https://crowdin.com/project/aospmods)
<hr>

### **Donations:**
This project is open source and free for usage, build or copy. However, if you really feel like it, you can donate to your favorite charity on our behalf, or help funding education for children in need, at [Child Foundation](https://mycf.childfoundation.org/s/donate)
<hr>

### **Credits / Thanks:**
- Android Team
- @topjohnwu for Magisk
- @rovo89 for Xposed
- Team LSPosed
- apsun@github for remote-preferences
- @nijel8 for double-tap to wake


**UI design:**  
- @Mahmud0808  

**Graphic design:**  
- JstormZx@Telegram (Icon and Banner) 
- RKBDI@Telegram  (Icon)

**Brought to you by:**
@siavash79 & @ElTifo
<hr>
