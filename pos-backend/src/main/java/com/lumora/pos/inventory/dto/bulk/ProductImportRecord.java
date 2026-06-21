package com.lumora.pos.inventory.dto.bulk;

import lombok.Data;

@Data
public class ProductImportRecord {
    private String name;
    private String sku;
    private String barcode;
    private String description;
    private String categoryName;
    private String brandName;
    private String basePrice;
    private String costPrice;
    private String stockQuantity;
    private String lowStockThreshold;
}
