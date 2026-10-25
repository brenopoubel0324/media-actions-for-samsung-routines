# Version 0.3 experimental downloads

- [Installable debug APK](media-actions-0.3-experimental-debug.apk)
- [Complete Android Studio source project](media-actions-0.3-source.zip)
- [Native Android widget preview](widget-preview.png)
- [SHA-256 checksums](SHA256SUMS.txt)

Install the APK, open Media Actions, grant Notification access, and choose **Widget appearance → Apply to widgets**. The certificate matches the previously supplied 0.1 and 0.2 APKs.

The source ZIP is the buildable project snapshot from commit b9e6fdbc798533c8e8524f299f42f8d2c8a0ad17, before download binaries were added to the repository. Its application code matches this release. The ZIP excludes signing keys, SDKs and build caches.

79 host tests and Android lint passed. Device-level Samsung/LockStar behavior remains unverified; see the root TEST_REPORT.md. The user-reported foreground-app behavior from a Samsung Routine remains unresolved.
