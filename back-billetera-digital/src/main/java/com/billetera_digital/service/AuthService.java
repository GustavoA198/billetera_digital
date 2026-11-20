package com.billetera_digital.service;

import com.billetera_digital.dto.LoginRequest;
import com.billetera_digital.dto.LoginResponse;
import com.billetera_digital.dto.RegisterRequest;
import com.billetera_digital.dto.UserResponse;
import com.billetera_digital.entity.AppUser;
import com.billetera_digital.entity.Role;
import com.billetera_digital.entity.Wallet;
import com.billetera_digital.entity.WalletStatus;
import com.billetera_digital.exception.AccountLockedException;
import com.billetera_digital.exception.EmailAlreadyRegisteredException;
import com.billetera_digital.exception.InvalidCredentialsException;
import com.billetera_digital.exception.PhoneAlreadyRegisteredException;
import com.billetera_digital.repository.AppUserRepository;
import com.billetera_digital.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;


    @Transactional
    public UserResponse register(RegisterRequest request) {
        String emailLowerCase = request.email().toLowerCase(Locale.ROOT);
        if(appUserRepository.existsByEmail(emailLowerCase)){
            throw new EmailAlreadyRegisteredException();
        }

        if(appUserRepository.existsByPhone(request.phone())){
            throw new PhoneAlreadyRegisteredException();
        }

        AppUser newUser = new AppUser();
        newUser.setEmail(emailLowerCase);
        newUser.setPhone(request.phone());
        newUser.setFullName(request.fullName());
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));
        newUser.setRole(Role.CUSTOMER);
        newUser.setMustChangePassword(false);
        newUser.setFailedLoginAttempts(0);
        AppUser savedUser = appUserRepository.save(newUser);

        Wallet newWallet = new Wallet();
        newWallet.setUser(savedUser);
        newWallet.setBalance(BigDecimal.ZERO);
        newWallet.setStatus(WalletStatus.ACTIVE);
        newWallet.setVersion(0);
        walletRepository.save(newWallet);

        UserResponse userResponse = new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getFullName(),
                savedUser.getRole()
        );

        return userResponse;
    }


    @Transactional
    public LoginResponse login(LoginRequest request){
        Instant instant = Instant.now();
        String emailLowerCase = request.email().toLowerCase(Locale.ROOT);

        Optional<AppUser> optionalAppUser = appUserRepository.findByEmail(emailLowerCase);
        AppUser user = optionalAppUser.orElseThrow(InvalidCredentialsException::new);

        if(user.getLockedUntil() != null && user.getLockedUntil().isAfter(instant)){
            throw new AccountLockedException();
        }

        if(!passwordEncoder.matches(request.password(), user.getPasswordHash())){
            if(loginAttemptService.registerFailedAttempt(user.getId())){
                throw new AccountLockedException();
            }
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        String jwt = jwtService.generateAccessToken(user);

        LoginResponse loginResponse = new LoginResponse(jwt, "Bearer", jwtService.getAccessTokenExpiresInSeconds());

        return loginResponse;

    }
}
