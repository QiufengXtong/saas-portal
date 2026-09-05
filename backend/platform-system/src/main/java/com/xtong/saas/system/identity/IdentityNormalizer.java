package com.xtong.saas.system.identity;

import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.exception.CommonErrorCode;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** 集中规范化并校验租户编码和用户名，避免身份查询与失败计数出现语义分叉。 */
public final class IdentityNormalizer {

    private static final int MAX_LENGTH = 64;
    private static final Pattern IDENTITY = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private IdentityNormalizer() {
    }

    /** 登录边界使用空值表示不合法，调用方可统一映射为无效凭据且不访问数据库。 */
    public static Optional<String> normalizeForLogin(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH || !IDENTITY.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    /** 管理和初始化边界对不合法身份返回统一参数错误。 */
    public static String requireManagement(String value) {
        return normalizeForLogin(value)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_PARAMETER));
    }
}
