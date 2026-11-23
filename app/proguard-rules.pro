# kotlinx.serialization：账本模型的序列化器必须留名字和字段，
# 否则 R8 之后读不出 ledger.json —— 这是记账 App 最不能坏的东西。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# 生成的 $$serializer 类整体保留（含描述符里的字段名）
-keep,includedescriptorclasses class cn.neeto.stockpot.**$$serializer { *; }

# data class 的字段名就是 JSON 的 key
-keepclassmembers class cn.neeto.stockpot.core.model.** {
    <fields>;
}

-keepclassmembers class cn.neeto.stockpot.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class cn.neeto.stockpot.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
