package com.changeyourlife.cyl.presentation.ai

internal object AiCellDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Cell,
)
