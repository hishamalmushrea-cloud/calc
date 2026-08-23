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
