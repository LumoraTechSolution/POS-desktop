package com.lumora.pos.user.service;

import com.lumora.pos.auth.entity.RoleEntity;
import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.RoleRepository;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.cashsession.repository.CashSessionRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.tenant.TenantContext;
import com.lumora.pos.user.dto.UserManagementDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BranchRepository branchRepository;
    private final CashSessionRepository cashSessionRepository;
    private final com.lumora.pos.superadmin.repository.TenantConfigurationRepository tenantConfigurationRepository;
    private final com.lumora.pos.auth.repository.RefreshTokenRepository refreshTokenRepository;
    private final com.lumora.pos.auth.service.PinLookupHasher pinLookupHasher;

    // ─── List ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        UUID tenantId = TenantContext.getTenantId();
        return userRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));
        return toResponse(user);
    }

    // ─── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        long currentCount = userRepository.countByTenantId(tenantId);
        com.lumora.pos.superadmin.entity.TenantConfigurationEntity config = tenantConfigurationRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant configuration not found"));
        if (currentCount >= config.getMaxUsers()) {
            throw new BusinessException("Subscription limit reached: Maximum users (" + config.getMaxUsers() + ") allowed.");
        }

        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new BusinessException("Email is already in use");
        }

        if (request.getPin() != null && !request.getPin().isBlank()) {
            assertPinAvailable(request.getPin(), null, tenantId);
        }

        Set<RoleEntity> roles = resolveRoles(request.getRoleNames(), tenantId);

        UserEntity user = UserEntity.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .isActive(true)
                .roles(roles)
                .build();

        if (request.getPin() != null && !request.getPin().isBlank()) {
            setPin(user, request.getPin());
        }

        user.setTenantId(tenantId);
        applyBranchAssignment(user, request.getBranchIds(), request.getPrimaryBranchId(), tenantId);
        return toResponse(userRepository.save(user));
    }

    // ─── Update ──────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (request.getFirstName() != null)
            user.setFirstName(request.getFirstName());
        if (request.getLastName() != null)
            user.setLastName(request.getLastName());
        if (request.getPhone() != null)
            user.setPhone(request.getPhone());

        if (request.getRoleNames() != null) {
            user.setRoles(resolveRoles(request.getRoleNames(), tenantId));
        }

        // Set/replace the PIN only when one was supplied; blank leaves it as-is.
        if (request.getPin() != null && !request.getPin().isBlank()) {
            assertPinAvailable(request.getPin(), user.getId(), tenantId);
            setPin(user, request.getPin());
        }

        // Branch reassignment is optional here: only touch it when branchIds is supplied.
        if (request.getBranchIds() != null) {
            assertNoOpenSession(user, "branches");
            applyBranchAssignment(user, request.getBranchIds(), request.getPrimaryBranchId(), tenantId);
        } else if (request.getPrimaryBranchId() != null) {
            assertNoOpenSession(user, "primary branch");
            setPrimaryBranch(user, request.getPrimaryBranchId());
        }

        return toResponse(userRepository.save(user));
    }

    // ─── Branch assignment ─────────────────────────────────────────────────────

    /**
     * Replaces the user's branch-access set and primary branch (PUT /users/{id}/branches).
     * Rejected while the user has an open cash session — the change would only safely apply
     * to their next session, so the admin must wait for the drawer to close.
     */
    @Transactional
    public UserResponse updateUserBranches(UUID id, UpdateBranchesRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));
        assertNoOpenSession(user, "branches");
        applyBranchAssignment(user, request.getBranchIds(), request.getPrimaryBranchId(), tenantId);
        return toResponse(userRepository.save(user));
    }

    /**
     * Sets the user's primary branch (PATCH /users/{id}/primary-branch). The branch must
     * already be in the user's assigned set. Rejected while a cash session is open.
     */
    @Transactional
    public UserResponse updatePrimaryBranch(UUID id, UpdatePrimaryBranchRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));
        assertNoOpenSession(user, "primary branch");
        setPrimaryBranch(user, request.getPrimaryBranchId());
        return toResponse(userRepository.save(user));
    }

    // ─── Toggle Status ───────────────────────────────────────────────────────

    @Transactional
    public UserResponse toggleUserStatus(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));

        user.setActive(!user.isActive());
        return toResponse(userRepository.save(user));
    }

    /**
     * Activates/deactivates the given users in one transaction. Tenant-scoped
     * (foreign/unknown ids skipped). Guards against self-lockout: the caller is
     * never deactivated, even if their id is in the list. Returns the number of
     * users whose status actually changed.
     */
    @Transactional
    public int bulkSetStatus(List<UUID> ids, boolean active, UUID currentUserId) {
        UUID tenantId = TenantContext.getTenantId();
        List<UserEntity> found = userRepository.findByIdInAndTenantId(ids, tenantId);
        int changed = 0;
        for (UserEntity user : found) {
            if (!active && user.getId().equals(currentUserId)) continue; // never deactivate self
            if (user.isActive() != active) {
                user.setActive(active);
                changed++;
            }
        }
        userRepository.saveAll(found);
        return changed;
    }

    // ─── Self-service (profile page) ───────────────────────────────────────────

    /** The signed-in user updates their own name/phone. */
    @Transactional
    public UserResponse updateMyProfile(UUID userId, UpdateMyProfileRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        return toResponse(userRepository.save(user));
    }

    /** The signed-in user sets/replaces their own PIN after re-entering their password. */
    @Transactional
    public void changeMyPin(UUID userId, ChangePinRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }
        assertPinAvailable(request.getNewPin(), user.getId(), tenantId);
        setPin(user, request.getNewPin());
        userRepository.save(user);
    }

    /** Sets both the bcrypt verifier and the keyed blind-index lookup for a PIN. */
    private void setPin(UserEntity user, String rawPin) {
        user.setPin(passwordEncoder.encode(rawPin));
        user.setPinLookup(pinLookupHasher.hash(rawPin));
    }

    /**
     * Rejects a PIN already in use by another user in the business. PINs are
     * unique business-wide so each PIN maps to exactly one person at login.
     * The new PIN is in plaintext here, so this is a cheap bcrypt compare per
     * existing PIN-holder.
     */
    private void assertPinAvailable(String rawPin, UUID excludeUserId, UUID tenantId) {
        boolean taken = userRepository.findAllWithPinByTenantId(tenantId).stream()
                .filter(u -> excludeUserId == null || !u.getId().equals(excludeUserId))
                .anyMatch(u -> passwordEncoder.matches(rawPin, u.getPin()));
        if (taken) {
            throw new BusinessException("This PIN is already in use. Please choose a different PIN.");
        }
    }

    /**
     * Reports users that share a PIN, by grouping on the keyed blind index
     * ({@code pin_lookup}) — instant, no brute force. Covers every PIN set since
     * the blind index landed (V54); a legacy PIN that predates it and hasn't been
     * re-set has a null lookup and isn't grouped, but such a collision can no
     * longer authenticate anyway — {@code AuthService.pinLogin} rejects an
     * ambiguous PIN. The PIN value is never returned, only who collides.
     */
    @Transactional(readOnly = true)
    public List<PinConflictGroup> findPinConflicts() {
        UUID tenantId = TenantContext.getTenantId();

        Map<String, List<UserEntity>> byLookup = userRepository.findAllWithPinByTenantId(tenantId).stream()
                .filter(u -> u.getPinLookup() != null)
                .collect(Collectors.groupingBy(UserEntity::getPinLookup));

        return byLookup.values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> new PinConflictGroup(group.stream()
                        .map(u -> new PinConflictGroup.ConflictingUser(
                                u.getId(), u.getFirstName(), u.getLastName(),
                                u.getBranches().stream()
                                        .map(b -> new BranchSummary(b.getId(), b.getName()))
                                        .collect(Collectors.toList())))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    // ─── Admin-mediated password reset ─────────────────────────────────────────

    /**
     * An ADMIN resets another user's password. The new password is single-use:
     * the target must change it on their next login (must_change_password=true),
     * and all their existing refresh tokens are revoked so active sessions drop.
     */
    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UserEntity user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(true);
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(id);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Replaces {@code user}'s branch set with the tenant-validated {@code branchIds} and sets the
     * primary branch. A null/empty branchIds clears the assignment. The primary defaults to the
     * first branch when not supplied (or stays null when there are no branches).
     */
    private void applyBranchAssignment(UserEntity user, List<UUID> branchIds, UUID primaryBranchId, UUID tenantId) {
        Set<BranchEntity> branches = resolveBranches(branchIds, tenantId);
        user.setBranches(branches);

        if (branches.isEmpty()) {
            user.setPrimaryBranch(null);
            return;
        }
        if (primaryBranchId == null) {
            user.setPrimaryBranch(branches.iterator().next());
            return;
        }
        BranchEntity primary = branches.stream()
                .filter(b -> b.getId().equals(primaryBranchId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Primary branch must be one of the assigned branches"));
        user.setPrimaryBranch(primary);
    }

    /** Sets the primary branch, requiring it to already be in the user's assigned set. */
    private void setPrimaryBranch(UserEntity user, UUID primaryBranchId) {
        if (primaryBranchId == null) {
            throw new BusinessException("Primary branch is required");
        }
        BranchEntity primary = user.getBranches().stream()
                .filter(b -> b.getId().equals(primaryBranchId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Primary branch must be one of the user's assigned branches"));
        user.setPrimaryBranch(primary);
    }

    private Set<BranchEntity> resolveBranches(List<UUID> branchIds, UUID tenantId) {
        if (branchIds == null || branchIds.isEmpty()) {
            return new HashSet<>();
        }
        Map<UUID, BranchEntity> byId = branchRepository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(BranchEntity::getId, b -> b));
        Set<BranchEntity> resolved = new HashSet<>();
        for (UUID branchId : branchIds) {
            BranchEntity branch = byId.get(branchId);
            if (branch == null) {
                throw new BusinessException("Branch not found: " + branchId);
            }
            resolved.add(branch);
        }
        return resolved;
    }

    /** Guards against reassigning branches while a drawer is open (variance would be unattributable). */
    private void assertNoOpenSession(UserEntity user, String what) {
        if (cashSessionRepository.findActiveByUserId(user.getId()).isPresent()) {
            throw new BusinessException(
                    "Cannot change " + what + " while this user has an open cash session. "
                            + "Ask them to close their drawer first.");
        }
    }

    private Set<RoleEntity> resolveRoles(List<String> roleNames, UUID tenantId) {
        if (roleNames == null || roleNames.isEmpty())
            return new HashSet<>();
        return roleNames.stream()
                .map(name -> roleRepository.findByNameAndTenantId(name, tenantId)
                        .orElseThrow(() -> new BusinessException("Role not found: " + name)))
                .collect(Collectors.toSet());
    }

    private UserResponse toResponse(UserEntity user) {
        UserResponse dto = new UserResponse();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setPhone(user.getPhone());
        dto.setActive(user.isActive());
        dto.setHasPin(user.getPin() != null && !user.getPin().isBlank());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoles(user.getRoles().stream()
                .map(RoleEntity::getName)
                .collect(Collectors.toList()));
        dto.setPrimaryBranchId(user.getPrimaryBranch() != null ? user.getPrimaryBranch().getId() : null);
        dto.setBranches(user.getBranches().stream()
                .map(b -> new BranchSummary(b.getId(), b.getName()))
                .sorted(Comparator.comparing(BranchSummary::getName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList()));
        return dto;
    }
}
