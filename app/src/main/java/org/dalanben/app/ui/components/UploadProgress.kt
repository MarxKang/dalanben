package org.dalanben.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 上传进度浮层（覆盖在预览区中心）。
 * @param uploading 是否正在上传
 * @param progress  进度百分比(0~100); 传 -1 表示未知 -> 显示环形不确定进度
 * @param barSize   环形指示器直径 / 线性条宽度
 */
@Composable
fun UploadProgressOverlay(
    uploading: Boolean,
    progress: Float,
    barSize: Dp,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    if (!uploading) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress < 0f) {
            CircularProgressIndicator(Modifier.size(barSize))
            if (label.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LinearProgressIndicator(
                progress = (progress / 100f).coerceIn(0f, 1f),
                modifier = Modifier.width(barSize)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (label.isEmpty()) "${progress.toInt()}%" else "$label ${progress.toInt()}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 上传进度条（行内，用于按钮旁 / 底部栏上方）。
 * @param barWidth 线性条宽度（barSize 为 0 时退化为环形不确定进度，适合窄空间）
 */
@Composable
fun UploadProgressBarRow(
    uploading: Boolean,
    progress: Float,
    barWidth: Dp = 80.dp,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    if (!uploading) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (progress < 0f) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LinearProgressIndicator(
                progress = (progress / 100f).coerceIn(0f, 1f),
                modifier = Modifier.width(barWidth)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (label.isEmpty()) "${progress.toInt()}%" else "$label ${progress.toInt()}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 全宽线性进度条（常用于底部输入栏上方） */
@Composable
fun UploadProgressLine(
    uploading: Boolean,
    progress: Float,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    if (!uploading) return
    if (progress < 0f) LinearProgressIndicator(modifier)
    else LinearProgressIndicator(
        progress = (progress / 100f).coerceIn(0f, 1f),
        modifier = modifier
    )
}
