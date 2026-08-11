# === 大蓝本 ProGuard 规则 ===

# --- 保留 WebView ---
-keep class org.dalanben.android.webview.** { *; }
-keepclassmembers class org.dalanben.android.webview.DalbenWebView$NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# --- 保留 JS 桥接接口 ---
-keepattributes *Annotation*
-keepattributes JavascriptInterface
-keep class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- OkHttp（更新检测） ---
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- AndroidX WebKit ---
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**

# --- Kotlin ---
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# --- Compose ---
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# --- 保留序列化 ---
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --- 保留 R 类 ---
-keep class **.R
-keep class **.R$* { <fields>; }

# --- 压缩优化 ---
-optimizationpasses 5
-allowaccessmodification
-repackageclasses
