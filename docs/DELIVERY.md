# تقرير التسليم الحالي

## الحالة
- تطبيق Android أصلي باسم **دفتري** مبني بـ Kotlin وCompose وRoom.
- الإصدار `1.3.0` (`versionCode=4`) و`targetSdk=35`، مع JDK 17.
- قاعدة البيانات بالإصدار 8 وبمسار ترحيل كامل دون destructive fallback.
- الميزات المنفذة موثقة في `FEATURES.md`، والاختبارات في `TESTING.md`.

## فحص CI دون APK
GitHub Actions مخصص للتحقق فقط:
1. ترجمة كود Debug واختبارات JVM وتشغيلها عبر `:app:testDebugUnitTest`.
2. تشغيل فحص Android Lint عبر `:app:lintDebug`.

لا يشغّل CI مهام `assembleDebug` أو `assembleRelease` أو `package`، ولا ينشئ أو
يرفع ملف APK. إنشاء APK/Bundle متروك للمطور داخل Android Studio.

## متطلبات Android Studio
- JDK 17.
- Android SDK Platform 35.
- استخدام Gradle Wrapper الموجود في المستودع.
- لإصدار النشر يلزم إعداد signing config خاص بالمطور خارج المستودع.

## خارج النطاق
مخزون كامل، فواتير بنود وضريبة، ومزامنة متعددة المستخدمين لحظية.
