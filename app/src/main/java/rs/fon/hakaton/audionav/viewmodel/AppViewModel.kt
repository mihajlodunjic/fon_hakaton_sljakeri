package rs.fon.hakaton.audionav.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.fon.hakaton.audionav.AppLogger
import rs.fon.hakaton.audionav.LogTag
import rs.fon.hakaton.audionav.ble.BeaconAdvertiseResult
import rs.fon.hakaton.audionav.ble.BeaconAdvertiserController
import rs.fon.hakaton.audionav.ble.BeaconPayloadCodec
import rs.fon.hakaton.audionav.ble.BeaconScanEvent
import rs.fon.hakaton.audionav.ble.BeaconScannerController
import rs.fon.hakaton.audionav.domain.AppMode
import rs.fon.hakaton.audionav.domain.AppUiState
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.BeaconConfigValidator
import rs.fon.hakaton.audionav.domain.BeaconScreenState
import rs.fon.hakaton.audionav.domain.BluetoothStatus
import rs.fon.hakaton.audionav.domain.MessageCatalog
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.domain.PermissionUiState
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority
import rs.fon.hakaton.audionav.domain.ReceiverScreenState
import rs.fon.hakaton.audionav.storage.BeaconConfigStorage

class AppViewModel(
    private val beaconConfigStorage: BeaconConfigStorage,
    private val beaconAdvertiserController: BeaconAdvertiserController,
    private val beaconScannerController: BeaconScannerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var receiverRetryJob: Job? = null
    private var receiverAutoRestartAllowed: Boolean = false

    fun onModeSelected(mode: AppMode) {
        AppLogger.d(LogTag.APP, "Mode selected: $mode")
        _uiState.update { currentState ->
            currentState.copy(selectedMode = mode)
        }
    }

    fun onRefreshPermissionStateRequested() {
        AppLogger.d(LogTag.APP, "Permission state refresh requested")
    }

    fun onSystemStatusChanged(
        permissionUiState: PermissionUiState,
        bluetoothStatus: BluetoothStatus,
    ) {
        AppLogger.d(
            LogTag.APP,
            "System status changed: permission=${permissionUiState.status}, bluetooth=$bluetoothStatus",
        )

        val readinessMessage = when {
            bluetoothStatus == BluetoothStatus.UNAVAILABLE -> "BLE nije dostupan na ovom uredaju."
            permissionUiState.status == PermissionStatus.PERMANENTLY_DENIED -> {
                "Dozvole su trajno odbijene. Otvorite podesavanja aplikacije."
            }

            permissionUiState.status == PermissionStatus.MISSING -> {
                "Nedostaju potrebne Bluetooth dozvole."
            }

            bluetoothStatus == BluetoothStatus.DISABLED -> "Bluetooth je iskljucen."
            else -> "Uredaj je spreman za sledecu fazu implementacije."
        }

        _uiState.update { currentState ->
            val updatedState = currentState.copy(
                permissionUiState = permissionUiState,
                bluetoothStatus = bluetoothStatus,
                readinessMessage = readinessMessage,
            )

            updatedState.copy(
                beaconState = recomputeBeaconState(updatedState, updatedState.beaconState),
                receiverState = recomputeReceiverState(updatedState, updatedState.receiverState),
            )
        }

        val receiverShouldStop = _uiState.value.receiverState.isScanning ||
            _uiState.value.receiverState.retryScheduled
        if (receiverShouldStop && !isReceiverRuntimeReady(_uiState.value)) {
            val errorText = when {
                permissionUiState.status != PermissionStatus.GRANTED -> {
                    "Nedostaju Bluetooth dozvole."
                }

                bluetoothStatus != BluetoothStatus.READY -> "Bluetooth je iskljucen."
                else -> "Receiver vise nije spreman za skeniranje."
            }
            stopReceiverScanning(errorText)
        }
    }

    fun loadPersistedBeaconConfig() {
        viewModelScope.launch {
            val persistedConfig = beaconConfigStorage.load()
            beaconConfigStorage.clearActiveFlag()

            if (persistedConfig != null) {
                _uiState.update { currentState ->
                    val loadedMessages = MessageCatalog.definitionsFor(persistedConfig.pointType)
                    val loadedBeaconState = currentState.beaconState.copy(
                        beaconId = persistedConfig.beaconId,
                        labelInput = persistedConfig.label,
                        selectedPointType = persistedConfig.pointType,
                        selectedPriority = persistedConfig.priority,
                        selectedMessageCode = persistedConfig.messageCode,
                        availableMessages = loadedMessages,
                        isAdvertising = false,
                        errorText = null,
                        lastEncodedPayloadHex = null,
                        statusText = "Idle",
                    )

                    currentState.copy(
                        beaconState = recomputeBeaconState(
                            currentState,
                            loadedBeaconState,
                        ),
                    )
                }
            }
        }
    }

    fun onBeaconLabelChanged(value: String) {
        updateBeaconDraft { currentState ->
            currentState.copy(
                labelInput = value,
                errorText = null,
            )
        }
    }

    fun onBeaconPointTypeSelected(pointType: PointType) {
        updateBeaconDraft { currentState ->
            val availableMessages = MessageCatalog.definitionsFor(pointType)
            currentState.copy(
                selectedPointType = pointType,
                selectedMessageCode = availableMessages.first().messageCode,
                availableMessages = availableMessages,
                errorText = null,
            )
        }
    }

    fun onBeaconPrioritySelected(priority: Priority) {
        updateBeaconDraft { currentState ->
            currentState.copy(
                selectedPriority = priority,
                errorText = null,
            )
        }
    }

    fun onBeaconMessageCodeSelected(messageCode: Short) {
        updateBeaconDraft { currentState ->
            val validSelection = currentState.availableMessages.any {
                it.messageCode == messageCode
            }
            if (validSelection) {
                currentState.copy(
                    selectedMessageCode = messageCode,
                    errorText = null,
                )
            } else {
                currentState
            }
        }
    }

    fun onStartBeaconClick() {
        val currentState = _uiState.value
        if (currentState.beaconState.isAdvertising) {
            return
        }

        val errorMessage = when {
            currentState.permissionUiState.status != PermissionStatus.GRANTED -> {
                "Nedostaju dozvole za beacon mod."
            }

            currentState.bluetoothStatus != BluetoothStatus.READY -> {
                "Bluetooth nije spreman."
            }

            !currentState.beaconState.advertiserSupported -> {
                "Uredaj ne podrzava BLE advertising."
            }

            !BeaconConfigValidator.isValid(currentBeaconConfig(isActive = true)) -> {
                "Beacon konfiguracija nije validna."
            }

            else -> null
        }

        if (errorMessage != null) {
            AppLogger.w(LogTag.BLE_ADV, errorMessage)
            _uiState.update { state ->
                state.copy(
                    beaconState = recomputeBeaconState(
                        state,
                        state.beaconState.copy(
                            isAdvertising = false,
                            errorText = errorMessage,
                        ),
                        allowReadyStatus = false,
                    ),
                )
            }
            return
        }

        val config = currentBeaconConfig(isActive = true)
        val payloadHex = BeaconPayloadCodec.encode(config).toHexString()
        AppLogger.d(LogTag.BLE_ADV, "Starting BLE advertising for beaconId=${config.beaconId}")

        beaconAdvertiserController.startAdvertising(config) { result ->
            when (result) {
                BeaconAdvertiseResult.Started -> {
                    AppLogger.d(LogTag.BLE_ADV, "BLE advertising started")
                    viewModelScope.launch {
                        beaconConfigStorage.save(config.copy(isActive = true))
                    }
                    _uiState.update { state ->
                        state.copy(
                            beaconState = recomputeBeaconState(
                                state,
                                state.beaconState.copy(
                                    isAdvertising = true,
                                    errorText = null,
                                    lastEncodedPayloadHex = payloadHex,
                                ),
                                allowReadyStatus = false,
                            ),
                        )
                    }
                }

                is BeaconAdvertiseResult.Failure -> {
                    AppLogger.e(
                        LogTag.BLE_ADV,
                        "BLE advertising failed with code=${result.code}: ${result.message}",
                    )
                    persistCurrentBeaconConfig(isActive = false)
                    _uiState.update { state ->
                        state.copy(
                            beaconState = recomputeBeaconState(
                                state,
                                state.beaconState.copy(
                                    isAdvertising = false,
                                    errorText = result.message,
                                    lastEncodedPayloadHex = payloadHex,
                                ),
                                allowReadyStatus = false,
                            ),
                        )
                    }
                }

                BeaconAdvertiseResult.Stopped -> {
                    AppLogger.d(LogTag.BLE_ADV, "BLE advertising stopped callback received")
                    persistCurrentBeaconConfig(isActive = false)
                    _uiState.update { state ->
                        state.copy(
                            beaconState = recomputeBeaconState(
                                state,
                                state.beaconState.copy(
                                    isAdvertising = false,
                                    errorText = null,
                                ),
                                allowReadyStatus = false,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun onStartReceiverClick() {
        val currentState = _uiState.value
        if (currentState.receiverState.isScanning) {
            return
        }

        receiverAutoRestartAllowed = true
        cancelReceiverRetry()

        val errorMessage = when {
            currentState.permissionUiState.status != PermissionStatus.GRANTED -> {
                "Nedostaju Bluetooth dozvole."
            }

            currentState.bluetoothStatus != BluetoothStatus.READY -> {
                "Bluetooth nije spreman."
            }

            !currentState.receiverState.scannerSupported -> {
                "Uredaj ne podrzava BLE skeniranje."
            }

            else -> null
        }

        if (errorMessage != null) {
            receiverAutoRestartAllowed = false
            AppLogger.w(LogTag.BLE_SCAN, errorMessage)
            _uiState.update { state ->
                val updatedState = state.copy(selectedMode = AppMode.RECEIVER)
                updatedState.copy(
                    receiverState = recomputeReceiverState(
                        updatedState,
                        updatedState.receiverState.copy(
                            isScanning = false,
                            retryScheduled = false,
                            errorText = errorMessage,
                        ),
                    ),
                )
            }
            return
        }

        _uiState.update { state ->
            val updatedState = state.copy(selectedMode = AppMode.RECEIVER)
            updatedState.copy(
                receiverState = recomputeReceiverState(
                    updatedState,
                    updatedState.receiverState.copy(
                        isScanning = true,
                        retryScheduled = false,
                        errorText = null,
                    ),
                ),
            )
        }

        AppLogger.d(LogTag.BLE_SCAN, "Starting BLE receiver scanning")
        beaconScannerController.startScanning(::handleScanEvent)
    }

    fun onStopClick(mode: AppMode) {
        AppLogger.d(LogTag.APP, "Stop clicked for $mode")
        when (mode) {
            AppMode.BEACON -> {
                beaconAdvertiserController.stopAdvertising()
                persistCurrentBeaconConfig(isActive = false)
                _uiState.update { currentState ->
                    currentState.copy(
                        beaconState = recomputeBeaconState(
                            currentState,
                            currentState.beaconState.copy(
                                isAdvertising = false,
                                errorText = null,
                                statusText = "Idle",
                            ),
                            allowReadyStatus = false,
                        ),
                    )
                }
            }

            AppMode.RECEIVER -> stopReceiverScanning(errorText = null)
            AppMode.NONE -> Unit
        }
    }

    private fun handleScanEvent(event: BeaconScanEvent) {
        when (event) {
            BeaconScanEvent.Started -> {
                AppLogger.d(LogTag.BLE_SCAN, "BLE scan started")
                cancelReceiverRetry()
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                isScanning = true,
                                retryScheduled = false,
                                errorText = null,
                            ),
                        ),
                    )
                }
            }

            is BeaconScanEvent.BeaconDetected -> {
                val message = MessageCatalog.resolve(
                    pointType = event.payload.pointType,
                    messageCode = event.payload.messageCode,
                )
                val decodedText = message?.ttsText ?: "Nepoznata lokalna poruka za ovaj beacon."
                AppLogger.d(
                    LogTag.BLE_SCAN,
                    "Beacon detected: beaconId=${event.payload.beaconId}, messageCode=${event.payload.messageCode}, rssi=${event.rssi}",
                )
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                isScanning = true,
                                retryScheduled = false,
                                errorText = null,
                                lastDetectedBeaconId = event.payload.beaconId,
                                lastDetectedPointType = event.payload.pointType,
                                lastDetectedPriority = event.payload.priority,
                                lastDetectedMessageCode = event.payload.messageCode,
                                lastDecodedText = decodedText,
                                lastDetectedAt = event.detectedAt,
                                lastRssi = event.rssi,
                            ),
                        ),
                    )
                }
            }

            is BeaconScanEvent.Failure -> {
                AppLogger.e(
                    LogTag.BLE_SCAN,
                    "BLE scan failure code=${event.code}: ${event.message}",
                )
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                isScanning = false,
                                retryScheduled = false,
                                errorText = event.message,
                            ),
                        ),
                    )
                }

                if (event.retryable && shouldScheduleReceiverRetry()) {
                    scheduleReceiverRetry()
                } else {
                    receiverAutoRestartAllowed = false
                }
            }

            BeaconScanEvent.Stopped -> {
                AppLogger.d(LogTag.BLE_SCAN, "BLE scan stopped")
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                isScanning = false,
                                retryScheduled = false,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun scheduleReceiverRetry() {
        if (receiverRetryJob?.isActive == true) {
            return
        }

        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(retryScheduled = true),
                ),
            )
        }

        receiverRetryJob = viewModelScope.launch {
            AppLogger.d(LogTag.BLE_SCAN, "Scheduling BLE scan retry in ${RECEIVER_RETRY_DELAY_MS}ms")
            delay(RECEIVER_RETRY_DELAY_MS)
            receiverRetryJob = null

            val currentState = _uiState.value
            if (!receiverAutoRestartAllowed || !isReceiverRuntimeReady(currentState)) {
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(retryScheduled = false),
                        ),
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    receiverState = recomputeReceiverState(
                        state,
                        state.receiverState.copy(
                            isScanning = true,
                            retryScheduled = false,
                            errorText = null,
                        ),
                    ),
                )
            }

            beaconScannerController.startScanning(::handleScanEvent)
        }
    }

    private fun shouldScheduleReceiverRetry(): Boolean {
        val state = _uiState.value
        return receiverAutoRestartAllowed &&
            state.selectedMode == AppMode.RECEIVER &&
            isReceiverRuntimeReady(state)
    }

    private fun stopReceiverScanning(errorText: String?) {
        receiverAutoRestartAllowed = false
        cancelReceiverRetry()
        beaconScannerController.stopScanning()

        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(
                        isScanning = false,
                        retryScheduled = false,
                        errorText = errorText,
                    ),
                ),
            )
        }
    }

    private fun cancelReceiverRetry() {
        receiverRetryJob?.cancel()
        receiverRetryJob = null
    }

    private fun isReceiverRuntimeReady(state: AppUiState): Boolean {
        return state.permissionUiState.status == PermissionStatus.GRANTED &&
            state.bluetoothStatus == BluetoothStatus.READY &&
            state.receiverState.scannerSupported
    }

    private fun updateBeaconDraft(
        transform: (BeaconScreenState) -> BeaconScreenState,
    ) {
        _uiState.update { currentState ->
            val updatedBeaconState = transform(currentState.beaconState)
            currentState.copy(
                beaconState = recomputeBeaconState(
                    currentState,
                    updatedBeaconState,
                ),
            )
        }
        persistCurrentBeaconConfig(isActive = false)
    }

    private fun createInitialUiState(): AppUiState {
        val initialPointType = MessageCatalog.supportedPointTypes().first()
        val availableMessages = MessageCatalog.definitionsFor(initialPointType)
        val initialMessage = availableMessages.first()
        val initialBeaconState = BeaconScreenState(
            beaconId = UUID.randomUUID().toString(),
            labelInput = "",
            selectedPointType = initialPointType,
            selectedPriority = Priority.MEDIUM,
            selectedMessageCode = initialMessage.messageCode,
            availableMessages = availableMessages,
            advertiserSupported = beaconAdvertiserController.isSupported(),
        )
        val initialReceiverState = ReceiverScreenState(
            scannerSupported = beaconScannerController.isSupported(),
        )

        return AppUiState(
            beaconState = initialBeaconState,
            receiverState = initialReceiverState,
        )
    }

    private fun recomputeBeaconState(
        appState: AppUiState,
        beaconState: BeaconScreenState,
        allowReadyStatus: Boolean = true,
    ): BeaconScreenState {
        val advertiserSupported = beaconAdvertiserController.isSupported()
        val ready = appState.permissionUiState.status == PermissionStatus.GRANTED &&
            appState.bluetoothStatus == BluetoothStatus.READY &&
            advertiserSupported &&
            BeaconConfigValidator.isValid(currentBeaconConfig(beaconState = beaconState))

        val statusText = when {
            beaconState.errorText != null -> "Error"
            beaconState.isAdvertising -> "Advertising"
            ready && allowReadyStatus -> "Ready"
            else -> "Idle"
        }

        return beaconState.copy(
            isReady = ready,
            advertiserSupported = advertiserSupported,
            statusText = statusText,
        )
    }

    private fun recomputeReceiverState(
        appState: AppUiState,
        receiverState: ReceiverScreenState,
    ): ReceiverScreenState {
        val scannerSupported = beaconScannerController.isSupported()
        val ready = appState.permissionUiState.status == PermissionStatus.GRANTED &&
            appState.bluetoothStatus == BluetoothStatus.READY &&
            scannerSupported

        val statusText = when {
            receiverState.errorText != null -> "Error"
            receiverState.isScanning -> "Scanning"
            else -> "Not Scanning"
        }

        return receiverState.copy(
            scannerSupported = scannerSupported,
            isReady = ready,
            statusText = statusText,
        )
    }

    private fun currentBeaconConfig(
        beaconState: BeaconScreenState = _uiState.value.beaconState,
        isActive: Boolean = beaconState.isAdvertising,
    ): BeaconConfig {
        return BeaconConfig(
            beaconId = beaconState.beaconId,
            label = beaconState.labelInput.trim(),
            pointType = beaconState.selectedPointType,
            priority = beaconState.selectedPriority,
            messageCode = beaconState.selectedMessageCode,
            isActive = isActive,
            lastUpdatedAt = System.currentTimeMillis(),
        )
    }

    private fun persistCurrentBeaconConfig(isActive: Boolean) {
        val config = currentBeaconConfig(isActive = isActive)
        if (!BeaconConfigValidator.isValid(config)) {
            return
        }

        viewModelScope.launch {
            beaconConfigStorage.save(config)
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = " ") { byte ->
            "%02X".format(byte)
        }
    }

    companion object {
        private const val RECEIVER_RETRY_DELAY_MS = 3_000L
    }
}
