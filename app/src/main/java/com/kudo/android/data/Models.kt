package com.kudo.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.Instant
import java.time.temporal.ChronoUnit

data class Member(
    val id: Int, val username: String, val name: String, val avatar: String,
    val bio: String = "", val trustLevel: Int = 0, val unifiedNew: Boolean = false,
    val animatedAvatar: String = "", val flair: AvatarFlair? = null, val status: UserStatus? = null,
    val avatarDetailsLoaded: Boolean = false,
    val canFollow: Boolean = false, val isFollowed: Boolean = false,
    val canIgnoreUsers: Boolean = false, val ignoredUsers: Set<String> = emptySet(),
    val profileBackground: String = "", val cardBackground: String = "",
) {
    val displayName get() = name.ifBlank { username }
    val isSiteOwner get() = username.equals("neo", ignoreCase = true)
}

data class AvatarFlair(val name: String, val source: String, val background: String = "", val foreground: String = "")
data class UserStatus(val emoji: String, val description: String, val endsAt: Instant? = null) {
    fun isActive(now: Instant = Instant.now()): Boolean = endsAt?.isAfter(now) != false
}
data class Category(val id: Int, val name: String, val slug: String, val color: String, val description: String, val count: Int, val icon: String = "")
data class TopicTag(val name: String, val slug: String = "", val icon: String = "", val color: String = "")
data class Topic(
    val id: Int, val title: String, val excerpt: String, val author: Member?, val categoryId: Int,
    val tags: List<String>, val replies: Int, val views: Int, val likes: Int, val image: String,
    val bumpedAt: String, val pinned: Boolean = false, val closed: Boolean = false,
    val tagDetails: List<TopicTag> = tags.map { TopicTag(it) },
)
data class TopicPage(val topics: List<Topic>, val next: String?)

enum class FeedFilter(val value: String, val label: String, val personal: Boolean = false) {
    UNREAD("unread", "未读", true), LATEST("latest", "最新"), NEW("new", "新", true),
    HOT("hot", "热门"), TOP("top", "排行榜"), POSTED("posted", "我的帖子", true),
    READ("read", "已读", true), BOOKMARKS("bookmarks", "书签", true);

    companion object {
        fun available(loggedIn: Boolean, inCategory: Boolean = false): List<FeedFilter> = entries.filter {
            (loggedIn || !it.personal) && (!inCategory || it !in listOf(POSTED, READ, BOOKMARKS))
        }
    }
}
data class Post(
    val id: Int, val number: Int, val author: Member, val cooked: String, val createdAt: String,
    val likes: Int, val liked: Boolean, val bookmarkId: Int?, val replyTo: Int?, val canLike: Boolean,
    val reactions: List<PostReaction> = emptyList(), val currentReaction: UserReaction? = null,
    val reactionCount: Int = likes, val reactionsAvailable: Boolean = false, val canUndoLike: Boolean = true,
    val updatedAt: String = createdAt, val version: Int = 1,
    val canViewEditHistory: Boolean = false, val availableFlags: Set<Int> = emptySet(),
    val signature: String = "", val replyCount: Int = 0,
) {
    val activeReaction: String? get() = currentReaction?.id ?: "heart".takeIf { liked }
    val canReact: Boolean get() = if (activeReaction != null) currentReaction?.canUndo ?: canUndoLike else canLike
    val edited: Boolean get() = version > 1
    val hasEditHistory: Boolean get() = edited && canViewEditHistory
}
data class PostReaction(val id: String, val count: Int)
data class UserReaction(val id: String, val canUndo: Boolean)
data class TopicDetail(
    val id: Int, val title: String, val categoryId: Int, val posts: List<Post>, val stream: List<Int>, val closed: Boolean,
    val author: Member? = posts.firstOrNull { it.number == 1 }?.author,
    val createdAt: String = posts.firstOrNull { it.number == 1 }?.createdAt.orEmpty(),
    val validReactions: List<String> = emptyList(), val pinnedOriginal: Post? = null,
    val tags: List<TopicTag> = emptyList(),
) {
    val originalPost: Post? get() = posts.firstOrNull { it.number == 1 } ?: pinnedOriginal
}
data class Notice(val id: Int, val title: String, val description: String, val topicId: Int?, val postNumber: Int?, val read: Boolean, val time: String)
data class SavedTopic(val id: Int, val topicId: Int, val title: String, val excerpt: String, val postNumber: Int?)
data class PublishResult(val topicId: Int?, val queued: Boolean)

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
internal fun JSONArray?.topicTags(): List<TopicTag> = if (this == null) emptyList() else (0 until length()).mapNotNull {
    when (val value = opt(it)) {
        is String -> value.trim().takeIf(String::isNotEmpty)?.let { TopicTag(it) }
        is JSONObject -> value.stringField("name").takeIf(String::isNotEmpty)?.let {
            TopicTag(it, value.stringField("slug"), value.stringField("icon"), value.stringField("color"))
        }
        else -> null
    }
}.distinctBy { it.name }
internal fun JSONArray?.tagNames(): List<String> = topicTags().map { it.name }
internal fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
internal fun JSONObject.stringField(key: String): String = (opt(key) as? String).orEmpty().trim()
internal fun JSONObject.positiveInt(key: String): Int? = optInt(key).takeIf { it > 0 }
fun plainText(html: String): String = Jsoup.parseBodyFragment(html).text()

