<!-- 文件作用：提供租户角色分页查询、维护、启停、删除和菜单树授权页面。 -->
<script setup lang="ts">
import {ElMessage, ElMessageBox, ElTree, type FormInstance, type FormRules} from 'element-plus'
import {nextTick, onMounted, reactive, ref} from 'vue'

import {getMenuTree} from '@/api/menu'
import * as roleApi from '@/api/role'
import {usePermission} from '@/composables/usePermission'
import type {EnabledStatus, MenuTreeNode, RoleQuery, RoleView} from '@/types/system'
import {formatDateTime} from '@/utils/format'

const {hasPermission} = usePermission()
const loading = ref(false)
const submitting = ref(false)
const records = ref<RoleView[]>([])
const total = ref(0)
const query = reactive<RoleQuery>({pageNum: 1, pageSize: 20})

const editVisible = ref(false)
const editingRole = ref<RoleView>()
const editFormRef = ref<FormInstance>()
const editForm = reactive({roleCode: '', roleName: ''})
const editRules: FormRules = {
    roleCode: [{required: true, message: '请输入角色编码', trigger: 'blur'}, {pattern: /^[A-Za-z0-9][A-Za-z0-9._-]*$/, message: '仅支持字母、数字、点、下划线和连字符', trigger: 'blur'}],
    roleName: [{required: true, message: '请输入角色名称', trigger: 'blur'}, {max: 128, message: '最多 128 个字符', trigger: 'blur'}],
}

