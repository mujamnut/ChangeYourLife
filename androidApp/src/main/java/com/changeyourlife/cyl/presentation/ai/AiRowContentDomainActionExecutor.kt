package com.changeyourlife.cyl.presentation.ai

internal object AiRowContentDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.RowContent,
)
