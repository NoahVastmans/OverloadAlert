package kth.nova.overloadalert.domain.usecases

import android.util.Log
import kth.nova.overloadalert.data.local.CalendarSyncDao
import kth.nova.overloadalert.data.local.CalendarSyncEntity
import kth.nova.overloadalert.data.remote.EventDateTime
import kth.nova.overloadalert.data.remote.GoogleAuthRepository
import kth.nova.overloadalert.data.remote.GoogleCalendarEvent
import kth.nova.overloadalert.data.remote.GoogleCalendarRepository
import kth.nova.overloadalert.domain.plan.DailyPlan
import kth.nova.overloadalert.domain.plan.RunType
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan
import java.util.Locale

class CalendarSyncService(
    private val calendarSyncDao: CalendarSyncDao,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val authRepository: GoogleAuthRepository
) {

    suspend fun syncPlanToCalendar(plan: WeeklyTrainingPlan) {
        Log.d("CalendarSyncService", "Starting syncPlanToCalendar. Plan days: ${plan.days.size}")
        
        // If not connected, do nothing
        if (authRepository.getLastSignedInAccount() == null) {
            Log.d("CalendarSyncService", "Not connected to Google Account. Aborting sync.")
            return
        }

        plan.days.forEach { dailyPlan ->
            val dateStr = dailyPlan.date.toString() // YYYY-MM-DD
            val syncEntity = calendarSyncDao.getSyncEntity(dateStr)

            // 1. Handle REST Days: Remove event if it exists
            if (dailyPlan.runType == RunType.REST) {
                if (syncEntity != null) {
                    Log.d("CalendarSyncService", "Rest day detected. Deleting existing event for $dateStr")
                    try {
                        googleCalendarRepository.deleteEvent(syncEntity.googleEventId)
                    } catch (e: Exception) {
                        Log.w("CalendarSyncService", "Failed to delete event ${syncEntity.googleEventId}: ${e.message}")
                    }
                    calendarSyncDao.deleteSyncEntity(dateStr)
                }
                return@forEach
            }

            // --- Event Formatting ---
            val distanceKm = String.format(Locale.US, "%.1f", dailyPlan.plannedDistance / 1000f)
            val eventTitle = "${dailyPlan.runType.name.lowercase().replaceFirstChar { it.uppercase() }} Run: $distanceKm km"
            val description = "Created by OverloadAlert"

            if (syncEntity != null) {
                // Event exists, check for updates
                Log.d("CalendarSyncService", "Updating existing event for $dateStr")
                try {
                    val existingEvent = googleCalendarRepository.getEvent(syncEntity.googleEventId)
                    
                    // Ownership Rules & Conflict Handling
                    val isTimedEvent = existingEvent.start?.dateTime != null
                    
                    val currentDescription = existingEvent.description ?: ""
                    val existingUserNotes = if (currentDescription.contains("Created by OverloadAlert")) {
                         currentDescription.substringAfter("Created by OverloadAlert")
                    } else {
                        // Preserve original description if our tag is missing, assuming user overwrote it.
                        currentDescription
                    }
                    
                    val newDescription = "$description$existingUserNotes"

                    // Prepare Update
                    val eventUpdates = GoogleCalendarEvent(
                        summary = eventTitle,
                        description = newDescription,
                        start = if (isTimedEvent) null else EventDateTime(date = dateStr),
                        end = if (isTimedEvent) null else EventDateTime(date = dateStr)
                    )

                    googleCalendarRepository.patchEvent(syncEntity.googleEventId, eventUpdates)

                    // Update local sync record
                    calendarSyncDao.insertOrUpdate(
                        syncEntity.copy(
                            lastSyncedAt = System.currentTimeMillis(),
                            userModifiedTime = isTimedEvent
                        )
                    )

                } catch (e: Exception) {
                    Log.e("CalendarSyncService", "Error updating event $dateStr", e)
                    if (e.message?.contains("404") == true || e.message?.contains("Not Found") == true) {
                        createEvent(dailyPlan, dateStr, eventTitle, description)
                    }
                }

            } else {
                // No existing sync record, create new event
                Log.d("CalendarSyncService", "Creating new event for $dateStr")
                createEvent(dailyPlan, dateStr, eventTitle, description)
            }
        }
    }

    private suspend fun createEvent(dailyPlan: DailyPlan, dateStr: String, title: String, description: String) {
        try {
            val event = GoogleCalendarEvent(
                summary = title,
                description = description,
                start = EventDateTime(date = dateStr),
                end = EventDateTime(date = dateStr)
            )

            val createdEvent = googleCalendarRepository.createEvent(event)
            Log.d("CalendarSyncService", "Event created: ${createdEvent.id}")
            
            createdEvent.id?.let { id ->
                calendarSyncDao.insertOrUpdate(
                    CalendarSyncEntity(
                        date = dateStr,
                        googleEventId = id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("CalendarSyncService", "Error creating event", e)
            e.printStackTrace()
        }
    }
}