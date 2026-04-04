package rs.fon.hakaton.audionav

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.ui.PametniAudioNavApp
import rs.fon.hakaton.audionav.ui.theme.PametniAudioNavTheme
import rs.fon.hakaton.audionav.viewmodel.AppViewModel
import rs.fon.hakaton.audionav.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory(applicationContext)
    }
    private var hasRequestedPermissions: Boolean = false
    private var bluetoothStateReceiverRegistered: Boolean = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        AppLogger.d(LogTag.APP, "Runtime permission request completed")
        refreshSystemStatus()
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                AppLogger.d(LogTag.APP, "Bluetooth adapter state changed")
                refreshSystemStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.d(LogTag.APP, "MainActivity created")
        refreshSystemStatus()
        viewModel.loadPersistedBeaconConfig()

        setContent {
            PametniAudioNavTheme {
                Surface {
                    PametniAudioNavApp(
                        viewModel = viewModel,
                        onRequestPermissions = { permissions ->
                            if (permissions.isNotEmpty()) {
                                hasRequestedPermissions = true
                                permissionsLauncher.launch(permissions.toTypedArray())
                            } else {
                                refreshSystemStatus()
                            }
                        },
                        onOpenSettings = ::openAppSettings,
                        onRefreshSystemStatus = ::refreshSystemStatus,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerBluetoothStateReceiver()
    }

    override fun onResume() {
        super.onResume()
        AppLogger.d(LogTag.APP, "MainActivity resumed")
        refreshSystemStatus()
    }

    override fun onStop() {
        super.onStop()
        unregisterBluetoothStateReceiver()
    }

    private fun refreshSystemStatus() {
        val permissionUiState = DeviceReadinessHelper.buildPermissionUiState(
            activity = this,
            hasRequestedPermissions = hasRequestedPermissions,
        )
        val bluetoothStatus = DeviceReadinessHelper.resolveBluetoothStatus(
            context = this,
            permissionsGranted = permissionUiState.status == PermissionStatus.GRANTED,
        )
        viewModel.onSystemStatusChanged(permissionUiState, bluetoothStatus)
    }

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiverRegistered) {
            return
        }

        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bluetoothStateReceiverRegistered = true
    }

    private fun unregisterBluetoothStateReceiver() {
        if (!bluetoothStateReceiverRegistered) {
            return
        }

        runCatching {
            unregisterReceiver(bluetoothStateReceiver)
        }
        bluetoothStateReceiverRegistered = false
    }

    private fun openAppSettings() {
        AppLogger.d(LogTag.APP, "Opening application settings")
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }
}
