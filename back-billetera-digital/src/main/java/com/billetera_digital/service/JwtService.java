package com.billetera_digital.service;

import com.billetera_digital.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private static final Duration ACCESS_TOKEN_DURATION = Duration.ofMinutes(15);


    public String generateAccessToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TOKEN_DURATION.toSeconds()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtEncoderParameters parameters = JwtEncoderParameters.from(header, claims);

        Jwt jwt = jwtEncoder.encode(parameters);
        return jwt.getTokenValue();
    }

    public long getAccessTokenExpiresInSeconds() {
        return ACCESS_TOKEN_DURATION.toSeconds();
    }
}
