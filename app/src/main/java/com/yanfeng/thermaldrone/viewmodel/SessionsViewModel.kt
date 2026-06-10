package com.yanfeng.thermaldrone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yanfeng.thermaldrone.App
import com.yanfeng.thermaldrone.model.SessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** SESSIONS tab: list / delete / export. */
class SessionsViewModel(private val app: App) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _sessions.value = app.sessionRepo.listSessions()
        }
    }

    fun delete(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.sessionRepo.deleteSession(name)
            refresh()
        }
    }

    /** ZIP + CSV + PDF for one session, then ACTION_SEND_MULTIPLE. */
    fun exportSession(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                val files = listOfNotNull(
                    app.exportManager.exportZip(name),
                    app.exportManager.exportCsv(name),
                    app.exportManager.exportPdf(name)
                )
                if (files.isEmpty()) {
                    _status.value = "Nothing to export for $name"
                } else {
                    app.exportManager.share(files)
                    _status.value = "Exported ${files.size} file(s) for $name"
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** Bulk export: every session as ZIP in one share sheet. */
    fun exportAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                val files = ArrayList<File>()
                for (s in _sessions.value) {
                    app.exportManager.exportZip(s.name)?.let { files.add(it) }
                }
                if (files.isEmpty()) _status.value = "No sessions to export"
                else {
                    app.exportManager.share(files)
                    _status.value = "Exported ${files.size} session ZIP(s)"
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearStatus() { _status.value = null }
}
