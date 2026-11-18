package f3l1x.catter.ui.convert

import f3l1x.catter.data.DownloadRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConvertPicker {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _selected = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val selected: StateFlow<List<DownloadRecord>> = _selected.asStateFlow()

    fun open() {
        _active.value = true
    }

    fun cancel() {
        _active.value = false
        _selected.value = emptyList()
    }

    fun confirm(records: List<DownloadRecord>) {
        _selected.value = records
        _active.value = false
    }

    fun select(record: DownloadRecord) {
        _selected.value = listOf(record)
        _active.value = false
    }

    fun clear() {
        _selected.value = emptyList()
    }
}
