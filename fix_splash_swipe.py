# -*- coding: utf-8 -*-
p = r"C:\Users\chaok\WorkBuddy\2026-07-28-17-31-19\DalanbenApp\app\src\main\java\org\dalanben\app\ui\AppRoot.kt"
s = open(p, encoding="utf-8").read()

# 1. 加 imports
s = s.replace("import androidx.compose.foundation.clickable",
              "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectVerticalDragGestures")
s = s.replace("import androidx.compose.ui.platform.LocalContext",
              "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalDensity")

# 2. 注释更新
old = "/** \u5168\u5c4f\u542f\u52a8\u56fe\uff1a\u5012\u8ba1\u65f6\u53ef\u8df3\u8fc7\uff0c\u70b9\u51fb\u53ef\u8df3\u8f6c\uff08action_type=url\uff09\uff0c\u8d85\u65f6\u81ea\u52a8\u5173\u95ed\u3002\n *  \u80cc\u666f\u7528\u4e3b\u9898\u6d45\u8272\u800c\u975e\u7eaf\u9ed1\uff0c\u907f\u514d\u56fe\u7247\u52a0\u8f7d\u4e2d/\u5931\u8d25\u65f6\u6574\u5c4f\u9ed1\uff1b\u56fe\u7247\u52a0\u8f7d\u5931\u8d25\u7ed9\u63d0\u793a\u4e0e\u8fdb\u5165\u6309\u94ae\u3002 */"
new = "/** \u5168\u5c4f\u542f\u52a8\u56fe\uff1a\u5012\u8ba1\u65f6\u53ef\u8df3\u8fc7\uff0c\u5411\u4e0a\u6ed1\u52a8\u8df3\u8f6c\u94fe\u63a5\uff08action_type=url\uff09\uff0c\u8d85\u65f6\u81ea\u52a8\u5173\u95ed\u3002\n *  \u70b9\u51fb\u4e0d\u518d\u89e6\u53d1\u4efb\u4f55\u52a8\u4f5c\uff08\u907f\u514d\u8bef\u89e6\u8df3\u8fc7\uff09\uff1b\u4e0a\u6ed1\u8d85\u8fc7\u9608\u503c\u624d\u6253\u5f00\u94fe\u63a5\u5e76\u5173\u95ed\u3002\n *  \u80cc\u666f\u7528\u4e3b\u9898\u6d45\u8272\u800c\u975e\u7eaf\u9ed1\uff0c\u907f\u514d\u56fe\u7247\u52a0\u8f7d\u4e2d/\u5931\u8d25\u65f6\u6574\u5c4f\u9ed1\uff1b\u56fe\u7247\u52a0\u8f7d\u5931\u8d25\u7ed9\u63d0\u793a\u4e0e\u8fdb\u5165\u6309\u94ae\u3002 */"
assert old in s
s = s.replace(old, new, 1)

# 3. Box: clickable -> 上滑手势
old_box = """    Box(
        Modifier
            .fillMaxSize()
            .then(modifier)
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                if (splash.action_type == "url" && splash.action_target.isNotBlank()) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(splash.action_target)))
                    } catch (_: Exception) {}
                }
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {"""
new_box = """    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .then(modifier)
            .background(MaterialTheme.colorScheme.surface)
            // 滑动交互：向上滑动超过阈值 → 打开链接并关闭（点击无动作，避免误触）
            .pointerInput(splash.id) {
                var swiped = false
                var acc = 0f
                val threshold = with(density) { 120.dp.toPx() }
                detectVerticalDragGestures(
                    onDragEnd = { acc = 0f },
                    onDragCancel = { acc = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        acc += dragAmount
                        if (!swiped && acc < -threshold) {
                            swiped = true
                            if (splash.action_type == "url" && splash.action_target.isNotBlank()) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(splash.action_target)))
                                } catch (_: Exception) {}
                            }
                            onDismiss()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {"""
assert old_box in s
s = s.replace(old_box, new_box, 1)

# 4. 上滑提示
old_tip = """        // 底部标题
        if (splash.title.isNotBlank()) {"""
new_tip = """        // 上滑提示（仅当配置了跳转链接时显示）
        if (splash.action_type == "url" && splash.action_target.isNotBlank()) {
            Text(
                "上滑查看详情",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        // 底部标题
        if (splash.title.isNotBlank()) {"""
assert old_tip in s
s = s.replace(old_tip, new_tip, 1)

open(p, "w", encoding="utf-8").write(s)
print("OK:", "detectVerticalDragGestures" in s and "\u4e0a\u6ed1\u67e5\u770b\u8be6\u60c5" in s and "swiped" in s and ".clickable {" not in s.split("SplashOverlay")[1][:800])
