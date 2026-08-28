package com.daftari.ledger.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val DEFAULT_PARTY_CATEGORY = "عادي"

@Entity(tableName = "shops")
data class ShopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val currencyCode: String = "SAR",
    val fractionDigits: Int = 2,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val nextDocumentNumber: Long = 1
)

@Entity(
    tableName = "parties",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("shopId"), Index("name"), Index("kind")]
)
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val kind: String, // CUSTOMER / SUPPLIER
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val category: String = DEFAULT_PARTY_CATEGORY,
    val openingMinor: Long = 0,
    val cachedBalanceMinor: Long = 0,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val creditLimitMinor: Long = 0
)

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("shopId"), Index(value = ["shopId", "code"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val code: String,
    val name: String,
    val type: String, // AccountType
    val isCashLike: Boolean = false,
    val archived: Boolean = false
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.RESTRICT)],
    indices = [
        Index("shopId"), Index("occurredAt"), Index("partyId"), Index("docNumber"),
        Index("type"), Index("dueAt"), Index("categoryId"), Index("employeeId"),
        Index("shiftId"), Index("createdByEmployeeId")
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val type: String,
    val partyId: Long? = null,
    val cashAccountId: Long? = null,
    val counterAccountId: Long? = null,
    val amountMinor: Long,
    val occurredAt: Long,
    val dueAt: Long? = null,
    val categoryId: Long? = null,
    val employeeId: Long? = null,
    val shiftId: Long? = null,
    val createdByEmployeeId: Long? = null,
    val updatedByEmployeeId: Long? = null,
    val deletedByEmployeeId: Long? = null,
    val docNumber: String = "",
    val notes: String = "",
    val paymentMethod: String = "CASH",
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("documentId"), Index("accountId"), Index("partyId")]
)
data class JournalLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val accountId: Long,
    val partyId: Long? = null,
    val debitMinor: Long = 0,
    val creditMinor: Long = 0,
    val memo: String = ""
)

