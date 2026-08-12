package org.dalanben.app.data

import com.google.gson.annotations.SerializedName

/** 统一响应信封: {code, msg, data} */
data class ApiResponse<T>(
    val code: Int = -1,
    val msg: String? = null,
    val data: T? = null
) {
    val ok: Boolean get() = code == 0
}

/** App 版本信息(后端 app_versions 表, Gson 下划线映射自动生效) */
data class AppVersion(
    val id: Int = 0,
    val versionCode: Int = 0,
    val versionName: String = "",
    val downloadUrl: String = "",
    val changelog: String = "",
    val forceUpdate: Int = 0,
    val createdAt: Long = 0
)

/** 最新版本查询返回: {version: AppVersion?} */
data class AppVersionData(val version: AppVersion? = null)

/** 公告 */
data class Announcement(
    val id: Int = 0,
    val title: String? = null,
    val content: String? = null,
    val images: List<String> = emptyList(),
    @SerializedName("show_once") val showOnce: Int = 1,
    @SerializedName("action_type") val actionType: String? = null,
    @SerializedName("action_target") val actionTarget: String? = null,
    val active: Int = 1,
    val createdAt: Long = 0,
)

/** 公告查询返回: {announcement: Announcement?} */
data class AnnouncementData(val announcement: Announcement? = null)

/** 节日入口 / 快捷入口 */
data class FestiveEntry(
    val id: Int = 0,
    val title: String = "",
    @SerializedName("icon_url") val iconUrl: String = "",
    @SerializedName("bg_color") val bgColor: String = "",
    @SerializedName("text_color") val textColor: String = "",
    @SerializedName("action_type") val actionType: String = "none",
    @SerializedName("action_target") val actionTarget: String = "",
    val location: String = "home_top",
    @SerializedName("sort_order") val sortOrder: Int = 0
)

/** Banner 轮播 */
data class Banner(
    val id: Int = 0,
    val title: String = "",
    @SerializedName("image_url") val imageUrl: String = "",
    @SerializedName("action_type") val actionType: String = "none",
    @SerializedName("action_target") val actionTarget: String = "",
    val location: String = "home_banner",
    @SerializedName("sort_order") val sortOrder: Int = 0
)

/** 主题皮肤 */
data class AppTheme(
    @SerializedName("theme_key") val themeKey: String = "default",
    val name: String = "",
    @SerializedName("primary_color") val primaryColor: String = "",
    @SerializedName("secondary_color") val secondaryColor: String = "",
    @SerializedName("background_color") val backgroundColor: String = "",
    @SerializedName("surface_color") val surfaceColor: String = "",
    @SerializedName("background_image_url") val backgroundImageUrl: String = "",
    @SerializedName("font_family") val fontFamily: String = ""
)

/** 远程配置总包 */
data class RemoteConfig(
    val config: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
    val announcement: Announcement? = null,
    @SerializedName("festive_entries") val festiveEntries: List<FestiveEntry> = emptyList(),
    val banners: List<Banner> = emptyList(),
    val theme: AppTheme? = null,
    @SerializedName("fetched_at") val fetchedAt: Long = 0
)

data class RemoteConfigData(
    val config: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
    val announcement: Announcement? = null,
    @SerializedName("festive_entries") val festiveEntries: List<FestiveEntry> = emptyList(),
    val banners: List<Banner> = emptyList(),
    val theme: AppTheme? = null,
    @SerializedName("fetched_at") val fetchedAt: Long = 0
)

data class CaptchaData(
    val imgurl: String = "",
    val md5key: String = ""
)

/** 分页列表通用包裹 */
data class PagedData<T>(
    val list: List<T> = emptyList(),
    val page: Int = 1
)

/** 关注/粉丝列表（含隐私标记） */
data class FollowListData(
    val list: List<User> = emptyList(),
    val page: Int = 1,
    val private: Boolean = false,
    val privateType: String? = null
)

