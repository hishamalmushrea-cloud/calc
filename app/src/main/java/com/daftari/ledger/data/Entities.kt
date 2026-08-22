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
    val createdAt: Long = System.currentTimeMillis()
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
    indices = [Index("shopId"), Index("occurredAt"), Index("partyId"), Index("docNumber"), Index("type")]
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

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = System.currentTimeMillis(),
    val action: String,
    val entity: String,
    val entityId: Long?,
    val detail: String = ""
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
    val biometricUnlock: Boolean = false
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

/** آخر حركة بيع/تحصيل محسوبة في SQL بدل استعلام مستقل لكل عميل. */
data class PartyLastActivityRow(
    @Embedded val party: PartyEntity,
    val lastDate: Long?
)

data class AgingRow(
    val party: PartyEntity,
    val b0: Long,
    val b31: Long,
    val b61: Long,
    val b90: Long
)

data class CsvPreviewRow(
    val line: Int,
    val name: String,
    val kind: String,
    val amount: String,
    val type: String,
    val error: String? = null
)
