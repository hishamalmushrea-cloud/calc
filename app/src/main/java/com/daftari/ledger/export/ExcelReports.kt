package com.daftari.ledger.export

import android.content.Context
import com.daftari.ledger.domain.Money
import com.daftari.ledger.ui.UiState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * مولّد Excel (.xlsx) بدون مكتبات خارجية.
 *
 * ملف XLSX هو أرشيف ZIP يحتوي على ملفات XML وفق معيار Office Open XML.
 * هذا التنفيذ يولّد الحد الأدنى اللازم لفتح الملف في Excel / Google Sheets
 * مع دعم تبويبات متعددة وخلايا عريضة Bold في الترويسات.
 */
object ExcelReports {

    fun writePeriodExcel(ctx: Context, s: UiState): File {
        val f = File(ctx.cacheDir, "daftari-report.xlsx")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val byId = (s.customers + s.suppliers).associateBy { it.id }

        // --- Build sheet data ---
        val summaryRows = mutableListOf<List<String>>()
        summaryRows += listOf("البند", "المبلغ")
        summaryRows += listOf("مبيعات", Money(s.totals.sales).format())
        summaryRows += listOf("مشتريات", Money(s.totals.purchases).format())
        summaryRows += listOf("مصروفات", Money(s.totals.expenses).format())
        summaryRows += listOf("إيرادات أخرى", Money(s.totals.income).format())
        summaryRows += listOf("تحصيل", Money(s.totals.collections).format())
        summaryRows += listOf("سداد", Money(s.totals.payments).format())
        summaryRows += listOf("صافي نقدي", Money(s.totals.cashNet).format())
        summaryRows += listOf("ربح تقديري", Money(s.totals.estimatedProfit).format())

        val docsRows = mutableListOf<List<String>>()
        docsRows += listOf("التاريخ", "النوع", "المبلغ", "الطرف", "ملاحظات", "رقم السند")
        s.docs.sortedByDescending { it.occurredAt }.forEach { d ->
            val p = d.partyId?.let { byId[it] }
            docsRows += listOf(
                fmt.format(Date(d.occurredAt)),
                docTypeArabic(d.type),
                Money(d.amountMinor).format(),
                p?.name.orEmpty(),
                d.notes,
                d.docNumber
            )
        }

        val partiesRows = mutableListOf<List<String>>()
        partiesRows += listOf("الاسم", "النوع", "التصنيف", "الرصيد", "الهاتف")
        (s.customers + s.suppliers).sortedBy { it.name }.forEach { p ->
            partiesRows += listOf(
                p.name,
                if (p.kind == "CUSTOMER") "عميل" else "مورد",
                p.category,
                Money(p.cachedBalanceMinor).format(),
                p.phone
            )
        }

        val sheets = listOf(
            "الملخص" to summaryRows,
            "العمليات" to docsRows,
            "الحسابات" to partiesRows
        )

        writeXlsx(f, sheets)
        return f
    }

    // ──────── XLSX Writer (zero dependencies) ────────

    private fun writeXlsx(file: File, sheets: List<Pair<String, List<List<String>>>>) {
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

            // Collect shared strings
            val allStrings = mutableListOf<String>()
            val stringIndex = mutableMapOf<String, Int>()
            for ((_, rows) in sheets) {
                for (row in rows) {
                    for (cell in row) {
                        if (cell !in stringIndex) {
                            stringIndex[cell] = allStrings.size
                            allStrings += cell
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
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="true" tabSelected="${if (i == 0) "1" else "0"}" workbookViewId="0"/></sheetViews><sheetData>""")
                rows.forEachIndexed { r, row ->
                    sb.append("""<row r="${r + 1}">""")
                    row.forEachIndexed { c, cell ->
                        val ref = colLetter(c) + (r + 1)
                        val idx = stringIndex[cell] ?: 0
                        val style = if (r == 0) " s=\"1\"" else ""
                        sb.append("""<c r="$ref" t="s"$style><v>$idx</v></c>""")
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

    private fun docTypeArabic(t: String) = when (t) {
        "SALE" -> "بيع"; "PURCHASE" -> "شراء"; "EXPENSE" -> "مصروف"; "INCOME" -> "إيراد"
        "COLLECT" -> "تحصيل"; "PAY" -> "سداد"; "TRANSFER" -> "تحويل"; "OPENING" -> "افتتاحي"
        else -> t
    }
}
