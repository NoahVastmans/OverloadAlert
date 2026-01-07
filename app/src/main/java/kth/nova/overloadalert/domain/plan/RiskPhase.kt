package kth.nova.overloadalert.domain.plan

/**
 * Represents the specific phase of risk management within a training plan, dictating how an athlete should adjust their training load.
 *
 * @property LONG_RUN_LIMITED Triggered after a single run spike (acute overload). The athlete should limit the duration of long runs to prevent injury while maintaining general volume.
 * @property DELOAD Triggered after a period of overtraining (sustained high load). This phase requires a significant reduction in overall volume and intensity to allow the body to recover from accumulated fatigue.
 * @property COOLDOWN The transition phase where training resumes normally after a [DELOAD] or [REBUILDING] phase. It acts as a buffer to ensure stability before ramping up again.
 * @property REBUILDING Triggered after being in a state of undertraining. This phase focuses on gradually increasing load to safe levels to avoid the injury risk associated with sudden spikes in activity after inactivity.
 */
enum class RiskPhase {
    LONG_RUN_LIMITED,
    DELOAD,
    COOLDOWN,
    REBUILDING
}