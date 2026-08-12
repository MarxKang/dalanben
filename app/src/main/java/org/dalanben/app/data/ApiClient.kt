package org.dalanben.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.Buffer
import okio.ForwardingSink
import okio.buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit

const val BASE_URL = "https://dalanben.org"

/** 构造分享链接: /s/ 前缀页含 OG 元数据, QQ/微信/社媒可展示卡片预览 */
fun shareUrl(type: String, id: Int): String = "$BASE_URL/s/$type/$id"

/** 网页版默认背景图: 用户未自定义背景图时统一使用, 与网页版保持一致 */
const val DEFAULT_BG = "https://dalanben-uqewhjj.cdn.7caiyun.com/file_00000000f51c7207abb8c06bfec2ad74.png"

val gson: Gson = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()

/** 包装类型 */
data class HotSearchData(val list: List<HotSearchItem> = emptyList())
data class TopicListData(val list: List<Topic> = emptyList())
data class BlockListData(val list: List<User> = emptyList())
data class ChatListData(val list: List<ChatSession> = emptyList())
data class DraftListData(val list: List<Draft> = emptyList())
data class TopicDetailData(
    val topic: Topic? = null,
    val list: List<Post> = emptyList(),
    val page: Int = 1
)

/** 启动图（服务端下发，管理员在后台增/改/启停） */
data class SplashData(
    val id: Int = 0,
    val title: String = "",
    val image_url: String = "",
    val action_type: String = "",
    val action_target: String = "",
    val duration: Int = 3
)
data class SplashActiveData(val splash: SplashData? = null)

/** IP 属地（服务端查 apihz 返回，5 小时刷新一次） */
data class IpRegionData(val ip_region: String? = null)

