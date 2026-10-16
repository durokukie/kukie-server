package com.duro.kukie.actionplan.application.port.out

import com.duro.kukie.actionplan.domain.ActionPlan

interface ActionPlanMarkdownRenderer {
    fun render(plan: ActionPlan): String
}