data class LoginData(
    val token: String? = null,
    @SerializedName("user_id") val userId: Int = 0,
    val nickname: String? = null,
    val blueId: String? = null,
    val role: String? = null,
    val status: String? = null,
    @SerializedName("shareholder_no") val shareholderNo: Int = 0,
    @SerializedName("invite_code") val inviteCode: String? = null,
    @SerializedName("total_partners") val totalPartners: Int = 0,
    @SerializedName("inviter_name") val inviterName: String? = null,
    @SerializedName("inviter_avatar") val inviterAvatar: String? = null
)

data class User(
    val id: Int = 0,
    val blueId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val bgUrl: String? = null,
    val signature: String? = null,
    val gender: Int = 0,
    val birthday: String? = null,
    val region: String? = null,
    val bio: String? = null,
    val ipRegion: String? = null, // IP 属地 (如: 重庆市 / 美国·加利福尼亚州)
    val isBlueV: Int = 0,
    val title: String? = null,
    val role: String? = null,
    val status: String? = null,
    val privacyDm: Int = 0,
    val privacyAt: Int = 0,
    val privacyFollow: Int = 0,
    val privacyFollowing: Int = 0,
    val privacyFollowers: Int = 0,
    val phone: String? = null,
    val phoneVerified: Any? = null,
    val createdAt: Long = 0,
    // 统计/关系(资料页返回)
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
    val likeCount: Int = 0,
    var isFollowed: Boolean = false,
    var isBlocked: Boolean = false,
    val activePenalty: ActivePenalty? = null,
    @SerializedName("shareholder_no") val shareholderNo: Int = 0,
    @SerializedName("invite_code") val inviteCode: String? = null,
    @SerializedName("total_partners") val totalPartners: Int = 0,
    @SerializedName("inviter_name") val inviterName: String? = null,
    @SerializedName("inviter_avatar") val inviterAvatar: String? = null,
    // ── 积分 / 等级 / 签到 (服务端注入) ──
    @SerializedName("level") val level: Int = 0,
    @SerializedName("level_title") val levelTitle: String? = null,
    @SerializedName("points") val points: Int = 0,
    @SerializedName("checkin_streak") val checkinStreak: Int = 0,
    @SerializedName("last_checkin") val lastCheckin: String? = null,
    @SerializedName("total_checkin_days") val totalCheckinDays: Int = 0,
    @SerializedName("invite_earned_points") val inviteEarnedPoints: Int = 0,
    // ── 官方认证头衔 (管理端可设置，用户端显示) ──
    @SerializedName("verify_title") val verifyTitle: String? = null,
    @SerializedName("verify_style") val verifyStyle: String? = null,
    @SerializedName("next_level_points") val nextLevelPoints: Int? = null,
    @SerializedName("points_to_next") val pointsToNext: Int = 0
)

/** 判断手机号是否已验证: 兼容后端返回的多种类型(Gson 把数字反序列化为 Double) */
fun isPhoneVerified(v: Any?): Boolean = when (v) {
    null -> false
    is String -> v.isNotEmpty()
    is Number -> v.toLong() != 0L
    is Boolean -> v
    else -> true
}

data class ActivePenalty(
    val penaltyType: String? = null,
    val reason: String? = null,
    val expireAt: Long = 0
)

data class Post(
    val id: Int = 0,
    val userId: Int = 0,
    val postType: String = "article", // article | image | video
    var status: String? = null, // approved | pending | rejected | taken_down
    val title: String? = null,
    val content: String? = null,
    val coverUrl: String? = null,
    val cover: String? = null,
    val musicUrl: String? = null, // 背景音乐 URL
    var mediaUrls: List<String> = emptyList(),
    val viewCount: Int = 0,
    var likeCount: Int = 0,
    var collectCount: Int = 0,
    var commentCount: Int = 0,
    var shareCount: Int = 0,
    val isFeatured: Int = 0,
    var featuredApplyStatus: String? = null, // 作者本人视角: pending/approved/rejected/null
    val hotScore: Double = 0.0,
    val createdAt: Long = 0,
    // feed 冗余作者字段
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val isBlueV: Int = 0,
    val authorIpRegion: String? = null, // 作者 IP 属地 (feed 平铺字段)
    // 互动态
    var liked: Boolean = false,
    var collected: Boolean = false,
    var followedAuthor: Boolean = false,
    // 详情页扩展
    val author: User? = null,
    val topics: List<Topic> = emptyList(),
    val visibility: Int = 0,
    @SerializedName("allow_download") val allowDownload: Int = 0,
    @SerializedName("watermark") val watermark: Int = 1
)

