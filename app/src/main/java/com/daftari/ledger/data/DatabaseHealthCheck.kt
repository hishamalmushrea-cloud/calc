package com.daftari.ledger.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * فحص صحة قاعدة البيانات التشغيلية والنسخ الاحتياطية.
 *
 * بعض العلاقات أضيفت تاريخيًا دون Foreign Keys كاملة حتى لا نكسر بيانات المستخدم
 * بترحيل كبير. لذلك نحتفظ هنا بفحوص SQL صريحة تكشف العلاقات اليتيمة والقواعد
 * المحاسبية الأساسية قبل الاستعادة أو من شاشة تشخيص مستقبلية.
 */
object DatabaseHealthCheck {
    data class SqlCheck(
        val code: String,
        val message: String,
        val sql: String,
        val minVersion: Int = 1
    )

    data class Issue(val code: String, val message: String, val count: Long)

    data class Report(val issues: List<Issue>) {
        val ok: Boolean get() = issues.isEmpty()
    }

    fun inspect(db: AppDb): Report {
        val sqlite = db.openHelper.readableDatabase
        val version = sqlite.queryLong("PRAGMA user_version").toInt().takeIf { it > 0 } ?: AppDb.VERSION
        val issues = checks(version).mapNotNull { check ->
            val count = sqlite.queryLong(check.sql)
            if (count > 0) Issue(check.code, check.message, count) else null
        }
        return Report(issues)
    }

