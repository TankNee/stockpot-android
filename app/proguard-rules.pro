# Keep kotlinx.serialization generated serializers for the ledger models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class cn.neeto.stockpot.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class cn.neeto.stockpot.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
