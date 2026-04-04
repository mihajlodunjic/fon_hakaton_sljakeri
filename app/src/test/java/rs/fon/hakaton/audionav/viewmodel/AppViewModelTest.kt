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
import rs.fon.hakaton.audionav.domain.AppMode
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.MessageCatalog
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.domain.PermissionUiState
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority
import rs.fon.hakaton.audionav.domain.BluetoothStatus
import rs.fon.hakaton.audionav.storage.BeaconConfigStorage

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model initializes beacon draft with valid uuid and default catalog`() {
        val viewModel = AppViewModel(
            beaconConfigStorage = FakeBeaconConfigStorage(),
            beaconAdvertiserController = FakeBeaconAdvertiserController(),
        )

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
        val viewModel = AppViewModel(
            beaconConfigStorage = storage,
            beaconAdvertiserController = FakeBeaconAdvertiserController(),
        )

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
            val viewModel = AppViewModel(
                beaconConfigStorage = FakeBeaconConfigStorage(),
                beaconAdvertiserController = FakeBeaconAdvertiserController(),
            )

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
        val viewModel = AppViewModel(
            beaconConfigStorage = FakeBeaconConfigStorage(),
            beaconAdvertiserController = advertiser,
        )
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
        val viewModel = AppViewModel(
            beaconConfigStorage = storage,
            beaconAdvertiserController = advertiser,
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
        val viewModel = AppViewModel(
            beaconConfigStorage = storage,
            beaconAdvertiserController = advertiser,
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
