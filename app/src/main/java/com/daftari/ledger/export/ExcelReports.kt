package com.daftari.ledger.export

import android.content.Context
import com.daftari.ledger.R
import com.daftari.ledger.ui.UiState
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * مولّد Excel (.xlsx) بدون مكتبات خارجية.
 *
 * ملف XLSX هو أرشيف ZIP يحتوي على ملفات XML وفق معيار Office Open XML.
 * الأعمدة النقدية تُكتب كخلايا رقمية حقيقية (t="n") حتى تكون قابلة للجمع
 * والفرز في Excel / Google Sheets، وليس نصوصًا تشبه الأرقام.
 */
object ExcelReports {

    fun writePeriodExcel(ctx: Context, s: UiState): File {
        val f = File(ctx.cacheDir, "daftari-report.xlsx")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val digits = s.shop?.fractionDigits ?: 2

        val byId = (s.customers + s.suppliers).associateBy { it.id }

        val summaryRows = mutableListOf<List<XlsxCell>>()
        summaryRows += listOf(text(ctx.getString(R.string.report_item)), text(ctx.getString(R.string.report_value)))
        summaryRows += listOf(text(ctx.getString(R.string.sales)), number(s.totals.sales, digits))
        summaryRows += listOf(text(ctx.getString(R.string.purchases)), number(s.totals.purchases, digits))
        summaryRows += listOf(text(ctx.getString(R.string.expenses)), number(s.totals.expenses, digits))
        summaryRows += listOf(text(ctx.getString(R.string.other_income)), number(s.totals.income, digits))
        summaryRows += listOf(text(ctx.getString(R.string.collections)), number(s.totals.collections, digits))
        summaryRows += listOf(text(ctx.getString(R.string.payments)), number(s.totals.payments, digits))
        summaryRows += listOf(text(ctx.getString(R.string.cash_net)), number(s.totals.cashNet, digits))
        summaryRows += listOf(text(ctx.getString(R.string.estimated_profit)), number(s.totals.estimatedProfit, digits))

        val docsRows = mutableListOf<List<XlsxCell>>()
        docsRows += listOf(
            text(ctx.getString(R.string.date)), text(ctx.getString(R.string.type)), text(ctx.getString(R.string.report_value)),
            text(ctx.getString(R.string.party)), text(ctx.getString(R.string.notes)), text(ctx.getString(R.string.document_number))
        )
        s.docs.sortedByDescending { it.occurredAt }.forEach { document ->
            val party = document.partyId?.let { byId[it] }
            docsRows += listOf(
                text(fmt.format(Date(document.occurredAt))),
                text(ctx.getString(documentTypeString(document.type))),
                number(document.amountMinor, digits),
                text(party?.name.orEmpty()),
                text(document.notes),
                text(document.docNumber)
            )
        }

        val partiesRows = mutableListOf<List<XlsxCell>>()
        partiesRows += listOf(
            text(ctx.getString(R.string.name)), text(ctx.getString(R.string.type)), text(ctx.getString(R.string.category)),
            text(ctx.getString(R.string.balance)), text(ctx.getString(R.string.phone_optional))
        )
        (s.customers + s.suppliers).sortedBy { it.name }.forEach { party ->
            partiesRows += listOf(
                text(party.name),
                text(ctx.getString(if (party.kind == "CUSTOMER") R.string.customer else R.string.supplier)),
                text(party.category),
                number(party.cachedBalanceMinor, digits),
                text(party.phone)
            )
        }

        val sheets = listOf(
            ctx.getString(R.string.sheet_summary) to summaryRows,
            ctx.getString(R.string.sheet_documents) to docsRows,
            ctx.getString(R.string.sheet_accounts) to partiesRows
        )
        val rightToLeft = ctx.resources.configuration.layoutDirection == android.util.LayoutDirection.RTL
        writeXlsx(f, sheets, rightToLeft)
        return f
    }

    // ──────── نموذج الخلية ────────

    private data class XlsxCell(val text: String, val number: String? = null)

