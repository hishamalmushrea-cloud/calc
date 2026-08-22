# المعمارية

طبقات:
1. **ui** — Jetpack Compose + ViewModel (StateFlow)
2. **domain** — حالات استخدام، Money، LedgerService
3. **data** — Room، مستودعات، نسخ احتياطي

قواعد:
- Offline-first، مصدر الحقيقة محلي.
- لا شبكة في v1 للعمليات.
- كل مبلغ `Money(minor: Long, currencyCode: String)`.
- محل واحد نشط في الواجهة؛ التقارير المجمّعة اختيار.
- وحدات مستقبلية: inventory, invoicing, sync, multiuser — واجهات فارغة غير معروضة كأزرار وهمية.

التنقل:
أسفل: الرئيسية | الحسابات | العمليات | التقارير | المزيد
FAB: عملية سريعة.

## الحقن ودورة الحياة

لا يستخدم الإصدار الحالي Hilt. ينشئ `DaftariApp` قاعدة `AppDb` و`LedgerRepository`
و`BackupManager` ويتيحها للـ `MainViewModel` (حقن يدوي بسيط وواضح). مهام التهيئة
تعمل في `ProcessLifecycleOwner.lifecycleScope` بدل نطاق دائم غير قابل للإلغاء.

## تنظيم الواجهة

العقد موجود في `UiContract.kt` (`UiState` و`sealed interface UiEvent`)، بينما كل
وجهة Compose في ملف مستقل: Dashboard وParties وDocs وReports وMore، مع ملفات
مستقلة لنموذج العملية وتفاصيل الحساب. `DaftariRoot` مسؤول عن الهيكل والتنقل فقط.
