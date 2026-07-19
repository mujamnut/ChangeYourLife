package com.changeyourlife.cyl.presentation.ai

internal object AiColumnDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Column,
)
