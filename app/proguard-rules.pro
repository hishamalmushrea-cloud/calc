# احتفظ ببيانات التواقيع والتعليقات التي يعتمد عليها Gson وRoom.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Gson: الحقول المعلّمة وأصناف TypeToken يجب أن تبقى قابلة للانعكاس بعد R8.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowoptimization,allowobfuscation class com.google.gson.reflect.TypeToken
-keep,allowoptimization,allowobfuscation class * extends com.google.gson.reflect.TypeToken

# Room يولّد معظم القواعد تلقائيًا؛ هذه القواعد تحمي تعريف قاعدة البيانات
# والكيانات إذا استُخدمت لاحقًا عبر الانعكاس/التصدير من دون تعطيل تحسين R8.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class com.daftari.ledger.data.** { *; }
-dontwarn org.jetbrains.annotations.**
