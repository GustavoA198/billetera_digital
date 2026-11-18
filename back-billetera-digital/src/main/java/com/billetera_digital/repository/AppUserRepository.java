package com.billetera_digital.repository;

import com.billetera_digital.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    Optional<AppUser> findByEmail(String email);
}
