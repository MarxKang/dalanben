# 大蓝本 Dalanben · Android 客户端

> 一个面向年轻人的「蓝本」社区 App 客户端 —— 发布图文/视频、互动交流、蓝本号名片与作品下载。
> 本仓库仅包含 **Android 客户端** 源码（Kotlin + Jetpack Compose），服务端接口为私有实现，不在本仓库内。

## 🌐 官网 & 下载

- **官方网站**：<https://dalanben.org>
- **App 下载**：<https://dalanben.org/app>

> 大蓝本是一个面向年轻人的「蓝本」社区，欢迎前往官网了解最新动态，或直接下载 App 体验。

## ✨ 功能特性

- **内容流**：图文 / 视频 / 长文三种内容形态，推荐流 + 精选 + 最新
- **发布**：拍照/相册选图、视频拍摄与上传，多图配文
- **互动**：评论、点赞、收藏、关注、分享、搜索
- **蓝本号与名片**：专属蓝本号、个人主页、二维码名片
- **作品水印下载**：图片/视频一键保存到相册，自动打上文字水印（「大蓝本社区」+ 发布者蓝本号，视频由服务端 ffmpeg 烧录）
- **社区规范**：隐私政策 / 用户协议 / 社区规范 / 儿童政策（WebView 加载官方条款）
- **公告系统**：服务端公告按版本/平台定向推送，支持「只弹一次」
- **图形验证码**：动态星空云 GIF 验证码（Coil 支持）
- **二维码能力**：CameraX + ML Kit 扫码
- **远程配置**：服务端下发 UI 配置与版本强制更新

## 🛠 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3（Compose BOM 2024.12） |
| 网络 | Retrofit 2.11 + OkHttp 4.12 + Gson |
| 图片 | Coil 2.7（含 GIF） |
| 视频 | Media3 ExoPlayer 1.4（播放）/ Transformer（转码压缩） |
| 扫码 | CameraX 1.3 + ML Kit Barcode Scanning 17.2 |
| 导航 | Navigation Compose 2.8 |
| 存储 | DataStore Preferences |
| 其他 | ZXing 3.5、Accompanist Permissions 0.36 |

- 最低系统：Android 8.0（minSdk 26）
- 编译 SDK：36；JDK 17

## 📦 构建

1. 环境要求：Android Studio（Ladybug 或更新）、JDK 17、Android SDK 36
2. 打开项目根目录，等待 Gradle 同步完成
3. 直接运行：

```bash
# Debug 包
./gradlew assembleDebug

# Release 包（需先配置签名，见下）
./gradlew assembleRelease
```

> Windows 下如遇防病毒占用 dexBuilder 文件，可在 `gradle.properties` 临时设置 `org.gradle.workers.max=2`。

### 发布签名（Release）

签名信息从**本地** `keystore.properties` 读取（已被 `.gitignore` 排除，不会提交到仓库）：

```bash
cp keystore.properties.example keystore.properties   # 然后填入你自己的签名信息
keytool -genkey -v -keystore dalanben.keystore -alias dalanben \
        -keyalg RSA -keysize 2048 -validity 10000      # 生成你自己的密钥库
```

未配置 `keystore.properties` 时 Release 构建会因空密码失败，属预期行为。

### 后端地址

`app/src/main/java/org/dalanben/app/data/ApiClient.kt` 中 `BASE_URL` 指向生产域名。开源使用请替换为你自己的后端地址（服务端未随本仓库发布）。

## 📄 协议

[MIT](LICENSE) © 2026 MarxKang

## ⚠️ 声明

- 本仓库不含服务端代码、数据库结构及任何线上密钥/签名信息
- 项目中的品牌名称、域名、图标等归原项目所有，请勿用于误导性用途
