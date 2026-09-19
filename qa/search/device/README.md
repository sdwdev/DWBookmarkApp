# Galaxy S25 Ultra search verification — 2026-09-14

- Device: SM-S938N / R3CY20914DB.
- Existing Play package retained: com.gameitstudio.dwbookmarkapp, versionCode 8, versionName 1.0.6. Last update still 2026-09-12 14:55:25.
- Separate test app: com.gameitstudio.dwbookmarkapp.searchtest, label DWBookMark 검색 테스트. Uses Google test banner ID.
- Test build generated via isolated-test.init.gradle; production application ID and release configuration unchanged.
- Instrumentation: OK (2 tests).
- Verified Korean title matching; URL matching ignoring case; intersecting folder/query; switching back to all folders; no-results message; clearing query; activity recreation preserves query; collapsing SearchView.
- Test fixtures (two bookmarks and one folder) removed by test finally block. No deletion, reinstall, or data access on the Play package.
- search-match.png visually inspected: search field, folder chips, matching card, and FAB visible above navigation bar.
- search-empty.png is an immediate recreation capture and may include an in-progress layout; retained as raw diagnostic evidence.
- Animation and screen-on settings restored to saved values.
- Standard assembleDebug rerun after isolated build so normal APK output again uses original application ID. Isolated APK retained as dwbookmark-search-test.apk.
- This validates the search feature on a separate install; it does not validate an update of the Play-signed package.