interface ApiService {
    // ───────── Auth ─────────
    @POST("/api/auth/register")
    suspend fun register(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginData>

    @POST("/api/auth/login")
    suspend fun login(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginData>

    @POST("/api/auth/logout")
    suspend fun logout(): ApiResponse<Any>

    @POST("/api/auth/delete_me")
    suspend fun deleteMe(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/auth/me")
    suspend fun me(): ApiResponse<User>

    @POST("/api/auth/password")
    suspend fun changePassword(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/auth/captcha/get")
    suspend fun getCaptcha(): ApiResponse<CaptchaData>

    @POST("/api/auth/send_code")
    suspend fun sendAuthCode(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/auth/reset_password")
    suspend fun resetPassword(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    // ───────── User ─────────
    @POST("/api/user/send_code")
    suspend fun sendUserCode(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/user/verify_phone")
    suspend fun verifyPhone(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/user/change_phone")
    suspend fun changePhone(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    // ───────── User ─────────
    @POST("/api/user/update")
    suspend fun updateProfile(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/user/privacy")
    suspend fun privacy(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/user/follow")
    suspend fun follow(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<FollowResult>

    @GET("/api/user/follow_list")
    suspend fun followList(
        @Query("user_id") userId: Int,
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<FollowListData>

    @POST("/api/user/remove_follower")
    suspend fun removeFollower(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/user/block")
    suspend fun block(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<BlockResult>

    @GET("/api/user/block_list")
    suspend fun blockList(): ApiResponse<BlockListData>

    @GET("/api/user/profile")
    suspend fun profile(@Query("user_id") userId: Int): ApiResponse<User>

    @POST("/api/user/checkin")
    suspend fun checkin(): ApiResponse<CheckinResult>

    @GET("/api/user/points")
    suspend fun points(): ApiResponse<PointsData>

    @GET("/api/user/points/history")
    suspend fun pointsHistory(@Query("page") page: Int = 1, @Query("size") size: Int = 20): ApiResponse<JsonObject>

    @GET("/api/user/points/levels")
    suspend fun pointsLevels(): ApiResponse<JsonObject>

    @POST("/api/user/featured-apply")
    suspend fun featuredApply(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/user/audit-log")
    suspend fun auditLog(): ApiResponse<Any>

    // ───────── Post ─────────
    @POST("/api/post/create")
    suspend fun createPost(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<CreatePostData>

    @POST("/api/post/update")
    suspend fun updatePost(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/post/delete")
    suspend fun deletePost(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/post/feature")
    suspend fun applyFeature(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/post/detail")
    suspend fun postDetail(@Query("post_id") postId: Int): ApiResponse<Post>

    @GET("/api/post/list")
    suspend fun postList(
        @Query("user_id") userId: Int,
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("keyword") keyword: String? = null
    ): ApiResponse<PagedData<Post>>

    @POST("/api/post/browse")
    suspend fun browse(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/post/playback")
    suspend fun playback(@Query("post_id") postId: Int): ApiResponse<Any>

    @POST("/api/post/draft/save")
    suspend fun saveDraft(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<IdResult>

    @GET("/api/post/draft/list")
    suspend fun draftList(): ApiResponse<DraftListData>

    @POST("/api/post/draft/delete")
    suspend fun deleteDraft(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    // ───────── Channel (Feed) ─────────
    @GET("/api/channel/recommend")
    suspend fun recommend(@Query("page") page: Int, @Query("size") size: Int): ApiResponse<PagedData<Post>>

    @GET("/api/channel/featured")
    suspend fun featured(@Query("page") page: Int, @Query("size") size: Int): ApiResponse<PagedData<Post>>

    @GET("/api/channel/latest")
    suspend fun latest(@Query("page") page: Int, @Query("size") size: Int): ApiResponse<PagedData<Post>>

    @GET("/api/channel/hot_search")
    suspend fun hotSearch(): ApiResponse<HotSearchData>

    // ───────── Interact ─────────
    @POST("/api/interact/like")
    suspend fun like(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LikeResult>

    @POST("/api/interact/collect")
    suspend fun collect(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<CollectResult>

    @POST("/api/interact/share")
    suspend fun share(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/interact/dislike")
    suspend fun dislike(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/interact/block_author")
    suspend fun blockAuthor(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    // ───────── Comment ─────────
    @POST("/api/comment/create")
    suspend fun createComment(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<CommentCreateData>

    @POST("/api/comment/like")
    suspend fun likeComment(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LikeResult>

    @POST("/api/comment/delete")
    suspend fun deleteComment(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/comment/list")
    suspend fun commentList(
        @Query("post_id") postId: Int,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<PagedData<Comment>>

    // ───────── Message ─────────
    @POST("/api/msg/send")
    suspend fun sendMsg(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<IdResult>

    @POST("/api/msg/recall")
    suspend fun recallMsg(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/msg/delete_one")
    suspend fun deleteOneMsg(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/msg/clear_chat")
    suspend fun clearChat(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/msg/chat_list")
    suspend fun chatList(): ApiResponse<ChatListData>

    @GET("/api/msg/chat_history")
    suspend fun chatHistory(
        @Query("peer_id") peerId: Int,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<PagedData<Message>>

    @GET("/api/msg/notifications")
    suspend fun notifications(
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<PagedData<Notification>>

    @GET("/api/msg/unread_count")
    suspend fun unreadCount(): ApiResponse<UnreadCount>

    @POST("/api/msg/read_all")
    suspend fun readAll(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    // ───────── Social ─────────
    @GET("/api/social/topic/search")
    suspend fun topicSearch(@Query("kw") kw: String): ApiResponse<TopicListData>

    @POST("/api/social/topic/create")
    suspend fun topicCreate(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<IdResult>

    @GET("/api/social/topic/list")
    suspend fun topicList(@Query("page") page: Int, @Query("size") size: Int): ApiResponse<PagedData<Topic>>

    @GET("/api/social/topic/detail")
    suspend fun topicDetail(
        @Query("topic_id") topicId: Int,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<TopicDetailData>









    @GET("/api/social/search")
    suspend fun search(
        @Query("kw") kw: String,
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<SearchResult>

    @POST("/api/social/report")
    suspend fun report(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/social/appeal")
    suspend fun appeal(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/social/appeal/my")
    suspend fun myAppeal(): ApiResponse<Any>

    @POST("/api/social/feedback")
    suspend fun feedback(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<IdResult>

    @GET("/api/social/feedback")
    suspend fun myFeedback(@Query("page") page: Int, @Query("size") size: Int): ApiResponse<PagedData<Feedback>>

    // ───────── Upload ─────────
    @POST("/api/upload/sign")
    suspend fun uploadSign(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<SignResult>

    @POST("/api/upload/commit")
    suspend fun uploadCommit(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @Multipart
    @POST("/api/upload/local")
    suspend fun uploadLocal(
        @Part file: MultipartBody.Part,
        @Part("biz_type") bizType: RequestBody
    ): ApiResponse<UploadResult>

    // ───────── Admin ─────────
    @GET("/api/admin/stats")
    suspend fun adminStats(): ApiResponse<Any>

    @GET("/api/admin/review/posts")
    suspend fun adminReviewPosts(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<Any>

    @POST("/api/admin/review/post_action")
    suspend fun adminPostAction(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/review/comments")
    suspend fun adminReviewComments(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<Any>

    @POST("/api/admin/review/comment_action")
    suspend fun adminCommentAction(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/review/users")
    suspend fun adminReviewUsers(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<Any>

    @POST("/api/admin/review/user_action")
    suspend fun adminUserAction(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/penalty/all")
    suspend fun adminPenaltyAll(@Query("page") page: Int, @Query("size") size: Int): ApiResponse<Any>

    @POST("/api/admin/penalty")
    suspend fun adminPenalty(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/admin/penalty/remove")
    suspend fun adminPenaltyRemove(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/appeals")
    suspend fun adminAppeals(@Query("status") status: String): ApiResponse<Any>

    @POST("/api/admin/appeal/action")
    suspend fun adminAppealAction(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/feedback")
    suspend fun adminFeedback(@Query("status") status: String): ApiResponse<Any>

    @POST("/api/admin/feedback/reply")
    suspend fun adminFeedbackReply(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/reports")
    suspend fun adminReports(@Query("status") status: String): ApiResponse<Any>

    @POST("/api/admin/report/action")
    suspend fun adminReportAction(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>



    @GET("/api/admin/ai/logs")
    suspend fun adminAiLogs(@Query("page") page: Int, @Query("size") size: Int): ApiResponse<Any>

    @GET("/api/admin/users/list")
    suspend fun adminUserList(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("kw") kw: String
    ): ApiResponse<Any>

    @POST("/api/admin/user/blue_v")
    suspend fun adminBlueV(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @POST("/api/admin/user/title")
    suspend fun adminTitle(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/featured/list")
    suspend fun adminFeaturedList(@Query("status") status: String): ApiResponse<Any>

    @POST("/api/admin/featured/action")
    suspend fun adminFeaturedAction(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/post-featured/list")
    suspend fun adminPostFeatureList(@Query("status") status: String): ApiResponse<Any>

    @POST("/api/admin/post-featured/action")
    suspend fun adminPostFeatureAction(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/config")
    suspend fun adminConfig(): ApiResponse<Any>

    @POST("/api/admin/config")
    suspend fun adminConfigUpdate(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    @GET("/api/admin/user/history")
    suspend fun adminUserHistory(@Query("user_id") userId: Int): ApiResponse<Any>

    // ───────── App 版本 ─────────
    @GET("/api/app/version/latest")
    suspend fun latestAppVersion(): ApiResponse<AppVersionData>

    @GET("/api/app/versions")
    suspend fun appVersions(): ApiResponse<AppVersionListData>

    @POST("/api/admin/app/version")
    suspend fun adminPublishVersion(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Any>

    // ───────── 公告 ─────────
    @GET("/api/announcement/active")
    suspend fun announcementActive(): ApiResponse<AnnouncementData>

    // ───────── 热更新配置 ─────────
    @GET("/api/remote_config/active")
    suspend fun remoteConfigActive(
        @Query("platform") platform: String = "android",
        @Query("version_code") versionCode: Int
    ): ApiResponse<RemoteConfigData>

    // ───────── 启动图 ─────────
    @GET("/api/splash/active")
    suspend fun splashActive(): ApiResponse<SplashActiveData>

    // ───────── IP 属地 ─────────
    @POST("/api/ip/region")
    suspend fun updateIpRegion(): ApiResponse<IpRegionData>
}

// ───────── Session (token + current user) ─────────
private val Context.dataStore by preferencesDataStore(name = "dalanben_session")

object SessionManager {
    private val TOKEN = stringPreferencesKey("token")
    private val USER = stringPreferencesKey("user_json")
    private val ANNOUNCEMENT_SEEN = intPreferencesKey("announcement_seen_id")
    private val VIDEO_PROGRESS = stringPreferencesKey("video_progress")

    suspend fun saveToken(context: Context, token: String) {
        context.dataStore.edit { it[TOKEN] = token }
    }
    suspend fun saveUser(context: Context, user: User) {
        context.dataStore.edit { it[USER] = gson.toJson(user) }
    }
    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }
    fun getToken(context: Context): String? = runBlocking {
        context.dataStore.data.first()[TOKEN]
    }
    fun getUser(context: Context): User? = runBlocking {
        context.dataStore.data.first()[USER]?.let { gson.fromJson(it, User::class.java) }
    }
    fun getAnnouncementSeenId(context: Context): Int = runBlocking {
        context.dataStore.data.first()[ANNOUNCEMENT_SEEN] ?: 0
    }
    suspend fun setAnnouncementSeenId(context: Context, id: Int) {
        context.dataStore.edit { it[ANNOUNCEMENT_SEEN] = id }
    }

    // ───────── 视频断点续播（站内全域, url -> positionMs）─────────
    private fun readProgressMap(context: Context): HashMap<String, Long> {
        val json = runBlocking { context.dataStore.data.first()[VIDEO_PROGRESS] } ?: return HashMap()
        return try {
            gson.fromJson(json, object : TypeToken<HashMap<String, Long>>() {}.type) ?: HashMap()
        } catch (_: Exception) { HashMap() }
    }

    /** 读取某视频的续播位置(ms), 无则 0 */
    fun getVideoProgress(context: Context, url: String): Long =
        readProgressMap(context)[url] ?: 0L

    /** 保存续播位置; posMs<=3000 视为未观看, 自动清除记录 */
    fun saveVideoProgress(context: Context, url: String, posMs: Long) {
        // 注意: 不能在上面的 edit 块内读 data 流(DataStore 单写者会死锁), 先外部读再写入
        val map = readProgressMap(context)
        if (posMs > 3000) map[url] = posMs else map.remove(url)
        while (map.size > 60) map.remove(map.keys.first())
        val json = gson.toJson(map)
        runBlocking { context.dataStore.edit { it[VIDEO_PROGRESS] = json } }
    }

    /** 播放完成/重新观看时清除该视频记录 */
    fun clearVideoProgress(context: Context, url: String) {
        val map = readProgressMap(context)
        if (map.remove(url) != null) {
            val json = gson.toJson(map)
            runBlocking { context.dataStore.edit { it[VIDEO_PROGRESS] = json } }
        }
    }
}

/** 全局会话状态(内存), 启动时从 DataStore 恢复 */
object Session {
    var token: String? = null
    var user: User? = null
    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()
    val isAdmin: Boolean get() = user?.role == "admin" || user?.role == "super_admin"

    fun set(context: Context, token: String, user: User) {
        this.token = token
        this.user = user
        runBlocking {
            SessionManager.saveToken(context, token)
            SessionManager.saveUser(context, user)
        }
    }
    fun clear(context: Context) {
        token = null
        user = null
        runBlocking { SessionManager.clear(context) }
    }
    fun restore(context: Context) {
        token = SessionManager.getToken(context)
        user = SessionManager.getUser(context)
    }
}

object Api {
    lateinit var service: ApiService
        private set
    lateinit var okHttpClient: OkHttpClient
        private set

    fun init(context: Context) {
        val authInterceptor = Interceptor { chain ->
            val orig = chain.request()
            val reqBuilder = orig.newBuilder()
            Session.token?.let { reqBuilder.header("Authorization", "Bearer $it") }
            chain.proceed(reqBuilder.build())
        }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .build()
        okHttpClient = client

        service = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}

suspend fun <T> ApiService.uploadFile(
    bizType: String,
    file: File,
    onProgress: ((Float) -> Unit)? = null
): UploadResult {
    val base = file.asRequestBody("application/octet-stream".toMediaType())
    val reqFile = if (onProgress != null) ProgressRequestBody(base, onProgress) else base
    val part = MultipartBody.Part.createFormData("file", file.name, reqFile)
    val biz = bizType.toRequestBody("text/plain".toMediaType())
    val resp = uploadLocal(part, biz)
    return resp.data ?: throw IllegalStateException(resp.msg ?: "上传失败")
}

/** 带上传进度回调的 RequestBody：writeTo 时统计已写字节, 回调进度(0~100) */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Float) -> Unit
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val counting = object : ForwardingSink(sink) {
            private var sent = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                sent += byteCount
                if (total > 0L) {
                    val p = (sent * 100f / total).coerceIn(0f, 100f)
                    onProgress(p)
                }
            }
        }
        val buffered = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
