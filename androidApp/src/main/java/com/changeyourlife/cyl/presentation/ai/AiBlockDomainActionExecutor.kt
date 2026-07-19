package com.changeyourlife.cyl.presentation.ai

internal object AiBlockDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Block,
)
