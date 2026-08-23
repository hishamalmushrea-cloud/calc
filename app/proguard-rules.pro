# احتفظ ببيانات التواقيع والتعليقات التي يعتمد عليها Gson وRoom.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Gson: الحقول المعلّمة وأصناف TypeToken يجب أن تبقى قابلة للانعكاس بعد R8.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowoptimization,allowobfuscation class com.google.gson.reflect.TypeToken
-keep,allowoptimization,allowobfuscation class * extends com.google.gson.reflect.TypeToken

# نماذج أرشيف النسخ الاحتياطي تُقرأ وتُكتب بأسماء حقولها عبر Gson.
# لا نسمح لـ R8 بتغيير هذه الأسماء حتى تبقى ملفات .dfb متوافقة بين الإصدارات.
-keep class com.daftari.ledger.backup.BackupManifest { *; }
-keep class com.daftari.ledger.backup.BackupAttachment { *; }
-keep class com.daftari.ledger.backup.RemoteBackup { *; }

# Room يولّد معظم القواعد تلقائيًا؛ هذه القواعد تحمي تعريف قاعدة البيانات
# والكيانات إذا استُخدمت لاحقًا عبر الانعكاس/التصدير من دون تعطيل تحسين R8.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class com.daftari.ledger.data.** { *; }
-dontwarn org.jetbrains.annotations.**
