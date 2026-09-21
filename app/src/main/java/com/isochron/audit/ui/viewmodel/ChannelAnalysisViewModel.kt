package com.isochron.audit.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isochron.audit.R
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.util.ChannelAnalysis
import com.isochron.audit.util.ChannelAnalyzer
import com.isochron.audit.util.WifiScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChannelAnalysisViewModel(app: Application) : AndroidViewModel(app) {
    val wifiScanner = WifiScanner(app)

    var analysis by mutableStateOf<ChannelAnalysis?>(null)
    var isScanning by mutableStateOf(false)
    var selectedBand by mutableStateOf("2.4")

    val wifiEnabled: StateFlow<Boolean> = wifiScanner.wifiEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), wifiScanner.isWifiEnabled())

    fun doScan() {
        if (!wifiScanner.isWifiEnabled()) {
            UiMessageBus.post(R.string.err_wifi_off)
            return
        }
        isScanning = true
        wifiScanner.startScan { results ->
            try {
                analysis = ChannelAnalyzer.analyze(results)
            } catch (e: Exception) {
                android.util.Log.e("ChannelAnalysis", "Error analyzing", e)
                UiMessageBus.post(R.string.err_channel_analysis)
            }
            isScanning = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiScanner.cleanup()
    }
}
