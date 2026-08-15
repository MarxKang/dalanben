# -*- coding: utf-8 -*-
import io

# 1. NotificationsScreen.kt: notifLabel 加 feedback_reply
p = "app/src/main/java/org/dalanben/app/ui/screens/NotificationsScreen.kt"
s = io.open(p, encoding="utf-8").read()
old = '    "report_result" -> "\u4e6a\u62a5\u53cd\u9988"\n    else -> "\u901a\u77e5"'
new = '    "report_result" -> "\u4e6a\u62a5\u53cd\u9988"\n    "feedback_reply" -> "\u5b98\u65b9\u56de\u590d\u4e86\u4f60\u7684\u53cd\u9988"\n    else -> "\u901a\u77e5"'
assert old in s, "notifLabel not found"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8").write(s)
print("NotificationsScreen ok")

# 2. QrScanScreen.kt: toast + vibration
p = "app/src/main/java/org/dalanben/app/ui/screens/QrScanScreen.kt"
s = io.open(p, encoding="utf-8").read()
# vibration imports
if "android.os.Vibrator" not in s:
    s = s.replace("import java.util.concurrent.Executors",
                  "import android.os.VibrationEffect\nimport android.os.Vibrator\nimport java.util.concurrent.Executors")
# toast + vibration on scan
old = """                            scanned = true
                                                            onResult(value)"""
new = """                            scanned = true
                                                            try {
                                                                val vib = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                                                                vib?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                                                            } catch (_: Exception) {}
                                                            android.widget.Toast.makeText(ctx, "\u626b\u63cf\u6210\u529f", android.widget.Toast.LENGTH_SHORT).show()
                                                            onResult(value)"""
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8").write(s)
print("QrScanScreen ok")

# 3. AppRoot.kt: 扫码回调 - 兼容纯数字蓝本码
p = "app/src/main/java/org/dalanben/app/ui/AppRoot.kt"
s = io.open(p, encoding="utf-8").read()
old = """                    composable(Routes.QR_SCAN) { QrScanScreen(navController) { result ->
                        val uri = try { android.net.Uri.parse(result) } catch (_: Exception) { null }
                        handleDeepLink(uri)
                        navController.popBackStack()
                    } }"""
new = """                    composable(Routes.QR_SCAN) { QrScanScreen(navController) { result ->
                        // \u9002\u914d\u5404\u7c7b\u4e8c\u7ef4\u7801\uff1aURL\u6df1\u94fe / \u7eaf\u6570\u5b57\u84dd\u672c\u7801ID / \u5fae\u4fe1\u7b49
                        val trimmed = result.trim()
                        when {
                            trimmed.matches(Regex("^\\\\d+$")) && trimmed.toIntOrNull() != null && trimmed.toInt() > 0 -> {
                                navController.navigate(Routes.profile(trimmed.toInt()))
                            }
                            else -> {
                                val uri = try { android.net.Uri.parse(trimmed) } catch (_: Exception) { null }
                                handleDeepLink(uri)
                            }
                        }
                        navController.popBackStack()
                    } }"""
assert old in s, "QR_SCAN callback not found"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8").write(s)
print("AppRoot ok")
