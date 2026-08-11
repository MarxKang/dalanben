package org.dalanben.app.ui

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController

/**
 * 统一处理热更新配置的 action_type / action_target。
 * 支持：none / url(外链/H5) / page(原生页) / post / user / topic
 */
fun handleRemoteAction(
    navController: NavController,
    actionType: String?,
    actionTarget: String?,
    defaultTitle: String = ""
) {
    val type = actionType?.lowercase() ?: "none"
    val target = actionTarget ?: ""
    if (type == "none" || target.isBlank()) return

    when (type) {
        "url" -> {
            // 站内 H5 用 WebView 打开；纯外链用系统浏览器
            if (target.startsWith("https://dalanben.org") || target.startsWith("http://dalanben.org")) {
                navController.navigate(Routes.webView(defaultTitle, target))
            } else {
                val ctx = navController.context
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            }
        }
        "page" -> {
            // 原生页路由直接跳转
            navController.navigate(target)
        }
        "post" -> {
            val id = target.toIntOrNull() ?: return
            navController.navigate(Routes.postDetail(id))
        }
        "user" -> {
            val id = target.toIntOrNull() ?: return
            navController.navigate(Routes.profile(id))
        }
        "topic" -> {
            val id = target.toIntOrNull() ?: return
            navController.navigate(Routes.topicDetail(id))
        }
        else -> {
            // 兜底：如果是 http(s) 链接就当 URL 处理
            if (target.startsWith("http://") || target.startsWith("https://")) {
                navController.navigate(Routes.webView(defaultTitle, target))
            }
        }
    }
}
