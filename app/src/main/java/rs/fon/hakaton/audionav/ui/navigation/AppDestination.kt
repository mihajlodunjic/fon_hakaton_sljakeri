package rs.fon.hakaton.audionav.ui.navigation

sealed class AppDestination(val route: String, val title: String) {
    data object ModeSelection : AppDestination("mode_selection", "Izbor moda")
    data object BeaconConfig : AppDestination("beacon_config", "Beacon mode")
    data object Receiver : AppDestination("receiver", "Receiver mode")
}

