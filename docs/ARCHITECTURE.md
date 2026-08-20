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

Hilt غير مستخدم في v1 لتقليل التعقيد؛ حقن يدوي عبر `AppContainer`.
