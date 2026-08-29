package com.daftari.ledger.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.daftari.ledger.backup.CloudBackupManager
import com.daftari.ledger.data.AgingRow
import com.daftari.ledger.data.AuditLogEntity
import com.daftari.ledger.data.CategoryEntity
import com.daftari.ledger.data.CategoryTotal
import com.daftari.ledger.data.CsvPreviewRow
import com.daftari.ledger.data.DatabaseHealthCheck
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.DocumentLineEntity
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.data.ShopEntity
import com.daftari.ledger.data.StatementLine
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.PartyKind
import java.io.File

enum class Period { TODAY, YESTERDAY, WEEK, MONTH, YEAR, CUSTOM }

sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Dynamic(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Dynamic -> value
}

data class UiState(
    val shops: List<ShopEntity> = emptyList(),
    val shop: ShopEntity? = null,
    val customers: List<PartyEntity> = emptyList(),
    val suppliers: List<PartyEntity> = emptyList(),
    val docs: List<DocumentEntity> = emptyList(),
    val totals: LedgerRepository.PeriodTotals = EMPTY_TOTALS,
    val owedToYou: Long = 0,
    val customerAdvances: Long = 0,
    val youOwe: Long = 0,
    val supplierCredits: Long = 0,
    val period: Period = Period.TODAY,
    val customFrom: Long? = null,
    val customTo: Long? = null,
    val message: UiText? = null,
    val loading: Boolean = true,
    val locked: Boolean = false,
    val hasPin: Boolean = false,
    val biometric: Boolean = false,
    val autoBackup: Boolean = false,
    val hideBalances: Boolean = false,
    val latinDigits: Boolean = true,
    val pinLockedUntil: Long = 0,
    val categories: List<CategoryEntity> = emptyList(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val aging: List<AgingRow> = emptyList(),
    val csvPreview: List<CsvPreviewRow> = emptyList(),
    val shareFile: File? = null,
    val prevTotals: LedgerRepository.PeriodTotals = EMPTY_TOTALS,
    val selectedParty: PartyEntity? = null,
    val partyStats: PartyStats? = null,
    val audit: List<AuditLogEntity> = emptyList(),
    val backups: List<File> = emptyList(),
    val shareText: String? = null,
    val agingAlert: Int = 0,
    val late: List<LedgerRepository.LateRow> = emptyList(),
    val nextDocumentNumber: Long = 1,
    val undoDocumentId: Long? = null,
    val cloudSettings: CloudBackupManager.Settings = CloudBackupManager.Settings(),
    val salesLedger: SalesLedgerState = SalesLedgerState(),
    val employees: EmployeeUiState = EmployeeUiState(),
    val googleBackup: GoogleBackupUiState = GoogleBackupUiState(),
    val inventory: InventoryUiState = InventoryUiState(),
    val book: AccountsBookUiState = AccountsBookUiState(),
    val healthIssues: List<DatabaseHealthCheck.Issue> = emptyList(),
    val healthCheckedAt: Long = 0,
    val restartRequested: Boolean = false,
    val invoiceLines: List<DocumentLineEntity> = emptyList()
)

data class PartyStats(
    val sales: Long = 0,
    val purchases: Long = 0,
    val collections: Long = 0,
    val payments: Long = 0,
    val docs: List<DocumentEntity> = emptyList(),
    val statementLines: List<StatementLine> = emptyList()
) {
    val collectionRate: Int
        get() = if (sales == 0L) 0 else ((collections * 100) / sales).toInt().coerceIn(0, 100)
}

data class DocumentDraft(
    val type: DocType,
    val amount: String,
    val partyId: Long?,
    val credit: Boolean,
    val notes: String,
    val documentNumber: String,
    val newPartyName: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val categoryId: Long? = null
)

sealed interface UiEvent {
    data class SetPeriod(val period: Period) : UiEvent
    data class SetCustomRange(val from: Long, val to: Long) : UiEvent
    data class SelectShop(val shop: ShopEntity) : UiEvent
    data class AddShop(val name: String) : UiEvent
    data class AddParty(
        val kind: PartyKind,
        val name: String,
        val phone: String,
        val openingMajor: String,
        val category: String,
        val limitMajor: String
    ) : UiEvent
    data class UpdateParty(val id: Long, val category: String, val limitMajor: String) : UiEvent
    data class AddDocument(val draft: DocumentDraft) : UiEvent
    data class UpdateDocument(
        val id: Long,
        val amount: String,
        val notes: String,
        val documentNumber: String,
        val credit: Boolean,
        val occurredAt: Long,
        val dueAt: Long?,
        val categoryId: Long?,
        val partyId: Long? = null,
        val newPartyName: String? = null
    ) : UiEvent
    data class DeleteDocument(val id: Long) : UiEvent
    data object UndoDeleteDocument : UiEvent
    data class LoadInvoiceLines(val documentId: Long) : UiEvent
    data object ClearInvoiceLines : UiEvent
    data class ShareReceipt(val document: DocumentEntity) : UiEvent
    data class Unlock(val pin: String) : UiEvent
    data object BiometricUnlocked : UiEvent
    data class SavePin(val pin: String) : UiEvent
    data object ClearPin : UiEvent
    data class ToggleBackup(val enabled: Boolean) : UiEvent
    data class ToggleBiometric(val enabled: Boolean) : UiEvent
    data class TogglePrivacy(val enabled: Boolean) : UiEvent
    data class ToggleLatinDigits(val enabled: Boolean) : UiEvent
    data class UpdateCurrency(val currencyCode: String) : UiEvent
    data class AddCategory(val kind: String, val name: String) : UiEvent
    data class CloseDay(val actual: String, val notes: String) : UiEvent
    data class CloseParty(val id: Long) : UiEvent
    data object LoadInsights : UiEvent
    data class PreviewCsv(val text: String) : UiEvent
    data object CommitCsv : UiEvent
    data object ExportPdf : UiEvent
    data object ExportExcel : UiEvent
    data object BackupNow : UiEvent
    data object RefreshBackups : UiEvent
    data class RestoreBackup(val file: File, val password: String?) : UiEvent
    data class BackupEncrypted(val password: String) : UiEvent
    data class OpenParty(val party: PartyEntity) : UiEvent
    data object ClosePartyDialog : UiEvent
    data class ShareStatement(val party: PartyEntity) : UiEvent
    data object ExportCsv : UiEvent
    data object RunDatabaseHealthCheck : UiEvent
    data object ClearDatabaseHealthCheck : UiEvent
    data object ChooseCloudFolder : UiEvent
    data class CloudFolderSelected(val uri: String) : UiEvent
    data object ClearCloudFolder : UiEvent
    data class SaveWebDav(val url: String, val user: String, val password: String) : UiEvent
    data object ClearWebDav : UiEvent
    data object CloudBackupNow : UiEvent
    data object ChooseCloudRestoreFile : UiEvent
    data class RestoreCloudFile(val uri: String) : UiEvent
    data object RestoreLatestWebDav : UiEvent
    data object OpenGoogleBackup : UiEvent
    data object CloseGoogleBackup : UiEvent
    data object LinkGoogleBackup : UiEvent
    data object UnlinkGoogleBackup : UiEvent
    data object RefreshGoogleBackups : UiEvent
    data object GoogleBackupNow : UiEvent
    data class SetGoogleBackupAutomatic(val enabled: Boolean) : UiEvent
    data class SetGoogleBackupWifiOnly(val enabled: Boolean) : UiEvent
    data class PrepareGoogleRestore(val backup: com.daftari.ledger.backup.RemoteBackup?) : UiEvent
    data object ConfirmGoogleRestore : UiEvent
    data class PrepareGoogleBackupDelete(val backup: com.daftari.ledger.backup.RemoteBackup?) : UiEvent
    data object ConfirmGoogleBackupDelete : UiEvent
    data class GoogleBackupAuthorized(
        val accountEmail: String,
        val accountSubject: String,
        val accessToken: String,
        val action: String
    ) : UiEvent
    data class GoogleBackupAuthorizationFailed(val message: String) : UiEvent
    data class CallPhone(val phone: String) : UiEvent
    data class OpenWhatsApp(val phone: String) : UiEvent

    data object LoadSalesLedger : UiEvent
    data class SetSalesBookView(val view: SalesBookView) : UiEvent
    data class SetSalesBookRange(val range: SalesBookRange) : UiEvent
    data class SetSalesBookCustomRange(val from: Long, val to: Long) : UiEvent
    data class SelectSalesDay(val dayStart: Long) : UiEvent
    data object CloseSalesDayPage : UiEvent
    data class SaveSalesEntry(val draft: SalesEntryDraft) : UiEvent
    data class UpdateSalesEntry(val id: Long, val draft: SalesEntryDraft) : UiEvent
    data class ArchiveSalesEntry(val id: Long) : UiEvent
    data class DuplicateSalesEntry(val id: Long, val occurredAt: Long) : UiEvent
    data class SaveSalesDayNotes(val dayStart: Long, val notes: String) : UiEvent
    data class CloseSalesBookDay(val dayStart: Long, val notes: String) : UiEvent
    data class ReopenSalesBookDay(val dayStart: Long) : UiEvent
    data class SearchSalesBook(
        val query: String,
        val entryType: String?,
        val paymentMethod: String?,
        val categoryId: Long?
    ) : UiEvent
    data class ShareSalesDay(val dayStart: Long, val detailed: Boolean) : UiEvent
    data class ExportSalesPeriod(val from: Long, val to: Long, val format: String) : UiEvent

    /**
     * يُغلق أي شاشة ثانوية مفتوحة فوق التبويبات (دفتر الحسابات، المخزون، النسخ السحابي،
     * الموظفون). يستعمله شريط التنقّل حتى يظهر التبويب المختار فعلًا، وزر الرجوع.
     */
    data object CloseSecondaryScreens : UiEvent

    data object OpenAccountsBook : UiEvent
    data object CloseAccountsBook : UiEvent
    data class SearchAccountsBook(val query: String) : UiEvent
    data class SelectBookPerson(val id: Long) : UiEvent
    data object CloseBookPerson : UiEvent
    data class SetBookPersonEditor(val editor: BookPersonEditor?) : UiEvent
    data class SaveBookPerson(val draft: BookPersonDraft) : UiEvent
    data class ArchiveBookPerson(val id: Long) : UiEvent
    data class OpenBookEntrySheet(
        val personId: Long,
        val presetKind: com.daftari.ledger.domain.BookEntryKind? = null,
        val editEntryId: Long? = null
    ) : UiEvent
    data object CloseBookEntrySheet : UiEvent
    data object UndoDeleteBookEntry : UiEvent
    data class SaveBookEntry(val draft: BookEntryDraft) : UiEvent
    data class DeleteBookEntry(val id: Long) : UiEvent
    data object ShareBookStatement : UiEvent
    data object ShareBookStatementPdf : UiEvent
    data class SetBookCurrencyManager(val open: Boolean) : UiEvent
    data class SetBookCurrencyEditor(val draft: BookCurrencyDraft?) : UiEvent
    data class SaveBookCurrency(val draft: BookCurrencyDraft) : UiEvent
    data class ArchiveBookCurrency(val id: Long) : UiEvent
    data class SetDefaultBookCurrency(val id: Long) : UiEvent

    data object OpenInventory : UiEvent
    data object CloseInventory : UiEvent
    data class SaveItem(val draft: ItemDraft) : UiEvent
    data class ArchiveItem(val id: Long) : UiEvent
    data class SetInvoiceSheet(val open: Boolean, val type: DocType = DocType.SALE) : UiEvent
    data class SaveInvoice(val draft: InvoiceDraft) : UiEvent

    data object OpenEmployees : UiEvent
    data object CloseEmployees : UiEvent
    data class SetEmployeesEnabled(val enabled: Boolean) : UiEvent
    data class AddEmployee(val draft: EmployeeDraft) : UiEvent
    data class UpdateEmployee(val id: Long, val draft: EmployeeDraft) : UiEvent
    data class ChangeEmployeeStatus(val id: Long, val status: String) : UiEvent
    data class SelectEmployee(val id: Long) : UiEvent
    data class SelectEmployeePeriod(val id: Long, val from: Long, val to: Long) : UiEvent
    data object CloseEmployeeDetail : UiEvent
    data class SetEmployeeRange(val range: SalesBookRange) : UiEvent
    data class LoginEmployee(val id: Long, val pin: String) : UiEvent
    data class SwitchToOwner(val pin: String) : UiEvent
    data class SetEmployeeSwitcher(val open: Boolean) : UiEvent
    data class AssignEmployeeShop(val employeeId: Long, val shopId: Long, val active: Boolean) : UiEvent
    data class OpenEmployeeShift(val employeeId: Long, val label: String, val openingCash: String) : UiEvent
    data class CloseEmployeeShift(val shiftId: Long, val actualCash: String, val notes: String) : UiEvent

    data object ConsumeMessage : UiEvent
    data object ConsumeShareFile : UiEvent
    data object ConsumeShareText : UiEvent
    data object ConsumeRestart : UiEvent
}

sealed interface UiEffect {
    data object PickCloudFolder : UiEffect
    data object PickBackupFile : UiEffect
    data class LinkGoogleBackup(val action: String = "LINK") : UiEffect
    data class AuthorizeGoogleBackup(val action: String) : UiEffect
    data object UnlinkGoogleBackup : UiEffect
    data class OpenUri(val uri: String) : UiEffect
}

private val EMPTY_TOTALS = LedgerRepository.PeriodTotals(0, 0, 0, 0, 0, 0, 0, 0)
