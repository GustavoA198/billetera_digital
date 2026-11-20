package com.billetera_digital.service;

import com.billetera_digital.entity.AppUser;
import com.billetera_digital.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final AppUserRepository appUserRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailedAttempt(UUID userId) {
        appUserRepository.incrementFailedLoginAttempts(userId);
        Optional<AppUser> optionalAppUser = appUserRepository.findById(userId);
        AppUser user = optionalAppUser.orElseThrow();
        if(user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS){
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            user.setFailedLoginAttempts(0);
            return true;
        }
        return false;
    }
}
