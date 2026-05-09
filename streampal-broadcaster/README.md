# StreamPal Broadcaster

Simple single-camera live streaming app for Android phones and Fire tablets.

**"Tap to go live. Your viewers watch on their phone."**

---

## What it does

- One-button RTMP streaming to your StreamPal server
- Three quality presets: HD 720p, SD 480p, Low 360p
- Camera flip and mute controls
- Live duration timer and bitrate stats
- Stream key saved locally — set once, use forever
- Works on phones, Android tablets, and Fire tablets (Android toolkit required)

---

## Quick Start

1. Install the APK on your device
2. Enter your stream key (provided by StreamPal)
3. Tap the big button to go live
4. Your viewers open their StreamPal viewer link

---

## Stream Settings

| Quality | Resolution | FPS | Bitrate | Best for |
|---------|-----------|-----|---------|----------|
| HD | 720p | 30 | 1.5 Mbps | Good WiFi or 4G |
| SD | 480p | 24 | 800 kbps | Weak 4G or hotspot |
| Low | 360p | 15 | 400 kbps | 3G or marginal signal |

---

## Building

### Debug APK (testing)
Push to `main` branch — GitHub Actions builds automatically.

### Release APK (distribution)
Go to GitHub Actions → Build StreamPal Broadcaster APK → Run workflow → select `release`.

Requires GitHub Secrets: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

### Manual build
```bash
npm install
npx cap sync android
cd android && ./gradlew assembleDebug
```

See `GRADLE_CHANGES.txt` for required manual edits after `cap sync`.

---

## Architecture

```
Web UI (src/index.html)
    ↓ Capacitor bridge
RtmpPlugin.java
    ↓ launches
StreamActivity.java
    ↓ RootEncoder
RTMP push → rtmp://157.245.208.49:1935/live/[stream-key]
    ↓ nginx-rtmp on DigitalOcean
HLS output → StreamPal viewers
```

---

## Based on

- [RootEncoder](https://github.com/pedroSG94/RootEncoder) by Pedro Rodrigues (MIT)
- Capacitor 5 by Ionic
- Forked from BroadcastPal APK project

---

*Part of the Peak Platforms StreamPal product suite.*
