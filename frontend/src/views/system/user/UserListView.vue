<!-- 文件作用：提供租户用户分页查询、维护、启停、删除、密码重置和角色分配页面。 -->
<script setup lang="ts">
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {onMounted, reactive, ref} from 'vue'

import {getRoles} from '@/api/role'
import * as userApi from '@/api/user'
import {usePermission} from '@/composables/usePermission'
import type {CreateUserCommand, EnabledStatus, RoleView, UserQuery, UserView} from '@/types/system'
import {formatDateTime} from '@/utils/format'

const {hasPermission} = usePermission()
const loading = ref(false)
const submitting = ref(false)
const records = ref<UserView[]>([])
const total = ref(0)
const query = reactive<UserQuery>({pageNum: 1, pageSize: 20})
const roles = ref<RoleView[]>([])

const editVisible = ref(false)
const editingId = ref<string>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<CreateUserCommand>({username: '', displayName: '', password: '', email: '', mobile: '', roleIds: []})
const editRules: FormRules<CreateUserCommand> = {
    username: [{required: true, message: '请输入用户名', trigger: 'blur'}, {pattern: /^[a-z0-9][a-z0-9._-]*$/i, message: '仅支持字母、数字、点、下划线和连字符', trigger: 'blur'}],
    displayName: [{required: true, message: '请输入显示名称', trigger: 'blur'}],
    password: [{validator: (_rule, value: string, callback) => editingId.value || value.length >= 8 ? callback() : callback(new Error('密码至少 8 个字符')), trigger: 'blur'}],
}

const roleVisible = ref(false)
const roleUser = ref<UserView>()
const selectedRoleIds = ref<string[]>([])
const passwordVisible = ref(false)
const passwordUser = ref<UserView>()
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({password: '', confirmPassword: ''})
const passwordRules: FormRules = {
    password: [{required: true, message: '请输入新密码', trigger: 'blur'}, {min: 8, message: '密码至少 8 个字符', trigger: 'blur'}],
    confirmPassword: [{validator: (_rule, value: string, callback) => value === passwordForm.password ? callback() : callback(new Error('两次密码不一致')), trigger: 'blur'}],
}

const load = async () => {
    loading.value = true
    try {
        const page = await userApi.getUsers(query)
        records.value = page.records
        total.value = page.total
    } catch {
        records.value = []
        total.value = 0
    } finally { loading.value = false }
}

const loadRoles = async () => {
    const page = await getRoles({pageNum: 1, pageSize: 500, status: 'ENABLED'})
    roles.value = page.records
}

const resetQuery = () => {
    query.pageNum = 1
    query.username = undefined
    query.status = undefined
    void load()
}

const openCreate = async () => {
    editingId.value = undefined
    Object.assign(editForm, {username: '', displayName: '', password: '', email: '', mobile: '', roleIds: []})
    await loadRoles()
    editVisible.value = true
}

const openEdit = async (user: UserView) => {
    editingId.value = user.id
    Object.assign(editForm, {username: user.username, displayName: user.displayName, password: '', email: user.email ?? '', mobile: user.mobile ?? '', roleIds: user.roleIds})
    editVisible.value = true
}

const submitEdit = async () => {
    if (!await editFormRef.value?.validate().catch(() => false)) return
    submitting.value = true
    try {
        if (editingId.value) {
            await userApi.updateUser(editingId.value, {username: editForm.username, displayName: editForm.displayName, email: editForm.email, mobile: editForm.mobile})
        } else {
            await userApi.createUser(editForm)
        }
        ElMessage.success(editingId.value ? '用户已更新' : '用户已创建')
        editVisible.value = false
        await load()
    } catch {
        // 请求层统一提示错误，并保留当前表单供用户修正。
    } finally { submitting.value = false }
}

const openRoles = async (user: UserView) => {
    roleUser.value = user
    selectedRoleIds.value = [...user.roleIds]
    try {
        await loadRoles()
        roleVisible.value = true
    } catch {
        // 请求层统一提示角色加载错误。
    }
}

