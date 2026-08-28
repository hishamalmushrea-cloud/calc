# سجل التنفيذ — Repair & Completion Mode

> بدأ التنفيذ: 2026-08-25 — الفرع `arena/01a0366f-calc`
> ملاحظة بيئة: لا JDK / لا Android SDK / لا Gradle في بيئة العمل → التحقق ساكن (مراجعة الكود والمراجع)؛ البناء الفعلي غير ممكن هنا وسيُصرَّح بذلك.

## الإصلاح 1 — Gradle Wrapper (P0) ✅ DONE
- **المشكلة:** `distributionUrl` يشير لمسار محلي `file:///C:/Users/Hisham/Downloads/gradle-8.7-bin.zip`.
- **الأثر:** كسر البناء على أي جهاز آخر وعلى CI.
- **الملفات:** `gradle/wrapper/gradle-wrapper.properties`.
- **التغيير:** الرابط أصبح `https://services.gradle.org/distributions/gradle-8.7-bin.zip`.
- **التحقق:** قراءة الملف بعد التعديل (ساكن). البناء الفعلي غير ممكن (لا JDK/SDK).

## الإصلاح 2 — .gitignore تالف (P0) ✅ DONE
- **المشكلة:** الملف كان مُرمَّزًا جزئيًا UTF-16 (NUL bytes) في ذيله، فالقواعد بعد `.DS_Store` غير فعّالة.
- **الأثر:** `*.zip` وغيرها قد لا تُتجاهل.
- **الملفات:** `.gitignore`.
- **التغيير:** إعادة كتابة الملف UTF-8 نظيف.
- **التحقق:** `od -c` يظهر لا NUL؛ `git check-ignore` يتجاهل `app/build`, `build`, `*.zip` بنجاح.

## الإصلاح 3 — فرض الصلاحيات في طبقة البيانات (P1) ✅ DONE
- **المشكلة:** عمليات حساسة بلا فحص في المستودع (كانت تعتمد على إخفاء الأزرار).
- **الملفات:** `data/LedgerRepository.kt`, `ui/MainViewModel*.kt`.
- **التغيير:** أُضيف فحص `requireActor/requireAny` في المستودع لكل: `postDocument`, `updateDocument`, `softDeleteDocument`, `restoreDocument`, `closePartyAccount`, `createShop`, `addParty`, `addCategory`, `updatePartyExtra`, `updateShopCurrency`, `importCsv`, `closeDay`, `saveSalesDayNotes`, `closeSalesDay`, `reopenSalesDay`, `upsertItem`, `archiveItem`, `postInvoice`. مرّر الـ ViewModel معرّف المنفّذ `currentActorId()`.

## الإصلاح 4 — فصل القراءة عن الكتابة (P1) ✅ DONE
- **المشكلة:** صلاحية «عرض الحسابات» (`VIEW_ACCOUNTS`) كانت بوابة كتابة لعمليات غير البيع.
- **الملفات:** `domain/StaffPermissions.kt` (صلاحية جديدة `MANAGE_ACCOUNTS(17)`)، `StaffRoles` (المحاسب يملكها)، `EmployeesScreen` (تسمية)، `MoreScreen` (بوابات الأقسام)، `PartyDetail` (إخفاء زر إغلاق الحساب)، `strings.xml/values-en` (تسمية)، `test/.../StaffPermissionsTest.kt` (اختبارات الفصل).
- **التغيير:** `VIEW_ACCOUNTS` أصبحت للقراءة فقط؛ الكتابة غير البيعية تتطلب `MANAGE_ACCOUNTS`.
- **التحقق (ساكن):** grep لجميع مراجع `MANAGE_ACCOUNTS` و`requireActor/writePermissionFor` — 18 نقطة فرض موزعة على كل الدوال الحساسة؛ كل استدعاءات الـ ViewModel تمرر `currentActorId()`.

