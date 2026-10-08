# DocuMate (Android, Kotlin + Jetpack Compose)

Hujjat o'quvchi/muharriri. Offline, INTERNET ruxsatisiz.

| Funksiya | Holati (0.3.0) |
|---|---|
| Birinchi ochilishda barcha fayllarga ruxsat so'rash (All-files) | ✅ |
| SAF papka tanlash (zaxira yo'l) | ✅ |
| PDF o'quvchi — zoom, tun rejimi, sahifa ko'rsatkichi | ✅ |
| PDF parol bilan ochish (offline decrypt) | ✅ |
| PDF ichidan matn qidirish + oxirgi sahifani eslash | ✅ |
| Word/Excel/PPT ichki ko'ruvchi (docx/xlsx/pptx) | ✅ |
| Eski .doc/.xls/.ppt | 🔶 tashqi ilovada ochiladi |
| TXT ichki + Matn → PDF | ✅ |
| Rasm → PDF (sahifa rasm o'lchamiga mos) + Kamera skaneri | ✅ |
| PDF birlashtirish (2+ fayl) | ✅ |
| Rezyume shabloni → PDF | ✅ |
| Til: O'zbek / Русский / English | ✅ |
| PDF muharrir to'liq (highlight/imzo) | ⏳ keyingi bosqich |
| OCR matn ajratish | ⏳ «Tez orada» |

Ruxsatlar: **INTERNET yo'q**. Fayllar + Kamera (skaner).

## GitHub Actions orqali APK olish (dmut)

Repo: https://github.com/Xusha-gis/dmut

Push qilingach **Actions → Build APK → Artifacts → documate-debug-apk**
ichidagi `app-debug.apk` ni telefonga o'rnating.

### Imzolangan release APK (ixtiyoriy)

Repo → Settings → Secrets and variables → Actions:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Mahalliy yig'ish

Android Studio'da papkani oching yoki: `gradle wrapper --gradle-version 8.10.2`
so'ng `./gradlew assembleDebug`.
