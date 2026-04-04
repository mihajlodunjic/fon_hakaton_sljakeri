package rs.fon.hakaton.audionav

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rs.fon.hakaton.audionav.domain.BluetoothStatus
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.domain.PermissionUiState

object DeviceReadinessHelper {

    fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun buildPermissionUiState(
        activity: ComponentActivity,
        hasRequestedPermissions: Boolean,
    ): PermissionUiState {
        val requiredPermissions = requiredPermissions()
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            return PermissionUiState(
                status = PermissionStatus.GRANTED,
                requiredPermissions = requiredPermissions,
            )
        }

        val permanentlyDenied = hasRequestedPermissions && missingPermissions.any { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        return PermissionUiState(
            status = if (permanentlyDenied) {
                PermissionStatus.PERMANENTLY_DENIED
            } else {
                PermissionStatus.MISSING
            },
            missingPermissions = missingPermissions,
            requiredPermissions = requiredPermissions,
        )
    }

    @SuppressLint("MissingPermission")
    fun resolveBluetoothStatus(
        context: Context,
        permissionsGranted: Boolean,
    ): BluetoothStatus {
        val packageManager = context.packageManager
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BluetoothStatus.UNAVAILABLE
        }

        if (!permissionsGranted) {
            return BluetoothStatus.UNKNOWN
        }

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter ?: return BluetoothStatus.UNAVAILABLE

        return if (bluetoothAdapter.isEnabled) {
            BluetoothStatus.READY
        } else {
            BluetoothStatus.DISABLED
        }
    }
}
