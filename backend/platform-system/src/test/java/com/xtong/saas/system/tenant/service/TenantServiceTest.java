package com.xtong.saas.system.tenant.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import com.xtong.saas.system.tenant.enums.TenantStatus;
import com.xtong.saas.system.tenant.exception.TenantErrorCode;
import com.xtong.saas.system.tenant.mapper.SystemTenantMapper;
import com.xtong.saas.system.tenant.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证租户查询服务只接受未删除的启用租户并返回稳定错误码。 */
class TenantServiceTest {

    private final SystemTenantMapper mapper = mock(SystemTenantMapper.class);
    private final TenantService service = new TenantServiceImpl(mapper);

    @Test
    void shouldReturnEnabledTenantByCodeAndExcludeDeletedRows() {
        SystemTenant tenant = tenant(TenantStatus.ENABLED);
        when(mapper.selectOne(any())).thenReturn(tenant);

        SystemTenant result = service.requireEnabledByCode("acme");

        assertThat(result).isSameAs(tenant);
        Wrapper<SystemTenant> query = captureSelectOneQuery();
        assertThat(query.getSqlSegment()).contains("tenant_code", "deleted");
        assertThat(parametersOf(query)).containsValues("acme", false);
    }

    @Test
    void shouldRejectMissingTenantWithStableErrorCode() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requireEnabledByCode("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TenantErrorCode.TENANT_NOT_FOUND);
    }

    @Test
    void shouldRejectDisabledTenantWithStableErrorCode() {
        when(mapper.selectOne(any())).thenReturn(tenant(TenantStatus.DISABLED));

        assertThatThrownBy(() -> service.requireEnabledByCode("disabled"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TenantErrorCode.TENANT_DISABLED);
    }

    @Test
    void shouldReportWhetherAnyNonDeletedTenantExists() {
        when(mapper.selectCount(any())).thenReturn(1L);

        assertThat(service.hasAnyTenant()).isTrue();
        Wrapper<SystemTenant> query = captureSelectCountQuery();
        assertThat(query.getSqlSegment()).contains("deleted");
        assertThat(parametersOf(query)).containsValue(false);
    }

    @Test
    void shouldReportNoTenantWhenNonDeletedCountIsZero() {
        when(mapper.selectCount(any())).thenReturn(0L);

        assertThat(service.hasAnyTenant()).isFalse();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Wrapper<SystemTenant> captureSelectOneQuery() {
        ArgumentCaptor<Wrapper<SystemTenant>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(mapper).selectOne(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Wrapper<SystemTenant> captureSelectCountQuery() {
        ArgumentCaptor<Wrapper<SystemTenant>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(mapper).selectCount(captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> parametersOf(Wrapper<SystemTenant> query) {
        assertThat(query).isInstanceOf(AbstractWrapper.class);
        return ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs();
    }

    private SystemTenant tenant(TenantStatus status) {
        SystemTenant tenant = new SystemTenant();
        tenant.setTenantCode("acme");
        tenant.setStatus(status);
        return tenant;
    }
}
