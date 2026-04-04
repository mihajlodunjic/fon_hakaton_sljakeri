package rs.fon.hakaton.audionav.domain

import java.util.UUID

object BeaconConfigValidator {

    fun validate(config: BeaconConfig): List<BeaconConfigValidationError> {
        val errors = mutableListOf<BeaconConfigValidationError>()

        val isUuidValid = runCatching { UUID.fromString(config.beaconId) }.isSuccess
        if (!isUuidValid) {
            errors += BeaconConfigValidationError.INVALID_BEACON_ID
        }

        if (config.messageCode <= 0) {
            errors += BeaconConfigValidationError.NON_POSITIVE_MESSAGE_CODE
        }

        if (MessageCatalog.resolve(config.pointType, config.messageCode) == null) {
            errors += BeaconConfigValidationError.MESSAGE_NOT_DEFINED_FOR_POINT_TYPE
        }

        return errors
    }

    fun isValid(config: BeaconConfig): Boolean = validate(config).isEmpty()
}
