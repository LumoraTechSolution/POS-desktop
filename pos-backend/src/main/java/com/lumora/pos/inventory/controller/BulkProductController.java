package com.lumora.pos.inventory.controller;

import com.lumora.pos.inventory.dto.bulk.BulkProductImportResponse;
import com.lumora.pos.inventory.service.BulkProductService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/bulk/products")
@RequiredArgsConstructor
public class BulkProductController {

    private final BulkProductService bulkProductService;

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BulkProductImportResponse> importProducts(@RequestParam("file") MultipartFile file)
            throws IOException {
        BulkProductImportResponse response = bulkProductService.importProducts(file.getInputStream());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public void exportProducts(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=products_export.csv");
        bulkProductService.exportProducts(response.getWriter());
    }
}
