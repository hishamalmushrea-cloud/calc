package com.daftari.ledger.backup

/**
 * قرار «هل تكفي المساحة لنسخة احتياطية آمنة؟» — منطق صرفي (بلا Android) حتى يُختبر
 * على JVM، بينما يقرأ `AutoBackupWorker` المساحة الفعلية عبر `StatFs`.
 *
 * المطلوب: متسع للنسخة نفسها + هامش أمان، لأن عملية النسخ تنشئ ملفًا مؤقتًا ثم
 * تعتمده، ولأن القاعدة قد تنمو أثناء النسخ.
 */
object BackupSpacePolicy {
    /** هامش أمان ثابت (٥٠ ميغابايت) فوق حجم النسخة المتوقع. */
    const val MIN_FREE_BYTES = 50L * 1024 * 1024

    /**
     * يعيد `true` إذا كانت [availableBytes] تكفي لنسخ قاعدة بحجم [dbBytes] بأمان:
     * ضعف حجم القاعدة (ملف مؤقت + نهائي) زائد هامش الأمان.
     */
    fun hasRoom(availableBytes: Long, dbBytes: Long): Boolean {
        val required = dbBytes.coerceAtLeast(0L) * 2 + MIN_FREE_BYTES
        return availableBytes >= required
    }
}
