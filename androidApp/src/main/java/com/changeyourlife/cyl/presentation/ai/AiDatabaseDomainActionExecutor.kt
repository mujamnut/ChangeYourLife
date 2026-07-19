package com.changeyourlife.cyl.presentation.ai

internal object AiDatabaseDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Database,
)
