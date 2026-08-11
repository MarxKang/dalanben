package org.dalanben.app.ui.screens

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.dalanben.app.data.Api
import org.dalanben.app.data.DEFAULT_BG
import org.dalanben.app.data.uploadFile
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.components.Avatar
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.ui.components.UploadProgressOverlay
import org.dalanben.app.util.compressBitmapToFile
import org.dalanben.app.util.fullUrl
import java.io.File
import kotlin.math.max
import kotlin.math.min

private val CROP_OUTPUT = 720

@Composable
fun EditProfileScreen(navController: NavController, appVm: AppViewModel) {
    val user by appVm.user.collectAsState()
    var nickname by remember(user) { mutableStateOf(user?.nickname ?: "") }
    var signature by remember(user) { mutableStateOf(user?.signature ?: "") }
    var bio by remember(user) { mutableStateOf(user?.bio ?: "") }
    var gender by remember(user) { mutableStateOf(user?.gender ?: 0) }
    var birthday by remember(user) { mutableStateOf(user?.birthday ?: "") }
    var region by remember(user) { mutableStateOf(user?.region ?: "") }
    var avatarUrl by remember(user) { mutableStateOf(user?.avatarUrl ?: "") }
    var bgUrl by remember(user) { mutableStateOf(user?.bgUrl ?: "") }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    var phaseLabel by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // 选图后待裁剪的原图
    var cropSrc by remember { mutableStateOf<Bitmap?>(null) }
    var bgCropSrc by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) { appVm.loadMe() }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeSampledBitmap(context.contentResolver, uri) }
            if (bmp != null) cropSrc = bmp
            else appVm.showToast("无法读取该图片")
        }
    }

    val bgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeSampledBitmap(context.contentResolver, uri) }
            if (bmp != null) bgCropSrc = bmp
            else appVm.showToast("无法读取该图片")
        }
    }

    fun uploadCropped(bmp: Bitmap) {
        uploading = true
        progress = -1f
        phaseLabel = "压缩中"
        scope.launch {
            try {
                // 裁剪结果压到 600KB 以内再上传
                val result = withContext(Dispatchers.IO) {
                    compressBitmapToFile(context, bmp, "avatar")
                }
                if (result.error != null) { appVm.showToast(result.error); return@launch }
                val file = result.file ?: return@launch
                phaseLabel = "上传中"
                val r = withContext(Dispatchers.IO) {
                    Api.service.uploadFile<Any>("avatar", file) { progress = it }
                }
                r.url?.let { avatarUrl = it; appVm.showToast("头像已上传, 记得保存") }
                file.delete()
            } catch (e: Exception) {
                appVm.showToast("上传失败: ${e.message}")
            } finally {
                uploading = false
                progress = -1f
                phaseLabel = ""
            }
        }
    }

    fun uploadCroppedBg(bmp: Bitmap) {
        uploading = true
        progress = -1f
        phaseLabel = "压缩中"
        scope.launch {
            try {
                // 裁剪结果压到 600KB 以内再上传
                val result = withContext(Dispatchers.IO) {
                    compressBitmapToFile(context, bmp, "bg")
                }
                if (result.error != null) { appVm.showToast(result.error); return@launch }
                val file = result.file ?: return@launch
                phaseLabel = "上传中"
                val r = withContext(Dispatchers.IO) {
                    Api.service.uploadFile<Any>("bg", file) { progress = it }
                }
                r.url?.let { bgUrl = it; appVm.showToast("背景图已上传, 记得保存") }
                file.delete()
            } catch (e: Exception) {
                appVm.showToast("上传失败: ${e.message}")
            } finally {
                uploading = false
                progress = -1f
                phaseLabel = ""
            }
        }
    }

    fun save() {
        if (nickname.isBlank()) { appVm.showToast("昵称不能为空"); return }
        saving = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    withTimeout(30_000L) {
                        Api.service.updateProfile(mapOf(
                            "nickname" to nickname, "signature" to signature, "bio" to bio,
                            "gender" to gender, "birthday" to birthday, "region" to region,
                            "avatar_url" to avatarUrl, "bg_url" to bgUrl
                        ))
                    }
                }
                if (r.ok) {
                    appVm.showToast("保存成功")
                    appVm.loadMe()
                    navController.popBackStack()
                } else appVm.showToast(r.msg ?: "保存失败")
            } catch (_: TimeoutCancellationException) {
                appVm.showToast("审核超时，请稍后重试")
            } catch (_: Exception) { appVm.showToast("网络错误") }
            saving = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("编辑资料", onBack = { navController.popBackStack() }, actions = {
                TextButton(onClick = { save() }, enabled = !saving && !uploading) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("保存中…")
                    } else Text("保存")
                }
            })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 背景图预览 + 选择入口
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { bgPicker.launch("image/*") }
                ) {
                    AsyncImage(
                        model = fullUrl(bgUrl.ifEmpty { DEFAULT_BG }), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                    Text(
                        "点击更换背景图", color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (uploading)                     UploadProgressOverlay(
                        uploading = uploading, progress = progress, barSize = 40.dp,
                        label = phaseLabel,
                        modifier = Modifier.align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                Box {
                    Avatar(avatarUrl, 88) { avatarPicker.launch("image/*") }
                    if (uploading)                     UploadProgressOverlay(
                        uploading = uploading, progress = progress, barSize = 72.dp,
                        label = phaseLabel,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Text("点击头像更换", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(nickname, { nickname = it }, label = { Text("昵称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(signature, { signature = it }, label = { Text("个性签名(100字内)") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(bio, { bio = it }, label = { Text("个人简介(500字内)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("性别: ", fontSize = 14.sp)
                    listOf(0 to "未设置", 1 to "男", 2 to "女").forEach { (v, label) ->
                        FilterChip(selected = gender == v, onClick = { gender = v },
                            label = { Text(label) }, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(birthday, { birthday = it }, label = { Text("生日(如 2000-01-01)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(region, { region = it }, label = { Text("地区") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }

        // 抖音式头像裁剪页（全屏覆盖）
        cropSrc?.let { src ->
            AvatarCropOverlay(
                src = src,
                onCancel = { cropSrc = null },
                onConfirm = { bmp -> cropSrc = null; uploadCropped(bmp) }
            )
        }
        // 抖音式背景图横幅裁剪页（16:9，全屏覆盖）
        bgCropSrc?.let { src ->
            BannerCropOverlay(
                src = src,
                onCancel = { bgCropSrc = null },
                onConfirm = { bmp -> bgCropSrc = null; uploadCroppedBg(bmp) }
            )
        }
    }
}

@Composable
private fun AvatarCropOverlay(
    src: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val densityObj = LocalDensity.current  // Density 对象用于 dp→px 转换
    val density = densityObj.density       // 每 dp 对应的物理像素(px/dp)
    val configuration = LocalConfiguration.current
    // 全部使用物理像素(px)，与手势 offset / Canvas DrawScope / Bitmap 尺寸统一坐标系
    val screenW = configuration.screenWidthDp * density
    val screenH = configuration.screenHeightDp * density
    val boxPx = min(screenW, screenH) * 0.82f
    val boxLeft = (screenW - boxPx) / 2f
    val boxTop = (screenH - boxPx) / 2f

    // 当前源图（旋转时会替换）
    var curSrc by remember(src) { mutableStateOf(src) }
    val minScale = remember(curSrc) { boxPx / min(curSrc.width, curSrc.height).toFloat() }
    var scale by remember(curSrc) { mutableStateOf(minScale) }
    var offset by remember(curSrc) { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(minScale, minScale * 6f)
        scale = next
        offset = Offset(offset.x + pan.x, offset.y + pan.y)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            bitmap = curSrc.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.None,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformableState)
        )

        // 遮罩：仅在选区外暗化（用 Path + EvenOdd 镂空选区），选区用亮色圆环 —— 框选效果
        val r = boxPx / 2f
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(screenW / 2f, screenH / 2f)
            val mask = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
            }
            mask.fillType = PathFillType.EvenOdd
            drawPath(mask, Color.Black.copy(alpha = 0.5f))
            drawCircle(
                color = Color.White,
                radius = r,
                center = c,
                style = Stroke(width = with(densityObj) { 3.dp.toPx() })
            )
        }

        Text(
            "拖动缩放, 选择头像区域",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
        )

        // 底部操作栏
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onCancel) { Text("取消", color = Color.White) }
            TextButton(onClick = { curSrc = rotateBitmap(curSrc, 90) }) { Text("旋转", color = Color.White) }
            Button(onClick = {
                val out = cropToBitmap(curSrc, scale, offset, screenW, screenH, boxPx, boxLeft, boxTop)
                onConfirm(out)
            }) { Text("完成") }
        }
    }
}

/** 按裁剪框从原图采样输出 CROP_OUTPUT×CROP_OUTPUT 方形图 */
private fun cropToBitmap(
    src: Bitmap,
    scale: Float,
    offset: Offset,
    screenW: Float,
    screenH: Float,
    boxPx: Float,
    boxLeft: Float,
    boxTop: Float
): Bitmap {
    val out = Bitmap.createBitmap(CROP_OUTPUT, CROP_OUTPUT, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(out)
    c.drawColor(AndroidColor.WHITE)
    val m = Matrix()
    // 原图像素坐标 -> 以图片中心为原点的坐标系
    m.postTranslate(-src.width / 2f, -src.height / 2f)
    // 缩放
    m.postScale(scale, scale)
    // 平移到屏幕上的图片中心
    m.postTranslate(screenW / 2f + offset.x, screenH / 2f + offset.y)
    // 把屏幕裁剪框映射到输出画布 (0..CROP_OUTPUT)
    // 注意顺序：先把裁剪框原点平移到 (0,0)，再整体缩放到输出尺寸；
    // 若先 scale 后 translate，平移量不会被缩放因子乘，导致裁切区域整体偏移（与预览不一致）。
    m.postTranslate(-boxLeft, -boxTop)
    m.postScale(CROP_OUTPUT / boxPx, CROP_OUTPUT / boxPx)
    val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    c.drawBitmap(src, m, paint)
    return out
}

private fun rotateBitmap(src: Bitmap, deg: Int): Bitmap {
    val m = Matrix().apply { postRotate(deg.toFloat()) }
    val r = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    if (r != src) src.recycle()
    return r
}

/** 解码并采样，限制最长边避免 OOM，自动处理 Exif 方向 */
private fun decodeSampledBitmap(resolver: ContentResolver, uri: Uri): Bitmap? {
    return try {
        val src = ImageDecoder.createSource(resolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val (w, h) = info.size.width to info.size.height
            val sample = max(1, max(w, h) / 1600)
            decoder.setTargetSampleSize(sample)
            decoder.isMutableRequired = true
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * 抖音式背景图横幅裁剪页：矩形(16:9)裁剪框，图片可双指缩放/拖动/旋转。
 */
@Composable
private fun BannerCropOverlay(
    src: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // 全部使用物理像素(px)，与手势 offset / Canvas DrawScope / Bitmap 尺寸统一坐标系
    val screenW = configuration.screenWidthDp * density.density
    val screenH = configuration.screenHeightDp * density.density
    var boxW = screenW * 0.9f
    var boxH = boxW * 9f / 16f
    if (boxH > screenH * 0.6f) {
        boxH = screenH * 0.6f
        boxW = boxH * 16f / 9f
    }
    val boxLeft = (screenW - boxW) / 2f
    val boxTop = (screenH - boxH) / 2f

    var curSrc by remember(src) { mutableStateOf(src) }
    val minScale = remember(curSrc) { max(boxW / curSrc.width, boxH / curSrc.height) }
    var scale by remember(curSrc) { mutableStateOf(minScale) }
    var offset by remember(curSrc) { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(minScale, minScale * 6f)
        scale = next
        offset = Offset(offset.x + pan.x, offset.y + pan.y)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = curSrc.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.None,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformableState)
        )

        // 遮罩：仅在选区外暗化（用 Path + EvenOdd 镂空选区），选区用亮色边框 —— 框选效果
        Canvas(Modifier.fillMaxSize()) {
            val mask = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                addRect(Rect(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH))
            }
            mask.fillType = PathFillType.EvenOdd
            drawPath(mask, Color.Black.copy(alpha = 0.5f))
            drawRect(
                color = Color.White,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                style = Stroke(width = with(density) { 3.dp.toPx() })
            )
        }

        Text(
            "拖动缩放, 选择背景图区域",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
        )

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onCancel) { Text("取消", color = Color.White) }
            TextButton(onClick = { curSrc = rotateBitmap(curSrc, 90) }) { Text("旋转", color = Color.White) }
            Button(onClick = {
                val outH = (1080f * boxH / boxW).toInt()
                val out = cropToBitmapRect(curSrc, scale, offset, screenW, screenH, boxW, boxH, boxLeft, boxTop, 1080, outH)
                onConfirm(out)
            }) { Text("完成") }
        }
    }
}

/** 按矩形裁剪框从原图采样输出 outW×outH 横幅图 */
private fun cropToBitmapRect(
    src: Bitmap,
    scale: Float,
    offset: Offset,
    screenW: Float,
    screenH: Float,
    boxW: Float,
    boxH: Float,
    boxLeft: Float,
    boxTop: Float,
    outW: Int,
    outH: Int
): Bitmap {
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(out)
    c.drawColor(AndroidColor.WHITE)
    val m = Matrix()
    m.postTranslate(-src.width / 2f, -src.height / 2f)
    m.postScale(scale, scale)
    m.postTranslate(screenW / 2f + offset.x, screenH / 2f + offset.y)
    m.postTranslate(-boxLeft, -boxTop)
    m.postScale(outW / boxW, outH / boxH)
    val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    c.drawBitmap(src, m, paint)
    return out
}
