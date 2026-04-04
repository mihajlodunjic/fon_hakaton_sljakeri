package rs.fon.hakaton.audionav.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import rs.fon.hakaton.audionav.AppLogger
import rs.fon.hakaton.audionav.LogTag
import rs.fon.hakaton.audionav.domain.AppMode
import rs.fon.hakaton.audionav.ui.navigation.AppDestination
import rs.fon.hakaton.audionav.ui.screens.beacon.BeaconScreen
import rs.fon.hakaton.audionav.ui.screens.mode.ModeSelectionScreen
import rs.fon.hakaton.audionav.ui.screens.receiver.ReceiverScreen
import rs.fon.hakaton.audionav.viewmodel.AppViewModel

@Composable
fun PametniAudioNavApp(
    viewModel: AppViewModel,
    onRequestPermissions: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshSystemStatus: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            AppLogger.d(LogTag.APP, "Navigated to ${destination.route}")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.ModeSelection.route,
    ) {
        composable(AppDestination.ModeSelection.route) {
            ModeSelectionScreen(
                readinessMessage = uiState.readinessMessage,
                permissionUiState = uiState.permissionUiState,
                onBeaconModeClick = {
                    viewModel.onModeSelected(AppMode.BEACON)
                    navController.navigate(AppDestination.BeaconConfig.route)
                },
                onReceiverModeClick = {
                    viewModel.onModeSelected(AppMode.RECEIVER)
                    navController.navigate(AppDestination.Receiver.route)
                },
                onRequestPermissionsClick = {
                    viewModel.onRefreshPermissionStateRequested()
                    onRequestPermissions(uiState.permissionUiState.requiredPermissions)
                },
                onOpenSettingsClick = onOpenSettings,
                onRefreshStatusClick = {
                    viewModel.onRefreshPermissionStateRequested()
                    onRefreshSystemStatus()
                },
            )
        }

        composable(AppDestination.BeaconConfig.route) {
            DisposableEffect(Unit) {
                viewModel.onBeaconScreenVisibilityChanged(true)
                onDispose {
                    viewModel.onBeaconScreenVisibilityChanged(false)
                }
            }
            BeaconScreen(
                state = uiState.beaconState,
                readinessMessage = uiState.readinessMessage,
                onNavigateBack = { navController.popBackStack() },
                onLabelChanged = viewModel::onBeaconLabelChanged,
                onPointTypeSelected = viewModel::onBeaconPointTypeSelected,
                onPrioritySelected = viewModel::onBeaconPrioritySelected,
                onMessageSelected = viewModel::onBeaconMessageCodeSelected,
                onAzimuthChanged = viewModel::onBeaconAzimuthChanged,
                onAdjustAzimuth = viewModel::onBeaconAdjustAzimuth,
                onStartClick = viewModel::onStartBeaconClick,
                onStopClick = { viewModel.onStopClick(AppMode.BEACON) },
            )
        }

        composable(AppDestination.Receiver.route) {
            DisposableEffect(Unit) {
                viewModel.onReceiverScreenVisibilityChanged(true)
                onDispose {
                    viewModel.onReceiverScreenVisibilityChanged(false)
                }
            }
            ReceiverScreen(
                state = uiState.receiverState,
                readinessMessage = uiState.readinessMessage,
                onNavigateBack = {
                    viewModel.onStopClick(AppMode.RECEIVER)
                    navController.popBackStack()
                },
                onStartClick = viewModel::onStartReceiverClick,
                onStopClick = { viewModel.onStopClick(AppMode.RECEIVER) },
                onCalibrateHeading = viewModel::onCalibrateReceiverHeading,
                onResetHeadingCalibration = viewModel::onResetReceiverHeadingCalibration,
            )
        }
    }
}
