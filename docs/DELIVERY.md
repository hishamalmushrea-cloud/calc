# تقرير التسليم الحالي

## الحالة
- تطبيق Android أصلي باسم **دفتري** مبني بـ Kotlin وCompose وRoom.
- الإصدار `1.3.0` (`versionCode=4`) و`targetSdk=35`، مع JDK 17.
- قاعدة البيانات بالإصدار 9 وبمسار ترحيل كامل دون destructive fallback.
- الميزات المنفذة موثقة في `FEATURES.md`، والاختبارات في `TESTING.md`.

## فحص CI
GitHub Actions (`.github/workflows/android.yml`) يعمل على الدفع إلى `main` وعلى طلبات السحب:
1. ترجمة كود Debug وتشغيل اختبارات JVM عبر `:app:testDebugUnitTest`.
2. فحص Android Lint عبر `:app:lintDebug`.
3. تجميع `:app:assembleDebug` ورفع ملف APK كـ artifact.
4. وظيفة مستقلة تشغّل اختبارات الجهاز `connectedDebugAndroidTest` على محاكي.

إصدار Release (APK/Bundle موقّع) ما زال متروكًا للمطور داخل Android Studio.

## متطلبات Android Studio
- JDK 17.
- Android SDK Platform 35.
- استخدام Gradle Wrapper الموجود في المستودع.
- لإصدار النشر يلزم إعداد signing config خاص بالمطور خارج المستودع.

## خارج النطاق
مخزون كامل، فواتير بنود وضريبة، ومزامنة متعددة المستخدمين لحظية.
