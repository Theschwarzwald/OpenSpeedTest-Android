package com.example.openspeedtest

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

private const val MAX_HISTORY = 500

class ServerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServerRepository(app)
    private val historyRepo = HistoryRepository(app)

    var servers by mutableStateOf(repo.loadServers())
        private set

    // The connected server. Only changes when YOU pick another one (or delete it).
    var selectedId by mutableStateOf(repo.loadSelectedId())
        private set

    val selectedServer: Server?
        get() = servers.firstOrNull { it.id == selectedId }

    var history by mutableStateOf(historyRepo.load())
        private set

    var oledMode by mutableStateOf(repo.loadOledMode())
        private set

    // Reported by the speed test page: is it in dark mode, and its background color.
    var pageDark by mutableStateOf<Boolean?>(null)
        private set
    var pageBackground by mutableStateOf<Int?>(null)
        private set

    fun changeOledMode(mode: OledMode) {
        oledMode = mode
        repo.saveOledMode(mode)
    }

    fun setPageLook(dark: Boolean, argb: Int) {
        pageDark = dark
        pageBackground = argb
    }

    fun select(server: Server) {
        selectedId = server.id
        repo.saveSelectedId(server.id)
    }

    fun save(server: Server) {
        servers = if (servers.any { it.id == server.id }) {
            servers.map { if (it.id == server.id) server else it }
        } else {
            servers + server
        }
        repo.saveServers(servers)
        if (selectedServer == null) select(server)
    }

    fun delete(server: Server) {
        servers = servers.filterNot { it.id == server.id }
        repo.saveServers(servers)
        if (selectedId == server.id) {
            selectedId = null
            repo.saveSelectedId(null)
        }
    }

    fun addResult(result: TestResult) {
        history = (listOf(result) + history).take(MAX_HISTORY)
        historyRepo.save(history)
    }

    fun deleteResult(result: TestResult) {
        history = history.filterNot { it.id == result.id }
        historyRepo.save(history)
    }

    fun clearHistory() {
        history = emptyList()
        historyRepo.save(history)
    }
}