data class PostDetailWrapper(
    val post: Post? = null
)

data class CreatePostData(
    @SerializedName("post_id") val postId: Int = 0,
    val status: String? = null
)

data class LikeResult(
    val liked: Boolean = false,
    val likeCount: Int = 0
)

data class CollectResult(
    val collected: Boolean = false
)

data class FollowResult(
    val followed: Boolean = false
)

data class BlockResult(
    val blocked: Boolean = false
)

data class Comment(
    val id: Int = 0,
    val postId: Int = 0,
    val userId: Int = 0,
    val parentId: Int = 0,
    val replyToUserId: Int = 0,
    val content: String? = null,
    val imageUrls: List<String> = emptyList(),
    val emojiUrl: String? = null,
    val status: String? = null,
    val aiRiskScore: Int = 0,
    val aiTags: List<String> = emptyList(),
    val createdAt: Long = 0,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val isBlueV: Int = 0,
    val blueId: String? = null,
    val ipRegion: String? = null, // 评论者 IP 属地
    var isLiked: Boolean = false,
    var likeCount: Int = 0,
    // ── 等级头衔 (服务端注入) ──
    @SerializedName("level") val level: Int = 1,
    @SerializedName("level_title") val levelTitle: String? = null,
    // ── 官方认证头衔 ──
    @SerializedName("verify_title") val verifyTitle: String? = null,
    @SerializedName("verify_style") val verifyStyle: String? = null,
    // ── 楼中楼回复(服务端按 parent_id IN (...) 一次性返回,挂在父评论下) ──
    val replies: List<Comment> = emptyList()
)

data class CommentCreateData(
    @SerializedName("comment_id") val commentId: Int = 0,
    val status: String? = null
)

/** 签到返回 */
data class CheckinResult(
    val already: Boolean = false,
    val earned: Int = 0,
    val points: Int = 0,
    @SerializedName("streak") val checkinStreak: Int = 0,
    @SerializedName("total_checkin_days") val totalCheckinDays: Int = 0,
    val level: Int = 0,
    @SerializedName("level_title") val levelTitle: String? = null,
    @SerializedName("next_level_points") val nextLevelPoints: Int? = null,
    @SerializedName("points_to_next") val pointsToNext: Int = 0
)

/** 积分/等级/签到总览 */
data class PointsData(
    val points: Int = 0,
    val level: Int = 0,
    @SerializedName("level_title") val levelTitle: String? = null,
    @SerializedName("next_level_points") val nextLevelPoints: Int? = null,
    @SerializedName("points_to_next") val pointsToNext: Int = 0,
    @SerializedName("checkin_streak") val checkinStreak: Int = 0,
    @SerializedName("checked_today") val checkedToday: Boolean = false,
    @SerializedName("total_checkin_days") val totalCheckinDays: Int = 0,
    @SerializedName("invite_earned_points") val inviteEarnedPoints: Int = 0,
    @SerializedName("invite_count") val inviteCount: Int = 0,
    @SerializedName("invite_code") val inviteCode: String? = null,
    @SerializedName("today_earned") val todayEarned: Int = 0,
    @SerializedName("next_level_title") val nextLevelTitle: String? = null
)

/** 积分变动记录 */
data class PointsHistoryItem(
    val id: Int = 0,
    @SerializedName("points_change") val pointsChange: Int = 0,
    @SerializedName("balance_after") val balanceAfter: Int = 0,
    val reason: String = "",
    @SerializedName("ref_id") val refId: String = "",
    @SerializedName("created_at") val createdAt: Long = 0
)

/** 等级段位配置 */
data class LevelTier(
    val threshold: Int = 0,
    val level: Int = 0,
    val title: String = ""
)

data class ChatPeer(val user: User? = null)
data class ChatSession(
    val peer: User? = null,
    val lastMsg: Message? = null,
    val unread: Int = 0
)

