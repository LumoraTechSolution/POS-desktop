package com.lumora.pos.hardware.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.hardware.service.QzSigningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * QZ Tray digital-signing endpoints (see {@link QzSigningService}). Authenticated
 * (any tenant user prints), but signing material itself lives only on the server.
 */
@RestController
@RequestMapping("/api/v1/hardware/qz")
@RequiredArgsConstructor
public class QzSigningController {

    private final QzSigningService qzSigningService;

    /**
     * Public certificate the QZ Tray app uses to trust this site. Returns 204 when
     * signing isn't configured so the frontend silently stays in unsigned mode.
     */
    @GetMapping("/certificate")
    public ResponseEntity<ApiResponse<String>> certificate() {
        if (!qzSigningService.isConfigured()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ApiResponse.success(qzSigningService.getCertificate(), "Certificate"));
    }

    /** Signs a QZ request payload, returning a base64 signature. */
    @PostMapping("/sign")
    public ResponseEntity<ApiResponse<SignResponse>> sign(@Valid @RequestBody SignRequest request) {
        if (!qzSigningService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("QZ signing is not configured"));
        }
        String signature = qzSigningService.sign(request.getRequest());
        return ResponseEntity.ok(ApiResponse.success(new SignResponse(signature), "Signed"));
    }

    @Data
    public static class SignRequest {
        @NotNull
        private String request;
    }

    public record SignResponse(String signature) {}
}
