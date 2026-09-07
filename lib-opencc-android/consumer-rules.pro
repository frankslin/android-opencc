# Shipped inside the AAR and applied to every app that consumes it.
#
# libChineseConverter.so binds its entry point by the mangled Java name
# Java_com_zqc_opencc_android_lib_ChineseConverter_convert, so neither the
# class nor its native method may be renamed or stripped by R8 / ProGuard.
# AGP's default rules keep native methods as well, but an app that ships its
# own rule set without them would otherwise get UnsatisfiedLinkError at the
# first conversion.
-keepclasseswithmembernames class com.zqc.opencc.android.lib.ChineseConverter {
    native <methods>;
}
