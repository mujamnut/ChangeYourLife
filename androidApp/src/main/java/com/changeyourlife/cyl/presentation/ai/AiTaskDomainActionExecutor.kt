package com.changeyourlife.cyl.presentation.ai

internal object AiTaskDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Task,
)

internal object AiReminderDomainActionExecutor : DelegatingAiPageActionDomainExecutor(
    domain = AiActionExecutionDomain.Reminder,
)
