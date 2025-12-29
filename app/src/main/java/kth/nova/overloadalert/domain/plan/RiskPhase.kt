package kth.nova.overloadalert.domain.plan

enum class RiskPhase {
    LONG_RUN_LIMITED,
    DELOAD,
    COOLDOWN,
    REBUILDING
}