const menuVisible = ref(false)
const menuRole = ref<RoleView>()
const menuTree = ref<MenuTreeNode[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()

const load = async () => {
    loading.value = true
    try {
        const page = await roleApi.getRoles(query)
        records.value = page.records
        total.value = page.total
    } catch {
        records.value = []
        total.value = 0
    } finally { loading.value = false }
}

const resetQuery = () => {
    Object.assign(query, {pageNum: 1, pageSize: query.pageSize, roleCode: undefined, roleName: undefined, status: undefined})
    void load()
}

const openCreate = () => {
    editingRole.value = undefined
    Object.assign(editForm, {roleCode: '', roleName: ''})
    editVisible.value = true
}

const openEdit = (role: RoleView) => {
    editingRole.value = role
    Object.assign(editForm, {roleCode: role.roleCode, roleName: role.roleName})
    editVisible.value = true
}

const submitEdit = async () => {
    if (!await editFormRef.value?.validate().catch(() => false)) return
    submitting.value = true
    try {
        if (editingRole.value) await roleApi.updateRole(editingRole.value.id, editForm.roleName)
        else await roleApi.createRole(editForm.roleCode, editForm.roleName)
        ElMessage.success(editingRole.value ? '角色已更新' : '角色已创建')
        editVisible.value = false
        await load()
    } catch {
        // 请求层统一提示错误，并保留当前表单供用户修正。
    } finally { submitting.value = false }
}

const openMenus = async (role: RoleView) => {
    menuRole.value = role
    try {
        const [tree, checkedIds] = await Promise.all([getMenuTree(), roleApi.getRoleMenuIds(role.id)])
        menuTree.value = tree
        menuVisible.value = true
        await nextTick()
        treeRef.value?.setCheckedKeys(checkedIds)
    } catch {
        // 请求层统一提示菜单授权加载错误。
    }
}

const submitMenus = async () => {
    if (!menuRole.value || !treeRef.value) return
    const menuIds = treeRef.value.getCheckedKeys(false).map(String)
    submitting.value = true
    try {
        await roleApi.assignRoleMenus(menuRole.value.id, menuIds)
        ElMessage.success('菜单授权已保存')
        menuVisible.value = false
    } catch {
        // 请求层统一提示错误，并保留当前选择。
    } finally { submitting.value = false }
}

const changeStatus = async (role: RoleView, status: EnabledStatus) => {
    try {
        await ElMessageBox.confirm(`确认${status === 'ENABLED' ? '启用' : '停用'}角色“${role.roleName}”吗？`, '状态确认', {type: 'warning'})
        if (status === 'ENABLED') await roleApi.enableRole(role.id)
        else await roleApi.disableRole(role.id)
        ElMessage.success('角色状态已更新')
        await load()
    } catch {
        // 用户取消或请求失败时保持列表现状。
    }
}

const remove = async (role: RoleView) => {
    try {
        await ElMessageBox.confirm(`删除角色“${role.roleName}”后不可恢复，确认继续吗？`, '删除确认', {type: 'warning'})
        await roleApi.deleteRole(role.id)
        ElMessage.success('角色已删除')
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
      <div><h1>角色管理</h1><p>维护角色状态及菜单、按钮权限</p></div><el-button
        v-if="hasPermission('system:role:create')"
        type="primary"
        @click="openCreate"
      >
        创建角色
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
        <el-form-item label="角色编码">
          <el-input
            v-model="query.roleCode"
            clearable
          />
        </el-form-item><el-form-item label="角色名称">
          <el-input
            v-model="query.roleName"
            clearable
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
            @click="query.pageNum=1; load()"
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
          prop="roleCode"
          label="角色编码"
          min-width="160"
        /><el-table-column
          prop="roleName"
          label="角色名称"
          min-width="160"
        /><el-table-column
          label="类型"
          width="100"
        >
          <template #default="{row}">
            <el-tag :type="row.builtIn ? 'warning' : 'info'">
              {{ row.builtIn ? '内置' : '普通' }}
            </el-tag>
          </template>
        </el-table-column><el-table-column
          label="状态"
          width="90"
        >
          <template #default="{row}">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column><el-table-column
          label="更新时间"
          min-width="180"
        >
          <template #default="{row}">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column><el-table-column
          label="操作"
          fixed="right"
          width="260"
        >
          <template #default="{row}">
            <el-button
              v-if="!row.builtIn && hasPermission('system:role:update')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button><el-button
              v-if="!row.builtIn && hasPermission('system:role:assign-menu')"
              link
              type="primary"
              @click="openMenus(row)"
            >
              菜单授权
            </el-button><el-button
              v-if="!row.builtIn && row.status === 'ENABLED' && hasPermission('system:role:disable')"
              link
              type="warning"
              @click="changeStatus(row, 'DISABLED')"
            >
              停用
            </el-button><el-button
              v-if="!row.builtIn && row.status === 'DISABLED' && hasPermission('system:role:enable')"
              link
              type="success"
              @click="changeStatus(row, 'ENABLED')"
            >
              启用
            </el-button><el-button
              v-if="!row.builtIn && hasPermission('system:role:delete')"
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
      :title="editingRole ? '编辑角色' : '创建角色'"
      width="min(500px, 92vw)"
      @closed="editFormRef?.resetFields()"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <el-form-item
          label="角色编码"
          prop="roleCode"
        >
          <el-input
            v-model="editForm.roleCode"
            :disabled="Boolean(editingRole)"
            maxlength="64"
          />
        </el-form-item><el-form-item
          label="角色名称"
          prop="roleName"
        >
          <el-input
            v-model="editForm.roleName"
            maxlength="128"
          />
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
      v-model="menuVisible"
      title="菜单授权"
      width="min(600px, 94vw)"
    >
      <el-alert
        title="授权采用整体替换，保存后相关用户需要重新建立会话。"
        type="info"
        :closable="false"
        show-icon
      /><el-tree
        ref="treeRef"
        class="menu-tree"
        :data="menuTree"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        :props="{label:'name', children:'children'}"
      >
        <template #default="{data}">
          <span>{{ data.name }}</span><el-tag
            v-if="data.type === 'BUTTON'"
            size="small"
            type="info"
            class="node-tag"
          >
            权限
          </el-tag>
        </template>
      </el-tree><template #footer>
        <el-button @click="menuVisible=false">
          取消
        </el-button><el-button
          type="primary"
          :loading="submitting"
          @click="submitMenus"
        >
          保存授权
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
:deep(.el-form--inline .el-form-item) { margin-bottom: 0; }.menu-tree { margin-top: 16px; max-height: 52vh; overflow: auto; }.node-tag { margin-left: 8px; }
@media (max-width: 720px) { :deep(.el-form--inline .el-form-item) { display:flex; margin:0 0 12px; }.pagination { justify-content:flex-start; overflow-x:auto; } }
</style>
