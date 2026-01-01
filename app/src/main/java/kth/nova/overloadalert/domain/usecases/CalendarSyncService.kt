package kth.nova.overloadalert.domain.usecases

import kth.nova.overloadalert.data.local.CalendarSyncDao
import kth.nova.overloadalert.data.local.CalendarSyncEntity
import kth.nova.overloadalert.data.remote.EventDateTime
import kth.nova.overloadalert.data.remote.GoogleAuthRepository
import kth.nova.overloadalert.data.remote.GoogleCalendarEvent
import kth.nova.overloadalert.data.remote.GoogleCalendarRepository
import kth.nova.overloadalert.domain.plan.DailyPlan
import kth.nova.overloadalert.domain.plan.RunType
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarSyncService(
    private val calendarSyncDao: CalendarSyncDao,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val authRepository: GoogleAuthRepository
) {

    suspend fun syncPlanToCalendar(plan: WeeklyTrainingPlan) {
        // If not connected, do nothing
        if (authRepository.getLastSignedInAccount() == null) return

        plan.days.forEach { dailyPlan ->
            if (dailyPlan.runType == RunType.REST) return@forEach

            val dateStr = dailyPlan.date.toString() // YYYY-MM-DD
            val syncEntity = calendarSyncDao.getSyncEntity(dateStr)

            val eventTitle = "${dailyPlan.runType.name.lowercase().replaceFirstChar { it.uppercase() }} Run"
            val distanceKm = String.format(Locale.US, "%.1f", dailyPlan.plannedDistance / 1000f)
            val description = "Target Distance: $distanceKm km\n\nRun Type: ${dailyPlan.runType}\n\n---\nCreated by OverloadAlert"

            if (syncEntity != null) {
                // Event exists, check for updates
                try {
                    val existingEvent = googleCalendarRepository.getEvent(syncEntity.googleEventId)
                    
                    // Ownership Rules & Conflict Handling
                    
                    // 1. Check if user modified the time (converted all-day to timed)
                    // If start.dateTime is present, it's a timed event. App owns date, but user owns time.
                    val isTimedEvent = existingEvent.start?.dateTime != null
                    
                    // 2. Check if user modified the description (notes)
                    // We only own the structured part. If description differs from what we expect, assume user added notes.
                    // Simple heuristic: If the description doesn't *contain* our signature or structure, append ours.
                    // Better: Split by separator. For now, we will simply prepend our info if it's missing or update it.
                    // But requirement says: "If the user edits notes, those edits must be preserved."
                    
                    val currentDescription = existingEvent.description ?: ""
                    val separator = "\n\n---\nCreated by OverloadAlert"
                    val userNotes = if (currentDescription.contains(separator)) {
                        currentDescription.substringAfter(separator).trim()
                    } else {
                        // Assume everything else is user notes if our separator isn't found exactly? 
                        // Or maybe the user edited our part.
                        // Let's assume if they edited the whole thing, we shouldn't overwrite blindly.
                        // Strategy: We strictly control the top part.
                        // If we can't parse it, we append to the bottom.
                         ""
                    }
                    
                    // Construct new description
                    // We put the structured part first.
                    // Wait, if user added notes, we should keep them. 
                    // Let's re-read: "The app owns... the structured part... The user owns... any manually added notes."
                    
                    // Let's try to preserve anything AFTER our signature line if it exists.
                    val existingUserNotes = if (currentDescription.contains("Created by OverloadAlert")) {
                         currentDescription.substringAfter("Created by OverloadAlert")
                    } else {
                        "" // Or should we treat the whole thing as user notes if we lost our tag? 
                           // For safety, let's treat unknown content as user notes to append.
                           // Actually, let's just REPLACE the structured part if we find it, or Prepend if we don't.
                           currentDescription.replace("Target Distance:.*Created by OverloadAlert".toRegex(RegexOption.DOT_MATCHES_ALL), "")
                    }
                    
                    val newDescription = "$description$existingUserNotes"

                    // 3. Prepare Update
                    // We update Title, Date (if all-day), and Description (structured part).
                    // We DO NOT update Start/End time if it is a timed event.
                    
                    val eventUpdates = GoogleCalendarEvent(
                        summary = eventTitle,
                        description = newDescription,
                        start = if (isTimedEvent) null else EventDateTime(date = dateStr),
                        end = if (isTimedEvent) null else EventDateTime(date = dateStr)
                    )

                    // Only patch if changes needed? API patch is cheap enough.
                    googleCalendarRepository.patchEvent(syncEntity.googleEventId, eventUpdates)

                    // Update local sync record
                    calendarSyncDao.insertOrUpdate(
                        syncEntity.copy(lastSyncedAt = System.currentTimeMillis())
                    )

                } catch (e: Exception) {
                    // Event might have been deleted by user
                    if (e.message?.contains("404") == true || e.message?.contains("Not Found") == true) {
                        // Re-create it? Or respect deletion? 
                        // Requirement: "If a conflict occurs, prefer user calendar changes"
                        // If user deleted it, maybe we should leave it deleted for this regeneration?
                        // Or should we recreate it because the plan says so?
                        // "If a training plan is regenerated, update existing... instead of duplicates"
                        // Let's re-create if missing, to ensure plan adherence.
                        createEvent(dailyPlan, dateStr, eventTitle, description)
                    } else {
                        e.printStackTrace()
                    }
                }

            } else {
                // No existing sync record, create new event
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
            e.printStackTrace()
        }
    }
}