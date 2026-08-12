package org.dalanben.app.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.dalanben.app.ui.components.TopBar

/**
 * 内置开源代码浏览器（本地化）：读取 assets/opensource 下随 APK 打包的项目源码，
 * 支持文件树浏览与轻量语法高亮，无需联网、不跳转浏览器。
 */
@Composable
fun OpenSourceScreen(navController: NavController) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var current by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        files = collectSourceFiles(context)
        loaded = true
    }
    LaunchedEffect(current) {
        content = current?.let { readAsset(context, it) ?: "(无法读取该文件)" } ?: ""
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            if (current == null) "开源代码" else current!!.substringAfterLast('/'),
            onBack = {
                if (current != null) current = null else navController.popBackStack()
            }
        )
        if (current == null) {
            // ── 文件树 ──
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        if (files.isEmpty()) {
                            val diag = buildDiagnostic(context)
                            Text(
                                "未检测到内置源码（当前 0 个文件）\n\n" + diag +
                                    "\n\n请将以上信息反馈给开发者，或确认 App 已从官方渠道更新到最新版本（设置 → 关于 → 版本号）。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        } else {
                            Text(
                                "本仓库（Android 客户端）源码已内置，共 ${files.size} 个源码文件 · MIT 协议",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        HorizontalDivider()
                    }
                    items(files) { f ->
                        FileRow(path = f, onClick = { current = f })
                    }
                }
            }
        } else {
            // ── 代码内容 ──
            Text(
                highlightCode(content),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun FileRow(path: String, onClick: () -> Unit) {
    val name = path.substringAfterLast('/')
    val dir = path.substringBeforeLast('/', "").removePrefix("opensource/")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (name.endsWith(".kt")) "📘" else if (name.endsWith(".kts")) "📘" else if (name.endsWith(".xml")) "🧩" else if (name.endsWith(".md")) "📖" else "📄", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, fontSize = 13.5.sp)
            if (dir.isNotEmpty()) {
                Text(dir, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

// ───────── 资源读取 ─────────
private fun collectSourceFiles(context: Context): List<String> {
    val assets = context.assets
    val result = mutableListOf<String>()
    fun walk(path: String) {
        val children = assets.list(path) ?: return
        // 关键: AssetManager.list() 对【文件】返回空数组[], 对【目录】返回非空子项数组
        // 因此空数组 = 文件(直接收录), 非空数组 = 目录(递归), null = 路径不存在
        if (children.isEmpty()) { result.add(path); return }
        for (c in children.sorted()) {
            val full = if (path.isEmpty()) c else "$path/$c"
            val sub = assets.list(full)
            if (sub != null && sub.isNotEmpty()) walk(full) else result.add(full)
        }
    }
    walk("opensource")
    return result
}

private fun readAsset(context: Context, path: String): String? = try {
    context.assets.open(path).bufferedReader().use { it.readText() }
} catch (_: Exception) {
    null
}

/** 空态诊断: 输出版本号 + assets 根目录列表, 用于定位"0 文件"根因 */
private fun buildDiagnostic(context: Context): String {
    val sb = StringBuilder()
    try {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        sb.append("App 版本: v").append(pkg.versionName)
            .append(" (code ").append(pkg.versionCode).append(")\n")
    } catch (_: Exception) {
        sb.append("App 版本: 未知\n")
    }
    try {
        val root = context.assets.list("") ?: emptyArray()
        sb.append("assets 根目录 (").append(root.size).append(" 项): ")
            .append(root.take(30).joinToString(", "))
        val probe = context.assets.list("opensource")
        sb.append("\nopensource 探测: ").append(probe?.size ?: "null").append(" 项")
    } catch (e: Exception) {
        sb.append("\nassets 读取异常: ").append(e.toString().take(120))
    }
    return sb.toString()
}

// ───────── 轻量语法高亮（Kotlin/XML/Gradle/Markdown 通用）─────────
private val KW = Regex(
    "\\b(?:package|import|fun|val|var|class|object|interface|data|sealed|enum|if|else|when|for|while|do|return|break|continue|try|catch|finally|throw|null|true|false|this|super|in|is|as|private|public|internal|protected|override|open|abstract|inline|suspend|const|by|companion|init|lateinit|lazy|get|set|typealias|operator|infix|tailrec|external|annotation|crossinline|noinline|reified|it|plugin|dependencies|android|defaultConfig|buildTypes|implementation|apply|val|tasks|register)\\b"
)
private val STR = Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'")
private val NUM = Regex("\\b\\d+(?:\\.\\d+)?\\b")
private val CMT_LINE = Regex("^\\s*(?://.*|#.*|\\*/|\\*.*|<!--.*)")
private val CMT_BLOCK = Regex("/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->")

private fun highlightCode(code: String): AnnotatedString {
    val commentColor = Color(0xFF7A8A99)
    val stringColor = Color(0xFF7EE787)
    val keywordColor = Color(0xFF58A6FF)
    val numColor = Color(0xFFE3B341)

    return buildAnnotatedString {
        val noBlock = code.replace(CMT_BLOCK, "")
        // 逐行处理：整行注释整行灰
        val lines = code.split("\n")
        var lineOffset = 0
        for (line in lines) {
            if (CMT_LINE.containsMatchIn(line)) {
                withStyle(SpanStyle(color = commentColor)) { append(line) }
            } else {
                var i = 0
                for (m in STR.findAll(line)) {
                    if (m.range.first > i) {
                        highlightSegment(this, line.substring(i, m.range.first), noBlock.contains(line.substring(i, m.range.first)), keywordColor, numColor)
                    }
                    withStyle(SpanStyle(color = stringColor)) { append(m.value) }
                    i = m.range.last + 1
                }
                if (i < line.length) highlightSegment(this, line.substring(i), noBlock.contains(line.substring(i)), keywordColor, numColor)
            }
            if (lineOffset < lines.size - 1) append("\n")
            lineOffset++
        }
    }
}

private fun highlightSegment(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    text: String,
    isPlain: Boolean,
    keywordColor: Color,
    numColor: Color
) {
    if (!isPlain) { builder.append(text); return }
    var last = 0
    val all = KW.findAll(text).toList() + NUM.findAll(text).toList()
    val sorted = all.sortedBy { it.range.first }
    for (m in sorted) {
        if (m.range.first < last) continue
        if (m.range.first > last) builder.append(text.substring(last, m.range.first))
        val color = if (KW.matches(m.value)) keywordColor else numColor
        builder.withStyle(SpanStyle(color = color)) { builder.append(m.value) }
        last = m.range.last + 1
    }
    if (last < text.length) builder.append(text.substring(last))
}
