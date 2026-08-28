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

## الإصدار 1.3
محلات، عملاء، موردون، بيع/شراء/مصروف/إيراد/تحصيل/سداد/تحويل، دفتر بيع يومي، موظفون وصلاحيات، أصناف وفواتير (شريحة أولى)، لوحة، تقارير، أرشفة ناعمة، قيد مزدوج داخلي، مبالغ `Long`.

## CI
يوجد مسار GitHub Actions (`.github/workflows/android.yml`) يعمل عند الدفع إلى `main` وطلبات الدمج أو يدويًا. يتكون من وظيفتين: `verify` (ترجمة + `testDebugUnitTest` + `lintDebug` + `assembleDebug` ورفع APK) و`android-tests` (تشغيل اختبارات الأجهزة `connectedDebugAndroidTest` على محاكي).
