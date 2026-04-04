package rs.fon.hakaton.audionav

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.ui.PametniAudioNavApp
import rs.fon.hakaton.audionav.ui.theme.PametniAudioNavTheme
import rs.fon.hakaton.audionav.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var hasRequestedPermissions: Boolean = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        AppLogger.d(LogTag.APP, "Runtime permission request completed")
        refreshSystemStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.d(LogTag.APP, "MainActivity created")
        refreshSystemStatus()

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

    override fun onResume() {
        super.onResume()
        AppLogger.d(LogTag.APP, "MainActivity resumed")
        refreshSystemStatus()
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
