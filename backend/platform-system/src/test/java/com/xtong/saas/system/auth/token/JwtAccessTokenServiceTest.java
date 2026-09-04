package com.xtong.saas.system.auth.token;

import com.xtong.saas.system.auth.config.AuthProperties;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Access JWT、Refresh Token 随机值与摘要的安全契约。 */
class JwtAccessTokenServiceTest {

    private static final String JWT_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void shouldRoundTripRequiredJwtClaimsWithoutEmbeddingPermissions() {
        AuthProperties properties = properties(Duration.ofMinutes(15));
        JwtAccessTokenService service = new JwtAccessTokenService(properties);
        AuthenticatedUser expected = new AuthenticatedUser(
                1L, 2L, "s1", "admin", Set.of("system:user:list"));
        Instant issuedAfter = Instant.now().minusSeconds(1);

        String token = service.issue(expected);
        AuthenticatedUser actual = service.parse(token);
        Jwt decoded = NimbusJwtDecoder.withSecretKey(secretKey(JWT_SECRET))
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build()
                .decode(token);

        assertThat(actual.tenantId()).isEqualTo(1L);
        assertThat(actual.userId()).isEqualTo(2L);
        assertThat(actual.sessionId()).isEqualTo("s1");
        assertThat(actual.username()).isEqualTo("admin");
        assertThat(actual.permissions()).isEmpty();
        assertThat(decoded.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(decoded.getIssuedAt()).isAfterOrEqualTo(issuedAfter);
        assertThat(decoded.getExpiresAt()).isEqualTo(decoded.getIssuedAt().plus(Duration.ofMinutes(15)));
        assertThat(decoded.getClaims()).doesNotContainKeys("permissions", "authorities", "refreshToken");
    }

    @Test
    void shouldRejectJwtSignedWithAnotherSecret() {
        JwtAccessTokenService issuer = new JwtAccessTokenService(properties(Duration.ofMinutes(15)));
        JwtAccessTokenService verifier = new JwtAccessTokenService(new AuthProperties(
                "abcdef0123456789abcdef0123456789",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                5,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15)));
        String token = issuer.issue(new AuthenticatedUser(1L, 2L, "s1", "admin", Set.of()));

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectExpiredJwt() {
        Clock expiredClock = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        JwtAccessTokenService service = new JwtAccessTokenService(properties(Duration.ofMinutes(15)), expiredClock);
        String token = service.issue(new AuthenticatedUser(1L, 2L, "s1", "admin", Set.of()));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectSignedJwtThatOmitsARequiredIdentityClaimAsJwtException() {
        JwtAccessTokenService service = new JwtAccessTokenService(properties(Duration.ofMinutes(15)));
        Instant issuedAt = Instant.now();
        JwtClaimsSet incompleteClaims = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .claim("tenantId", 1L)
                .claim("userId", 2L)
                .claim("username", "admin")
                .build();
        String token = NimbusJwtEncoder.withSecretKey(secretKey(JWT_SECRET))
                .algorithm(MacAlgorithm.HS256)
                .build()
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        incompleteClaims))
                .getTokenValue();

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldGenerateDistinctUrlSafeRefreshTokensWithAtLeastThirtyTwoRandomBytes() {
        RefreshTokenGenerator generator = new RefreshTokenGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("=", "+", "/");
        assertThat(Base64.getUrlDecoder().decode(first)).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void shouldHashRefreshTokenAsLowercaseSha256Hex() {
        assertThat(new TokenHashService().hash("refresh-token"))
                .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120");
    }

    @Test
    void shouldValidateJwtSecretAndTokenTtlRelationshipsAtConstruction() {
        assertThatThrownBy(() -> new AuthProperties(
                "short-secret",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                5,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32")
                .hasMessageNotContaining("short-secret");

        assertThatThrownBy(() -> properties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessTokenTtl");

        assertThatThrownBy(() -> new AuthProperties(
                JWT_SECRET,
                Duration.ofDays(7),
                Duration.ofDays(7),
                5,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessTokenTtl");
    }

    private static AuthProperties properties(Duration accessTokenTtl) {
        return new AuthProperties(
                JWT_SECRET,
                accessTokenTtl,
                Duration.ofDays(7),
                5,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15));
    }

    private static SecretKeySpec secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
