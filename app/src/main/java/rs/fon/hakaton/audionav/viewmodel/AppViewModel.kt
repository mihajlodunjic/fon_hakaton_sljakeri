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
import rs.fon.hakaton.audionav.domain.AnnouncementArbiter
import rs.fon.hakaton.audionav.domain.AnnouncementArbitrationResult
import rs.fon.hakaton.audionav.domain.AnnouncementCandidate
import rs.fon.hakaton.audionav.domain.AppMode
import rs.fon.hakaton.audionav.domain.AppUiState
import rs.fon.hakaton.audionav.domain.BehindPassResult
import rs.fon.hakaton.audionav.domain.BehindPassTracker
import rs.fon.hakaton.audionav.domain.BehindSpeechPolicy
import rs.fon.hakaton.audionav.domain.DirectionConfidence
import rs.fon.hakaton.audionav.domain.DirectionEstimator
import rs.fon.hakaton.audionav.domain.DirectionLabel
import rs.fon.hakaton.audionav.domain.DirectionPromptBuilder
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.BeaconConfigValidator
import rs.fon.hakaton.audionav.domain.BeaconScreenState
import rs.fon.hakaton.audionav.domain.BluetoothStatus
import rs.fon.hakaton.audionav.domain.DetectedBeaconEvent
import rs.fon.hakaton.audionav.domain.HeadingEstimate
import rs.fon.hakaton.audionav.domain.HeadingSensorController
import rs.fon.hakaton.audionav.domain.LocalDirectionFrame
import rs.fon.hakaton.audionav.domain.MessageCatalog
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.domain.PermissionUiState
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.PendingAnnouncementResult
import rs.fon.hakaton.audionav.domain.Priority
import rs.fon.hakaton.audionav.domain.ReceiverScreenState
import rs.fon.hakaton.audionav.domain.RssiRejectionReason
import rs.fon.hakaton.audionav.domain.RssiStabilizationResult
import rs.fon.hakaton.audionav.domain.RssiStabilizer
import rs.fon.hakaton.audionav.domain.toDisplayText as directionConfidenceToDisplayText
import rs.fon.hakaton.audionav.storage.BeaconConfigStorage
import rs.fon.hakaton.audionav.storage.CooldownCheckResult
import rs.fon.hakaton.audionav.storage.CooldownRepository
import rs.fon.hakaton.audionav.tts.TtsAnnouncer
import rs.fon.hakaton.audionav.tts.TtsPlaybackEvent
import rs.fon.hakaton.audionav.tts.TtsSpeakResult
import rs.fon.hakaton.audionav.tts.TtsStatus
import rs.fon.hakaton.audionav.tts.toDisplayText

