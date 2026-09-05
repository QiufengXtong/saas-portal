package com.xtong.saas.system.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.xtong.saas.system.identity.IdentityNormalizer;

/** 表示可更新的用户身份和资料，用户名存在时必须遵循统一身份规则。 */
public record UpdateUserDTO(
        @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String displayName,
        @Email @Size(max = 254) String email,
        @Size(max = 32) String mobile) {

    /** 兼容仅更新资料的调用方。 */
    public UpdateUserDTO(String displayName, String email, String mobile) {
        this(null, displayName, email, mobile);
    }

    /** 合法用户名在 DTO 边界即转为存储规范形式。 */
    public UpdateUserDTO {
        username = IdentityNormalizer.normalizeForLogin(username).orElse(username);
    }

    @jakarta.validation.constraints.AssertTrue(message = "用户名格式不合法")
    public boolean isUsernameValid() {
        return username == null || IdentityNormalizer.normalizeForLogin(username).isPresent();
    }
}