## الإصلاح 5/6/7 — دورة الفاتورة الكاملة (P1) ✅ DONE
- **المشكلة:** الفاتورة تُنشأ وبنودها لا تُعرض؛ وتعديلها من الشاشة العامة يغيّر المبلغ دون البنود/المخزون.
- **الملفات:** `ui/UiContract.kt` (حالة `invoiceLines` + حدثا `LoadInvoiceLines/ClearInvoiceLines`)، `ui/MainViewModel.kt` (تحميل/مسح البنود)، `ui/DocsScreen.kt` (تحميل البنود عند التعديل ومسحها عند الإغلاق)، `ui/DocSheet.kt` (عرض البنود + تعطيل حقل المبلغ)، `data/LedgerRepository.kt` (رفض التغيير المالي لفاتورة ذات بنود)، `strings` (نصوص العرض)، `androidTest/.../InvoiceIntegrationTest.kt` (اختبارات جديدة).
- **التغيير:** دورة «إنشاء → عرض بنود → تعديل آمن (غير مالي فقط) → أرشفة/استعادة عاكسة للمخزون» مكتملة.
- **التحقق (ساكن):** مراجعة كل ملف معدّل؛ لا يوجد مسار تعديل مالي للفاتورة يمر دون رفض؛ اختبار تكامل جديد يغطي المخزون/الأرشفة/الاستعادة/الرفض (غير مُشغَّل — لا SDK).

## الإصلاح 8 — رمز العملة (P1) ✅ DONE
- **المشكلة:** `Money.format` يعرض رمز العملة حسب Locale (يظهر «$» مع الأرقام اللاتينية/Locale.US).
- **الملفات:** `domain/Money.kt` (ضبط `DecimalFormatSymbols.currencySymbol` صراحةً مع fallback لرمز ISO)، `test/.../MoneyTest.kt` (3 اختبارات جديدة).
- **التغيير:** رمز العملة الصحيح (محلي/ISO) بدل رمز Locale الخاطئ.

## الإصلاح 9 — الأخطاء الوظيفية الصامتة (P2) ✅ DONE
- **`InventoryMath.parseQty`:** أُصلحت إشارة السالب عند الصفر («-0.5» كانت +500) ورفض الأجزاء الزائدة («1.5.5») وأكثر من 3 خانات عشرية. + اختبارات.
- **`importCsv`:** عدّاد `ok` لم يعد يحتسب السطر إذا كان المبلغ تالفًا/≤0 (يُحتسب تخطٍّ بدل نجاح كاذب).
- **«آخر حركة»:** التسمية أصبحت «أقدم استحقاق» (تطابق `MIN(dueAt)`) في ar/en + تعليق الكيان.
- **إغلاق اليوم المزدوج:** ميّزت التسميات («إغلاق اليوم وتسوية النقد» مقابل «إغلاق يوم البيع») في ar/en.
- **الويدجت والمحل النشط:** يحفظ `MainViewModel` المحل النشط في SharedPreferences ويقرأه الويدجت (بدل أول محل). الملفات: `DaftariWidget.kt`, `MainViewModel.kt`.

## الإصلاح 10 — Excel بأرقام حقيقية (P2) ✅ DONE
- **المشكلة:** كاتب XLSX اليدوي كان يكتب كل الخلايا كنصوص `t="s"` (لا جمع/فرز).
- **الملفات:** `export/ExcelReports.kt` (خلايا مختلطة نص/رقم `t="n"`، الأعمدة النقدية `BigDecimal` نقية).
- **التحقق (ساكن):** مراجعة الملف كاملًا؛ الأعمدة النقدية تُكتب `<v>1234.56</v>` بلا shared string.

## الإصلاح 11 — خصوصية/توطين/اعتماديات (P3) ✅ DONE
- **`FLAG_SECURE`:** أصبح مرتبطًا بوضع «إخفاء الأرصدة» بدل كونه دائمًا (`MainActivity.kt`).
- **اعتمادية ميتة:** أُزيل `androidx.datastore:datastore-preferences` غير المستخدمة (`app/build.gradle.kts`).
- **التواريخ:** تحققت — لا توجد أسماء أشهر/أيام مع `Locale.US` (جميعها تنسيقات أرقام مقصودة)؛ تصحيح لملاحظة سابقة.

## الإصلاح 12 — CI والتوثيق (P2) ✅ DONE
- **CI:** وظيفة `verify` (compile + unit + lint + `assembleDebug` + رفع APK) ووظيفة `android-tests` (محاكي + `connectedDebugAndroidTest`).
- **التوثيق:** `DELIVERY.md` (الإصدار 8)، `FEATURES.md` (مخطط 8)، `README.md` (1.3 + وصف CI)، `EMPLOYEE_SYSTEM_PLAN.md` (فصل القراءة/الكتابة).

