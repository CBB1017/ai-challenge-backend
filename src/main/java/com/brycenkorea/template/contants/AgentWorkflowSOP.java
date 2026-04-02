package com.brycenkorea.template.contants;

public record AgentWorkflowSOP(String intentId,      // 예: "OVERTIME", "VACATION", "GENERAL"
                               String rules,         // 프롬프트에 주입될 핵심 매뉴얼
                               boolean requiresRag   // 사내 지식(Vector DB) 검색 필요 여부
) {
    // 필요한 경우 유효성 검증 로직을 컴팩트하게 추가할 수 있습니다.
    public AgentWorkflowSOP {
        if (intentId == null || rules == null) {
            throw new IllegalArgumentException("intentId와 rules는 필수입니다.");
        }
    }
}