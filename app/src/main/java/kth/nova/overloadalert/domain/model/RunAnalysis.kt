package kth.nova.overloadalert.domain.model

/**
 * Holds the results of the running load analysis.
 */
data class RunAnalysis(
    val longestRunLast30Days: Float, // distance in meters
    val acuteLoad: Float, // total volume from the last 7 days, in meters
    val chronicLoad: Float, // average weekly volume from the previous 3 weeks, in meters
)