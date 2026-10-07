package com.kudo.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.Instant
import java.time.temporal.ChronoUnit

data class Member(val id: Int, val username: String, val name: String, val avatar: String, val bio: String = "", val trustLevel: Int = 0) {
    val displayName get() = name.ifBlank { username }
}
data class Category(val id: Int, val name: String, val slug: String, val color: String, val description: String, val count: Int)
data class Topic(
    val id: Int, val title: String, val excerpt: String, val author: Member?, val categoryId: Int,
    val tags: List<String>, val replies: Int, val views: Int, val likes: Int, val image: String,
    val bumpedAt: String, val pinned: Boolean = false, val closed: Boolean = false,
)
data class TopicPage(val topics: List<Topic>, val next: String?)
data class Post(
    val id: Int, val number: Int, val author: Member, val cooked: String, val createdAt: String,
    val likes: Int, val liked: Boolean, val bookmarkId: Int?, val replyTo: Int?, val canLike: Boolean,
)
data class TopicDetail(val id: Int, val title: String, val categoryId: Int, val posts: List<Post>, val stream: List<Int>, val closed: Boolean)
data class Notice(val id: Int, val title: String, val description: String, val topicId: Int?, val postNumber: Int?, val read: Boolean, val time: String)
data class SavedTopic(val id: Int, val topicId: Int, val title: String, val excerpt: String, val postNumber: Int?)
data class PublishResult(val topicId: Int?, val queued: Boolean)

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
internal fun JSONArray?.tagNames(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull {
    when (val value = opt(it)) {
        is String -> value
        is JSONObject -> value.text("name")
        else -> null
    }?.takeIf(String::isNotBlank)
}.distinct()
internal fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
internal fun JSONObject.positiveInt(key: String): Int? = optInt(key).takeIf { it > 0 }
fun plainText(html: String): String = Jsoup.parseBodyFragment(html).text()

object DiscourseParser {
    fun member(json: JSONObject): Member = Member(
        json.optInt("id"), json.text("username"), json.text("name"), json.text("avatar_template"),
        plainText(json.text("bio_cooked")), json.optInt("trust_level"),
    )

    fun categories(json: JSONObject): List<Category> =
        (json.optJSONArray("categories") ?: json.optJSONObject("category_list")?.optJSONArray("categories")).objects().map {
            Category(it.getInt("id"), it.text("name"), it.text("slug"), it.text("color"),
                plainText(it.text("description_text").ifBlank { it.text("description") }), it.optInt("topic_count"))
        }

    fun topicPage(json: JSONObject): TopicPage {
        val users = json.optJSONArray("users").objects().map(::member).associateBy { it.id }
        val list = json.optJSONObject("topic_list") ?: error("服务器未返回话题列表")
        val topics = list.optJSONArray("topics").objects().map { item ->
            val posters = item.optJSONArray("posters").objects()
            val author = posters.firstOrNull { it.text("description").contains("Original Poster", true) }
                ?: posters.firstOrNull()
            Topic(item.getInt("id"), item.text("title"), plainText(item.text("excerpt")), users[author?.optInt("user_id")],
                item.optInt("category_id"), item.optJSONArray("tags").tagNames(),
                (item.optInt("posts_count", 1) - 1).coerceAtLeast(0), item.optInt("views"), item.optInt("like_count"),
                item.text("image_url"), item.text("bumped_at"), item.optBoolean("pinned"), item.optBoolean("closed"))
        }
        return TopicPage(topics, list.text("more_topics_url").takeIf(String::isNotBlank))
    }

    fun post(json: JSONObject): Post {
        val action = json.optJSONArray("actions_summary").objects().firstOrNull { it.optInt("id") == 2 }
        val bookmarked = json.optJSONObject("bookmarks")
        return Post(json.getInt("id"), json.optInt("post_number"), member(json), json.text("cooked"), json.text("created_at"),
            action?.optInt("count") ?: 0, action?.optBoolean("acted") ?: false,
            json.positiveInt("bookmark_id") ?: bookmarked?.positiveInt("id")
                ?: json.optJSONArray("bookmarks").objects().firstOrNull()?.positiveInt("id"),
            json.positiveInt("reply_to_post_number"), action?.optBoolean("can_act", true) ?: true)
    }

    fun detail(json: JSONObject): TopicDetail {
        val stream = json.getJSONObject("post_stream")
        val ids = stream.optJSONArray("stream")
        return TopicDetail(json.getInt("id"), json.text("title"), json.optInt("category_id"),
            stream.optJSONArray("posts").objects().map(::post),
            if (ids == null) emptyList() else (0 until ids.length()).map { ids.getInt(it) }, json.optBoolean("closed"))
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
