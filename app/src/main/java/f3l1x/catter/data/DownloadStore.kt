package f3l1x.catter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DownloadStore {

    private const val PREFS_NAME = "catter_downloads"
    private const val KEY_RECORDS = "records"

    private lateinit var prefs: SharedPreferences

    private val _records = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _records.value = DownloadRecord.listFromJson(prefs.getString(KEY_RECORDS, null))
    }

    fun add(record: DownloadRecord) {
        _records.update { current -> listOf(record) + current.filterNot { it.id == record.id } }
        persist()
    }

    fun remove(id: String) {
        _records.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    fun clear() {
        _records.value = emptyList()
        persist()
    }

    private fun persist() {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_RECORDS, DownloadRecord.listToJson(_records.value)).apply()
    }
}
