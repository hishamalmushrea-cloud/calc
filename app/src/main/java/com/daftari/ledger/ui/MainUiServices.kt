package com.daftari.ledger.ui

import android.app.Application
import com.daftari.ledger.DaftariApp
import com.daftari.ledger.R
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.data.ShopEntity
import com.daftari.ledger.domain.Money
import com.daftari.ledger.export.ExcelReports
import com.daftari.ledger.export.PdfReports
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** خدمات الملفات والنسخ والمشاركة؛ تبقي MainViewModel مركزًا على الحالة والأحداث. */
internal class MainUiServices(
    private val app: Application,
    private val repo: LedgerRepository
) {
    private val daftariApp get() = app as DaftariApp

    suspend fun exportPdf(state: UiState): File = withContext(Dispatchers.IO) {
        PdfReports.writePeriodReport(app, state)
    }

    suspend fun exportExcel(state: UiState): File = withContext(Dispatchers.IO) {
        ExcelReports.writePeriodExcel(app, state)
    }

    suspend fun backupDatabase(): File = daftariApp.backup.exportDatabase()
    suspend fun backupEncrypted(password: String): File = daftariApp.backup.exportEncrypted(password)
    fun listBackups(): List<File> = daftariApp.backup.listBackups()

    suspend fun restore(file: File, password: String?) {
        if (file.name.endsWith(".enc")) daftariApp.backup.restoreEncrypted(file, password.orEmpty())
        else daftariApp.backup.restoreFrom(file)
    }

    suspend fun statement(party: PartyEntity, state: UiState): String {
        val lines = repo.statement(party)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return buildString {
            append(app.getString(R.string.statement_title, party.name)).append('\n')
            append(app.getString(R.string.statement_balance, state.displayMoney(party.cachedBalanceMinor, Locale.getDefault()))).append("\n\n")
            append(app.getString(R.string.statement_columns_running)).append('\n')
            lines.takeLast(50).asReversed().forEach { line ->
                append(dateFormat.format(Date(line.document.occurredAt)))
                    .append(" | ")
                    .append(app.getString(documentTypeString(line.document.type)))
                    .append(" | ")
                    .append(state.displayMoney(line.document.amountMinor, Locale.getDefault()))
                    .append(" | ")
                    .append(state.displayMoney(line.runningBalanceMinor, Locale.getDefault()))
                    .append('\n')
            }
        }
    }

    /** كشف حساب دفتر الحسابات كملف PDF (نفس محرّك التقارير المستخدم لبقية الكشوف). */
    suspend fun bookStatementPdf(title: String, lines: List<String>): File = withContext(Dispatchers.IO) {
        PdfReports.writeStatement(app, title, lines)
    }

    suspend fun receipt(document: DocumentEntity, party: PartyEntity?, shop: ShopEntity?, state: UiState): File =
        withContext(Dispatchers.IO) {
            PdfReports.writeDocumentReceipt(app, document, party, shop, state)
        }

    suspend fun exportCsv(shopId: Long): File = withContext(Dispatchers.IO) {
        val byId = repo.parties.listAll(shopId).associateBy { it.id }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val file = File(app.cacheDir, "daftari-export.csv")
        // كتابة متدفقة صفحية: لا نحتفظ بكامل الجدول في الذاكرة.
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.append("name,kind,amount,type,date,notes\n")
            var offset = 0
            val pageSize = 500
            while (true) {
                val docs = repo.documents.page(shopId, pageSize, offset)
                if (docs.isEmpty()) break
                docs.forEach { document ->
                    val party = document.partyId?.let(byId::get)
                    writer.append(
                        listOf(
                            party?.name.orEmpty(),
                            party?.kind.orEmpty(),
                            Money(document.amountMinor).toBigDecimal().toPlainString(),
                            document.type,
                            dateFormat.format(Date(document.occurredAt)),
                            document.notes
                        ).joinToString(",", transform = ::csvCell)
                    ).append('\n')
                }
                if (docs.size < pageSize) break
                offset += pageSize
            }
        }
        file
    }

    private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun documentTypeString(type: String): Int = when (type) {
        "SALE" -> R.string.doc_type_sale
        "PURCHASE" -> R.string.doc_type_purchase
        "EXPENSE" -> R.string.doc_type_expense
        "INCOME" -> R.string.doc_type_income
        "COLLECT" -> R.string.doc_type_collect
        "PAY" -> R.string.doc_type_pay
        "TRANSFER" -> R.string.doc_type_transfer
        "OPENING" -> R.string.doc_type_opening
        else -> R.string.doc_type_unknown
    }
}
