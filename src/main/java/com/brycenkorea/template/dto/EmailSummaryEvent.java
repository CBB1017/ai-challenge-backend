package com.brycenkorea.template.dto;

import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import java.util.UUID;

public record EmailSummaryEvent(
    String prompt,
    UUID roomId,
    String userId,
    String language,
    AgentWorkflowSOP sop,
    GroupwareAuthenticationToken auth
) {}
