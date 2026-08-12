package org.dalanben.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 内置表情包分类（Unicode Emoji，免费开源，系统彩色渲染） */
val EMOJI_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    "常用" to listOf(
        "😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩",
        "😢", "😭", "😡", "🤬", "🥺", "😱", "🤯", "😴", "🤔", "🙄",
        "👍", "👎", "👏", "🙏", "💪", "🤝", "✌️", "🤞", "👌", "🤙",
        "❤️", "💔", "💯", "🔥", "✨", "⭐", "🌟", "💫", "🎉", "🎊",
        "🚀", "💰", "🏆", "🎯", "💡", "🧠", "🎵", "📷", "🍀", "🌈"
    ),
    "笑脸" to listOf(
        "😄", "😃", "😅", "🤗", "😉", "🙂", "😌", "😋", "😛", "😜",
        "🤪", "😝", "🤑", "🤓", "😎", "🤠", "🥳", "🤡", "😺", "😸",
        "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "👻", "💀", "👽",
        "🤖", "🎃", "😈", "👿", "🤥", "😶", "😐", "😑", "😒", "😏",
        "😳", "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩"
    ),
    "手势" to listOf(
        "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞",
        "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✍️", "👏",
        "🙌", "🫶", "🤲", "🤝", "🙏", "💪", "🦾", "👍", "👎", "✊",
        "👊", "🤛", "🤜", "👐", "🤦", "🤷", "🙋", "🙆", "🙅", "💃"
    ),
    "爱心" to listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💕",
        "💞", "💓", "💗", "💖", "💘", "💝", "💟", "❣️", "💔", "❤️‍🔥",
        "❤️‍🩹", "💌", "💋", "👄", "👀", "💍", "🌹", "💐", "🌸", "🌺",
        "🌻", "🌷", "🪷", "🌼", "🍀", "🌿", "☘️", "💐", "🎀", "🎁"
    ),
    "动物" to listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
        "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐔", "🐧",
        "🐦", "🐤", "🦆", "🦅", "🦉", "🦄", "🐝", "🦋", "🐢", "🐍",
        "🦎", "🐙", "🦀", "🐬", "🐳", "🐋", "🦈", "🐊", "🦓", "🦍"
    ),
    "食物" to listOf(
        "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈",
        "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🥦", "🥕",
        "🌽", "🍄", "🥩", "🍗", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮",
        "🍜", "🍣", "🍤", "🍦", "🍰", "🎂", "🍫", "🍿", "☕", "🍺"
    ),
    "物品" to listOf(
        "📱", "💻", "⌨️", "🖥️", "📷", "📸", "🎥", "📺", "📻", "🎙️",
        "🔋", "💡", "🔦", "🕯️", "💰", "💎", "💳", "⌚", "📱", "🔒",
        "🔑", "🔨", "🛠️", "📚", "📖", "✏️", "📝", "📌", "📎", "📏",
        "🎮", "🎲", "🧩", "🎯", "🎸", "🎹", "🥁", "🎤", "🎧", "🚗"
    ),
    "活动" to listOf(
        "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏓", "🏸", "🥊", "⛳",
        "🏂", "🏄", "🚴", "🏋️", "🤸", "⛺", "🏕️", "🗺️", "🎣", "🥾",
        "🚀", "✈️", "🚁", "🚢", "⛵", "🚗", "🚕", "🏍️", "🚲", "🚉",
        "🏠", "🏡", "🏢", "🏔️", "🌋", "🏖️", "🌊", "🌅", "🌄", "🎆"
    ),
)

/** 全部表情平铺（供搜索/兜底） */
val EMOJI_LIST: List<String> = EMOJI_CATEGORIES.flatMap { it.second }

/**
 * 表情面板: 分类 + 网格展示, 点击回调
 * @param onPick 选择表情(回传 emoji 字符)
 * @param onDismiss 关闭面板
 */
@Composable
fun EmojiPanel(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        // 顶栏: 标题 + 分类横滑
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("表情包", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EMOJI_CATEGORIES.forEachIndexed { i, (name, _) ->
                    Text(
                        name,
                        fontSize = 11.sp,
                        color = if (i == category) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (i == category) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (i == category) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { category = i }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                }
            }
            Text("✕", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
        }
        // 表情网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxSize()
        ) {
            items(EMOJI_CATEGORIES[category].second) { e ->
                Text(
                    e,
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onPick(e) },
                    maxLines = 1
                )
            }
        }
    }
}
