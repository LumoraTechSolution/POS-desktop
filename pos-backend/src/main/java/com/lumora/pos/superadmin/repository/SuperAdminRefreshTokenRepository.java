package com.lumora.pos.superadmin.repository;

import com.lumora.pos.superadmin.entity.SuperAdminRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SuperAdminRefreshTokenRepository extends JpaRepository<SuperAdminRefreshTokenEntity, UUID> {

    Optional<SuperAdminRefreshTokenEntity> findByToken(String token);

    @Modifying
    @Query("UPDATE SuperAdminRefreshTokenEntity t SET t.isRevoked = true WHERE t.superAdminId = :superAdminId AND t.isRevoked = false")
    void revokeAllForSuperAdmin(@Param("superAdminId") UUID superAdminId);

    @Modifying
    @Query("UPDATE SuperAdminRefreshTokenEntity t SET t.isRevoked = true WHERE t.token = :token")
    void revokeByToken(@Param("token") String token);
}
