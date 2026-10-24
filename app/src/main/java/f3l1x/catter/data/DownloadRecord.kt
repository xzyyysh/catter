package f3l1x.catter.data

import org.json.JSONArray
import org.json.JSONObject

data class DownloadRecord(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val qualityLabel: String,
    val format: String,
    val mimeType: String,
    val uri: String,
    val fileName: String,
    val size: Long,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("thumbnailUrl", thumbnailUrl)
        put("qualityLabel", qualityLabel)
        put("format", format)
        put("mimeType", mimeType)
        put("uri", uri)
        put("fileName", fileName)
        put("size", size)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): DownloadRecord = DownloadRecord(
            id = json.optString("id"),
            title = json.optString("title"),
            author = json.optString("author"),
            thumbnailUrl = json.optString("thumbnailUrl"),
            qualityLabel = json.optString("qualityLabel"),
            format = json.optString("format"),
            mimeType = json.optString("mimeType"),
            uri = json.optString("uri"),
            fileName = json.optString("fileName"),
            size = json.optLong("size"),
            createdAt = json.optLong("createdAt"),
        )

        fun listFromJson(raw: String?): List<DownloadRecord> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.let { add(fromJson(it)) }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun listToJson(records: List<DownloadRecord>): String {
            val array = JSONArray()
            records.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