    private fun text(value: String) = XlsxCell(value)

    /** خلية رقمية حقيقية: النص العشري الدقيق (بدون رمز عملة) ليكون قابلاً للحساب. */
    private fun number(minor: Long, digits: Int): XlsxCell {
        val decimal = BigDecimal.valueOf(minor).movePointLeft(digits).toPlainString()
        return XlsxCell(decimal, decimal)
    }

    // ──────── XLSX Writer (zero dependencies) ────────

    private fun writeXlsx(file: File, sheets: List<Pair<String, List<List<XlsxCell>>>>, rightToLeft: Boolean) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->

            // [Content_Types].xml
            zip.putEntry("[Content_Types].xml")
            val ctOverrides = sheets.indices.joinToString("") { i ->
                """<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            zip.writeXml("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
$ctOverrides
</Types>""")

            // _rels/.rels
            zip.putEntry("_rels/.rels")
            zip.writeXml("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")

            // xl/_rels/workbook.xml.rels
            zip.putEntry("xl/_rels/workbook.xml.rels")
            val sheetRels = sheets.indices.joinToString("") { i ->
                """<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
            }
            val stylesRid = sheets.size + 1
            val ssRid = sheets.size + 2
            zip.writeXml("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$sheetRels
<Relationship Id="rId$stylesRid" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId$ssRid" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>""")

            // xl/styles.xml  (style 0 = normal, style 1 = bold header)
            zip.putEntry("xl/styles.xml")
            zip.writeXml("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
</styleSheet>""")

            // Collect shared strings (text cells only)
            val allStrings = mutableListOf<String>()
            val stringIndex = mutableMapOf<String, Int>()
            for ((_, rows) in sheets) {
                for (row in rows) {
                    for (cell in row) {
                        if (cell.number == null && cell.text !in stringIndex) {
                            stringIndex[cell.text] = allStrings.size
                            allStrings += cell.text
                        }
                    }
                }
            }

            // xl/sharedStrings.xml
            zip.putEntry("xl/sharedStrings.xml")
            val ssEntries = allStrings.joinToString("") { "<si><t>${xmlEscape(it)}</t></si>" }
            zip.writeXml("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${allStrings.size}" uniqueCount="${allStrings.size}">
$ssEntries
</sst>""")

            // xl/workbook.xml
            zip.putEntry("xl/workbook.xml")
            val sheetDefs = sheets.mapIndexed { i, (name, _) ->
                """<sheet name="${xmlEscape(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
            }.joinToString("")
            zip.writeXml("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$sheetDefs</sheets>
</workbook>""")

            // xl/worksheets/sheet{N}.xml
            sheets.forEachIndexed { i, (_, rows) ->
                zip.putEntry("xl/worksheets/sheet${i + 1}.xml")
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="${if (rightToLeft) "1" else "0"}" tabSelected="${if (i == 0) "1" else "0"}" workbookViewId="0"/></sheetViews><sheetData>""")
                rows.forEachIndexed { r, row ->
                    sb.append("""<row r="${r + 1}">""")
                    row.forEachIndexed { c, cell ->
                        val ref = colLetter(c) + (r + 1)
                        val style = if (r == 0) " s=\"1\"" else ""
                        val number = cell.number
                        if (number != null) {
                            // خلية رقمية حقيقية قابلة للحساب والفرز.
                            sb.append("""<c r="$ref"$style><v>$number</v></c>""")
                        } else {
                            val idx = stringIndex[cell.text] ?: 0
                            sb.append("""<c r="$ref" t="s"$style><v>$idx</v></c>""")
                        }
                    }
                    sb.append("</row>")
                }
                sb.append("</sheetData></worksheet>")
                zip.writeXml(sb.toString())
            }
        }
    }

    private fun colLetter(c: Int): String {
        var n = c
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun ZipOutputStream.putEntry(name: String) = putNextEntry(ZipEntry(name))
    private fun ZipOutputStream.writeXml(xml: String) {
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

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
