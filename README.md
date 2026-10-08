# DocuMate (Android, Kotlin + Jetpack Compose)

Hujjat o'quvchi/muharriri. Offline, INTERNET ruxsatisiz.

| Funksiya | Holati (0.2.0) |
|---|---|
| Fayllar ro'yxati — SAF papka, qidiruv, filtr, saralash, sevimlilar | ✅ |
| PDF o'quvchi — zoom, tun rejimi, sahifa ko'rsatkichi | ✅ |
| PDF parol bilan ochish (offline PdfBox decrypt) | ✅ |
| PDF ichidan matn qidirish (sahifalar bo'yicha sakrash) | ✅ |
| Oxirgi sahifani eslab qolish + «davom etish» | ✅ |
| Word/Excel/PPT ichki ko'ruvchi (docx/xlsx/pptx, POI-siz yengil parser) | ✅ |
| Eski .doc/.xls/.ppt | 🔶 tashqi ilovada ochiladi |
| TXT ichki + Matn → PDF | ✅ |
| Rasm → PDF (galereya) + Kamera skaneri → PDF | ✅ |
| PDF birlashtirish (2+ fayl) | ✅ |
| Shablonlar (Ariza, Rezyume → PDF) | ✅ |
| Til: O'zbek / Русский / English | ✅ |
| Qulf: 4 xonali PIN (SHA-256, qurilmada) | ✅ |
| PDF muharrir to'liq (highlight/imzo/sahifa o'chirish) | ⏳ keyingi bosqich |
| OCR matn ajratish | ⏳ «Tez orada» (offline model bilan keladi) |
| Word/PPT/Excel → PDF konvertatsiya | 🟡 matn ko'rinishi orqali (to'liq format keyingi bosqich) |

Nega Apache POI emas? POI ~15 MB va CI/build'ni og'irlashtiradi. Hozirgi
`OfficeXml.kt` docx/xlsx/pptx matnini ZIP+XML orqali o'qiydi — tez, offline,
APK'ni kattalashtirmaydi. Murakkab formatlash kerak bo'lsa POI keyin qo'shiladi.

Ruxsatlar: **INTERNET yo'q**. Faqat SAF papka + Kamera (skaner uchun).

## GitHub Actions orqali APK olish (dmut)

Repo bo'sh holatda tayyor: https://github.com/Xusha-gis/dmut

```bash
cd "documate-android (1)"
git init -b main
git add .
git commit -m "DocuMate 0.2.0: ichki office, parolli PDF, qidiruv, til, PIN"
git remote add origin https://github.com/Xusha-gis/dmut.git
git push -u origin main
```

Keyin GitHub'da: **Actions → Build APK → Artifacts → documate-debug-apk**
ichidagi `app-debug.apk` ni telefonga o'rnating.

### Imzolangan release APK (ixtiyoriy)

```bash
keytool -genkey -v -keystore release.jks -alias documate -keyalg RSA -keysize 2048 -validity 10000
# Windows (PowerShell): [Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Content b64.txt
```

Repo → Settings → Secrets and variables → Actions:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Secretlar bo'lsa workflow `documate-release-apk` ni ham yaratadi.

## Mahalliy yig'ish

Android Studio'da papkani oching yoki: `gradle wrapper --gradle-version 8.10.2`
so'ng `./gradlew assembleDebug`.
