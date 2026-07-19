package com.changeyourlife.cyl.presentation.ai

internal object AiPageDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Page,
)