class AppViewModel(
    private val beaconConfigStorage: BeaconConfigStorage,
    private val beaconAdvertiserController: BeaconAdvertiserController,
    private val beaconScannerController: BeaconScannerController,
    private val cooldownRepository: CooldownRepository,
    private val rssiStabilizer: RssiStabilizer,
    private val headingSensorController: HeadingSensorController,
    private val ttsAnnouncer: TtsAnnouncer,
    private val announcementArbiter: AnnouncementArbiter = AnnouncementArbiter(),
    private val behindPassTracker: BehindPassTracker = BehindPassTracker(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var receiverRetryJob: Job? = null
    private var receiverAutoRestartAllowed: Boolean = false
    private var announcementGapJob: Job? = null
    private var headingPollingJob: Job? = null
    private var announcementGapUntilMs: Long? = null
    private var latestHeadingEstimate: HeadingEstimate? = null
    private var receiverScreenVisible: Boolean = false
    private var receiverHeadingOffsetDegrees: Int? = null
    private var activeUiUtteranceId: String? = null
    private var interruptedAnnouncementUtteranceId: String? = null

    init {
        ttsAnnouncer.initialize(
            onStatusChanged = { status ->
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                ttsStatus = status,
                                ttsStatusText = status.toDisplayText(),
                            ),
                        ),
                    )
                }
            },
            onPlaybackEvent = ::handleTtsPlaybackEvent,
        )

        viewModelScope.launch {
            val snapshot = cooldownRepository.initialize()
            val lastAnnouncementAt = snapshot.recentEvents.firstOrNull { it.wasAnnounced }?.detectedAt
            _uiState.update { state ->
                state.copy(
                    receiverState = state.receiverState.copy(
                        recentEvents = snapshot.recentEvents,
                        lastAnnouncementAt = lastAnnouncementAt,
                    ),
                )
            }
        }
    }

    fun onModeSelected(mode: AppMode) {
        AppLogger.d(LogTag.APP, "Mode selected: $mode")
        _uiState.update { currentState ->
            currentState.copy(selectedMode = mode)
        }
    }

    fun onBeaconScreenVisibilityChanged(@Suppress("UNUSED_PARAMETER") visible: Boolean) {
        Unit
    }

    fun onReceiverScreenVisibilityChanged(visible: Boolean) {
        receiverScreenVisible = visible
        syncHeadingSensorLifecycle()
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
                        azimuthInput = persistedConfig.azimuthDegrees?.toString().orEmpty(),
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

    fun onBeaconAzimuthChanged(value: String) {
        if (value.isNotEmpty() && value.any { !it.isDigit() }) {
            return
        }
        updateBeaconDraft { currentState ->
            currentState.copy(
                azimuthInput = value,
                errorText = null,
            )
        }
    }

    fun onBeaconAdjustAzimuth(deltaDegrees: Int) {
        updateBeaconDraft { currentState ->
            val currentAzimuth = currentState.azimuthInput.toIntOrNull() ?: 0
            val adjusted = ((currentAzimuth + deltaDegrees) % 360 + 360) % 360
            currentState.copy(
                azimuthInput = adjusted.toString(),
                errorText = null,
            )
        }
    }

    fun onCalibrateReceiverHeading() {
        val headingEstimate = currentHeadingEstimate()
        if (headingEstimate == null || headingEstimate.confidence != DirectionConfidence.HIGH) {
            _uiState.update { state ->
                state.copy(
                    receiverState = recomputeReceiverState(
                        state,
                        state.receiverState.copy(
                            directionCalibrationText = "Kalibracija nije uspela: heading nije dovoljno stabilan.",
                        ),
                    ),
                )
            }
            return
        }

        receiverHeadingOffsetDegrees = headingEstimate.headingDegrees
        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(
                        directionCalibrationText = "Kalibrisan",
                    ),
                ),
            )
        }
    }

    fun onResetReceiverHeadingCalibration() {
        receiverHeadingOffsetDegrees = null
        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(
                        directionCalibrationText = "Nije kalibrisan",
                        lastDirectionLabel = DirectionLabel.UNKNOWN,
                        lastRelativeAngleDegrees = null,
                        directionFallbackReason = null,
                    ),
                ),
            )
        }
    }

    fun onTouchExploreControl(label: String) {
        if (label.isBlank()) {
            return
        }
        announceUiText(
            text = label,
            utterancePrefix = "ui-touch",
        )
    }

    fun onRepeatLastMessage() {
        val lastSpokenText = _uiState.value.receiverState.lastSpokenText ?: return
        announceUiText(
            text = lastSpokenText,
            utterancePrefix = "ui-repeat",
        )
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
        cancelAnnouncementGap()
        announcementArbiter.clear()
        behindPassTracker.reset()
        rssiStabilizer.reset()

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
                        stabilizationProgress = 0,
                        lastGateDecisionText = null,
                        lastEligibleForAnnouncement = null,
                        lastArbitrationDecisionText = null,
                        lastRelativeAngleDegrees = null,
                        lastDirectionLabel = DirectionLabel.UNKNOWN,
                        directionFallbackReason = null,
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
                if (!isReceiverSessionActive()) {
                    AppLogger.d(
                        LogTag.BLE_SCAN,
                        "Ignoring beacon event after receiver stop for beaconId=${event.payload.beaconId}",
                    )
                    return
                }
                handleDetectedBeacon(event)
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

    private fun handleDetectedBeacon(event: BeaconScanEvent.BeaconDetected) {
        if (!isReceiverSessionActive()) {
            AppLogger.d(
                LogTag.BLE_SCAN,
                "Ignoring detected beacon because receiver session is not active: beaconId=${event.payload.beaconId}",
            )
            return
        }
        val message = MessageCatalog.resolve(
            pointType = event.payload.pointType,
            messageCode = event.payload.messageCode,
        )
        val decodedText = message?.genericTtsText ?: UNKNOWN_LOCAL_MESSAGE_TEXT

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

        when (
            val stabilizationResult = rssiStabilizer.observe(
                payload = event.payload,
                rssi = event.rssi,
                detectedAt = event.detectedAt,
            )
        ) {
            is RssiStabilizationResult.Tracking -> {
                AppLogger.d(
                    LogTag.RSSI,
                    "Tracking beaconId=${event.payload.beaconId}, progress=${stabilizationResult.progress}/${_uiState.value.receiverState.requiredStabilizationCount}, rssi=${event.rssi}",
                )
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                stabilizationProgress = stabilizationResult.progress,
                                lastGateDecisionText = "${stabilizationResult.progress}/${state.receiverState.requiredStabilizationCount} iznad ${stabilizationResult.threshold} dBm.",
                                lastEligibleForAnnouncement = null,
                            ),
                        ),
                    )
                }

                if (stabilizationResult.hasStableWindow && stabilizationResult.smoothedRssi != null) {
                    handleBehindPassTrackingWindow(
                        payload = event.payload,
                        detectedAt = event.detectedAt,
                        smoothedRssi = stabilizationResult.smoothedRssi,
                    )
                }
            }

            is RssiStabilizationResult.Rejected -> {
                AppLogger.d(
                    LogTag.RSSI,
                    "Rejected beaconId=${event.payload.beaconId}, reason=${stabilizationResult.reason}, rssi=${event.rssi}",
                )
                val progress = when (stabilizationResult.reason) {
                    RssiRejectionReason.BELOW_THRESHOLD -> 0
                    RssiRejectionReason.SIGNAL_GAP_RESET -> 1
                }
                val gateText = when (stabilizationResult.reason) {
                    RssiRejectionReason.BELOW_THRESHOLD -> {
                        "Signal je ispod praga (${_uiState.value.receiverState.rssiThreshold} dBm)."
                    }

                    RssiRejectionReason.SIGNAL_GAP_RESET -> {
                        "Reset zbog gubitka signala. Pocetak stabilizacije ${progress}/${_uiState.value.receiverState.requiredStabilizationCount}."
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                stabilizationProgress = progress,
                                lastGateDecisionText = gateText,
                                lastEligibleForAnnouncement = false,
                            ),
                        ),
                    )
                }

                clearBehindPassTracking(
                    beaconId = event.payload.beaconId,
                    messageCode = event.payload.messageCode,
                )
            }

            is RssiStabilizationResult.Stable -> {
                AppLogger.d(
                    LogTag.RSSI,
                    "Stable beaconId=${event.payload.beaconId}, messageCode=${event.payload.messageCode}, rssi=${event.rssi}, smoothedRssi=${stabilizationResult.smoothedRssi}",
                )
                handleStableBeacon(stabilizationResult)
            }
        }
    }

    private fun handleStableBeacon(
        result: RssiStabilizationResult.Stable,
    ) {
        viewModelScope.launch {
            processStableCandidate(
                payload = result.payload,
                detectedAt = result.detectedAt,
                smoothedRssi = result.smoothedRssi,
                continuousBehindTrackingOnly = false,
            )
        }
    }

    private fun handleBehindPassTrackingWindow(
        payload: rs.fon.hakaton.audionav.domain.DecodedBeaconPayload,
        detectedAt: Long,
        smoothedRssi: Int,
    ) {
        viewModelScope.launch {
            processStableCandidate(
                payload = payload,
                detectedAt = detectedAt,
                smoothedRssi = smoothedRssi,
                continuousBehindTrackingOnly = true,
            )
        }
    }

    private suspend fun processStableCandidate(
        payload: rs.fon.hakaton.audionav.domain.DecodedBeaconPayload,
        detectedAt: Long,
        smoothedRssi: Int,
        continuousBehindTrackingOnly: Boolean,
    ) {
        val messageDefinition = MessageCatalog.resolve(
            pointType = payload.pointType,
            messageCode = payload.messageCode,
        )

        if (messageDefinition == null) {
            if (continuousBehindTrackingOnly) {
                return
            }

            AppLogger.w(
                LogTag.TTS,
                "Skipping TTS for beaconId=${payload.beaconId}, messageCode=${payload.messageCode}: no local message definition",
            )
            persistReceiverDecision(
                payload = payload,
                detectedAt = detectedAt,
                rssi = smoothedRssi,
                wasAnnounced = false,
                gateText = "Najava dozvoljena.",
                arbitrationText = "Nema lokalne TTS poruke za ovaj beacon.",
                ttsError = "Nema lokalne TTS poruke za ovaj beacon.",
            )
            return
        }

        var candidate = AnnouncementCandidate(
            beaconId = payload.beaconId,
            messageCode = payload.messageCode,
            pointType = payload.pointType,
            priority = payload.priority,
            protocolVersion = payload.protocolVersion,
            azimuthDegrees = payload.azimuthDegrees,
            messageDefinition = messageDefinition,
            detectedAt = detectedAt,
            smoothedRssi = smoothedRssi,
        )

        val directionResolution = resolveDirectionResolution(candidate)
        val shouldTrackBehindPass = messageDefinition.behindSpeechPolicy ==
            BehindSpeechPolicy.PASS_CONFIRMED_MESSAGE &&
            directionResolution.estimate.direction == DirectionLabel.BEHIND

        if (shouldTrackBehindPass) {
            when (
                val behindPassResult = behindPassTracker.observe(
                    beaconId = candidate.beaconId,
                    messageCode = candidate.messageCode,
                    smoothedRssi = candidate.smoothedRssi,
                    detectedAt = candidate.detectedAt,
                )
            ) {
                is BehindPassResult.Tracking -> {
                    AppLogger.d(
                        LogTag.RSSI,
                        "Behind pass tracking beaconId=${candidate.beaconId}, messageCode=${candidate.messageCode}, samples=${behindPassResult.sampleCount}, status=${behindPassResult.statusText}",
                    )
                    updateBehindPassUi(
                        candidate = candidate,
                        directionResolution = directionResolution,
                        statusText = behindPassResult.statusText,
                        sampleCount = behindPassResult.sampleCount,
                        gateText = "Objekat je iza vas, cekam potvrdu prolaska.",
                    )
                    return
                }

                is BehindPassResult.Passed -> {
                    AppLogger.d(
                        LogTag.RSSI,
                        "Behind pass confirmed beaconId=${candidate.beaconId}, messageCode=${candidate.messageCode}, samples=${behindPassResult.sampleCount}",
                    )
                    candidate = candidate.copy(passConfirmedBehind = true)
                    updateBehindPassUi(
                        candidate = candidate,
                        directionResolution = directionResolution,
                        statusText = behindPassResult.statusText,
                        sampleCount = behindPassResult.sampleCount,
                        gateText = "Prolazak potvrdjen.",
                    )
                }
            }
        } else {
            clearBehindPassTracking(candidate)
            if (continuousBehindTrackingOnly) {
                return
            }
        }

        if (continuousBehindTrackingOnly && !candidate.passConfirmedBehind) {
            return
        }

        val cooldownResult = cooldownRepository.check(
            beaconId = candidate.beaconId,
            messageCode = candidate.messageCode,
            now = candidate.detectedAt,
        )
        val gateText = gateDecisionText(cooldownResult)
        if (cooldownResult.isBlocked) {
            AppLogger.d(
                LogTag.RSSI,
                "Gate blocked beaconId=${candidate.beaconId}, messageCode=${candidate.messageCode}, remainingMs=${cooldownResult.remainingMs}",
            )
            persistReceiverDecision(
                payload = candidate.toPayload(),
                detectedAt = candidate.detectedAt,
                rssi = candidate.smoothedRssi,
                wasAnnounced = false,
                gateText = gateText,
                arbitrationText = "Kandidat je odbacen zbog cooldown-a.",
            )
            return
        }

        AppLogger.d(
            LogTag.RSSI,
            "Gate allowed beaconId=${candidate.beaconId}, messageCode=${candidate.messageCode}, detectedAt=${candidate.detectedAt}",
        )

        handleArbitrationDecision(
            decision = announcementArbiter.submitCandidate(
                candidate = candidate,
                canSpeakImmediately = canSpeakImmediately(candidate.detectedAt),
            ),
            gateText = gateText,
        )
    }

    private fun updateBehindPassUi(
        candidate: AnnouncementCandidate,
        directionResolution: DirectionResolution,
        statusText: String,
        sampleCount: Int,
        gateText: String,
    ) {
        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(
                        lastGateDecisionText = gateText,
                        lastEligibleForAnnouncement = null,
                        lastDirectionLabel = directionResolution.estimate.direction,
                        lastRelativeAngleDegrees = directionResolution.estimate.relativeAngleDegrees,
                        directionFallbackReason = directionResolution.fallbackReason,
                        behindPassStatusText = statusText,
                        behindPassTrackingBeaconId = candidate.beaconId,
                        behindPassSampleCount = sampleCount,
                    ),
                ),
            )
        }
    }

    private fun handleArbitrationDecision(
        decision: AnnouncementArbitrationResult,
        gateText: String,
    ) {
        when (decision) {
            is AnnouncementArbitrationResult.SpeakNow -> {
                queueAnnouncementCandidate(
                    candidate = decision.candidate,
                    gateText = gateText,
                    arbitrationText = "Kandidat ide odmah u glasovnu najavu.",
                )
            }

            is AnnouncementArbitrationResult.Queued -> {
                AppLogger.d(
                    LogTag.TTS,
                    "Candidate queued beaconId=${decision.candidate.beaconId}, priority=${decision.candidate.priority}, rssi=${decision.candidate.smoothedRssi}",
                )
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                lastGateDecisionText = gateText,
                                lastEligibleForAnnouncement = null,
                                lastArbitrationDecisionText = "Kandidat ceka zavrsetak trenutne poruke.",
                            ),
                        ),
                    )
                }
            }

            is AnnouncementArbitrationResult.ReplacedPending -> {
                AppLogger.d(
                    LogTag.TTS,
                    "Pending candidate replaced oldBeaconId=${decision.previousCandidate.beaconId}, newBeaconId=${decision.replacementCandidate.beaconId}",
                )
                viewModelScope.launch {
                    recordBackgroundDroppedCandidate(
                        candidate = decision.previousCandidate,
                        arbitrationText = "Kandidat je zamenjen boljim cekajucim beacon-om.",
                    )
                    _uiState.update { state ->
                        state.copy(
                            receiverState = recomputeReceiverState(
                                state,
                                state.receiverState.copy(
                                    lastGateDecisionText = gateText,
                                    lastEligibleForAnnouncement = null,
                                    lastArbitrationDecisionText = "Cekajuci kandidat je zamenjen boljim beacon-om.",
                                ),
                            ),
                        )
                    }
                }
            }

            is AnnouncementArbitrationResult.RefreshedPending -> {
                AppLogger.d(
                    LogTag.TTS,
                    "Pending candidate refreshed beaconId=${decision.candidate.beaconId}, rssi=${decision.candidate.smoothedRssi}",
                )
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(
                            state,
                            state.receiverState.copy(
                                lastGateDecisionText = gateText,
                                lastEligibleForAnnouncement = null,
                                lastArbitrationDecisionText = "Cekajuci kandidat je osvezen novijim signalom.",
                            ),
                        ),
                    )
                }
            }

            is AnnouncementArbitrationResult.DroppedLowerRank -> {
                AppLogger.d(
                    LogTag.TTS,
                    "Candidate dropped by arbiter beaconId=${decision.candidate.beaconId}, priority=${decision.candidate.priority}, rssi=${decision.candidate.smoothedRssi}",
                )
                viewModelScope.launch {
                    persistReceiverDecision(
                        payload = decision.candidate.toPayload(),
                        detectedAt = decision.candidate.detectedAt,
                        rssi = decision.candidate.smoothedRssi,
                        wasAnnounced = false,
                        gateText = gateText,
                        arbitrationText = "Kandidat je odbacen jer postoji vazniji ili blizi beacon.",
                    )
                }
            }
        }
    }

    private fun queueAnnouncementCandidate(
        candidate: AnnouncementCandidate,
        gateText: String,
        arbitrationText: String,
        preserveLastDetection: Boolean = false,
    ) {
        val directionResolution = resolveDirectionResolution(candidate)
        val resolvedText = if (candidate.passConfirmedBehind) {
            DirectionPromptBuilder.buildPassedText(candidate.messageDefinition)
        } else {
            directionResolution.resolvedText
        }
        val utteranceId = "audionav-${UUID.randomUUID()}"
        when (
            val speakResult = ttsAnnouncer.announce(
                text = resolvedText,
                utteranceId = utteranceId,
            )
        ) {
            TtsSpeakResult.Queued -> {
                val announcedAt = maxOf(candidate.detectedAt, timeProvider())
                AppLogger.d(
                    LogTag.TTS,
                    "TTS queued beaconId=${candidate.beaconId}, messageCode=${candidate.messageCode}, utteranceId=$utteranceId",
                )
                announcementArbiter.onAnnouncementQueued(candidate, utteranceId, resolvedText)
                viewModelScope.launch {
                    persistReceiverDecision(
                        payload = candidate.toPayload(),
                        detectedAt = candidate.detectedAt,
                        rssi = candidate.smoothedRssi,
                        wasAnnounced = true,
                        gateText = gateText,
                        arbitrationText = arbitrationText,
                        preserveLastDetection = preserveLastDetection,
                        announcedAt = announcedAt,
                        spokenText = resolvedText,
                        spokenAt = announcedAt,
                        resolvedText = resolvedText,
                        directionLabel = directionResolution.estimate.direction,
                        relativeAngleDegrees = directionResolution.estimate.relativeAngleDegrees,
                        directionFallbackReason = directionResolution.fallbackReason,
                    )
                }
            }

            is TtsSpeakResult.Failed -> {
                AppLogger.e(
                    LogTag.TTS,
                    "TTS failed before queue beaconId=${candidate.beaconId}, messageCode=${candidate.messageCode}: ${speakResult.message}",
                )
                viewModelScope.launch {
                    persistReceiverDecision(
                        payload = candidate.toPayload(),
                        detectedAt = candidate.detectedAt,
                        rssi = candidate.smoothedRssi,
                        wasAnnounced = false,
                        gateText = gateText,
                        arbitrationText = "Kandidat nije mogao da bude zakazan u TTS.",
                        preserveLastDetection = preserveLastDetection,
                        ttsError = speakResult.message,
                        resolvedText = resolvedText,
                        directionLabel = directionResolution.estimate.direction,
                        relativeAngleDegrees = directionResolution.estimate.relativeAngleDegrees,
                        directionFallbackReason = directionResolution.fallbackReason,
                    )
                }
            }

            is TtsSpeakResult.SkippedNotReady -> {
                AppLogger.w(
                    LogTag.TTS,
                    "TTS skipped before queue beaconId=${candidate.beaconId}, messageCode=${candidate.messageCode}: ${speakResult.message}",
                )
                viewModelScope.launch {
                    persistReceiverDecision(
                        payload = candidate.toPayload(),
                        detectedAt = candidate.detectedAt,
                        rssi = candidate.smoothedRssi,
                        wasAnnounced = false,
                        gateText = gateText,
                        arbitrationText = "Kandidat nije mogao da bude zakazan u TTS.",
                        preserveLastDetection = preserveLastDetection,
                        ttsError = speakResult.message,
                        resolvedText = resolvedText,
                        directionLabel = directionResolution.estimate.direction,
                        relativeAngleDegrees = directionResolution.estimate.relativeAngleDegrees,
                        directionFallbackReason = directionResolution.fallbackReason,
                    )
                }
            }
        }
    }

    private fun resolveDirectionResolution(
        candidate: AnnouncementCandidate,
    ): DirectionResolution {
        val headingEstimate = currentHeadingEstimate()
        val localHeadingDegrees = currentLocalHeadingDegrees(headingEstimate)
        val directionEstimate = DirectionEstimator.estimate(
            beaconAzimuthDegrees = candidate.azimuthDegrees,
            userHeadingDegrees = localHeadingDegrees,
            headingConfidence = headingEstimate?.confidence ?: DirectionConfidence.LOW,
        )
        val resolvedText = DirectionPromptBuilder.buildTtsText(
            definition = candidate.messageDefinition,
            directionEstimate = directionEstimate,
        )
        val fallbackReason = when {
            directionEstimate.direction != DirectionLabel.UNKNOWN -> null
            candidate.azimuthDegrees == null -> "Koriscena je genericka poruka jer beacon ne sadrzi azimut."
            receiverHeadingOffsetDegrees == null -> {
                "Koriscena je genericka poruka jer smer nije kalibrisan."
            }

            headingEstimate == null -> "Koriscena je genericka poruka jer heading jos nije dostupan."
            headingEstimate.confidence != DirectionConfidence.HIGH -> {
                "Koriscena je genericka poruka jer heading nije bio stabilan."
            }

            else -> "Koriscena je genericka poruka jer smer nije mogao da se odredi."
        }

        return DirectionResolution(
            estimate = directionEstimate,
            resolvedText = resolvedText,
            fallbackReason = fallbackReason,
        )
    }

    private fun buildPendingAnnouncementPreview(candidate: AnnouncementCandidate): String {
        if (candidate.passConfirmedBehind) {
            return DirectionPromptBuilder.buildPassedText(candidate.messageDefinition)
        }
        val headingEstimate = currentHeadingEstimate()
        val directionEstimate = DirectionEstimator.estimate(
            beaconAzimuthDegrees = candidate.azimuthDegrees,
            userHeadingDegrees = currentLocalHeadingDegrees(headingEstimate),
            headingConfidence = headingEstimate?.confidence ?: DirectionConfidence.LOW,
        )
        return DirectionPromptBuilder.buildUiText(candidate.messageDefinition, directionEstimate)
    }

    private fun clearBehindPassTracking(candidate: AnnouncementCandidate) {
        if (candidate.messageDefinition.behindSpeechPolicy != BehindSpeechPolicy.PASS_CONFIRMED_MESSAGE) {
            return
        }

        clearBehindPassTracking(
            beaconId = candidate.beaconId,
            messageCode = candidate.messageCode,
        )
    }

    private fun clearBehindPassTracking(
        beaconId: String,
        messageCode: Short,
    ) {
        behindPassTracker.clear(beaconId, messageCode)
        _uiState.update { state ->
            val shouldClearUi = state.receiverState.behindPassTrackingBeaconId == beaconId
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    if (shouldClearUi) {
                        state.receiverState.copy(
                            behindPassStatusText = null,
                            behindPassTrackingBeaconId = null,
                            behindPassSampleCount = 0,
                        )
                    } else {
                        state.receiverState
                    },
                ),
            )
        }
    }

    private fun currentHeadingEstimate(): HeadingEstimate? {
        val liveEstimate = headingSensorController.latestEstimate()
        if (liveEstimate != null) {
            latestHeadingEstimate = liveEstimate
            return liveEstimate
        }
        return latestHeadingEstimate
    }

    private fun currentLocalHeadingDegrees(
        headingEstimate: HeadingEstimate? = currentHeadingEstimate(),
    ): Int? {
        return LocalDirectionFrame.toLocalHeading(
            rawHeadingDegrees = headingEstimate?.headingDegrees,
            headingOffsetDegrees = receiverHeadingOffsetDegrees,
        )
    }

    private fun handleTtsPlaybackEvent(event: TtsPlaybackEvent) {
        viewModelScope.launch {
            val uiUtteranceId = activeUiUtteranceId
            if (uiUtteranceId != null && event.matchesUtterance(uiUtteranceId)) {
                handleUiTtsPlaybackEvent(event)
                return@launch
            }

            val interruptedUtteranceId = interruptedAnnouncementUtteranceId
            if (
                interruptedUtteranceId != null &&
                event is TtsPlaybackEvent.Stopped &&
                event.utteranceId == interruptedUtteranceId
            ) {
                AppLogger.d(
                    LogTag.TTS,
                    "Navigation TTS interrupted by UI explore for utteranceId=${event.utteranceId}",
                )
                interruptedAnnouncementUtteranceId = null
                val finishedAnnouncement = announcementArbiter.onPlaybackFinished(event.utteranceId)
                if (finishedAnnouncement != null) {
                    _uiState.update { state ->
                        state.copy(
                            receiverState = recomputeReceiverState(state, state.receiverState),
                        )
                    }
                }
                return@launch
            }

            when (event) {
                is TtsPlaybackEvent.Started -> {
                    AppLogger.d(LogTag.TTS, "TTS playback started for utteranceId=${event.utteranceId}")
                }

                is TtsPlaybackEvent.Done -> {
                    AppLogger.d(LogTag.TTS, "TTS playback done for utteranceId=${event.utteranceId}")
                    val finishedAnnouncement = announcementArbiter.onPlaybackFinished(event.utteranceId)
                    if (finishedAnnouncement != null) {
                        scheduleAnnouncementGap()
                    }
                }

                is TtsPlaybackEvent.Error -> {
                    AppLogger.e(
                        LogTag.TTS,
                        "TTS playback error for utteranceId=${event.utteranceId}: ${event.message}",
                    )
                    val finishedAnnouncement = announcementArbiter.onPlaybackFinished(event.utteranceId)
                    if (finishedAnnouncement != null) {
                        _uiState.update { state ->
                            state.copy(
                                receiverState = recomputeReceiverState(
                                    state,
                                    state.receiverState.copy(
                                        lastTtsError = event.message,
                                        lastArbitrationDecisionText = "Doslo je do TTS greske tokom reprodukcije.",
                                    ),
                                ),
                            )
                        }
                        dispatchPendingAnnouncementIfPossible()
                    }
                }

                is TtsPlaybackEvent.Stopped -> {
                    AppLogger.d(LogTag.TTS, "TTS playback stopped for utteranceId=${event.utteranceId}")
                    val finishedAnnouncement = announcementArbiter.onPlaybackFinished(event.utteranceId)
                    if (finishedAnnouncement != null) {
                        dispatchPendingAnnouncementIfPossible()
                    }
                }
            }
        }
    }

    private fun scheduleAnnouncementGap() {
        cancelAnnouncementGap()
        val gapStart = timeProvider()
        announcementGapUntilMs = gapStart + GLOBAL_ANNOUNCEMENT_GAP_MS
        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(
                        lastArbitrationDecisionText = "Globalni razmak izmedju dve najave je aktivan.",
                    ),
                ),
            )
        }

        announcementGapJob = viewModelScope.launch {
            delay(GLOBAL_ANNOUNCEMENT_GAP_MS)
            announcementGapJob = null
            announcementGapUntilMs = null
            dispatchPendingAnnouncementIfPossible()
        }
    }

    private suspend fun dispatchPendingAnnouncementIfPossible() {
        val now = timeProvider()
        if (isAnnouncementGapActive(now)) {
            return
        }
        if (announcementArbiter.currentActive() != null) {
            return
        }

        when (val pendingResult = announcementArbiter.takePendingCandidate(now)) {
            PendingAnnouncementResult.None -> {
                _uiState.update { state ->
                    state.copy(
                        receiverState = recomputeReceiverState(state, state.receiverState),
                    )
                }
            }

            is PendingAnnouncementResult.Ready -> {
                AppLogger.d(
                    LogTag.TTS,
                    "Pending candidate promoted beaconId=${pendingResult.candidate.beaconId}, priority=${pendingResult.candidate.priority}, rssi=${pendingResult.candidate.smoothedRssi}",
                )
                queueAnnouncementCandidate(
                    candidate = pendingResult.candidate,
                    gateText = "Najava dozvoljena.",
                    arbitrationText = "Cekajuci kandidat je dosao na red za glasovnu najavu.",
                    preserveLastDetection = true,
                )
            }
        }
    }

    private suspend fun recordBackgroundDroppedCandidate(
        candidate: AnnouncementCandidate,
        arbitrationText: String,
    ) {
        persistReceiverDecision(
            payload = candidate.toPayload(),
            detectedAt = candidate.detectedAt,
            rssi = candidate.smoothedRssi,
            wasAnnounced = false,
            gateText = "Najava dozvoljena.",
            arbitrationText = arbitrationText,
            preserveLastDetection = true,
        )
    }

    private suspend fun persistReceiverDecision(
        payload: rs.fon.hakaton.audionav.domain.DecodedBeaconPayload,
        detectedAt: Long,
        rssi: Int,
        wasAnnounced: Boolean,
        gateText: String,
        arbitrationText: String,
        preserveLastDetection: Boolean = false,
        ttsError: String? = null,
        announcedAt: Long = detectedAt,
        spokenText: String? = null,
        spokenAt: Long? = null,
        resolvedText: String? = null,
        directionLabel: DirectionLabel? = null,
        relativeAngleDegrees: Int? = null,
        directionFallbackReason: String? = null,
    ) {
        val detectedEvent = DetectedBeaconEvent(
            beaconId = payload.beaconId,
            detectedAt = detectedAt,
            rssi = rssi,
            pointType = payload.pointType,
            priority = payload.priority,
            messageCode = payload.messageCode,
            wasAnnounced = wasAnnounced,
        )
        cooldownRepository.recordStableEvent(detectedEvent, announcedAt)
        val recentEvents = cooldownRepository.recentEvents()

        _uiState.update { state ->
            val updatedReceiverState = state.receiverState.copy(
                lastGateDecisionText = gateText,
                lastEligibleForAnnouncement = wasAnnounced,
                lastAnnouncementAt = if (wasAnnounced) announcedAt else state.receiverState.lastAnnouncementAt,
                recentEvents = recentEvents,
                lastTtsError = ttsError,
                lastDecodedText = resolvedText ?: state.receiverState.lastDecodedText,
                lastSpokenText = spokenText ?: state.receiverState.lastSpokenText,
                lastSpokenAt = spokenAt ?: state.receiverState.lastSpokenAt,
                lastArbitrationDecisionText = arbitrationText,
                lastDirectionLabel = directionLabel ?: state.receiverState.lastDirectionLabel,
                lastRelativeAngleDegrees = relativeAngleDegrees ?: state.receiverState.lastRelativeAngleDegrees,
                directionFallbackReason = directionFallbackReason,
            )

            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    if (preserveLastDetection) {
                        updatedReceiverState
                    } else {
                        updatedReceiverState.copy(
                            lastDetectedBeaconId = payload.beaconId,
                            lastDetectedPointType = payload.pointType,
                            lastDetectedPriority = payload.priority,
                            lastDetectedMessageCode = payload.messageCode,
                            lastDetectedAt = detectedAt,
                            lastRssi = rssi,
                        )
                    },
                ),
            )
        }
    }

    private fun isAnnouncementGapActive(now: Long): Boolean {
        val gapUntil = announcementGapUntilMs ?: return false
        return if (now >= gapUntil) {
            announcementGapUntilMs = null
            false
        } else {
            true
        }
    }

    private fun canSpeakImmediately(now: Long): Boolean {
        return !isAnnouncementGapActive(now) &&
            announcementArbiter.currentActive() == null &&
            announcementArbiter.currentPending() == null
    }

    private fun gateDecisionText(cooldownResult: CooldownCheckResult): String {
        return if (!cooldownResult.isBlocked) {
            "Najava dozvoljena."
        } else {
            "Beacon je u cooldown-u jos ${formatRemainingCooldownSeconds(cooldownResult.remainingMs)}s."
        }
    }

    private fun scheduleReceiverRetry() {
        if (receiverRetryJob != null) {
            return
        }

        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(
                        isScanning = false,
                        retryScheduled = true,
                    ),
                ),
            )
        }

        receiverRetryJob = viewModelScope.launch {
            delay(RECEIVER_RETRY_DELAY_MS)
            receiverRetryJob = null

            if (!shouldScheduleReceiverRetry()) {
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
                        state.receiverState.copy(retryScheduled = false),
                    ),
                )
            }
            onStartReceiverClick()
        }
    }

    private fun shouldScheduleReceiverRetry(): Boolean {
        val state = _uiState.value
        return receiverAutoRestartAllowed &&
            state.selectedMode == AppMode.RECEIVER &&
            isReceiverRuntimeReady(state) &&
            !state.receiverState.isScanning
    }

    private fun stopReceiverScanning(errorText: String?) {
        receiverAutoRestartAllowed = false
        cancelReceiverRetry()
        cancelAnnouncementGap()
        beaconScannerController.stopScanning()
        ttsAnnouncer.stop()
        activeUiUtteranceId = null
        interruptedAnnouncementUtteranceId = null
        announcementArbiter.clear()
        behindPassTracker.reset()
        rssiStabilizer.reset()

        _uiState.update { state ->
            state.copy(
                receiverState = recomputeReceiverState(
                    state,
                    state.receiverState.copy(
                        isScanning = false,
                        retryScheduled = false,
                        errorText = errorText,
                        stabilizationProgress = 0,
                        lastGateDecisionText = null,
                        lastEligibleForAnnouncement = null,
                        lastArbitrationDecisionText = null,
                        lastRelativeAngleDegrees = null,
                        lastDirectionLabel = DirectionLabel.UNKNOWN,
                        directionFallbackReason = null,
                        behindPassStatusText = null,
                        behindPassTrackingBeaconId = null,
                        behindPassSampleCount = 0,
                    ),
                ),
            )
        }
    }

    private fun cancelReceiverRetry() {
        receiverRetryJob?.cancel()
        receiverRetryJob = null
    }

    private fun cancelAnnouncementGap() {
        announcementGapJob?.cancel()
        announcementGapJob = null
        announcementGapUntilMs = null
    }

    private fun isReceiverRuntimeReady(state: AppUiState): Boolean {
        return state.permissionUiState.status == PermissionStatus.GRANTED &&
            state.bluetoothStatus == BluetoothStatus.READY &&
            state.receiverState.scannerSupported
    }

    private fun isReceiverSessionActive(): Boolean {
        val state = _uiState.value
        return state.selectedMode == AppMode.RECEIVER &&
            receiverAutoRestartAllowed &&
            state.receiverState.isScanning
    }

    private fun updateBeaconDraft(
        transform: (BeaconScreenState) -> BeaconScreenState,
    ) {
        _uiState.update { currentState ->
            val transformedState = transform(currentState.beaconState)
            currentState.copy(
                beaconState = recomputeBeaconState(
                    currentState,
                    transformedState,
                ),
            )
        }

        val shouldPersist = BeaconConfigValidator.isValid(
            currentBeaconConfig(isActive = _uiState.value.beaconState.isAdvertising),
        )
        if (shouldPersist) {
            persistCurrentBeaconConfig(isActive = _uiState.value.beaconState.isAdvertising)
        }
    }

    private fun syncHeadingSensorLifecycle() {
        val shouldRun = receiverScreenVisible
        if (shouldRun) {
            headingSensorController.start()
            if (headingPollingJob == null) {
                headingPollingJob = viewModelScope.launch {
                    while (true) {
                        refreshHeadingSnapshot()
                        delay(250L)
                    }
                }
            } else {
                refreshHeadingSnapshot()
            }
        } else {
            headingPollingJob?.cancel()
            headingPollingJob = null
            headingSensorController.stop()
            latestHeadingEstimate = null
            _uiState.update { state ->
                state.copy(
                    beaconState = recomputeBeaconState(state, state.beaconState),
                    receiverState = recomputeReceiverState(state, state.receiverState),
                )
            }
        }
    }

    private fun refreshHeadingSnapshot() {
        latestHeadingEstimate = headingSensorController.latestEstimate()
        _uiState.update { state ->
            state.copy(
                beaconState = recomputeBeaconState(state, state.beaconState),
                receiverState = recomputeReceiverState(state, state.receiverState),
            )
        }
    }

    private fun createInitialUiState(): AppUiState {
        val initialBeaconState = BeaconScreenState(
            beaconId = UUID.randomUUID().toString(),
            advertiserSupported = beaconAdvertiserController.isSupported(),
        )
        val initialReceiverState = ReceiverScreenState(
            scannerSupported = beaconScannerController.isSupported(),
            requiredStabilizationCount = rssiStabilizer.requiredConsecutiveReads,
            rssiThreshold = rssiStabilizer.rssiThresholdDbm,
        )
        val initialState = AppUiState(
            beaconState = initialBeaconState,
            receiverState = initialReceiverState,
        )

        return initialState.copy(
            beaconState = recomputeBeaconState(initialState, initialBeaconState),
            receiverState = recomputeReceiverState(initialState, initialReceiverState),
        )
    }

    private fun recomputeBeaconState(
        state: AppUiState,
        beaconState: BeaconScreenState,
        allowReadyStatus: Boolean = true,
    ): BeaconScreenState {
        val advertiserSupported = beaconAdvertiserController.isSupported()
        val validConfig = BeaconConfigValidator.isValid(
            BeaconConfig(
                beaconId = beaconState.beaconId,
                label = beaconState.labelInput.trim(),
                pointType = beaconState.selectedPointType,
                priority = beaconState.selectedPriority,
                messageCode = beaconState.selectedMessageCode,
                azimuthDegrees = beaconState.azimuthInput.toIntOrNull(),
                isActive = beaconState.isAdvertising,
                lastUpdatedAt = System.currentTimeMillis(),
            ),
        )
        val isReady = state.permissionUiState.status == PermissionStatus.GRANTED &&
            state.bluetoothStatus == BluetoothStatus.READY &&
            advertiserSupported &&
            validConfig

        val statusText = when {
            beaconState.errorText != null -> "Error"
            beaconState.isAdvertising -> "Advertising"
            isReady && allowReadyStatus -> "Ready"
            else -> "Idle"
        }

        return beaconState.copy(
            advertiserSupported = advertiserSupported,
            isReady = isReady,
            statusText = statusText,
            currentHeadingDegrees = latestHeadingEstimate?.headingDegrees,
            headingConfidenceText = latestHeadingEstimate?.confidence?.let { it.directionConfidenceToDisplayText() }
                ?: DirectionConfidence.LOW.directionConfidenceToDisplayText(),
        )
    }

    private fun recomputeReceiverState(
        state: AppUiState,
        receiverState: ReceiverScreenState,
    ): ReceiverScreenState {
        val scannerSupported = beaconScannerController.isSupported()
        val headingEstimate = latestHeadingEstimate
        val localHeadingDegrees = currentLocalHeadingDegrees(headingEstimate)
        val isReady = state.permissionUiState.status == PermissionStatus.GRANTED &&
            state.bluetoothStatus == BluetoothStatus.READY &&
            scannerSupported

        val statusText = when {
            receiverState.errorText != null -> "Error"
            receiverState.isScanning -> "Scanning"
            else -> "Not Scanning"
        }

        return receiverState.copy(
            scannerSupported = scannerSupported,
            isReady = isReady,
            statusText = statusText,
            currentAnnouncementBeaconId = announcementArbiter.currentActive()?.candidate?.beaconId,
            currentAnnouncementText = announcementArbiter.currentActive()?.spokenText,
            currentAnnouncementPriority = announcementArbiter.currentActive()?.candidate?.priority,
            currentAnnouncementRssi = announcementArbiter.currentActive()?.candidate?.smoothedRssi,
            pendingAnnouncementBeaconId = announcementArbiter.currentPending()?.beaconId,
            pendingAnnouncementText = announcementArbiter.currentPending()?.let(::buildPendingAnnouncementPreview),
            pendingAnnouncementPriority = announcementArbiter.currentPending()?.priority,
            pendingAnnouncementRssi = announcementArbiter.currentPending()?.smoothedRssi,
            globalAnnouncementGapUntil = announcementGapUntilMs,
            currentHeadingDegrees = headingEstimate?.headingDegrees,
            localHeadingDegrees = localHeadingDegrees,
            headingConfidenceText = headingEstimate?.confidence?.let { it.directionConfidenceToDisplayText() }
                ?: DirectionConfidence.LOW.directionConfidenceToDisplayText(),
            isDirectionCalibrated = receiverHeadingOffsetDegrees != null,
            directionCalibrationText = receiverState.directionCalibrationText,
        )
    }

    private fun currentBeaconConfig(isActive: Boolean): BeaconConfig {
        val beaconState = _uiState.value.beaconState
        return BeaconConfig(
            beaconId = beaconState.beaconId,
            label = beaconState.labelInput.trim(),
            pointType = beaconState.selectedPointType,
            priority = beaconState.selectedPriority,
            messageCode = beaconState.selectedMessageCode,
            azimuthDegrees = beaconState.azimuthInput.toIntOrNull(),
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

    private fun announceUiText(
        text: String,
        utterancePrefix: String,
    ) {
        val utteranceId = "$utterancePrefix-${UUID.randomUUID()}"
        val interruptedUtteranceId = announcementArbiter.currentActive()?.utteranceId
        when (
            val speakResult = ttsAnnouncer.announce(
                text = text,
                utteranceId = utteranceId,
            )
        ) {
            TtsSpeakResult.Queued -> {
                activeUiUtteranceId = utteranceId
                interruptedAnnouncementUtteranceId = interruptedUtteranceId
                AppLogger.d(
                    LogTag.TTS,
                    "UI TTS queued for utteranceId=$utteranceId",
                )
            }

            is TtsSpeakResult.SkippedNotReady -> {
                activeUiUtteranceId = null
                interruptedAnnouncementUtteranceId = null
                AppLogger.w(
                    LogTag.TTS,
                    "UI TTS skipped for utteranceId=$utteranceId: ${speakResult.message}",
                )
            }

            is TtsSpeakResult.Failed -> {
                activeUiUtteranceId = null
                interruptedAnnouncementUtteranceId = null
                AppLogger.e(
                    LogTag.TTS,
                    "UI TTS failed for utteranceId=$utteranceId: ${speakResult.message}",
                )
            }
        }
    }

    private fun handleUiTtsPlaybackEvent(event: TtsPlaybackEvent) {
        when (event) {
            is TtsPlaybackEvent.Started -> {
                AppLogger.d(LogTag.TTS, "UI TTS started for utteranceId=${event.utteranceId}")
            }

            is TtsPlaybackEvent.Done -> {
                AppLogger.d(LogTag.TTS, "UI TTS done for utteranceId=${event.utteranceId}")
                activeUiUtteranceId = null
            }

            is TtsPlaybackEvent.Error -> {
                AppLogger.w(
                    LogTag.TTS,
                    "UI TTS error for utteranceId=${event.utteranceId}: ${event.message}",
                )
                activeUiUtteranceId = null
            }

            is TtsPlaybackEvent.Stopped -> {
                AppLogger.d(LogTag.TTS, "UI TTS stopped for utteranceId=${event.utteranceId}")
                activeUiUtteranceId = null
            }
        }
    }

    private fun TtsPlaybackEvent.matchesUtterance(utteranceId: String): Boolean {
        return when (this) {
            is TtsPlaybackEvent.Started -> this.utteranceId == utteranceId
            is TtsPlaybackEvent.Done -> this.utteranceId == utteranceId
            is TtsPlaybackEvent.Error -> this.utteranceId == utteranceId
            is TtsPlaybackEvent.Stopped -> this.utteranceId == utteranceId
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }

    private fun formatRemainingCooldownSeconds(remainingMs: Long): Long {
        return (remainingMs + 999L) / 1_000L
    }

    override fun onCleared() {
        cancelAnnouncementGap()
        announcementArbiter.clear()
        headingPollingJob?.cancel()
        headingSensorController.stop()
        super.onCleared()
        ttsAnnouncer.shutdown()
    }

    companion object {
        private const val RECEIVER_RETRY_DELAY_MS = 3_000L
        private const val GLOBAL_ANNOUNCEMENT_GAP_MS = 2_000L
        private const val UNKNOWN_LOCAL_MESSAGE_TEXT = "Nepoznata lokalna poruka za ovaj beacon."
    }
}

private data class DirectionResolution(
    val estimate: rs.fon.hakaton.audionav.domain.DirectionEstimate,
    val resolvedText: String,
    val fallbackReason: String?,
)

private fun AnnouncementCandidate.toPayload(): rs.fon.hakaton.audionav.domain.DecodedBeaconPayload {
    return rs.fon.hakaton.audionav.domain.DecodedBeaconPayload(
        protocolVersion = protocolVersion,
        beaconId = beaconId,
        pointType = pointType,
        priority = priority,
        messageCode = messageCode,
        azimuthDegrees = azimuthDegrees,
    )
}
