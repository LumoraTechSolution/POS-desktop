package com.lumora.pos.inventory.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkProductImportResponse {
    private int totalRows;
    private int successCount;
    private int failureCount;

    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class ImportError {
        private int rowNumber;
        private String field;
        private String message;
    }
}
