# قاعدة البيانات (Room / SQLite)

## الجداول
- shops
- parties (customer/supplier) + shopId
- accounts (chart of accounts per shop)
- categories (expense/income)
- payment_methods
- documents (transactions header)
- journal_lines (debit/credit, accountId, partyId nullable)
- fiscal_years
- audit_logs
- settings
- backups_meta
- daily_closings

## مفاتيح
- PK Long autoGenerate
- FK مع ON DELETE RESTRICT للقيود (لا حذف سلسلة يدمّر الأستاذ)
- Soft delete: `deletedAt` nullable
- فهارس: shopId+date, partyId, documentNumber, accountId

## الترحيل
Database version 1. أي تغيير لاحق Migration لا destructive fallback في الإنتاج.

## المال
`amountMinor INTEGER NOT NULL` — Long.
