package rs.fon.hakaton.audionav.viewmodel

import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import rs.fon.hakaton.audionav.MainDispatcherRule
import rs.fon.hakaton.audionav.ble.BeaconAdvertiseResult
import rs.fon.hakaton.audionav.ble.BeaconAdvertiserController
import rs.fon.hakaton.audionav.ble.BeaconScanEvent
import rs.fon.hakaton.audionav.ble.BeaconScannerController
import rs.fon.hakaton.audionav.domain.AppMode
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.BluetoothStatus
import rs.fon.hakaton.audionav.domain.CooldownEntry
import rs.fon.hakaton.audionav.domain.DecodedBeaconPayload
import rs.fon.hakaton.audionav.domain.DetectedBeaconEvent
import rs.fon.hakaton.audionav.domain.DirectionConfidence
import rs.fon.hakaton.audionav.domain.DirectionLabel
import rs.fon.hakaton.audionav.domain.HeadingEstimate
import rs.fon.hakaton.audionav.domain.HeadingSensorController
import rs.fon.hakaton.audionav.domain.MessageCatalog
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.domain.PermissionUiState
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority
import rs.fon.hakaton.audionav.domain.RssiStabilizer
import rs.fon.hakaton.audionav.storage.BeaconConfigStorage
import rs.fon.hakaton.audionav.storage.CooldownRepository
import rs.fon.hakaton.audionav.storage.ReceiverRuntimeSnapshot
import rs.fon.hakaton.audionav.storage.ReceiverRuntimeStorage
import rs.fon.hakaton.audionav.tts.TtsAnnouncer
import rs.fon.hakaton.audionav.tts.TtsPlaybackEvent
import rs.fon.hakaton.audionav.tts.TtsSpeakResult
import rs.fon.hakaton.audionav.tts.TtsStatus

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model initializes beacon draft with valid uuid and default catalog`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value.beaconState
        val uuid = UUID.fromString(state.beaconId)

        assertNotNull(uuid)
        assertEquals(PointType.CROSSWALK, state.selectedPointType)
        assertEquals(
            MessageCatalog.definitionsFor(PointType.CROSSWALK),
            state.availableMessages,
        )
        assertTrue(
            state.availableMessages.any { it.messageCode == state.selectedMessageCode },
        )
    }

    @Test
    fun `load persisted beacon config hydrates beacon state`() = runTest {
        val storage = FakeBeaconConfigStorage(
            storedConfig = BeaconConfig(
                beaconId = "123e4567-e89b-12d3-a456-426614174000",
                label = "Saved beacon",
                pointType = PointType.STAIRS,
                priority = Priority.HIGH,
                messageCode = 2,
                azimuthDegrees = 90,
                isActive = true,
                lastUpdatedAt = 99L,
            ),
        )
        val viewModel = createViewModel(storage = storage)

        viewModel.loadPersistedBeaconConfig()
        advanceUntilIdle()

        val state = viewModel.uiState.value.beaconState
        assertEquals("Saved beacon", state.labelInput)
        assertEquals(PointType.STAIRS, state.selectedPointType)
        assertEquals(Priority.HIGH, state.selectedPriority)
        assertEquals(2.toShort(), state.selectedMessageCode)
        assertEquals("90", state.azimuthInput)
        assertEquals(false, storage.storedConfig?.isActive)
        assertEquals(false, state.isAdvertising)
    }

    @Test
    fun `on beacon point type selected refreshes available messages and first valid message code`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onBeaconPointTypeSelected(PointType.STAIRS)
            advanceUntilIdle()

            val state = viewModel.uiState.value.beaconState
            assertEquals(PointType.STAIRS, state.selectedPointType)
            assertEquals(MessageCatalog.definitionsFor(PointType.STAIRS), state.availableMessages)
            assertEquals(2.toShort(), state.selectedMessageCode)
        }

    @Test
    fun `start guard blocks advertising when advertiser is unsupported`() = runTest {
        val advertiser = FakeBeaconAdvertiserController(supported = false)
        val viewModel = createViewModel(advertiser = advertiser)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onBeaconAzimuthChanged("90")

        viewModel.onStartBeaconClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value.beaconState
        assertEquals(0, advertiser.startCalls)
        assertEquals("Error", state.statusText)
        assertEquals("Uredaj ne podrzava BLE advertising.", state.errorText)
    }

    @Test
    fun `successful beacon start moves view model into advertising state and stores payload`() = runTest {
        val storage = FakeBeaconConfigStorage()
        val advertiser = FakeBeaconAdvertiserController(nextResult = BeaconAdvertiseResult.Started)
        val viewModel = createViewModel(
            storage = storage,
            advertiser = advertiser,
        )
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onBeaconAzimuthChanged("90")

        viewModel.onStartBeaconClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value.beaconState
        assertEquals(true, state.isAdvertising)
        assertEquals("Advertising", state.statusText)
        assertTrue(state.lastEncodedPayloadHex?.isNotBlank() == true)
        assertEquals(true, storage.storedConfig?.isActive)
    }

    @Test
    fun `stop beacon returns state to idle and persists inactive config`() = runTest {
        val storage = FakeBeaconConfigStorage()
        val advertiser = FakeBeaconAdvertiserController(nextResult = BeaconAdvertiseResult.Started)
        val viewModel = createViewModel(
            storage = storage,
            advertiser = advertiser,
        )
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onBeaconAzimuthChanged("90")
        viewModel.onStartBeaconClick()
        advanceUntilIdle()

        viewModel.onStopClick(AppMode.BEACON)
        advanceUntilIdle()

        val state = viewModel.uiState.value.beaconState
        assertEquals(false, state.isAdvertising)
        assertEquals("Idle", state.statusText)
        assertEquals(false, storage.storedConfig?.isActive)
        assertEquals(1, advertiser.stopCalls)
    }

    @Test
    fun `receiver start begins scanning when permissions and bluetooth are ready`() = runTest {
        val scanner = FakeBeaconScannerController()
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )

        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value.receiverState
        assertEquals(1, scanner.startCalls)
        assertEquals(true, state.isScanning)
        assertEquals("Scanning", state.statusText)
        assertEquals(null, state.errorText)
    }

    @Test
    fun `receiver start guard blocks scanning when scanner unsupported`() = runTest {
        val scanner = FakeBeaconScannerController(supported = false)
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )

        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value.receiverState
        assertEquals(0, scanner.startCalls)
        assertEquals("Error", state.statusText)
        assertEquals("Uredaj ne podrzava BLE skeniranje.", state.errorText)
    }

    @Test
    fun `receiver stop ignores late scan callbacks and stops active tts`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(beaconDetected(rssi = -60, detectedAt = 100L + index))
            advanceUntilIdle()
        }

        val stateBeforeStop = viewModel.uiState.value.receiverState
        assertEquals(1, ttsAnnouncer.announceCalls)
        assertEquals(102L, stateBeforeStop.lastDetectedAt)
        assertEquals(1, stateBeforeStop.recentEvents.size)

        viewModel.onStopClick(AppMode.RECEIVER)
        advanceUntilIdle()

        scanner.emitLate(beaconDetected(rssi = -55, detectedAt = 400L))
        advanceUntilIdle()

        val stateAfterLateEvent = viewModel.uiState.value.receiverState
        assertEquals(1, scanner.stopCalls)
        assertEquals(1, ttsAnnouncer.stopCalls)
        assertEquals(false, stateAfterLateEvent.isScanning)
        assertEquals(1, ttsAnnouncer.announceCalls)
        assertEquals(102L, stateAfterLateEvent.lastDetectedAt)
        assertEquals(1, stateAfterLateEvent.recentEvents.size)
    }

    @Test
    fun `receiver can restart scanning after stop`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        viewModel.onStopClick(AppMode.RECEIVER)
        advanceUntilIdle()

        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(beaconDetected(rssi = -60, detectedAt = 500L + index))
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals(2, scanner.startCalls)
        assertEquals(1, scanner.stopCalls)
        assertEquals(1, ttsAnnouncer.stopCalls)
        assertEquals(1, ttsAnnouncer.announceCalls)
        assertEquals(true, state.isScanning)
        assertEquals(1, state.recentEvents.size)
    }

    @Test
    fun `receiver needs three valid reads before stable event is recorded`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        scanner.emit(beaconDetected(rssi = -62, detectedAt = 100L))
        advanceUntilIdle()
        scanner.emit(beaconDetected(rssi = -61, detectedAt = 200L))
        advanceUntilIdle()

        var state = viewModel.uiState.value.receiverState
        assertEquals(2, state.stabilizationProgress)
        assertEquals(0, state.recentEvents.size)
        assertEquals(null, state.lastEligibleForAnnouncement)

        scanner.emit(beaconDetected(rssi = -60, detectedAt = 300L))
        advanceUntilIdle()

        state = viewModel.uiState.value.receiverState
        assertEquals(1, state.recentEvents.size)
        assertEquals(true, state.lastEligibleForAnnouncement)
        assertEquals("Najava dozvoljena.", state.lastGateDecisionText)
        assertEquals("Pe\u0161a\u010dki prelaz je ispred vas.", state.lastDecodedText)
        assertEquals("Pe\u0161a\u010dki prelaz je ispred vas.", state.lastSpokenText)
        assertEquals(DirectionLabel.AHEAD, state.lastDirectionLabel)
        assertEquals(300L, state.lastSpokenAt)
        assertEquals(1, ttsAnnouncer.announceCalls)
    }

    @Test
    fun `receiver without calibration falls back to generic direction prompt`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    pointType = PointType.ENTRANCE,
                    messageCode = 4,
                    rssi = -60,
                    detectedAt = 400L + index,
                    azimuthDegrees = 90,
                ),
            )
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals("Ulaz u blizini.", state.lastDecodedText)
        assertEquals(DirectionLabel.UNKNOWN, state.lastDirectionLabel)
        assertEquals(
            "Koriscena je genericka poruka jer smer nije kalibrisan.",
            state.directionFallbackReason,
        )
    }

    @Test
    fun `repeat last message does nothing when there is no spoken text`() = runTest {
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(ttsAnnouncer = ttsAnnouncer)
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value.receiverState

        viewModel.onRepeatLastMessage()
        advanceUntilIdle()

        assertEquals(0, ttsAnnouncer.announceCalls)
        assertEquals(stateBefore, viewModel.uiState.value.receiverState)
    }

    @Test
    fun `repeat last message replays only the last spoken text`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    pointType = PointType.STAIRS,
                    messageCode = 2,
                    rssi = -60,
                    detectedAt = 700L + index,
                ),
            )
            advanceUntilIdle()
        }

        val stateBeforeRepeat = viewModel.uiState.value.receiverState
        assertEquals("Stepenice su ispred vas.", stateBeforeRepeat.lastSpokenText)
        assertEquals(1, ttsAnnouncer.announceCalls)

        viewModel.onRepeatLastMessage()
        advanceUntilIdle()

        assertEquals(2, ttsAnnouncer.announceCalls)
        assertEquals("Stepenice su ispred vas.", ttsAnnouncer.announcedTexts.last())
        assertTrue(ttsAnnouncer.announcedUtteranceIds.last().startsWith("ui-repeat-"))
        assertEquals(stateBeforeRepeat, viewModel.uiState.value.receiverState)
    }

    @Test
    fun `touch explore announces control label without touching receiver pipeline`() = runTest {
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(ttsAnnouncer = ttsAnnouncer)
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value.receiverState

        viewModel.onTouchExploreControl("Pokreni skeniranje")
        advanceUntilIdle()

        assertEquals(1, ttsAnnouncer.announceCalls)
        assertEquals("Pokreni skeniranje", ttsAnnouncer.announcedTexts.last())
        assertTrue(ttsAnnouncer.announcedUtteranceIds.last().startsWith("ui-touch-"))
        assertEquals(stateBefore, viewModel.uiState.value.receiverState)
    }

    @Test
    fun `receiver valid payload with unknown message code shows fallback text after stabilization`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                BeaconScanEvent.BeaconDetected(
                    payload = DecodedBeaconPayload(
                        protocolVersion = 2,
                        beaconId = "123e4567-e89b-12d3-a456-426614174000",
                        pointType = PointType.CROSSWALK,
                        priority = Priority.MEDIUM,
                        messageCode = 99,
                        azimuthDegrees = 0,
                    ),
                    rssi = -58,
                    detectedAt = 500L + index,
                ),
            )
            advanceUntilIdle()
        }

        assertEquals(
            "Nepoznata lokalna poruka za ovaj beacon.",
            viewModel.uiState.value.receiverState.lastDecodedText,
        )
        assertEquals(1, viewModel.uiState.value.receiverState.recentEvents.size)
        assertEquals(0, ttsAnnouncer.announceCalls)
        assertEquals(
            "Nema lokalne TTS poruke za ovaj beacon.",
            viewModel.uiState.value.receiverState.lastTtsError,
        )
    }

    @Test
    fun `stable event inside cooldown is recorded but blocked`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(beaconDetected(rssi = -60, detectedAt = 1_000L + index))
            advanceUntilIdle()
        }

        scanner.emit(beaconDetected(rssi = -90, detectedAt = 2_000L))
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(beaconDetected(rssi = -60, detectedAt = 2_100L + index))
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals(2, state.recentEvents.size)
        assertEquals(false, state.recentEvents.first().wasAnnounced)
        assertEquals(false, state.lastEligibleForAnnouncement)
        assertTrue(state.lastGateDecisionText?.contains("cooldown-u") == true)
        assertEquals(1, ttsAnnouncer.announceCalls)
    }

    @Test
    fun `crosswalk behind tracking stays silent while signal is still rising`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        listOf(
            beaconDetected(rssi = -66, detectedAt = 100L, azimuthDegrees = 0),
            beaconDetected(rssi = -64, detectedAt = 200L, azimuthDegrees = 0),
            beaconDetected(rssi = -62, detectedAt = 300L, azimuthDegrees = 0),
            beaconDetected(rssi = -60, detectedAt = 800L, azimuthDegrees = 0),
            beaconDetected(rssi = -58, detectedAt = 1_300L, azimuthDegrees = 0),
            beaconDetected(rssi = -57, detectedAt = 1_800L, azimuthDegrees = 0),
        ).forEach { event ->
            scanner.emit(event)
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals(0, ttsAnnouncer.announceCalls)
        assertEquals(0, state.recentEvents.size)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", state.behindPassTrackingBeaconId)
        assertEquals(4, state.behindPassSampleCount)
        assertEquals("Objekat je iza vas, cekam potvrdu prolaska.", state.lastGateDecisionText)
        assertTrue(state.behindPassStatusText?.isNotBlank() == true)
    }

    @Test
    fun `crosswalk behind speaks passed message after confirmed decline`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        listOf(
            beaconDetected(rssi = -66, detectedAt = 100L, azimuthDegrees = 0),
            beaconDetected(rssi = -64, detectedAt = 200L, azimuthDegrees = 0),
            beaconDetected(rssi = -62, detectedAt = 300L, azimuthDegrees = 0),
            beaconDetected(rssi = -60, detectedAt = 800L, azimuthDegrees = 0),
            beaconDetected(rssi = -58, detectedAt = 1_300L, azimuthDegrees = 0),
            beaconDetected(rssi = -60, detectedAt = 1_800L, azimuthDegrees = 0),
            beaconDetected(rssi = -66, detectedAt = 2_300L, azimuthDegrees = 0),
            beaconDetected(rssi = -74, detectedAt = 2_800L, azimuthDegrees = 0),
        ).forEach { event ->
            scanner.emit(event)
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals(1, ttsAnnouncer.announceCalls)
        assertEquals("Pro\u0161li ste pe\u0161a\u010dki prelaz.", ttsAnnouncer.announcedTexts.last())
        assertEquals("Pro\u0161li ste pe\u0161a\u010dki prelaz.", state.lastSpokenText)
        assertEquals("Prolazak potvr\u0111en.", state.behindPassStatusText)
        assertEquals(1, state.recentEvents.size)
        assertEquals(true, state.recentEvents.first().wasAnnounced)
    }

    @Test
    fun `traffic light behind speaks passed message after confirmed decline`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        listOf(
            beaconDetected(
                rssi = -66,
                detectedAt = 100L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
            beaconDetected(
                rssi = -64,
                detectedAt = 200L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
            beaconDetected(
                rssi = -62,
                detectedAt = 300L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
            beaconDetected(
                rssi = -60,
                detectedAt = 800L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
            beaconDetected(
                rssi = -58,
                detectedAt = 1_300L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
            beaconDetected(
                rssi = -60,
                detectedAt = 1_800L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
            beaconDetected(
                rssi = -66,
                detectedAt = 2_300L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
            beaconDetected(
                rssi = -74,
                detectedAt = 2_800L,
                pointType = PointType.TRAFFIC_LIGHT,
                messageCode = 6,
                azimuthDegrees = 0,
            ),
        ).forEach { event ->
            scanner.emit(event)
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals(1, ttsAnnouncer.announceCalls)
        assertEquals("Pro\u0161li ste semafor.", ttsAnnouncer.announcedTexts.last())
        assertEquals("Pro\u0161li ste semafor.", state.lastSpokenText)
        assertEquals(true, state.recentEvents.first().wasAnnounced)
    }

    @Test
    fun `tts skipped not ready updates receiver state without breaking scan`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer(
            announceResult = TtsSpeakResult.SkippedNotReady("TTS jos nije spreman."),
        )
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(beaconDetected(rssi = -60, detectedAt = 10L + index))
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals(true, state.isScanning)
        assertEquals("TTS jos nije spreman.", state.lastTtsError)
    }

    @Test
    fun `tts failure updates receiver state without breaking scan`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer(
            announceResult = TtsSpeakResult.Failed("TTS nije uspeo da zakaze glasovnu najavu."),
        )
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(beaconDetected(rssi = -60, detectedAt = 20L + index))
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals(true, state.isScanning)
        assertEquals("TTS nije uspeo da zakaze glasovnu najavu.", state.lastTtsError)
    }

    @Test
    fun `retryable receiver failure schedules a single retry and stop cancels it`() = runTest {
        val scanner = FakeBeaconScannerController()
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        scanner.emit(
            BeaconScanEvent.Failure(
                code = 1,
                message = "Doslo je do interne BLE greske.",
                retryable = true,
            ),
        )

        assertEquals(true, viewModel.uiState.value.receiverState.retryScheduled)
        assertEquals(1, scanner.startCalls)

        viewModel.onStopClick(AppMode.RECEIVER)
        advanceUntilIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(3_000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value.receiverState
        assertEquals(false, state.retryScheduled)
        assertEquals(false, state.isScanning)
        assertEquals(1, scanner.startCalls)
    }

    @Test
    fun `retryable receiver failure restarts scan after delay`() = runTest {
        val scanner = FakeBeaconScannerController()
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        scanner.emit(
            BeaconScanEvent.Failure(
                code = 2,
                message = "BLE skeniranje nije moglo da se registruje.",
                retryable = true,
            ),
        )
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(3_000L)
        advanceUntilIdle()

        assertEquals(2, scanner.startCalls)
        assertEquals(true, viewModel.uiState.value.receiverState.isScanning)
    }

    @Test
    fun `higher priority beacon is queued and spoken after current playback finishes`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val clock = FakeClock()
        val viewModel = createViewModel(
            scanner = scanner,
            ttsAnnouncer = ttsAnnouncer,
            clock = clock,
        )
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    beaconId = "beacon-a",
                    priority = Priority.MEDIUM,
                    rssi = -60 + index,
                    detectedAt = 100L + index,
                ),
            )
            advanceUntilIdle()
        }

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    beaconId = "beacon-b",
                    pointType = PointType.STAIRS,
                    messageCode = 2,
                    priority = Priority.HIGH,
                    rssi = -67 + index,
                    detectedAt = 200L + index,
                ),
            )
            advanceUntilIdle()
        }

        var state = viewModel.uiState.value.receiverState
        assertEquals(1, ttsAnnouncer.announceCalls)
        assertEquals("beacon-b", state.pendingAnnouncementBeaconId)
        assertEquals("Kandidat ceka zavrsetak trenutne poruke.", state.lastArbitrationDecisionText)

        clock.nowMs = 3_000L
        ttsAnnouncer.emitPlaybackEvent(
            TtsPlaybackEvent.Done(ttsAnnouncer.announcedUtteranceIds.first()),
        )
        advanceUntilIdle()

        state = viewModel.uiState.value.receiverState
        assertEquals(2, ttsAnnouncer.announceCalls)
        assertEquals("Stepenice su ispred vas.", ttsAnnouncer.announcedTexts.last())
        assertEquals(null, state.pendingAnnouncementBeaconId)
        assertEquals("Cekajuci kandidat je dosao na red za glasovnu najavu.", state.lastArbitrationDecisionText)
    }

    @Test
    fun `low confidence heading falls back to generic prompt`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val headingController = FakeHeadingSensorController()
        val viewModel = createViewModel(
            scanner = scanner,
            ttsAnnouncer = ttsAnnouncer,
            headingController = headingController,
        )
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        headingController.estimate = HeadingEstimate(
            headingDegrees = 0,
            confidence = DirectionConfidence.LOW,
            sampleCount = 6,
        )
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    pointType = PointType.ENTRANCE,
                    messageCode = 4,
                    rssi = -60,
                    detectedAt = 900L + index,
                    azimuthDegrees = 90,
                ),
            )
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals("Ulaz u blizini.", state.lastDecodedText)
        assertEquals(DirectionLabel.UNKNOWN, state.lastDirectionLabel)
        assertEquals(
            "Koriscena je genericka poruka jer heading nije bio stabilan.",
            state.directionFallbackReason,
        )
    }

    @Test
    fun `same priority weaker beacon does not replace stronger pending candidate`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val viewModel = createViewModel(scanner = scanner, ttsAnnouncer = ttsAnnouncer)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    beaconId = "beacon-a",
                    rssi = -60 + index,
                    detectedAt = 100L + index,
                ),
            )
            advanceUntilIdle()
        }

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    beaconId = "pending-strong",
                    rssi = -62 + index,
                    detectedAt = 200L + index,
                ),
            )
            advanceUntilIdle()
        }

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    beaconId = "pending-weak",
                    rssi = -66 + index,
                    detectedAt = 300L + index,
                ),
            )
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value.receiverState
        assertEquals("pending-strong", state.pendingAnnouncementBeaconId)
        assertEquals(
            "Kandidat je odbacen jer postoji vazniji ili blizi beacon.",
            state.lastArbitrationDecisionText,
        )
    }

    @Test
    fun `pending candidate older than four seconds is still spoken when it reaches the front`() = runTest {
        val scanner = FakeBeaconScannerController()
        val ttsAnnouncer = FakeTtsAnnouncer()
        val clock = FakeClock()
        val viewModel = createViewModel(
            scanner = scanner,
            ttsAnnouncer = ttsAnnouncer,
            clock = clock,
        )
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionStatus.GRANTED.let { PermissionUiState(status = it) },
            bluetoothStatus = BluetoothStatus.READY,
        )
        calibrateReceiverDirection(viewModel)
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    beaconId = "beacon-a",
                    rssi = -60 + index,
                    detectedAt = 100L + index,
                ),
            )
            advanceUntilIdle()
        }

        repeat(3) { index ->
            scanner.emit(
                beaconDetected(
                    beaconId = "beacon-pending",
                    pointType = PointType.STAIRS,
                    messageCode = 2,
                    priority = Priority.HIGH,
                    rssi = -61 + index,
                    detectedAt = 200L + index,
                ),
            )
            advanceUntilIdle()
        }

        clock.nowMs = 5_500L
        ttsAnnouncer.emitPlaybackEvent(
            TtsPlaybackEvent.Done(ttsAnnouncer.announcedUtteranceIds.first()),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value.receiverState
        assertEquals(2, ttsAnnouncer.announceCalls)
        assertEquals(null, state.pendingAnnouncementBeaconId)
        assertEquals("Stepenice su ispred vas.", ttsAnnouncer.announcedTexts.last())
        assertEquals("Cekajuci kandidat je dosao na red za glasovnu najavu.", state.lastArbitrationDecisionText)
        assertEquals(true, state.recentEvents.first().wasAnnounced)
    }

    @Test
    fun `bluetooth disabled while receiver scanning stops scan and updates status`() = runTest {
        val scanner = FakeBeaconScannerController()
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.DISABLED,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value.receiverState
        assertEquals(false, state.isScanning)
        assertEquals(false, state.retryScheduled)
        assertEquals("Error", state.statusText)
        assertEquals("Bluetooth je iskljucen.", state.errorText)
        assertEquals(1, scanner.stopCalls)
    }

    @Test
    fun `view model loads receiver runtime snapshot on startup`() = runTest {
        val runtimeStorage = FakeReceiverRuntimeStorage(
            snapshot = ReceiverRuntimeSnapshot(
                cooldownEntries = listOf(
                    CooldownEntry(
                        beaconId = "123e4567-e89b-12d3-a456-426614174000",
                        messageCode = 1,
                        lastTriggeredAt = 1_000L,
                    ),
                ),
                recentEvents = listOf(
                    DetectedBeaconEvent(
                        beaconId = "123e4567-e89b-12d3-a456-426614174000",
                        detectedAt = 1_500L,
                        rssi = -60,
                        pointType = PointType.CROSSWALK,
                        priority = Priority.MEDIUM,
                        messageCode = 1,
                        wasAnnounced = true,
                    ),
                ),
            ),
        )

        val viewModel = createViewModel(runtimeStorage = runtimeStorage)
        advanceUntilIdle()

        val state = viewModel.uiState.value.receiverState
        assertEquals(1, state.recentEvents.size)
        assertEquals(1_500L, state.lastAnnouncementAt)
    }

    private fun createViewModel(
        storage: FakeBeaconConfigStorage = FakeBeaconConfigStorage(),
        advertiser: FakeBeaconAdvertiserController = FakeBeaconAdvertiserController(),
        scanner: FakeBeaconScannerController = FakeBeaconScannerController(),
        runtimeStorage: FakeReceiverRuntimeStorage = FakeReceiverRuntimeStorage(),
        ttsAnnouncer: FakeTtsAnnouncer = FakeTtsAnnouncer(),
        headingController: FakeHeadingSensorController = FakeHeadingSensorController(),
        clock: FakeClock = FakeClock(),
    ): AppViewModel {
        return AppViewModel(
            beaconConfigStorage = storage,
            beaconAdvertiserController = advertiser,
            beaconScannerController = scanner,
            cooldownRepository = CooldownRepository(runtimeStorage),
            rssiStabilizer = RssiStabilizer(),
            headingSensorController = headingController,
            ttsAnnouncer = ttsAnnouncer,
            timeProvider = clock::now,
        )
    }

    private fun TestScope.calibrateReceiverDirection(viewModel: AppViewModel) {
        viewModel.onCalibrateReceiverHeading()
        advanceUntilIdle()
    }

    private fun beaconDetected(
        rssi: Int,
        detectedAt: Long,
        messageCode: Short = 1,
        pointType: PointType = PointType.CROSSWALK,
        beaconId: String = "123e4567-e89b-12d3-a456-426614174000",
        priority: Priority = Priority.MEDIUM,
        azimuthDegrees: Int? = 180,
    ): BeaconScanEvent.BeaconDetected {
        return BeaconScanEvent.BeaconDetected(
            payload = DecodedBeaconPayload(
                protocolVersion = if (azimuthDegrees == null) 1 else 2,
                beaconId = beaconId,
                pointType = pointType,
                priority = priority,
                messageCode = messageCode,
                azimuthDegrees = azimuthDegrees,
            ),
            rssi = rssi,
            detectedAt = detectedAt,
        )
    }
}

private class FakeBeaconConfigStorage(
    var storedConfig: BeaconConfig? = null,
) : BeaconConfigStorage {

    override suspend fun save(config: BeaconConfig) {
        storedConfig = config
    }

    override suspend fun load(): BeaconConfig? = storedConfig

    override suspend fun clearActiveFlag() {
        storedConfig = storedConfig?.copy(isActive = false)
    }
}

private class FakeHeadingSensorController(
    var estimate: HeadingEstimate? = HeadingEstimate(
        headingDegrees = 0,
        confidence = DirectionConfidence.HIGH,
        sampleCount = 8,
    ),
) : HeadingSensorController {

    override fun start() = Unit

    override fun stop() = Unit

    override fun latestEstimate(): HeadingEstimate? = estimate
}

private class FakeTtsAnnouncer(
    private val initialStatus: TtsStatus = TtsStatus.READY_SR,
    private val announceResult: TtsSpeakResult = TtsSpeakResult.Queued,
) : TtsAnnouncer {

    var announceCalls: Int = 0
    var stopCalls: Int = 0
    var shutdownCalls: Int = 0
    private var statusCallback: ((TtsStatus) -> Unit)? = null
    private var playbackCallback: ((TtsPlaybackEvent) -> Unit)? = null
    val announcedTexts = mutableListOf<String>()
    val announcedUtteranceIds = mutableListOf<String>()

    override fun initialize(
        onStatusChanged: (TtsStatus) -> Unit,
        onPlaybackEvent: (TtsPlaybackEvent) -> Unit,
    ) {
        statusCallback = onStatusChanged
        playbackCallback = onPlaybackEvent
        onStatusChanged(initialStatus)
    }

    override fun announce(
        text: String,
        utteranceId: String,
    ): TtsSpeakResult {
        announceCalls += 1
        announcedTexts += text
        announcedUtteranceIds += utteranceId
        return announceResult
    }

    override fun stop() {
        stopCalls += 1
    }

    override fun shutdown() {
        shutdownCalls += 1
    }

    fun emitPlaybackEvent(event: TtsPlaybackEvent) {
        playbackCallback?.invoke(event)
    }
}

private class FakeReceiverRuntimeStorage(
    var snapshot: ReceiverRuntimeSnapshot = ReceiverRuntimeSnapshot(),
) : ReceiverRuntimeStorage {

    override suspend fun load(): ReceiverRuntimeSnapshot = snapshot

    override suspend fun save(snapshot: ReceiverRuntimeSnapshot) {
        this.snapshot = snapshot
    }
}

private class FakeBeaconAdvertiserController(
    private val supported: Boolean = true,
    var nextResult: BeaconAdvertiseResult = BeaconAdvertiseResult.Started,
) : BeaconAdvertiserController {

    var startCalls: Int = 0
    var stopCalls: Int = 0

    override fun isSupported(): Boolean = supported

    override fun startAdvertising(
        config: BeaconConfig,
        onResult: (BeaconAdvertiseResult) -> Unit,
    ) {
        startCalls += 1
        onResult(nextResult)
    }

    override fun stopAdvertising() {
        stopCalls += 1
    }
}

private class FakeBeaconScannerController(
    private val supported: Boolean = true,
) : BeaconScannerController {

    var startCalls: Int = 0
    var stopCalls: Int = 0
    private var callback: ((BeaconScanEvent) -> Unit)? = null
    private var lastCallback: ((BeaconScanEvent) -> Unit)? = null

    override fun isSupported(): Boolean = supported

    override fun startScanning(onEvent: (BeaconScanEvent) -> Unit) {
        startCalls += 1
        callback = onEvent
        lastCallback = onEvent
        onEvent(BeaconScanEvent.Started)
    }

    override fun stopScanning() {
        stopCalls += 1
        callback = null
    }

    fun emit(event: BeaconScanEvent) {
        callback?.invoke(event)
    }

    fun emitLate(event: BeaconScanEvent) {
        lastCallback?.invoke(event)
    }
}

private class FakeClock(
    var nowMs: Long = 0L,
) {
    fun now(): Long = nowMs
}
