# قاعدة البيانات (Room / SQLite)

## الجداول الفعلية
- `shops`
- `parties` (عميل/مورد مرتبط بـ `shopId`)
- `accounts` (دليل الحسابات لكل محل)
- `documents` (رأس العملية)
- `journal_lines` (مدين/دائن، وحساب وطرف اختياري)
- `audit_logs`
- `app_settings`
- `daily_closings`
- `parties_fts` — جدول FTS4 مساعد للبحث، تتم مزامنته بمحفزات SQLite.

## المفاتيح والسياسات
- مفاتيح أساسية `Long` مع `autoGenerate` للجداول التشغيلية.
- علاقات مهمة بـ `ON DELETE RESTRICT`، والعمليات تستخدم الأرشفة الناعمة
  (`deletedAt`) بدل الحذف المدمر.
- فهارس على `shopId` و`partyId` و`occurredAt` و`docNumber` ونوع العملية.
- بحث الحسابات يستخدم FTS4 وبحث البادئة بدل `LIKE '%query%'`.

## الترحيل
إصدار قاعدة البيانات الحالي **4**:
- `1 → 2`: إضافة `creditLimitMinor`.
- `2 → 3`: إنشاء فهرس FTS ومحفزات مزامنته.
- `3 → 4`: إضافة تاريخ استحقاق البيع الآجل وتسلسل أرقام السندات لكل محل.

كل المسارات موجودة في `Migrations.ALL` ولا يوجد
`fallbackToDestructiveMigration`. تُختبر الترحيلات بـ `MigrationTestHelper`
مقابل المخططات المحفوظة في `app/schemas`.

## المال
كل مبلغ يخزن كـ `INTEGER NOT NULL` ويُمثّل في Kotlin بـ `Long` بوحدة العملة
الصغرى؛ لا تُستخدم أعداد عائمة للحسابات.