const submitRoles = async () => {
    if (!roleUser.value) return
    submitting.value = true
    try {
        await userApi.assignUserRoles(roleUser.value.id, selectedRoleIds.value)
        ElMessage.success('角色分配已保存')
        roleVisible.value = false
        await load()
    } catch {
        // 请求层统一提示错误，并保留当前选择。
    } finally { submitting.value = false }
}

const openPassword = (user: UserView) => {
    passwordUser.value = user
    Object.assign(passwordForm, {password: '', confirmPassword: ''})
    passwordVisible.value = true
}

const submitPassword = async () => {
    if (!passwordUser.value || !await passwordFormRef.value?.validate().catch(() => false)) return
    submitting.value = true
    try {
        await userApi.resetUserPassword(passwordUser.value.id, passwordForm.password)
        ElMessage.success('密码已重置')
        passwordVisible.value = false
    } catch {
        // 请求层统一提示错误，并保留当前表单。
    } finally { submitting.value = false }
}

const changeStatus = async (user: UserView, status: EnabledStatus) => {
    try {
        await ElMessageBox.confirm(`确认${status === 'ENABLED' ? '启用' : '停用'}用户“${user.displayName}”吗？`, '状态确认', {type: 'warning'})
        if (status === 'ENABLED') await userApi.enableUser(user.id)
        else await userApi.disableUser(user.id)
        ElMessage.success('用户状态已更新')
        await load()
    } catch {
        // 用户取消或请求失败时保持列表现状。
    }
}

