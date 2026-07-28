package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageTableView

internal sealed interface TableSavedViewMutation {
    data class Create(
        val name: String,
        val view: PageTableView,
    ) : TableSavedViewMutation

    data class Rename(
        val viewId: String,
        val currentName: String,
        val newName: String,
    ) : TableSavedViewMutation

    data class Delete(
        val viewId: String,
        val name: String,
    ) : TableSavedViewMutation

    data class Activate(
        val viewId: String,
        val name: String,
    ) : TableSavedViewMutation
}
