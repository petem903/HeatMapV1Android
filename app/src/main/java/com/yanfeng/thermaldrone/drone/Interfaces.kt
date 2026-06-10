package com.yanfeng.thermaldrone.drone

/** Flight commands accepted by the ground-control server API. */
sealed class DroneCommand {
    object Takeoff : DroneCommand()
    object Land : DroneCommand()
    object ReturnHome : DroneCommand()
    data class SetAltitude(val altitudeM: Double) : DroneCommand()
    data class MoveRelative(val dxM: Double, val dyM: Double, val dzM: Double) : DroneCommand()
}

sealed class CommandResult {
    object Accepted : CommandResult()
    data class Rejected(val reason: String) : CommandResult()
}
