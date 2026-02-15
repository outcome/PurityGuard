package com.purityguard.app

enum class DisableStage { WAIT_15, WAIT_5 }

data class DisableFlowConfig(
    val waitPrimarySeconds: Int,
    val waitConfirmSeconds: Int
)

data class DisableFlowState(
    val stage: DisableStage?,
    val secondsLeft: Int,
    val protectionEnabled: Boolean
)

object DisableFlowPolicy {
    fun start(): DisableFlowState = start(DisableFlowConfig(waitPrimarySeconds = 15, waitConfirmSeconds = 5))

    fun start(config: DisableFlowConfig): DisableFlowState = DisableFlowState(
        stage = DisableStage.WAIT_15,
        secondsLeft = config.waitPrimarySeconds.coerceAtLeast(0),
        protectionEnabled = true
    )

    fun tick(state: DisableFlowState): DisableFlowState {
        val stage = state.stage ?: return state
        if (state.secondsLeft <= 0) return state.copy(stage = stage, secondsLeft = 0)
        return state.copy(secondsLeft = state.secondsLeft - 1)
    }

    fun confirm(state: DisableFlowState, config: DisableFlowConfig): DisableFlowState {
        val stage = state.stage ?: return state
        if (state.secondsLeft > 0) return state

        return when (stage) {
            DisableStage.WAIT_15 -> DisableFlowState(
                stage = DisableStage.WAIT_5,
                secondsLeft = config.waitConfirmSeconds.coerceAtLeast(0),
                protectionEnabled = true
            )

            DisableStage.WAIT_5 -> DisableFlowState(
                stage = null,
                secondsLeft = 0,
                protectionEnabled = false
            )
        }
    }

    fun cancel(): DisableFlowState = DisableFlowState(
        stage = null,
        secondsLeft = 0,
        protectionEnabled = true
    )
}