const remove = async (user: UserView) => {
    try {
        await ElMessageBox.confirm(`删除用户“${user.displayName}”后不可恢复，确认继续吗？`, '删除确认', {type: 'warning'})
        await userApi.deleteUser(user.id)
        ElMessage.success('用户已删除')
        if (records.value.length === 1 && query.pageNum > 1) query.pageNum--
        await load()
    } catch {
        // 用户取消或请求失败时保持列表现状。
    }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-heading">
      <div><h1>用户管理</h1><p>维护租户成员及其角色和登录状态</p></div><el-button
        v-if="hasPermission('system:user:create')"
        type="primary"
        @click="openCreate"
      >
        创建用户
      </el-button>
    </header>
    <el-card
      class="filter-card"
      shadow="never"
    >
      <el-form
        :inline="true"
        :model="query"
      >
        <el-form-item label="用户名">
          <el-input
            v-model="query.username"
            clearable
            placeholder="用户名"
            @keyup.enter="query.pageNum = 1; load()"
          />
        </el-form-item><el-form-item label="状态">
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部"
            style="width:130px"
          >
            <el-option
              label="启用"
              value="ENABLED"
            /><el-option
              label="停用"
              value="DISABLED"
            />
          </el-select>
        </el-form-item><el-form-item>
          <el-button
            type="primary"
            @click="query.pageNum = 1; load()"
          >
            查询
          </el-button><el-button @click="resetQuery">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card
      class="table-card"
      shadow="never"
    >
      <el-table
        v-loading="loading"
        :data="records"
      >
        <el-table-column
          prop="username"
          label="用户名"
          min-width="130"
        /><el-table-column
          prop="displayName"
          label="显示名称"
          min-width="140"
        /><el-table-column
          prop="email"
          label="邮箱"
          min-width="190"
          show-overflow-tooltip
        /><el-table-column
          prop="mobile"
          label="手机号"
          min-width="130"
        /><el-table-column
          label="状态"
          width="90"
        >
          <template #default="{row}">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column><el-table-column
          label="最近登录"
          min-width="180"
        >
          <template #default="{row}">
            {{ formatDateTime(row.lastLoginAt) }}
          </template>
        </el-table-column><el-table-column
          label="操作"
          fixed="right"
          width="310"
        >
          <template #default="{row}">
            <el-button
              v-if="hasPermission('system:user:update')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button><el-button
              v-if="hasPermission('system:user:assign-role')"
              link
              type="primary"
              @click="openRoles(row)"
            >
              分配角色
            </el-button><el-button
              v-if="hasPermission('system:user:reset-password')"
              link
              type="primary"
              @click="openPassword(row)"
            >
              重置密码
            </el-button><el-button
              v-if="row.status === 'ENABLED' && hasPermission('system:user:disable')"
              link
              type="warning"
              @click="changeStatus(row, 'DISABLED')"
            >
              停用
            </el-button><el-button
              v-if="row.status === 'DISABLED' && hasPermission('system:user:enable')"
              link
              type="success"
              @click="changeStatus(row, 'ENABLED')"
            >
              启用
            </el-button><el-button
              v-if="hasPermission('system:user:delete')"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="pagination"
        :total="total"
        :page-sizes="[10,20,50,100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="editVisible"
      :title="editingId ? '编辑用户' : '创建用户'"
      width="min(560px, 92vw)"
      destroy-on-close
      @closed="editFormRef?.resetFields()"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="88px"
      >
        <el-form-item
          label="用户名"
          prop="username"
        >
          <el-input
            v-model="editForm.username"
            maxlength="64"
          />
        </el-form-item><el-form-item
          label="显示名称"
          prop="displayName"
        >
          <el-input
            v-model="editForm.displayName"
            maxlength="128"
          />
        </el-form-item><el-form-item
          v-if="!editingId"
          label="初始密码"
          prop="password"
        >
          <el-input
            v-model="editForm.password"
            type="password"
            show-password
            maxlength="72"
          />
        </el-form-item><el-form-item label="邮箱">
          <el-input
            v-model="editForm.email"
            maxlength="254"
          />
        </el-form-item><el-form-item label="手机号">
          <el-input
            v-model="editForm.mobile"
            maxlength="32"
          />
        </el-form-item><el-form-item
          v-if="!editingId"
          label="初始角色"
        >
          <el-select
            v-model="editForm.roleIds"
            multiple
            filterable
            style="width:100%"
          >
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form><template #footer>
        <el-button @click="editVisible=false">
          取消
        </el-button><el-button
          type="primary"
          :loading="submitting"
          @click="submitEdit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="roleVisible"
      title="分配角色"
      width="min(500px, 92vw)"
    >
      <el-select
        v-model="selectedRoleIds"
        multiple
        filterable
        style="width:100%"
        placeholder="请选择角色"
      >
        <el-option
          v-for="role in roles"
          :key="role.id"
          :label="`${role.roleName}（${role.roleCode}）`"
          :value="role.id"
        />
      </el-select><template #footer>
        <el-button @click="roleVisible=false">
          取消
        </el-button><el-button
          type="primary"
          :loading="submitting"
          @click="submitRoles"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="passwordVisible"
      title="重置密码"
      width="min(460px, 92vw)"
      @closed="passwordFormRef?.resetFields()"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="90px"
      >
        <el-form-item
          label="新密码"
          prop="password"
        >
          <el-input
            v-model="passwordForm.password"
            type="password"
            show-password
            maxlength="72"
          />
        </el-form-item><el-form-item
          label="确认密码"
          prop="confirmPassword"
        >
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            maxlength="72"
          />
        </el-form-item>
      </el-form><template #footer>
        <el-button @click="passwordVisible=false">
          取消
        </el-button><el-button
          type="primary"
          :loading="submitting"
          @click="submitPassword"
        >
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
:deep(.el-form--inline .el-form-item) { margin-bottom: 0; } :deep(.el-table .cell) { white-space: nowrap; }
@media (max-width: 720px) { :deep(.el-form--inline .el-form-item) { display: flex; margin: 0 0 12px; } .pagination { justify-content: flex-start; overflow-x: auto; } }
</style>
