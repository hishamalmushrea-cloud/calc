package com.daftari.ledger.backup

/**
 * ملف نسخة احتياطية كما تراه سياسة الاحتفاظ — بلا أي اعتماد على Android حتى
 * يمكن اختبار المنطق على JVM بسرعة.
 */
data class BackupFile(val name: String, val lastModified: Long, val size: Long = 0L)

/**
 * سياسة الاحتفاظ بالنسخ: تقرير أي النسخ تُحذف عند الدوران التلقائي.
 *
 * - تُدوَّر النسخ الآلية فقط (`daftari-backup-<timestamp>.db`)؛ أما النسخ المشفّرة
 *   اليدوية (`.enc`) فلا تحذفها هذه السياسة أبدًا.
 * - [keep] تساوي [KEEP_ALL] (صفر) أو أقل تعني «الاحتفاظ بكل النسخ» فلا حذف.
 * - تُرتَّب النسخ من الأحدث إلى الأقدم، ويُحذف ما يتجاوز [keep].
 *
 * المنطق معزول هنا (لا I/O) ليسهل اختباره، بينما يتولى `AutoBackupWorker` الحذف الفعلي.
 */
object BackupRetention {
    const val KEEP_ALL = 0
    private val AUTO_BACKUP = Regex("^daftari-backup-\\d+\\.db$")

    /** هل الاسم نسخة آلية قابلة للتدوير (وليست مشفّرة يدوية ولا نسخة أمان)؟ */
    fun isRotatable(name: String): Boolean = AUTO_BACKUP.matches(name)

    /** النسخ التي يجب حذفها لاحترام سقف [keep]؛ قائمة فارغة عند [KEEP_ALL]. */
    fun selectDeletions(files: List<BackupFile>, keep: Int): List<BackupFile> {
        if (keep <= KEEP_ALL) return emptyList()
        return files.filter { isRotatable(it.name) }
            .sortedByDescending { it.lastModified }
            .drop(keep)
    }
}