object DiscourseParser {
    fun member(json: JSONObject, fromPost: Boolean = false, avatarDetailsLoaded: Boolean = false): Member = Member(
        json.optInt(if (fromPost) "user_id" else "id"), json.text("username"), json.text("name"), json.text("avatar_template"),
        plainText(json.text("bio_cooked")), json.optInt("trust_level"), json.optBoolean("unified_new_enabled"),
        json.stringField("animated_avatar"),
        json.stringField("flair_url").takeIf(String::isNotEmpty)?.let {
            AvatarFlair(json.stringField("flair_name"), it, json.stringField("flair_bg_color"), json.stringField("flair_color"))
        },
        userStatus(json.optJSONObject("status") ?: json.optJSONObject("user_status")),
        avatarDetailsLoaded || json.has("status") || json.has("user_status"),
        json.optBoolean("can_follow"), json.optBoolean("is_followed"),
        json.optBoolean("can_ignore_users"), (json.optJSONArray("ignored_users") ?: json.optJSONArray("ignored_usernames")).strings().toSet(),
        json.stringField("profile_background_upload_url"), json.stringField("card_background_upload_url"),
    )

    private fun userStatus(json: JSONObject?): UserStatus? {
        json ?: return null
        val emoji = json.stringField("emoji").takeIf(String::isNotEmpty) ?: return null
        if (!json.isNull("ends_at") && json.opt("ends_at") !is String) return null
        val end = json.stringField("ends_at")
        val endsAt = if (end.isEmpty()) null else { runCatching { Instant.parse(end) }.getOrNull() ?: return null }
        return UserStatus(emoji, json.stringField("description"), endsAt)
    }

    fun emojiUrls(json: JSONObject): Map<String, String> = json.keys().asSequence()
        .flatMap { json.optJSONArray(it).objects().asSequence() }
        .mapNotNull { emoji ->
            val name = emoji.stringField("name")
            val url = emoji.stringField("url")
            if (name.isEmpty() || url.isEmpty()) null else name to url
        }.toMap()

    fun categories(json: JSONObject): List<Category> =
        (json.optJSONArray("categories") ?: json.optJSONObject("category_list")?.optJSONArray("categories")).objects().map {
            Category(it.getInt("id"), it.text("name"), it.text("slug"), it.text("color"),
                plainText(it.text("description_text").ifBlank { it.text("description") }), it.optInt("topic_count"),
                (it.opt("icon") as? String).orEmpty().trim())
        }

    fun topicPage(json: JSONObject): TopicPage {
        val users = json.optJSONArray("users").objects().map { member(it) }.associateBy { it.id }
        val list = json.optJSONObject("topic_list") ?: error("服务器未返回话题列表")
        val topics = list.optJSONArray("topics").objects().map { item ->
            val posters = item.optJSONArray("posters").objects()
            val author = posters.firstOrNull { it.text("description").contains("Original Poster", true) }
                ?: posters.firstOrNull()
            Topic(item.getInt("id"), item.text("title"), plainText(item.text("excerpt")), users[author?.optInt("user_id")],
                item.optInt("category_id"), item.optJSONArray("tags").tagNames(),
                (item.optInt("posts_count", 1) - 1).coerceAtLeast(0), item.optInt("views"), item.optInt("like_count"),
                item.text("image_url"), item.text("bumped_at"), item.optBoolean("pinned"), item.optBoolean("closed"),
                tagDetails = item.optJSONArray("tags").topicTags())
        }
        return TopicPage(topics, list.text("more_topics_url").takeIf(String::isNotBlank))
    }

