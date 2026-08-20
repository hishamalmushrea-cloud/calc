# النسخ الاحتياطي

صيغة الملف: `daftari-backup-v1.json`
الحقول: schemaVersion, appVersion, exportedAt, shops, parties, accounts, documents, lines, settings.

الاستعادة:
1. التحقق من JSON والمخطط.
2. نسخ احتياطي تلقائي للقاعدة الحالية.
3. معاملة واحدة للكتابة.
4. فشل = rollback كامل.

النسخ التلقائي: WorkManager يومي/أسبوعي، الاحتفاظ بـ N ملفات في مجلد التطبيق.
