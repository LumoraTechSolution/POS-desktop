package com.lumora.pos.auth.repository;

import com.lumora.pos.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByEmailAndTenantId(String email, UUID tenantId);

    /**
     * Global (tenant-agnostic) lookup by email. Each deployment hosts a single
     * business, so email is unique across the database and this resolves the
     * user — and therefore their tenant — without the client supplying a tenant.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    List<UserEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantId(UUID tenantId);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<UserEntity> findByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    /**
     * Find a user by tenant — used for PIN login where we search by tenantId
     * and then validate the PIN in the service layer.
     */
    @Query("SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.pin IS NOT NULL AND u.isActive = true")
    java.util.List<UserEntity> findActiveUsersWithPinByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * All users that have a PIN set, active or not. Used to enforce PIN
     * uniqueness across the business (a reactivated user must not collide).
     */
    @Query("SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.pin IS NOT NULL")
    java.util.List<UserEntity> findAllWithPinByTenantId(@Param("tenantId") UUID tenantId);

    /** Ids of the branches a user is assigned to (for branch-access enforcement). */
    @Query("SELECT b.id FROM UserEntity u JOIN u.branches b WHERE u.id = :userId")
    Set<UUID> findBranchIdsByUserId(@Param("userId") UUID userId);
}
