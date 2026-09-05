package com.xtong.saas.system.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 承载只能使用一次的原始 Refresh Token，不对令牌内容做规范化。 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
