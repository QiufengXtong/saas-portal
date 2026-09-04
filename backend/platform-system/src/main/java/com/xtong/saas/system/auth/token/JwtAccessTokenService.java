package com.xtong.saas.system.auth.token;

import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/** 使用 HS256 签发和验证仅包含身份及会话声明的短期 JWT。 */
@Service
public class JwtAccessTokenService implements AccessTokenService {

    private static final String TENANT_ID_CLAIM = "tenantId";
    private static final String USER_ID_CLAIM = "userId";
    private static final String SESSION_ID_CLAIM = "sessionId";
    private static final String USERNAME_CLAIM = "username";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties properties;
    private final Clock clock;

    @Autowired
    public JwtAccessTokenService(AuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtAccessTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SecretKey secretKey = new SecretKeySpec(
                properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
        this.decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Override
    public String issue(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(user.userId()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
                .claim(TENANT_ID_CLAIM, user.tenantId())
                .claim(USER_ID_CLAIM, user.userId())
                .claim(SESSION_ID_CLAIM, user.sessionId())
                .claim(USERNAME_CLAIM, user.username())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public AuthenticatedUser parse(String token) {
        Jwt jwt = decoder.decode(token);
        return new AuthenticatedUser(
                requiredLongClaim(jwt, TENANT_ID_CLAIM),
                requiredLongClaim(jwt, USER_ID_CLAIM),
                requiredStringClaim(jwt, SESSION_ID_CLAIM),
                requiredStringClaim(jwt, USERNAME_CLAIM),
                Set.of());
    }

    private static long requiredLongClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        if (!(value instanceof Number number)) {
            throw new BadJwtException("JWT has an invalid required claim: " + claimName);
        }
        return number.longValue();
    }

    private static String requiredStringClaim(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            throw new BadJwtException("JWT has an invalid required claim: " + claimName);
        }
        return value;
    }
}
