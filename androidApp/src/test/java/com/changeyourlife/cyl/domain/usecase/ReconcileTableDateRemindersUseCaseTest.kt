package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateCellValue
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReconcileTableDateRemindersUseCaseTest {
    @Test
    fun changedDateUpsertsSameReminderAfterDocumentMutation() = runBlocking {
        val repository = RecordingReminderRepository()
        val reconcile = ReconcileTableDateRemindersUseCase(
            ScheduleTableDateReminderUseCase(repository),
        )
        val page = page()

        reconcile(
            previousPage = page,
            currentPage = page,
            previousDocument = document(date = "2099-07-20"),
            currentDocument = document(date = "2099-07-21"),
        )

        val reminder = repository.writes.single()
        assertEquals("table-date:page-1:table-1:row-1:date", reminder.id)
        assertNull(reminder.deletedAt)
        assertEquals("Makan · Date · Transactions", reminder.title)
    }

    @Test
    fun removedRowCancelsItsPersistedReminder() = runBlocking {
        val repository = RecordingReminderRepository()
        val reconcile = ReconcileTableDateRemindersUseCase(
            ScheduleTableDateReminderUseCase(repository),
        )
        val page = page()

        reconcile(
            previousPage = page,
            currentPage = page,
            previousDocument = document(date = "2099-07-20"),
            currentDocument = document(date = null),
        )

        val cancellation = repository.writes.single()
        assertEquals("table-date:page-1:table-1:row-1:date", cancellation.id)
        assertNotNull(cancellation.deletedAt)
    }

    @Test
    fun restoringPageSchedulesEveryDateReminder() = runBlocking {
        val repository = RecordingReminderRepository()
        val reconcile = ReconcileTableDateRemindersUseCase(
            ScheduleTableDateReminderUseCase(repository),
        )
        val page = page()

        reconcile.scheduleAll(
            page = page,
            document = document(date = "2099-07-20"),
        )

        assertEquals(1, repository.writes.count { reminder -> reminder.deletedAt == null })
    }

    @Test
    fun dateCellsWithoutReminderDoNotCreateCancellationTombstones() = runBlocking {
        val repository = RecordingReminderRepository()
        val reconcile = ReconcileTableDateRemindersUseCase(
            ScheduleTableDateReminderUseCase(repository),
        )

        reconcile.scheduleAll(
            page = page(),
            document = document(
                date = "2099-07-20",
                defaultReminder = PageTableDateReminder.None,
            ),
        )

        assertEquals(emptyList<Reminder>(), repository.writes)
    }

    @Test
    fun explicitNoneReminderCancelsPreviousColumnDefaultReminder() = runBlocking {
        val repository = RecordingReminderRepository()
        val reconcile = ReconcileTableDateRemindersUseCase(
            ScheduleTableDateReminderUseCase(repository),
        )
        val noReminderValue = Json.encodeToString(
            PageTableDateCellValue(
                startDate = "2099-07-20",
                reminder = PageTableDateReminder.None,
            ),
        )

        reconcile(
            previousPage = page(),
            currentPage = page(),
            previousDocument = document(date = "2099-07-20"),
            currentDocument = document(date = noReminderValue),
        )

        val cancellation = repository.writes.single()
        assertNotNull(cancellation.deletedAt)
    }

    private fun document(
        date: String?,
        defaultReminder: PageTableDateReminder = PageTableDateReminder.OneDayBefore,
    ): PageBlockDocument {
        val columns = listOf(
            PageTableColumn(id = "name", name = "Name"),
            PageTableColumn(
                id = "date",
                name = "Date",
                type = PageTableColumnType.Date,
                dateReminder = defaultReminder,
            ),
        )
        val rows = if (date == null) {
            emptyList()
        } else {
            listOf(
                PageTableRow(
                    id = "row-1",
                    cells = mapOf("name" to "Makan", "date" to date),
                ),
            )
        }
        return PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "table-1",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Transactions",
                        columns = columns,
                        rows = rows,
                    ),
                ),
            ),
        )
    }

    private fun page(): Page {
        return Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget",
            content = "",
            sortOrder = 0,
            createdAt = 1,
            updatedAt = 1,
            deletedAt = null,
        )
    }

    private class RecordingReminderRepository : ReminderRepository {
        val writes = mutableListOf<Reminder>()

        override fun observePendingReminders(): Flow<List<Reminder>> = flowOf(emptyList())

        override fun observePendingReminders(workspaceId: String): Flow<List<Reminder>> = flowOf(emptyList())

        override fun observePendingReminderCount(): Flow<Int> = flowOf(0)

        override fun observePendingReminderCount(workspaceId: String): Flow<Int> = flowOf(0)

        override suspend fun getReminderForTask(taskId: String): Reminder? = null

        override suspend fun upsertReminder(reminder: Reminder) {
            writes += reminder
        }

        override suspend fun reschedulePendingReminders() = Unit

        override suspend fun createReminder(
            workspaceId: String,
            title: String,
            remindAt: Long,
            pageId: String?,
            taskId: String?,
            id: String?,
        ): Reminder = error("Not used")
    }
}