@Entity(
    tableName = "categories",
    indices = [Index("shopId"), Index(value = ["shopId", "kind", "name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val kind: String,
    val name: String,
    val archived: Boolean = false
)

@Entity(
    tableName = "employees",
    indices = [Index("name"), Index("phone"), Index("status"), Index("role")]
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val jobTitle: String = "",
    val role: String = "SELLER",
    val permissions: Long = 0,
    val baseSalaryMinor: Long = 0,
    val commissionBasisPoints: Int = 0,
    val monthlyTargetMinor: Long = 0,
    val startDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val status: String = "ACTIVE",
    val username: String = "",
    val pinHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val inactiveAt: Long? = null
)

@Entity(
    tableName = "employee_shops",
    indices = [Index("shopId"), Index("employeeId")]
)
data class EmployeeShopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val shopId: Long,
    val active: Boolean = true,
    val assignedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)

@Entity(
    tableName = "employee_shifts",
    indices = [Index("shopId"), Index("employeeId"), Index("status"), Index("openedAt")]
)
data class EmployeeShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val employeeId: Long,
    val label: String = "",
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingCashMinor: Long = 0,
    val expectedCashMinor: Long = 0,
    val actualCashMinor: Long? = null,
    val differenceMinor: Long? = null,
    val status: String = "OPEN",
    val notes: String = "",
    val openedByEmployeeId: Long? = null,
    val closedByEmployeeId: Long? = null
)

data class EmployeePerformanceRow(
    val employeeId: Long,
    val employeeName: String,
    val jobTitle: String,
    val role: String,
    val status: String,
    val salesMinor: Long,
    val transactionCount: Int,
    val cashMinor: Long,
    val bankMinor: Long,
    val cardMinor: Long,
    val walletMinor: Long,
    val creditMinor: Long
) {
    val averageSaleMinor: Long get() = if (transactionCount == 0) 0 else salesMinor / transactionCount
}

data class ShiftCashSummary(
    val cashSalesMinor: Long,
    val cashOutflowsMinor: Long
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = System.currentTimeMillis(),
    val action: String,
    val entity: String,
    val entityId: Long?,
    val detail: String = "",
    val actorEmployeeId: Long? = null,
    val beforeValue: String = "",
    val afterValue: String = ""
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val fiscalEnabled: Boolean = false,
    val fiscalStart: Long? = null,
    val fiscalEnd: Long? = null,
    val pinHash: String? = null,
    val hideBalances: Boolean = false,
    val uniqueDocPerParty: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val autoBackupKeep: Int = 7,
    val biometricUnlock: Boolean = false,
    val latinDigits: Boolean = true,
    val failedPinAttempts: Int = 0,
    val pinLockedUntil: Long = 0,
    val employeesEnabled: Boolean = false,
    val currentEmployeeId: Long? = null,
    /** العملة الافتراضية في دفتر الحسابات؛ null تعني عملة المحل ثم أول عملة. */
    val defaultCurrencyId: Long? = null
)

/**
 * عملة من عملات دفتر الحسابات. ليست محدودة بالعملات الرسمية: يمكن للمستخدم إنشاء
 * عملة باسم يختاره ورمز يختاره (أو بلا رمز مثل «محلي»)، وعدد خانات عشرية مناسب.
 */
@Entity(tableName = "currencies", indices = [Index(value = ["code"], unique = true)])
data class CurrencyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val symbol: String = "",
    val fractionDigits: Int = 2,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** شخص في دفتر الحسابات (مستقل عن العملاء/الموردين في جدول parties). */
@Entity(
    tableName = "book_persons",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("shopId"), Index("name")]
)
data class BookPersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * عملية واحدة في دفتر الحسابات.
 *
 * - [kind]: نوع العملية المعروض في السجل (`LE` له / `DEBT` عليه / `SETTLEMENT` تسديد).
 * - [side]: الجانب الفعلي المحفوظ عند التسجيل؛ التسديد يُحفظ بجانب الرصيد الذي قلّصه
 *   حتى لا يتغير معنى العملية لو حُرّرت عمليات أقدم منها.
 * - [amountMinor]: مقدار موجب دائمًا بالوحدة الصغرى لعملة [currencyId].
 */
