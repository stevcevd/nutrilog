# NutriLog

Paste lines like:
2026-10-19 08:30 Breakfast (Banica & Yogurt): 1446 kcal | P 46.4g | C 137.6g | F 78.0g | Fiber 4.2g | Na 900mg

## Tabs
Home, Log, Diary, Stats, Me. Settings, Goals, Reminders, Security and Backups live under Me.

## Updating the app
Every build is signed with the same key (nutrilog.keystore) and has a rising version code, so
installing a new APK over the old one UPDATES it and keeps your data. Android shows
"Do you want to update this existing application?".
First time only: builds made before v4 used a different random key, so uninstall that old
copy once (use Goals > Copy text first). Health Connect data is not affected.

## Build the APK
GitHub: upload the project, open Actions > Build APK > Run. The APK appears under Releases
(direct download link) and as an artifact. Or open in Android Studio: Build > Build APK(s).
