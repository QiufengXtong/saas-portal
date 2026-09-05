package com.xtong.saas.system.auth.vo;

/** 返回短期 Access Token、轮换 Refresh Token 及标准 Bearer 元数据。 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {
}
