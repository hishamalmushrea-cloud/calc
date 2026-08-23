# إعداد النسخ الاحتياطي عبر Google

> لا تضع Client Secret أو كلمة مرور Google أو Refresh Token داخل المشروع.

يستخدم التطبيق Credential Manager 1.3.0 وGoogle ID 1.1.1، وهما أحدث إصدارين متوافقين مع سلسلة Kotlin 1.9 الحالية؛ لا يستخدم Google Sign-In القديم.

## 1. Google Cloud / Google Auth Platform

1. أنشئ مشروعًا في Google Cloud Console.
2. فعّل **Google Drive API**.
3. أكمل Branding واسم التطبيق وسياسة الخصوصية في Google Auth Platform.
4. أضف النطاق غير الحساس فقط:
   `https://www.googleapis.com/auth/drive.appdata`
5. أنشئ Android OAuth Client للحزمة `com.daftari.ledger`.
6. أضف SHA-1 لشهادة debug أثناء التطوير، ثم SHA-1 لشهادة App Signing من Google Play للإصدار المنشور.
7. أنشئ Web OAuth Client ID لاستخدام Credential Manager. الـClient ID قيمة عامة؛ لا تضف Client Secret إلى Android.

## 2. Web Client ID

المشروع مهيأ افتراضيًا بالـWeb Client ID العام:

```text
466687335416-bu46jej7d5mccp16ehp4l83afia195fg.apps.googleusercontent.com
```

يمكن استبداله لبيئة أخرى دون تعديل Git بوضع القيمة التالية في `~/.gradle/gradle.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=000000000000-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

هذه القيمة معرف عام وليست Client Secret. يجب أن يكون نوعها في Google Cloud هو **Web application**، مع وجود Android OAuth Client منفصل للحزمة وشهادة التوقيع.

## 3. سلوك الأمان

- المصادقة: Android Credential Manager + Sign in with Google.
- التفويض: Google Identity Services `AuthorizationClient`.
- التخزين: Drive API v3 داخل `appDataFolder` المخفي.
- لا يخزن التطبيق كلمة مرور Google أو Access Token أو Refresh Token.
- Access Token قصير العمر ويطلبه التطبيق عند الحاجة بعد موافقة المستخدم.
- اختار المشروع حماية حساب Google بدل كلمة استرداد إضافية؛ النسخ ليست E2E بمفتاح مستقل عن Google.

## 4. اختبار حقيقي

يتطلب الاختبار جهازًا أو Emulator يحوي Google Play services وحساب اختبار مسموحًا في شاشة OAuth:

1. اربط الحساب وأنشئ نسخة يدوية.
2. تحقق من ظهور التاريخ والحجم والجهاز.
3. فعّل النسخ اليومي وجرب Wi-Fi فقط ثم الشبكة المتصلة.
4. افصل الشبكة وتحقق من حالة «في الانتظار».
5. ثبّت التطبيق على جهاز اختبار آخر بنفس package/signing، اربط الحساب نفسه، ثم استعد النسخة.
6. تحقق من المحلات والحسابات والعمليات والموظفين والورديات والديون والعلاقات.
7. اختبر وجود بيانات محلية وتأكد من ظهور التحذير وإنشاء `pre-restore-*`.
8. اختبر حساب Google مختلفًا، ونسخة تالفة، ونسخة قاعدة أحدث من التطبيق.

## 5. سياسة النسخ

- النسخ ملفات كاملة مستقلة ومضغوطة، وليست سلسلة incremental هشة.
- لا ترفع نسخة مكررة إذا لم تتغير قاعدة البيانات.
- الرفع Resumable لجميع الأحجام.
- يحتفظ النظام بآخر 7 نسخ يومية و4 أسبوعية لكل جهاز، ولا يحذف نسخ التعارض تلقائيًا.
- لا توجد مرفقات دائمة في التطبيق حاليًا. تنسيق الحزمة يحجز قائمة attachments لإضافتها مستقبلًا.