    fun post(json: JSONObject): Post {
        val action = json.optJSONArray("actions_summary").objects().firstOrNull { it.optInt("id") == 2 }
        val bookmarked = json.optJSONObject("bookmarks")
        val current = json.optJSONObject("current_user_reaction")?.takeIf { it.stringField("type") == "emoji" }
            ?.let { item -> item.stringField("id").takeIf(String::isNotEmpty)?.let { UserReaction(it, item.optBoolean("can_undo")) } }
        val reactions = json.optJSONArray("reactions").objects().mapNotNull {
            val id = it.stringField("id")
            if (id.isEmpty() || it.stringField("type") != "emoji" || it.optInt("count") <= 0) null else PostReaction(id, it.getInt("count"))
        }
        val liked = if (json.has("current_user_reaction")) current?.id == "heart" else action?.optBoolean("acted") ?: false
        return Post(json.getInt("id"), json.optInt("post_number"), member(json, fromPost = true, avatarDetailsLoaded = true), json.text("cooked"), json.text("created_at"),
            action?.optInt("count")?.coerceAtLeast(0) ?: 0, liked,
            json.positiveInt("bookmark_id") ?: bookmarked?.positiveInt("id")
                ?: json.optJSONArray("bookmarks").objects().firstOrNull()?.positiveInt("id"),
            json.positiveInt("reply_to_post_number"), action?.optBoolean("can_act") ?: false,
            reactions, current,
            json.optInt("reaction_users_count", if (json.has("reactions")) reactions.sumOf { it.count } else action?.optInt("count") ?: 0).coerceAtLeast(0),
            json.has("reactions") || json.has("current_user_reaction"), action?.optBoolean("can_undo") ?: false,
            updatedAt = json.stringField("updated_at").ifBlank { json.text("created_at") }, version = json.positiveInt("version") ?: 1,
            canViewEditHistory = json.optBoolean("can_view_edit_history"),
            availableFlags = json.optJSONArray("actions_summary").objects().filter { it.optBoolean("can_act") && it.optInt("id") != 2 }.mapNotNull { it.positiveInt("id") }.toSet(),
            signature = json.stringField("user_signature"), replyCount = json.optInt("reply_count").coerceAtLeast(0))
    }

    fun detail(json: JSONObject): TopicDetail {
        val stream = json.getJSONObject("post_stream")
        val ids = stream.optJSONArray("stream")
        val posts = stream.optJSONArray("posts").objects().map(::post)
        val original = posts.firstOrNull { it.number == 1 }
        return TopicDetail(json.getInt("id"), json.text("title"), json.optInt("category_id"),
            posts, if (ids == null) emptyList() else (0 until ids.length()).map { ids.getInt(it) }, json.optBoolean("closed"),
            original?.author ?: json.optJSONObject("details")?.optJSONObject("created_by")?.let { member(it) },
            json.text("created_at").ifBlank { original?.createdAt.orEmpty() },
            json.optJSONArray("valid_reactions").strings().filter { it.matches(Regex("[a-zA-Z0-9_+:-]+")) }.distinct(),
            tags = json.optJSONArray("tags").topicTags())
    }

    fun notices(json: JSONObject): List<Notice> = json.optJSONArray("notifications").objects().map { item ->
        val data = item.optJSONObject("data") ?: JSONObject()
        val kind = when (item.optInt("notification_type")) { 1 -> "提到了你"; 2 -> "回复了你"; 3 -> "引用了你"; 5 -> "赞了你的回复"; 6 -> "发来私信"; 9 -> "发布了新话题"; else -> "有新的动态" }
        Notice(item.getInt("id"), plainText(data.text("title").ifBlank { data.text("topic_title") }).ifBlank { "社区通知" },
            "${data.text("display_username").ifBlank { data.text("original_username") }} $kind".trim(),
            item.positiveInt("topic_id"), item.positiveInt("post_number"), item.optBoolean("read"), item.text("created_at"))
    }
}

fun relativeTime(value: String): String = runCatching {
    val instant = Instant.parse(value)
    val minutes = ChronoUnit.MINUTES.between(instant, Instant.now()).coerceAtLeast(0)
    when { minutes < 1 -> "刚刚"; minutes < 60 -> "${minutes}分钟前"; minutes < 1440 -> "${minutes / 60}小时前"; minutes < 43200 -> "${minutes / 1440}天前"; else -> value.take(10) }
}.getOrDefault("")

fun compactNumber(number: Int): String = if (number >= 10000) String.format(java.util.Locale.ROOT, "%.1f万", number / 10000f) else number.toString()
