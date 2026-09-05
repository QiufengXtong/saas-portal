package com.xtong.saas.system.identity;

import com.xtong.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证租户编码和用户名共享严格、确定且不可混淆的 ASCII 规范化规则。 */
class IdentityNormalizerTest {

    @Test
    void shouldStripAndLowercaseValidAsciiIdentity() {
        assertThat(IdentityNormalizer.normalizeForLogin("  Acme.Admin-1  ")).contains("acme.admin-1");
    }

    @Test
    void shouldRejectColonUnicodeAccentAndWhitespaceOnlyIdentity() {
        assertThat(IdentityNormalizer.normalizeForLogin("acme:admin")).isEmpty();
        assertThat(IdentityNormalizer.normalizeForLogin("admín")).isEmpty();
        assertThat(IdentityNormalizer.normalizeForLogin("   ")).isEmpty();
        assertThatThrownBy(() -> IdentityNormalizer.requireManagement("acme:admin"))
                .isInstanceOf(BusinessException.class);
    }
}
