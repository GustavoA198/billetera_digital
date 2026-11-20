package com.billetera_digital.repository;

import com.billetera_digital.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    Optional<AppUser> findByEmail(String email);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE AppUser u
        SET u.failedLoginAttempts = u.failedLoginAttempts + 1
        WHERE u.id = :id
    """)
    void incrementFailedLoginAttempts(@Param("id") UUID id);

}
