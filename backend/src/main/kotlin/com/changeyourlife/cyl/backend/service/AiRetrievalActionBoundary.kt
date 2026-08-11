package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import java.util.Locale

internal object AiRetrievalActionBoundary {
    fun enforce(
        result: AiService.AiActionResult,
        pages: List<AiPageContext>,
    ): AiService.AiActionResult {
        if (result.actions.isEmpty()) return result

        val createdPageTitles = result.actions
            .mapNotNull { action -> action.createdRootPageTitle() }
            .mapTo(mutableSetOf()) { title -> title.normalizedPageReference() }
        val boundaryIssues = mutableListOf<AiActionValidationIssue>()

        result.actions.forEachIndexed { actionIndex, action ->
            val blockedField = action.firstMetadataOnlyPageField(
                pages = pages,
                createdPageTitles = createdPageTitles,
            )
            if (blockedField == null) {
                if (action.type.uppercase(Locale.ROOT) == CreateSubpageAction) {
                    action.title.normalizedPageReference()
                        .takeIf(String::isNotBlank)
                        ?.let(createdPageTitles::add)
                }
            } else {
                boundaryIssues += AiActionValidationIssue(
                    actionIndex = actionIndex,
                    field = blockedField,
                    code = TargetOutsideRetrievalScopeCode,
                    message = "The page is metadata-only. Open, mention, or retrieve it before reading or changing it.",
                )
            }
        }

        return result.copy(
            validationIssues = (result.validationIssues + boundaryIssues)
                .distinctBy { issue ->
                    "${issue.actionIndex}:${issue.field}:${issue.code}:${issue.message}"
                },
        )
    }

    private fun AiService.AiActionItem.firstMetadataOnlyPageField(
        pages: List<AiPageContext>,
        createdPageTitles: Set<String>,
    ): String? {
        val actionType = type.uppercase(Locale.ROOT)
        val references = buildList {
            if (actionType != CreateRootPageAction) {
                add(
                    PageReference(
                        field = "targetTitle",
                        value = targetTitle,
                        mayReferenceNewPage = true,
                    ),
                )
            }
            if (actionType == AttachTableDataSourceAction) {
                add(
                    PageReference(
                        field = if (sourcePageId.isNotBlank()) "sourcePageId" else "sourcePageTitle",
                        value = sourcePageId.ifBlank { sourcePageTitle },
                    ),
                )
            }
            if (actionType == MovePageAction && !movesPageToWorkspaceRoot()) {
                add(
                    PageReference(
                        field = if (parentPageId.isNotBlank()) "parentPageId" else "parentPageTitle",
                        value = parentPageId.ifBlank { parentPageTitle },
                    ),
                )
            }
        }
        return references.firstNotNullOfOrNull { reference ->
            if (
                reference.value.isBlank() ||
                reference.mayReferenceNewPage &&
                reference.value.normalizedPageReference() in createdPageTitles
            ) {
                return@firstNotNullOfOrNull null
            }
            val page = AiPageTargetMatcher.findPageByAiTitle(
                pages = pages,
                rawTitle = reference.value,
            )
            reference.field.takeIf {
                page != null && !page.hasReadableAiContext()
            }
        }
    }

    private fun AiPageContext.hasReadableAiContext(): Boolean =
        access.equals(AccessTarget, ignoreCase = true) ||
            access.equals(AccessRetrieved, ignoreCase = true)

    private fun String.normalizedPageReference(): String =
        trim()
            .removePrefix("@")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun AiService.AiActionItem.createdRootPageTitle(): String? {
        return when (type.uppercase(Locale.ROOT)) {
            CreateRootPageAction -> title.ifBlank { tableTitle }.ifBlank { content }
            "CREATE_DATABASE", "CREATE_TABLE" -> if (targetTitle.isBlank()) {
                title.ifBlank { tableTitle }.ifBlank { content }
            } else {
                ""
            }
            else -> ""
        }.takeIf(String::isNotBlank)
    }

    private fun AiService.AiActionItem.movesPageToWorkspaceRoot(): Boolean {
        if (parentPageId.isNotBlank()) return false
        val parent = parentPageTitle.trim().removePrefix("@").trim()
        return parent.isBlank() ||
            parent.equals("root", ignoreCase = true) ||
            parent.equals("workspace", ignoreCase = true)
    }

    private data class PageReference(
        val field: String,
        val value: String,
        val mayReferenceNewPage: Boolean = false,
    )

    const val TargetOutsideRetrievalScopeCode = "target_outside_retrieval_scope"

    private const val AccessTarget = "Target"
    private const val AccessRetrieved = "Retrieved"
    private const val CreateRootPageAction = "CREATE_PAGE"
    private const val CreateSubpageAction = "CREATE_SUBPAGE"
    private const val AttachTableDataSourceAction = "ATTACH_TABLE_DATA_SOURCE"
    private const val MovePageAction = "MOVE_PAGE"
}