data class Message(
    val id: Int = 0,
    val senderId: Int = 0,
    val receiverId: Int = 0,
    val msgType: String = "text", // text | image | emoji
    val content: String? = null,
    val imageUrl: String? = null,
    val recalled: Int = 0,
    val readed: Int = 0,
    val createdAt: Long = 0
)

/** 站内分享卡片内容(作为私信 msg_type='share' 的 content JSON) */
data class ShareContent(
    val shareType: String = "",
    val targetId: Int = 0,
    val title: String? = null,
    val cover: String? = null,
    val desc: String? = null
)

/** 从私信消息解析分享卡片(非 share 类型或解析失败返回 null) */
fun Message.shareContent(): ShareContent? {
    if (msgType != "share" || content.isNullOrBlank()) return null
    return try { gson.fromJson(content, ShareContent::class.java) } catch (_: Exception) { null }
}

/** 由正文生成分享卡片摘要(去除 HTML 标签 + 截断) */
fun shareSummary(text: String?, max: Int = 60): String? {
    if (text.isNullOrBlank()) return null
    val clean = text.replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (clean.isEmpty()) return null
    return if (clean.length <= max) clean else clean.take(max) + "…"
}

data class Notification(
    val id: Int = 0,
    val userId: Int = 0,
    @SerializedName("notif_type") val notifType: String = "",
    val fromUserId: Int = 0,
    val postId: Int = 0,
    val commentId: Int = 0,
    val content: String? = null,
    val createdAt: Long = 0,
    val readed: Int = 0,
    val nickname: String? = null,
    val avatarUrl: String? = null
)

data class Topic(
    val id: Int = 0,
    val name: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val hotScore: Double = 0.0,
    val postCount: Int = 0,
    val createdAt: Long = 0
)

data class SearchResult(
    val users: List<User> = emptyList(),
    val posts: List<Post> = emptyList(),
    val topics: List<Topic> = emptyList(),
)

data class Draft(
    val id: Int = 0,
    val postType: String = "article",
    val title: String? = null,
    val content: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val coverUrl: String? = null,
    val musicUrl: String? = null,
    val topicIds: List<Int> = emptyList(),
    val topicNames: List<String> = emptyList(),
    val visibility: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

data class UploadResult(
    @SerializedName("file_key") val fileKey: String? = null,
    val url: String? = null,
    val local: Boolean = false,
    @SerializedName("cover_url") val coverUrl: String? = null
)

data class SignResult(
    val cosEnabled: Boolean = false,
    val method: String? = null,
    @SerializedName("upload_url") val uploadUrl: String? = null,
    @SerializedName("user_id") val userId: Int = 0,
    @SerializedName("max_size") val maxSize: Long = 0,
    val useProxy: Boolean = false,
    @SerializedName("file_key_hint") val fileKeyHint: String? = null
)

data class UnreadCount(
    val msg: Int = 0,
    val notif: Int = 0,
    val total: Int = 0
)

data class HotSearchItem(
    val rank: Int = 0,
    val keyword: String? = null,
    val ktype: String? = null, // topic | user
    @SerializedName("hot_score") val hotScore: Double = 0.0,
    @SerializedName("ref_id") val refId: Int = 0
)

/** 通用反馈/申诉/举报返回 */
data class IdResult(
    @SerializedName("feedback_id") val feedbackId: Int = 0,
    @SerializedName("topic_id") val topicId: Int = 0,
    val status: String? = null
)

/** 意见反馈记录（用户视角，含官方回复） */
data class Feedback(
    val id: Int = 0,
    @SerializedName("fb_type") val fbType: String = "",
    val content: String = "",
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerializedName("video_urls") val videoUrls: List<String> = emptyList(),
    val contact: String = "",
    val status: String = "",
    @SerializedName("admin_reply") val adminReply: String? = null,
    @SerializedName("replied_at") val repliedAt: Long? = null,
    @SerializedName("created_at") val createdAt: Long = 0,
)

/** 后台数据(结构不定, 用 Any 兜底) */
data class AdminStats(
    val userCount: Int = 0,
    val postCount: Int = 0,
    val pendingCount: Int = 0
)
