package com.brycenkorea.template.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class BulkImportResult {
    private int total;
    private int success;
    private int fail;
    private List<FailedItem> failedItems;

    @AllArgsConstructor
    @Data
    @Builder
    public static class FailedItem {
        private String keyField;
        private String reason; // 예: "멤버 없음", "데이터 중복", "필수값 누락" 등
    }
}