package kth.nova.overloadalert.domain.plan

/**
 * Represents the rate at which a training plan or workload should progress relative to the chronic load.
 *
 * This enum defines the strategy for calculating the next target load based on the current chronic load (CL).
 *
 * @property RETAIN Maintains the current level of intensity, targeting the current chronic load.
 * @property SLOW Indicates a conservative increase, targeting 110% of the current chronic load (CL * 1.1).
 * @property FAST Indicates an aggressive increase, targeting 130% of the current chronic load (CL * 1.3).
 */
enum class ProgressionRate {
    RETAIN,
    SLOW,
    FAST
}