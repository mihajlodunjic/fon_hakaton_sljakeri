package rs.fon.hakaton.audionav.viewmodel

import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `receiver needs three valid reads before stable event is recorded`() = runTest {
        val scanner = FakeBeaconScannerController()
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
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
        assertEquals("Pesacki prelaz ispred vas.", state.lastDecodedText)
    }

    @Test
    fun `receiver valid payload with unknown message code shows fallback text after stabilization`() = runTest {
        val scanner = FakeBeaconScannerController()
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
        viewModel.onStartReceiverClick()
        advanceUntilIdle()

        repeat(3) { index ->
            scanner.emit(
                BeaconScanEvent.BeaconDetected(
                    payload = DecodedBeaconPayload(
                        protocolVersion = 1,
                        beaconId = "123e4567-e89b-12d3-a456-426614174000",
                        pointType = PointType.CROSSWALK,
                        priority = Priority.MEDIUM,
                        messageCode = 99,
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
    }

    @Test
    fun `stable event inside cooldown is recorded but blocked`() = runTest {
        val scanner = FakeBeaconScannerController()
        val viewModel = createViewModel(scanner = scanner)
        viewModel.onSystemStatusChanged(
            permissionUiState = PermissionUiState(status = PermissionStatus.GRANTED),
            bluetoothStatus = BluetoothStatus.READY,
        )
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
    ): AppViewModel {
        return AppViewModel(
            beaconConfigStorage = storage,
            beaconAdvertiserController = advertiser,
            beaconScannerController = scanner,
            cooldownRepository = CooldownRepository(runtimeStorage),
            rssiStabilizer = RssiStabilizer(),
        )
    }

    private fun beaconDetected(
        rssi: Int,
        detectedAt: Long,
        messageCode: Short = 1,
        pointType: PointType = PointType.CROSSWALK,
    ): BeaconScanEvent.BeaconDetected {
        return BeaconScanEvent.BeaconDetected(
            payload = DecodedBeaconPayload(
                protocolVersion = 1,
                beaconId = "123e4567-e89b-12d3-a456-426614174000",
                pointType = pointType,
                priority = Priority.MEDIUM,
                messageCode = messageCode,
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

    override fun isSupported(): Boolean = supported

    override fun startScanning(onEvent: (BeaconScanEvent) -> Unit) {
        startCalls += 1
        callback = onEvent
        onEvent(BeaconScanEvent.Started)
    }

    override fun stopScanning() {
        stopCalls += 1
        callback = null
    }

    fun emit(event: BeaconScanEvent) {
        callback?.invoke(event)
    }
}
