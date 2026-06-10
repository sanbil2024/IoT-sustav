package hr.aspira.sandrozavrsni

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.aspira.sandrozavrsni.api.ApiClient
import hr.aspira.sandrozavrsni.api.HistoryPoint
import hr.aspira.sandrozavrsni.api.SensorNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainVm : ViewModel() {
    private val _status = MutableStateFlow("Spajam se na Raspberry Pi…")
    val status = _status.asStateFlow()

    private val _now = MutableStateFlow<SensorNow?>(null)
    val now = _now.asStateFlow()

    private val _hist = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val hist = _hist.asStateFlow()

    private val _rangeMinutes = MutableStateFlow(120) // default 2h
    val rangeMinutes = _rangeMinutes.asStateFlow()


    fun start(pollSec: Long = 2, minutesHistory: Int = 120) {
        viewModelScope.launch {
            // inicijalno učitaj povijest
            loadHistory(minutesHistory)

            // periodički dohvat trenutnih vrijednosti
            while (isActive) {
                runCatching { ApiClient.api.current() }
                    .onSuccess { n ->
                        _now.value = n
                        _status.value = "Spojeno na Raspberry Pi"
                    }
                    .onFailure {
                        it.printStackTrace()
                        _status.value = "Greška: ${it.localizedMessage}"
                    }

                delay(pollSec * 1000)
            }
        }
    }

    fun loadHistory(minutes: Int) {
        _rangeMinutes.value = minutes
        viewModelScope.launch {
            runCatching { ApiClient.api.history(minutes = minutes) }
                .onSuccess {
                    _hist.value = it.data
                    _status.value = "Spojeno na Raspberry Pi"
                }
                .onFailure {
                    _status.value = "Nije moguće dohvatiti povijest (provjeri Wi-Fi)"
                }
        }
    }

}
