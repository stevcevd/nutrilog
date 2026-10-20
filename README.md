# NutriLog

Paste lines like:
2026-10-19 08:30 Breakfast (Banica & Yogurt): 1446 kcal | P 46.4g | C 137.6g | F 78.0g | Fiber 4.2g | Na 900mg

Labels: P C F SatF MonoF PolyF TransF Fiber Sugar Chol Na K Ca Fe Mg P_ Zn Cu Mn Se Cr Mo I Cl Caff
VitA VitB6 VitB12 VitC VitD VitE VitK Biotin Folate FolicAcid Niacin PantAcid Ribo Thiam
Missing nutrients = 0. One entry per line; several lines can be saved at once.

## Get the APK without Android Studio
1. Create a new GitHub repo and upload all these files (keep the .github folder).
2. Open the Actions tab -> "Build APK" -> run it (it also runs on every push).
3. Download the "nutrilog-apk" artifact, unzip, install app-debug.apk on your phone.

## With Android Studio
Open the folder, let it sync, then Build > Build APK(s).

## Tabs
- Log: paste entries (Template button adds a ready line with the current time).
- Diary: browse any day, see progress vs goals, tap an entry for all nutrients, Copy to Log or Delete.
- Goals: edit every nutrient target, copy as text, or import a "Daily Targets" line. Goals are stored on the phone.
