# 🌟 calc

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white) ![Repo Size](https://img.shields.io/github/repo-size/hishamalmushrea-cloud/calc?style=for-the-badge) ![Issues](https://img.shields.io/github/issues/hishamalmushrea-cloud/calc?style=for-the-badge) ![Last Commit](https://img.shields.io/github/last-commit/hishamalmushrea-cloud/calc?style=for-the-badge) [![License](https://img.shields.io/github/license/hishamalmushrea-cloud/calc?style=for-the-badge)](https://github.com/hishamalmushrea-cloud/calc/blob/main/LICENSE)

## 📖 About this Project
Welcome to the calc repository!

## 🚀 Tech Stack
- **Primary Language:** Kotlin

## 🔗 Connect & Support
[![Trendshift](https://trendshift.io/api/badge/repositories/4119)](https://trendshift.io/)
[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA?style=for-the-badge&logo=discord&logoColor=white)](https://discord.com/)
[![X (formerly Twitter) Follow](https://img.shields.io/twitter/follow/hishamalmushrea-cloud?style=social)](https://x.com/hishamalmushrea-cloud)

---

# دفتري — دفتر حسابات للمحل والتاجر

تطبيق Android أصلي (Kotlin + Jetpack Compose + Room) يعمل **دون إنترنت**.

## التشغيل
يتطلب JDK 17 و Android SDK. من جذر المشروع:

```
./gradlew :app:assembleDebug
```

في هذه البيئة السحابية قد لا يتوفر Android SDK؛ المصدر كامل تحت `app/`.

## التوثيق
انظر مجلد `docs/`.

## الإصدار 1
محلات، عملاء، موردون، بيع/شراء/مصروف/إيراد/تحصيل/سداد/تحويل، لوحة، تقارير، أرشفة ناعمة، قيد مزدوج داخلي، مبالغ `Long`.

## CI
يوجد مسار GitHub Actions (`.github/workflows/android.yml`) يعمل عند الدفع إلى `main` وطلبات الدمج أو يدويًا. وظيفته **الفحص فقط دون إنشاء APK**: يجهّز JDK 17 وAndroid SDK، ثم يترجم كود التطبيق واختبارات JVM عبر `testDebugUnitTest` ويشغّل `lintDebug`. لا يستدعي `assemble` أو `package` ولا يرفع APK كـ artifact.