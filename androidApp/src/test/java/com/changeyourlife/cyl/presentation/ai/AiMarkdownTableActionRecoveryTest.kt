package com.changeyourlife.cyl.presentation.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMarkdownTableActionRecoveryTest {
    @Test
    fun recoversHomeCreateTableFromMarkdownReply() {
        val actions = AiMarkdownTableActionRecovery.recover(
            prompt = "buat jadual penjagaan ayam",
            reply = """
                ## Jadual Penjagaan Ayam (Perawatan Ayam)

                | **Waktu** | **Tugas** | **Keterangan / Tips** |
                |-----------|-----------|-----------------------|
                | **Setiap Hari** | 1. **Pagi (08:00)**<br>• Beri pakan porsi harian. | *Gunakan pakan seimbang.* |
                | **Setiap Minggu** | 1. **Pembersihan Kandang** | • Bersihkan lantai kandang. |
            """.trimIndent(),
        )

        val action = actions.single()
        assertEquals("CREATE_PAGE", action.type)
        assertEquals("Penjagaan Ayam", action.title)
        assertEquals("Penjagaan Ayam", action.tableTitle)
        assertEquals(listOf("Waktu", "Tugas", "Keterangan / Tips"), action.tableColumns.map { it.name })
        assertEquals("Setiap Hari", action.tableRows.first()["Waktu"])
        assertEquals("1. Pagi (08:00); Beri pakan porsi harian.", action.tableRows.first()["Tugas"])
    }

    @Test
    fun recoversPageScopedCreateDatabaseFromMarkdownReply() {
        val actions = AiMarkdownTableActionRecovery.recover(
            prompt = "buat jadual penjagaan ayam",
            reply = """
                | Waktu | Tugas |
                |-------|-------|
                | Pagi | Beri makan |
            """.trimIndent(),
            targetPageTitle = "Reban Ayam",
        )

        val action = actions.single()
        assertEquals("CREATE_DATABASE", action.type)
        assertEquals("Reban Ayam", action.targetTitle)
        assertEquals("Penjagaan Ayam", action.tableTitle)
    }

    @Test
    fun doesNotRecoverPlanningRequestWithoutCreateIntent() {
        val actions = AiMarkdownTableActionRecovery.recover(
            prompt = "boleh cadangkan jadual penjagaan ayam?",
            reply = """
                | Waktu | Tugas |
                |-------|-------|
                | Pagi | Beri makan |
            """.trimIndent(),
        )

        assertTrue(actions.isEmpty())
    }
}
