package kth.nova.overloadalert.domain.plan

/**
 * Represents the intensity or category of a planned running activity for a specific day.
 *
 * This classification helps in managing training load and preventing overload by categorizing
 * daily activities into distinct types of exertion.
 *
 * @property LONG A high-volume run intended to build endurance.
 * @property MODERATE A medium-intensity or medium-distance run.
 * @property SHORT A low-volume run, often used for recovery or speed work.
 * @property REST A scheduled day off with no running activity to allow for physiological recovery.
 */
enum class RunType {
    LONG,
    MODERATE,
    SHORT,
    REST
}