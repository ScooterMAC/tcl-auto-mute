# AdMuter V3 (AutoMute)
Android app that listens via microphone and mutes Roku TVs during commercials.

## How to build via GitHub Actions
1. Create a GitHub repo and push this project to it (all files in this folder).
2. Actions workflow is at .github/workflows/android-build.yml and will build a debug APK.
3. After workflow finishes, download the APK artifact and sideload on your device.

## Notes
- Ensure your phone and Roku are on the same Wi-Fi network.
- Roku ECP (port 8060) must be reachable.
