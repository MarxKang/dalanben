package org.dalanben.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 内置表情包（Unicode Emoji，点击即发送/插入） */
val EMOJI_LIST = listOf(
    "😀", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤔",
    "😢", "😭", "😡", "🤬", "🥺", "😱", "🤯", "😴",
    "👍", "👎", "👏", "🙏", "💪", "🤝", "✌️", "🤞",
    "❤️", "💔", "💯", "🔥", "✨", "⭐", "🌟", "💫",
    "🎉", "🎊", "🎂", "🎁", "🌹", "🌻", "🍺", "☕",
    "🐶", "🐱", "🦁", "🐷", "🌈", "⚡", "🎵", "📷",
    "🚀", "💡", "🧠", "🎯", "🏆", "💰", "🕐", "❓"
)

/**
 * 表情面板: 网格展示内置 emoji, 点击回调
 * @param onPick 选择表情(回传 emoji 字符)
 * @param onDismiss 点击面板外/关闭
 */
@Composable
fun EmojiPanel(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("表情包", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Text("✕", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxSize()
        ) {
            items(EMOJI_LIST) { e ->
                Text(
                    e,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(3.dp)
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onPick(e) },
                    maxLines = 1
                )
            }
        }
    }
}
