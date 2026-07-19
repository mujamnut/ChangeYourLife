package com.changeyourlife.cyl.presentation.ai

internal object AiPropertyDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Property,
)
