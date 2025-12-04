package kth.nova.overloadalert.data.remote.dto

import kth.nova.overloadalert.data.local.Run
import java.time.OffsetDateTime

fun SummaryActivity.toRun(): Run {
    return Run(
        id = id,
        name = name,
        distance = distance,
        movingTime = movingTime,
        startDate = OffsetDateTime.parse(startDate)
    )
}