## الإصلاح 13 — أداء التصدير (P2) ✅ DONE
- **`exportCsv`:** كتابة متدفقة صفحية (500/صفحة) بدل تحميل كامل الجدول في الذاكرة (`ui/MainUiServices.kt`).
- **`salesBookDays`:** تُرك كما هو (التجميع باليوم يعتمد على المنطقة الزمنية المحلية ولا يمكن نقله بأمان إلى SQLite دون إعادة تصميم) — مُوثَّق كتحسين مستقبلي.

## مؤجلات / BLOCKED
- **البناء/الاختبار الفعلي:** BLOCKED — لا JDK/Android SDK/Gradle في بيئة العمل؛ كل التحقق ساكن. المطلوب: بيئة Android أو تشغيل CI.
- **`salesBookDays` SQL:** مؤجّل (يتطلب تصميم تجميع يومي محلي المنطقة الزمنية) — ليس عائق إطلاق للفترات القصيرة.
- **MainViewModel refactor:** مؤجّل P3 (تحسين بنية دون تغيير سلوك).

## جولة الاختبار الفعلي على GitHub CI (أحدث التزامات)

بعد تجهيز CI، أُجري الاختبار الفعلي على خوادم GitHub وكشفت الاختبارات مشاكل **سابقة الوجود** لم تكن تُكتشف لأن CI لم يكن يشغّل androidTest من قبل:

### الإصلاح 14 — عدم تطابق مخطط الترحيل 7→8 (P1) ✅ DONE
- **المشكلة:** الترحيل 7→8 ينشئ فهرسًا جزئيًا (`index_items_shopId_sku_present`) غير مُعلن في `ItemEntity` → فشل تحقق Room (`Migration didn't properly handle: items`) واختلاف مخطط "المُهاجَر" عن "التثبيت الجديد".
- **التغيير:** إزالة الفهرس الزائد من `MIGRATION_7_8` (تفرد SKU يبقى مفروضًا في `upsertItem` عبر `countSku`).

### الإصلاح 15 — اختبار ترحيل FTS (P2) ✅ DONE
- **المشكلة:** `MigrationTestHelper.runMigrationsAndValidate` يعتبر جدول `parties_fts` الخارجي «جدولًا غير متوقع» (حدّ معروف في Room).
- **التغيير:** إعادة كتابة `AppDbMigrationTest` لتشغيل كائنات Migration يدويًا للمسارات التي تنشئ FTS، مع إبقاء `runMigrationsAndValidate` لمسار 1→2 (بلا FTS) للحفاظ على تحقق المخطط.

### الإصلاح 16 — خطأ `lateinit` على نوع بدائي (P2) ✅ DONE
- **المشكلة:** `private lateinit var shopId: Long` في `LedgerRepositoryIntegrationTest` (خطأ ترجمة Kotlin سابق الوجود؛ لم يظهر لأن CI لم يكن يجمّع androidTest).
- **التغيير:** `private var shopId = 0L`.

### الإصلاح 17 — فشل بناء عدّاء اختبار الفئة (P2) ✅ DONE
- **المشكلة:** `LedgerRepositoryIntegrationTest > initializationError` (Failed to instantiate test runner) بشكل ثابت عبر 3 تشغيلات — سببه النمط المتداخل `assertThrows { runBlocking { ... } }` (غير ضروري داخل `= runBlocking {}`) الذي يولّد فئات حالة اصطناعية زائدة.
- **التغيير:** استبدال النمط باستدعاء مباشر للدالة suspend داخل سياق الكوروتين + try/catch عادي.

### النتيجة النهائية (تشغيل CI رقم 32812226963)
- ✅ `verify`: ترجمة Kotlin + اختبارات وحدة JVM + lint + بناء APK (ورفع artifact).
- ✅ `android-tests`: **22/22 اختبار جهاز ناجح** (ترحيل 3 + صحة 5 + فواتير 3 + مستودع 8 + دفتر بيع 3).
