package com.lumora.pos.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for User Management operations.
 */
public class UserManagementDtos {

    /** Response DTO — returned in list and detail views */
    @Data
    public static class UserResponse {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private boolean active;
        /** Whether this user has a 4-digit PIN set — used as manager-approval
         *  authorization for POS overrides (e.g. payment corrections). */
        private boolean hasPin;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
        private List<String> roles;
        /** Branch-access assignment (relevant when the BRANCH_RESTRICTIONS feature is on). */
        private UUID primaryBranchId;
        private List<BranchSummary> branches;
    }

    /** Lightweight branch reference for embedding in {@link UserResponse}. */
    @Data
    public static class BranchSummary {
        private UUID id;
        private String name;

        public BranchSummary() {}
        public BranchSummary(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Request DTO — create a new employee */
    @Data
    public static class CreateUserRequest {
        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @Size(min = 4, max = 4, message = "PIN must be exactly 4 digits")
        private String pin;

        private String phone;

        private List<String> roleNames; // e.g. ["CASHIER", "MANAGER"]

        /** Branches this user may operate at (used when BRANCH_RESTRICTIONS is on). Optional. */
        private List<UUID> branchIds;

        /** Primary branch — must be one of {@link #branchIds} when supplied. Optional. */
        private UUID primaryBranchId;
    }

    /** Request DTO — update an existing user's basic profile */
    @Data
    public static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        private String phone;
        private List<String> roleNames;

        /** Optional. When non-blank, sets/replaces the user's 4-digit PIN; a
         *  blank/null value leaves the existing PIN untouched. There is no way to
         *  clear a PIN through this path (that would silently disable a manager's
         *  approval ability). */
        @Size(min = 4, max = 4, message = "PIN must be exactly 4 digits")
        private String pin;

        /** When non-null, replaces the user's branch assignment. */
        private List<UUID> branchIds;

        /** When non-null, sets the primary branch (must be in {@link #branchIds}, or the
         *  user's existing branches when branchIds is omitted). */
        private UUID primaryBranchId;
    }

    /** Request DTO — replace a user's branch-access set (PUT /users/{id}/branches). */
    @Data
    public static class UpdateBranchesRequest {
        private List<UUID> branchIds;
        private UUID primaryBranchId;
    }

    /** Request DTO — set a user's primary branch (PATCH /users/{id}/primary-branch). */
    @Data
    public static class UpdatePrimaryBranchRequest {
        private UUID primaryBranchId;
    }

    /** Request DTO — the signed-in user edits their own basic profile (PUT /users/me). */
    @Data
    public static class UpdateMyProfileRequest {
        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        private String phone;
    }

    /** Request DTO — the signed-in user sets/replaces their own PIN (POST /users/me/pin). */
    @Data
    public static class ChangePinRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @NotBlank(message = "New PIN is required")
        @Size(min = 4, max = 4, message = "PIN must be exactly 4 digits")
        private String newPin;
    }

    /** A set of users that share the same 4-digit PIN. The PIN value itself is
     *  never exposed — only who collides, so an admin can reassign. */
    @Data
    public static class PinConflictGroup {
        private List<ConflictingUser> users;

        public PinConflictGroup() {}
        public PinConflictGroup(List<ConflictingUser> users) {
            this.users = users;
        }

        @Data
        public static class ConflictingUser {
            private UUID id;
            private String firstName;
            private String lastName;
            private List<BranchSummary> branches;

            public ConflictingUser() {}
            public ConflictingUser(UUID id, String firstName, String lastName, List<BranchSummary> branches) {
                this.id = id;
                this.firstName = firstName;
                this.lastName = lastName;
                this.branches = branches;
            }
        }
    }

    /** Request DTO — an ADMIN resets another user's password (POST /users/{id}/reset-password).
     *  The target is forced to change it on next login. */
    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "Temporary password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;
    }
}
