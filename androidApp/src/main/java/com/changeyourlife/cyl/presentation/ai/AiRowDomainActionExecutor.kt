package com.changeyourlife.cyl.presentation.ai

internal object AiRowDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Row,
)
