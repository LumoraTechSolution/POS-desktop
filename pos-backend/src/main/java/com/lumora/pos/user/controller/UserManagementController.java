package com.lumora.pos.user.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.common.dto.BulkStatusRequest;
import com.lumora.pos.user.dto.UserManagementDtos.*;
import com.lumora.pos.user.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.getAllUsers(),
                "Users retrieved successfully"));
    }

    @GetMapping("/pin-conflicts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PinConflictGroup>>> getPinConflicts() {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.findPinConflicts(),
                "PIN conflicts retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.getUserById(id),
                "User retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                userManagementService.createUser(request),
                "User created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.updateUser(id, request),
                "User updated successfully"));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> toggleStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.toggleUserStatus(id),
                "User status updated"));
    }

    @PutMapping("/{id}/branches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateBranches(
            @PathVariable UUID id,
            @RequestBody UpdateBranchesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.updateUserBranches(id, request),
                "Branch assignment updated"));
    }

    @PatchMapping("/{id}/primary-branch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updatePrimaryBranch(
            @PathVariable UUID id,
            @RequestBody UpdatePrimaryBranchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.updatePrimaryBranch(id, request),
                "Primary branch updated"));
    }

    // ─── Self-service (profile page) ───────────────────────────────────────────

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @Valid @RequestBody UpdateMyProfileRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.updateMyProfile(currentUserId, request),
                "Profile updated"));
    }

    @PostMapping("/me/pin")
    public ResponseEntity<ApiResponse<Void>> changeMyPin(
            @Valid @RequestBody ChangePinRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        userManagementService.changeMyPin(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "PIN updated"));
    }

    // ─── Admin-mediated password reset ─────────────────────────────────────────

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userManagementService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success(null,
                "Password reset. The user must change it on next login."));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> bulkSetStatus(
            @Valid @RequestBody BulkStatusRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        int updated = userManagementService.bulkSetStatus(request.getIds(), request.isActive(), currentUserId);
        String state = request.isActive() ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.success(updated, updated + " user(s) " + state));
    }
}