    fun checks(version: Int): List<SqlCheck> = buildList {
        // علاقات أساسية لا يجب أن تنكسر.
        add(SqlCheck("orphan_party_shop", "أطراف مرتبطة بمحل غير موجود", "SELECT COUNT(*) FROM parties p WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = p.shopId)"))
        add(SqlCheck("orphan_account_shop", "حسابات مرتبطة بمحل غير موجود", "SELECT COUNT(*) FROM accounts a WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = a.shopId)"))
        add(SqlCheck("orphan_document_shop", "مستندات مرتبطة بمحل غير موجود", "SELECT COUNT(*) FROM documents d WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = d.shopId)"))
        add(SqlCheck("orphan_document_party", "مستندات تشير إلى طرف غير موجود", "SELECT COUNT(*) FROM documents d WHERE d.partyId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM parties p WHERE p.id = d.partyId)"))
        add(SqlCheck("orphan_journal_document", "قيود يومية تشير إلى مستند غير موجود", "SELECT COUNT(*) FROM journal_lines j WHERE NOT EXISTS (SELECT 1 FROM documents d WHERE d.id = j.documentId)"))
        add(SqlCheck("orphan_journal_account", "قيود يومية تشير إلى حساب غير موجود", "SELECT COUNT(*) FROM journal_lines j WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = j.accountId)"))
        add(SqlCheck("orphan_journal_party", "قيود يومية تشير إلى طرف غير موجود", "SELECT COUNT(*) FROM journal_lines j WHERE j.partyId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM parties p WHERE p.id = j.partyId)"))
        add(SqlCheck("orphan_closing_shop", "إغلاقات يومية مرتبطة بمحل غير موجود", "SELECT COUNT(*) FROM daily_closings c WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = c.shopId)"))

        // قواعد محاسبية أساسية.
        add(
            SqlCheck(
                "unbalanced_documents",
                "مستندات غير متوازنة أو بلا قيود فعالة",
                """
                SELECT COUNT(*) FROM (
                    SELECT d.id,
                           COALESCE(SUM(j.debitMinor), 0) AS debit,
                           COALESCE(SUM(j.creditMinor), 0) AS credit
                    FROM documents d
                    LEFT JOIN journal_lines j ON j.documentId = d.id
                    WHERE d.deletedAt IS NULL
                    GROUP BY d.id
                    HAVING debit != credit OR debit <= 0
                ) bad
                """.trimIndent()
            )
        )
        add(SqlCheck("credit_sale_without_customer", "مبيعات آجلة بلا عميل", "SELECT COUNT(*) FROM documents WHERE deletedAt IS NULL AND type = 'SALE' AND paymentMethod = 'CREDIT' AND partyId IS NULL"))
        add(SqlCheck("credit_purchase_without_supplier", "مشتريات آجلة بلا مورد", "SELECT COUNT(*) FROM documents WHERE deletedAt IS NULL AND type = 'PURCHASE' AND paymentMethod = 'CREDIT' AND partyId IS NULL"))
        add(SqlCheck("credit_expense", "مصروفات مسجلة كآجلة بدل شراء آجل", "SELECT COUNT(*) FROM documents WHERE deletedAt IS NULL AND type = 'EXPENSE' AND paymentMethod = 'CREDIT'"))
        add(
            SqlCheck(
                "wrong_party_kind",
                "عمليات مرتبطة بنوع طرف غير صحيح",
                """
                SELECT COUNT(*) FROM documents d
                JOIN parties p ON p.id = d.partyId
                WHERE d.deletedAt IS NULL AND (
                    (d.type IN ('SALE', 'COLLECT') AND p.kind != 'CUSTOMER') OR
                    (d.type IN ('PURCHASE', 'PAY') AND p.kind != 'SUPPLIER')
                )
                """.trimIndent()
            )
        )
        add(
            SqlCheck(
                "party_shop_mismatch",
                "عمليات مرتبطة بطرف من محل آخر",
                """
                SELECT COUNT(*) FROM documents d
                JOIN parties p ON p.id = d.partyId
                WHERE d.partyId IS NOT NULL AND d.shopId != p.shopId
                """.trimIndent()
            )
        )
        add(
            SqlCheck(
                "cached_balance_mismatch",
                "أرصدة أطراف مخزنة لا تطابق القيود",
                """
                SELECT COUNT(*) FROM parties p
                WHERE p.deletedAt IS NULL
                  AND p.cachedBalanceMinor != CASE
                    WHEN p.kind = 'CUSTOMER' THEN COALESCE((
                        SELECT SUM(j.debitMinor - j.creditMinor)
                        FROM journal_lines j
                        JOIN documents d ON d.id = j.documentId
                        WHERE d.deletedAt IS NULL AND j.partyId = p.id
                    ), 0)
                    ELSE 0 - COALESCE((
                        SELECT SUM(j.debitMinor - j.creditMinor)
                        FROM journal_lines j
                        JOIN documents d ON d.id = j.documentId
                        WHERE d.deletedAt IS NULL AND j.partyId = p.id
                    ), 0)
                  END
                """.trimIndent()
            )
        )

        if (version >= 5) {
            add(SqlCheck("orphan_category_shop", "تصنيفات مرتبطة بمحل غير موجود", "SELECT COUNT(*) FROM categories c WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = c.shopId)", minVersion = 5))
            add(SqlCheck("orphan_document_category", "مستندات تشير إلى تصنيف غير موجود", "SELECT COUNT(*) FROM documents d WHERE d.categoryId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = d.categoryId)", minVersion = 5))
        }
        if (version >= 6) {
            add(SqlCheck("orphan_daily_book_shop", "دفاتر يومية مرتبطة بمحل غير موجود", "SELECT COUNT(*) FROM daily_books b WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = b.shopId)", minVersion = 6))
        }
        if (version >= 7) {
            add(SqlCheck("orphan_employee_shop_employee", "ربط موظف بمحل يشير إلى موظف غير موجود", "SELECT COUNT(*) FROM employee_shops es WHERE NOT EXISTS (SELECT 1 FROM employees e WHERE e.id = es.employeeId)", minVersion = 7))
            add(SqlCheck("orphan_employee_shop_branch", "ربط موظف بمحل يشير إلى محل غير موجود", "SELECT COUNT(*) FROM employee_shops es WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = es.shopId)", minVersion = 7))
            add(SqlCheck("orphan_shift_employee", "وردية تشير إلى موظف غير موجود", "SELECT COUNT(*) FROM employee_shifts sh WHERE NOT EXISTS (SELECT 1 FROM employees e WHERE e.id = sh.employeeId)", minVersion = 7))
            add(SqlCheck("orphan_shift_shop", "وردية تشير إلى محل غير موجود", "SELECT COUNT(*) FROM employee_shifts sh WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = sh.shopId)", minVersion = 7))
            add(SqlCheck("orphan_document_employee", "مستندات تشير إلى موظف غير موجود", "SELECT COUNT(*) FROM documents d WHERE d.employeeId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.id = d.employeeId)", minVersion = 7))
            add(SqlCheck("orphan_document_shift", "مستندات تشير إلى وردية غير موجودة", "SELECT COUNT(*) FROM documents d WHERE d.shiftId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM employee_shifts sh WHERE sh.id = d.shiftId)", minVersion = 7))
            add(SqlCheck("orphan_audit_actor", "سجلات تدقيق تشير إلى موظف منفذ غير موجود", "SELECT COUNT(*) FROM audit_logs a WHERE a.actorEmployeeId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.id = a.actorEmployeeId)", minVersion = 7))
        }
        if (version >= 9) {
            add(SqlCheck("orphan_book_person_shop", "أشخاص في دفتر الحسابات مرتبطون بمحل غير موجود", "SELECT COUNT(*) FROM book_persons bp WHERE NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = bp.shopId)", minVersion = 9))
            add(SqlCheck("orphan_book_entry_person", "عمليات دفتر تشير إلى شخص غير موجود", "SELECT COUNT(*) FROM book_entries e WHERE NOT EXISTS (SELECT 1 FROM book_persons p WHERE p.id = e.personId)", minVersion = 9))
            add(SqlCheck("orphan_book_entry_currency", "عمليات دفتر تشير إلى عملة غير موجودة", "SELECT COUNT(*) FROM book_entries e WHERE NOT EXISTS (SELECT 1 FROM currencies c WHERE c.id = e.currencyId)", minVersion = 9))
            add(SqlCheck("book_entry_bad_amount", "عمليات دفتر بمبلغ غير موجب", "SELECT COUNT(*) FROM book_entries WHERE deletedAt IS NULL AND amountMinor <= 0", minVersion = 9))
            add(SqlCheck("book_entry_bad_side", "عمليات دفتر بجانب أو نوع غير معروف", "SELECT COUNT(*) FROM book_entries WHERE deletedAt IS NULL AND (side NOT IN ('LE','DEBT') OR kind NOT IN ('LE','DEBT','SETTLEMENT'))", minVersion = 9))
            add(SqlCheck("no_currencies", "لا توجد عملات لدفتر الحسابات", "SELECT CASE WHEN (SELECT COUNT(*) FROM currencies) = 0 THEN 1 ELSE 0 END", minVersion = 9))
            add(SqlCheck("orphan_book_person_currency", "أشخاص في دفتر الحسابات بعملات غير موجودة", "SELECT COUNT(*) FROM book_persons bp WHERE bp.currencyId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM currencies c WHERE c.id = bp.currencyId)", minVersion = 10))
        }
    }.filter { it.minVersion <= version }

    private fun SupportSQLiteDatabase.queryLong(sql: String): Long =
        query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
}
