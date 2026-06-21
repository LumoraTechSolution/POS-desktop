package com.lumora.pos.tenant.service;

import com.lumora.pos.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

/**
 * Validates an uploaded logo and encodes it as a self-contained data URI.
 *
 * The logo is persisted on the tenant row (not on disk) so it survives restarts
 * and prints without a network fetch — see {@code TenantEntity.logoDataUri}.
 */
@Service
public class LogoEncodingService {

    /** Capped well below the 5 MB multipart limit: the data URI is embedded in DB
     *  rows and receipt HTML, so it must stay small enough to print fast. */
    private static final long MAX_BYTES = 512L * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /** Validates the file and returns it as "data:&lt;type&gt;;base64,&lt;…&gt;". */
    public String toDataUri(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Only JPEG, PNG, GIF, and WebP images are allowed");
        }
        if (file.isEmpty()) {
            throw new BusinessException("File must not be empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("File size must not exceed 512 KB");
        }

        try {
            String encoded = Base64.getEncoder().encodeToString(file.getBytes());
            return "data:" + contentType + ";base64," + encoded;
        } catch (IOException e) {
            throw new BusinessException("Failed to read file — please try again");
        }
    }
}
