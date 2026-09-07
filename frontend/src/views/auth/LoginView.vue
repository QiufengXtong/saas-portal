<!-- 文件作用：提供租户用户登录表单并在成功后恢复原目标页面。 -->
<script setup lang="ts">
import type {FormInstance, FormRules} from 'element-plus'
import {reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import ThemeToggle from '@/components/ThemeToggle.vue'
import {useAuthStore} from '@/stores/auth'
import type {LoginCommand} from '@/types/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<LoginCommand>({tenantCode: '', username: '', password: ''})
const rules: FormRules<LoginCommand> = {
    tenantCode: [{required: true, message: '请输入租户编码', trigger: 'blur'}, {max: 64, message: '最多 64 个字符', trigger: 'blur'}],
    username: [{required: true, message: '请输入用户名', trigger: 'blur'}, {max: 64, message: '最多 64 个字符', trigger: 'blur'}],
    password: [{required: true, message: '请输入密码', trigger: 'blur'}],
}

const submit = async () => {
    if (!await formRef.value?.validate().catch(() => false)) return
    submitting.value = true
    try {
        await authStore.login(form)
        const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
            ? route.query.redirect : '/dashboard'
        await router.replace(redirect)
    } catch {
        // 统一请求层已经展示安全错误信息。
    } finally {
        submitting.value = false
    }
}
</script>

<template>
  <main class="login-page">
    <ThemeToggle class="login-theme" />
    <section class="login-intro">
      <p class="eyebrow">
        SAAS PORTAL
      </p>
      <h1>让租户、用户与权限管理<br>保持清晰可控</h1>
      <p>统一身份认证与精确授权，为后续业务模块提供稳定入口。</p>
    </section>
    <el-card
      class="login-card"
      shadow="always"
    >
      <h2>登录管理后台</h2>
      <p class="hint">
        请输入租户账号信息继续
      </p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="submit"
      >
        <el-form-item
          label="租户编码"
          prop="tenantCode"
        >
          <el-input
            v-model="form.tenantCode"
            maxlength="64"
            autocomplete="organization"
          />
        </el-form-item>
        <el-form-item
          label="用户名"
          prop="username"
        >
          <el-input
            v-model="form.username"
            maxlength="64"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item
          label="密码"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button
          :loading="submitting"
          type="primary"
          class="submit"
          @click="submit"
        >
          登录
        </el-button>
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page { position: relative; min-height: 100vh; display: grid; grid-template-columns: 1.2fr minmax(360px, 480px); align-items: center; gap: 8vw; padding: 8vw; color: var(--login-heading); background: var(--login-bg); transition: color .25s, background .25s; }
.login-theme { position: absolute; top: 24px; right: 28px; }
.login-intro h1 { margin: 12px 0 20px; font-size: clamp(34px, 5vw, 64px); line-height: 1.15; letter-spacing: -.04em; }
.login-intro p { max-width: 620px; color: var(--login-copy); font-size: 17px; }
.eyebrow { color: var(--el-color-primary) !important; font-size: 13px !important; font-weight: 800; letter-spacing: .18em; }
.login-card { padding: 18px; border: 1px solid var(--app-border); border-radius: 18px; background: var(--app-surface); box-shadow: 0 24px 70px rgb(15 23 42 / 16%); }
.login-card h2 { margin: 0; color: var(--app-text); font-size: 26px; }
.hint { margin: 8px 0 24px; color: var(--app-text-muted); }
.submit { width: 100%; height: 42px; margin-top: 8px; }
@media (max-width: 860px) { .login-page { grid-template-columns: 1fr; padding: 24px; } .login-intro { display: none; } .login-card { width: min(100%, 480px); justify-self: center; } }
</style>