@Entity(
    tableName = "book_entries",
    foreignKeys = [
        ForeignKey(entity = BookPersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["id"], childColumns = ["currencyId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("personId"), Index("currencyId"), Index("occurredAt")]
)
data class BookEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val currencyId: Long,
    val kind: String,
    val side: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val details: String = "",
    val opening: Boolean = false,
    val createdByEmployeeId: Long? = null,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** رصيد شخص في عملة واحدة؛ الصافي = عليه − له (موجب = عليه، سالب = له). */
data class BookBalanceRow(
    val personId: Long,
    val currencyId: Long,
    val creditMinor: Long,
    val debtMinor: Long,
    val settledMinor: Long
) {
    val netMinor: Long get() = debtMinor - creditMinor
}

/** آخر حركة لكل شخص في الدفتر. */
data class BookActivityRow(
    val personId: Long,
    val lastAt: Long?
)

/** رصيد افتتاحي يُسجَّل كعملية عادية عند إنشاء الشخص. */
data class BookOpeningBalance(
    val currencyId: Long,
    val kind: String,
    val amountMinor: Long
)

@Entity(
    tableName = "daily_closings",
    indices = [Index(value = ["shopId", "dayStart"], unique = true)]
)
data class DailyClosingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val dayStart: Long,
    val salesMinor: Long,
    val expensesMinor: Long,
    val cashExpectedMinor: Long,
    val cashActualMinor: Long,
    val differenceMinor: Long,
    val notes: String = "",
    val closedAt: Long = System.currentTimeMillis()
)

/** بيانات صفحة اليوم فقط؛ المبالغ تبقى في documents ولا تُكرر هنا. */
@Entity(
    tableName = "daily_books",
    indices = [Index(value = ["shopId", "dayStart"], unique = true), Index("dayStart")]
)
data class DailyBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val dayStart: Long,
    val notes: String = "",
    val status: String = "OPEN",
    val closedAt: Long? = null,
    val reopenedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class PaymentTotal(
    val method: String,
    val amountMinor: Long,
    val count: Int
)

data class DailyBookSummary(
    val dayStart: Long,
    val salesMinor: Long,
    val outflowsMinor: Long,
    val cashSalesMinor: Long,
    val cashOutflowsMinor: Long,
    val saleCount: Int,
    val outflowCount: Int,
    val notes: String,
    val status: String,
    val closedAt: Long?,
    val payments: List<PaymentTotal>
) {
    val transactionCount: Int get() = saleCount + outflowCount
    val netCashMovementMinor: Long get() = cashSalesMinor - cashOutflowsMinor
}

data class SalesBookEntryInput(
    val type: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val categoryId: Long? = null,
    val paymentMethod: String = "CASH",
    val partyId: Long? = null,
    val employeeId: Long? = null,
    val newPartyName: String? = null,
    val notes: String = "",
    val documentNumber: String = "",
    val dueAt: Long? = null
)

data class SalesBookPeriodSummary(
    val salesMinor: Long,
    val outflowsMinor: Long,
    val netCashMovementMinor: Long,
    val saleCount: Int,
    val outflowCount: Int,
    val activeDays: Int,
    val dailyAverageSalesMinor: Long,
    val bestDay: DailyBookSummary?,
    val weakestDay: DailyBookSummary?,
    val paymentTotals: List<PaymentTotal>
)

data class PartyStatsAggregate(
    val sales: Long,
    val purchases: Long,
    val collections: Long,
    val payments: Long
)

/** صف واحد من استعلام الشيخوخة الموحّد؛ تُجمع الصفوف حسب الطرف في المستودع. */
data class AgingDocumentRow(
    @Embedded val party: PartyEntity,
    val invoiceAmountMinor: Long?,
    val invoiceOccurredAt: Long?
)

/** أقدم تاريخ استحقاق لبيع آجل غير مسدَّد (محسوب في SQL بدل استعلام مستقل لكل عميل). */
data class PartyLastActivityRow(
    @Embedded val party: PartyEntity,
    val lastDate: Long?
)

data class PartyStatementRow(
    @Embedded val document: DocumentEntity,
    val netDebitDelta: Long
)

data class StatementLine(
    val document: DocumentEntity,
    val deltaMinor: Long,
    val runningBalanceMinor: Long
)

data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String,
    val totalMinor: Long
)

data class OverduePartyRow(
    val partyId: Long,
    val partyName: String,
    val documentCount: Int,
    val totalMinor: Long,
    val oldestDueAt: Long
)

data class AgingRow(
    val party: PartyEntity,
    val b0: Long,
    val b31: Long,
    val b61: Long,
    val b90: Long
)

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("shopId"), Index(value = ["shopId", "sku"]), Index("name")]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val name: String,
    val sku: String = "",
    val unit: String = "قطعة",
    val sellPriceMinor: Long = 0,
    val costPriceMinor: Long = 0,
    val qtyMilli: Long = 0,
    val reorderQtyMilli: Long = 0,
    val trackStock: Boolean = true,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_lines",
    foreignKeys = [
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("documentId"), Index("itemId")]
)
data class DocumentLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val itemId: Long,
    val itemName: String,
    val qtyMilli: Long,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val trackStock: Boolean = true
)

data class InvoiceLineInput(
    val itemId: Long,
    val qtyMilli: Long,
    val unitPriceMinor: Long
)

data class CsvPreviewRow(
    val line: Int,
    val name: String,
    val kind: String,
    val amount: String,
    val type: String,
    val error: String? = null
)
