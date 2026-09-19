# Search verification — 2026-09-14

- Debug APK build: passed.
- BookmarkSearchTest: 4 passed (title/URL, Korean, case, whitespace, literal punctuation, folder intersection, order).
- BookmarkSearchUiTest: passed on Pixel_7_Pro emulator (API 35); input, no-results copy, activity recreation/query preservation, clear and collapse.
- lintVitalRelease: passed. Existing Java 8 compilation deprecation warnings remain.
- Screenshot: search-ui.png, inspected after activity recreation.
- Initial UI run was blocked by emulator System UI ANR. Dismissed emulator dialog.
- Recreation exposed existing oversized status-bar scrim; set initial height to zero and request fresh insets.
- Test animations temporarily disabled and restored afterwards.
- No phone installation or data deletion. No release version/signing changes.
