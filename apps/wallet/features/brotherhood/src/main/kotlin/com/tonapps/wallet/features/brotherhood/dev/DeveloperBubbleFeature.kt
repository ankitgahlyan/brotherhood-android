package com.tonapps.wallet.features.brotherhood.dev

import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.hydrator.HydratedAccountState
import com.tonapps.wallet.data.brotherhood.network.TelemetryRepository
import com.tonapps.wallet.data.brotherhood.network.TelemetrySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DeveloperPanelTab(val id: String, val label: String) {
    TELEMETRY("telemetry", "RPC Telemetry"),
    PROVIDERS("providers", "Rate Limiters"),
    CACHE("cache", "BOC Cache"),
    CONTROLS("controls", "Dev Controls"),
}

class DeveloperBubbleFeature(
    private val telemetryRepository: TelemetryRepository,
    private val hydrator: AccountStateHydrator,
) : AsyncViewModel() {

    val isDeveloperModeEnabled: StateFlow<Boolean> = telemetryRepository.isDeveloperModeEnabled
    val isTestnetEnabled: StateFlow<Boolean> = telemetryRepository.isTestnetEnabled
    val isAutoFundEnabled: StateFlow<Boolean> = telemetryRepository.isAutoFundEnabled
    val telemetrySnapshot: StateFlow<TelemetrySnapshot> = telemetryRepository.snapshot
    val hydratedStates: StateFlow<Map<String, HydratedAccountState>> = hydrator.hydratedStates

    private val _isSheetExpanded = MutableStateFlow(false)
    val isSheetExpanded: StateFlow<Boolean> = _isSheetExpanded.asStateFlow()

    private val _selectedTab = MutableStateFlow(DeveloperPanelTab.TELEMETRY)
    val selectedTab: StateFlow<DeveloperPanelTab> = _selectedTab.asStateFlow()

    private val _inspectAddressInput = MutableStateFlow("")
    val inspectAddressInput: StateFlow<String> = _inspectAddressInput.asStateFlow()

    private val _inspectedState = MutableStateFlow<HydratedAccountState?>(null)
    val inspectedState: StateFlow<HydratedAccountState?> = _inspectedState.asStateFlow()

    fun toggleSheetExpanded() {
        _isSheetExpanded.value = !_isSheetExpanded.value
    }

    fun closeSheet() {
        _isSheetExpanded.value = false
    }

    fun selectTab(tab: DeveloperPanelTab) {
        _selectedTab.value = tab
    }

    fun registerSecretVersionTap(): Int {
        return telemetryRepository.registerSecretVersionTap()
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        telemetryRepository.setDeveloperModeEnabled(enabled)
        if (!enabled) {
            _isSheetExpanded.value = false
        }
    }

    fun setTestnetEnabled(testnet: Boolean) {
        telemetryRepository.setTestnetEnabled(testnet)
    }

    fun setAutoFundEnabled(enabled: Boolean) {
        telemetryRepository.setAutoFundEnabled(enabled)
    }

    fun clearTelemetry() {
        telemetryRepository.clearTelemetry()
    }

    fun updateInspectAddressInput(input: String) {
        _inspectAddressInput.value = input
    }

    fun inspectContractAddress() {
        val addr = _inspectAddressInput.value.trim()
        if (addr.isBlank()) {
            return
        }
        bgScope.launch {
            val state = hydrator.hydrateAddress(
                address = addr,
                forceRefresh = true,
            )
            _inspectedState.value = state
        }
    }
}
