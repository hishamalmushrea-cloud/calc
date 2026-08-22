package com.daftari.ledger.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * فهرس FTS4 منفصل للبحث في الحسابات. أبقيناه خارج مخطط Room المعلن حتى لا
 * تصبح جداول البحث الافتراضية جزءًا من النسخ المنطقية أو علاقات الكيانات.
 * المزامنة تتم بمحفزات SQLite، لذلك لا توجد كتابة مزدوجة في المستودع.
 */
object PartySearchIndex {
    private const val TABLE = "parties_fts"

    fun ensure(db: SupportSQLiteDatabase) {
        val existed = db.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(TABLE)
        ).use { it.moveToFirst() }

        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS parties_fts
            USING fts4(partyId, name, phone, address, notes)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS parties_fts_ai AFTER INSERT ON parties BEGIN
                INSERT INTO parties_fts(partyId, name, phone, address, notes)
                VALUES (new.id, new.name, new.phone, new.address, new.notes);
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS parties_fts_ad AFTER DELETE ON parties BEGIN
                DELETE FROM parties_fts WHERE partyId = old.id;
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS parties_fts_au AFTER UPDATE ON parties BEGIN
                DELETE FROM parties_fts WHERE partyId = old.id;
                INSERT INTO parties_fts(partyId, name, phone, address, notes)
                VALUES (new.id, new.name, new.phone, new.address, new.notes);
            END
            """.trimIndent()
        )

        if (!existed) {
            db.execSQL(
                """
                INSERT INTO parties_fts(partyId, name, phone, address, notes)
                SELECT id, name, phone, address, notes FROM parties
                """.trimIndent()
            )
        }
    }
}
