<!-- 文件作用：提供全局目录、页面菜单和按钮权限的树形维护页面。 -->
<script setup lang="ts">
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {computed, onMounted, reactive, ref} from 'vue'

import * as menuApi from '@/api/menu'
import {usePermission} from '@/composables/usePermission'
import type {EnabledStatus, MenuCommand, MenuTreeNode, MenuType, MenuView} from '@/types/system'

interface ParentOption {
    id: string
    label: string
    type: MenuType
    status: EnabledStatus
}

const {hasPermission} = usePermission()
const loading = ref(false)
const submitting = ref(false)
const tree = ref<MenuTreeNode[]>([])
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editingMenu = ref<MenuView>()
const form = reactive<MenuCommand>(emptyCommand())
const typeLabels: Record<MenuType, string> = {DIRECTORY: '目录', MENU: '页面菜单', BUTTON: '按钮权限'}
const typeTag: Record<MenuType, 'primary' | 'success' | 'info'> = {DIRECTORY: 'primary', MENU: 'success', BUTTON: 'info'}
const rules: FormRules = {
    name: [{required: true, message: '请输入名称', trigger: 'blur'}, {max: 128, message: '最多 128 个字符', trigger: 'blur'}],
    type: [{required: true, message: '请选择节点类型', trigger: 'change'}],
    routePath: [{max: 255, message: '最多 255 个字符', trigger: 'blur'}],
    component: [{max: 255, message: '最多 255 个字符', trigger: 'blur'}],
    icon: [{max: 64, message: '最多 64 个字符', trigger: 'blur'}],
    permissionCode: [
        {max: 128, message: '最多 128 个字符', trigger: 'blur'},
        {pattern: /^[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*){2,}$/, message: '格式示例：system:user:list', trigger: 'blur'},
    ],
}

const isBuiltInEdit = computed(() => Boolean(editingMenu.value?.builtIn))
const dialogTitle = computed(() => editingMenu.value ? '编辑菜单资源' : '创建菜单资源')
const parentOptions = computed(() => flattenParents(tree.value))
const selectableParents = computed(() => parentOptions.value.filter(option => {
    if (option.id === editingMenu.value?.id) return false
    return form.type === 'BUTTON' ? option.type === 'MENU' : option.type === 'DIRECTORY'
}))

/** 生成新建表单默认值，避免复用上一次编辑状态。 */
function emptyCommand(): MenuCommand {
    return {parentId: null, name: '', type: 'DIRECTORY', routePath: null, component: null, icon: null, permissionCode: null, sortOrder: 0, visible: true}
}

/** 将菜单树展开为带缩进的父节点选项。 */
function flattenParents(nodes: MenuTreeNode[], depth = 0): ParentOption[] {
    return nodes.flatMap(node => [
        {id: node.id, label: `${'　'.repeat(depth)}${node.name}`, type: node.type, status: node.status},
        ...flattenParents(node.children, depth + 1),
    ])
}

/** 加载包含停用节点的完整管理树。 */
async function load() {
    loading.value = true
    try {
        tree.value = await menuApi.getManagementMenuTree()
    } catch {
        tree.value = []
    } finally {
        loading.value = false
    }
}

/** 打开根目录创建表单。 */
function openCreateRoot() {
    editingMenu.value = undefined
    Object.assign(form, emptyCommand())
    dialogVisible.value = true
}

/** 根据父节点类型打开合法的子节点创建表单。 */
function openCreateChild(parent: MenuTreeNode) {
    editingMenu.value = undefined
    Object.assign(form, emptyCommand(), {
        parentId: parent.id,
        type: parent.type === 'DIRECTORY' ? 'MENU' : 'BUTTON',
        visible: parent.type === 'DIRECTORY',
    })
    dialogVisible.value = true
}

/** 查询最新详情并打开编辑表单。 */
async function openEdit(row: MenuTreeNode) {
    try {
        const detail = await menuApi.getMenu(row.id)
        editingMenu.value = detail
        Object.assign(form, {
            parentId: detail.parentId,
            name: detail.name,
            type: detail.type,
            routePath: detail.routePath,
            component: detail.component,
            icon: detail.icon,
            permissionCode: detail.permissionCode,
            sortOrder: detail.sortOrder,
            visible: detail.visible,
        })
        dialogVisible.value = true
    } catch {
        // 请求层统一展示详情加载错误。
    }
}

/** 切换节点类型时清除该类型不接受的字段。 */
function changeType(type: MenuType) {
    form.parentId = null
    if (type === 'DIRECTORY') {
        form.component = null
        form.permissionCode = null
        form.visible = true
    } else if (type === 'MENU') {
        form.permissionCode = null
        form.visible = true
    } else {
        form.routePath = null
        form.component = null
        form.icon = null
        form.visible = false
    }
}

/** 执行与菜单类型相关的必填校验。 */
function validateTypeFields(): boolean {
    if (form.type !== 'DIRECTORY' && !form.parentId) return fail('请选择上级节点')
    if (form.type === 'MENU' && (!form.routePath?.trim() || !form.component?.trim())) return fail('页面菜单必须填写路由地址和组件路径')
    if (form.type === 'BUTTON' && !form.permissionCode?.trim()) return fail('按钮权限必须填写权限码')
    return true
}

/** 统一显示表单业务校验失败信息。 */
function fail(message: string): false {
    ElMessage.warning(message)
    return false
}

/** 规范化可选文本后提交创建或更新。 */
async function submit() {
    if (!await formRef.value?.validate().catch(() => false) || !validateTypeFields()) return
    const command: MenuCommand = {
        ...form,
        name: form.name.trim(),
        routePath: form.routePath?.trim() || null,
        component: form.component?.trim() || null,
        icon: form.icon?.trim() || null,
        permissionCode: form.permissionCode?.trim() || null,
    }
    submitting.value = true
    try {
        if (editingMenu.value) await menuApi.updateMenu(editingMenu.value.id, command)
        else await menuApi.createMenu(command)
        ElMessage.success(editingMenu.value ? '菜单资源已更新' : '菜单资源已创建')
        dialogVisible.value = false
        await load()
    } catch {
        // 请求层统一提示错误，并保留当前表单供用户修正。
    } finally {
        submitting.value = false
    }
}

/** 经确认后启用或停用菜单资源。 */
async function changeStatus(row: MenuTreeNode, status: EnabledStatus) {
    try {
        const action = status === 'ENABLED' ? '启用' : '停用'
        await ElMessageBox.confirm(`确认${action}“${row.name}”吗？权限变更会使受影响用户重新登录。`, '状态确认', {type: 'warning'})
        if (status === 'ENABLED') await menuApi.enableMenu(row.id)
        else await menuApi.disableMenu(row.id)
        ElMessage.success(`菜单资源已${action}`)
        await load()
    } catch {
        // 用户取消或请求失败时保持树形列表现状。
    }
}

/** 经确认后删除无子节点、无角色关联的自定义资源。 */
async function remove(row: MenuTreeNode) {
    try {
        await ElMessageBox.confirm(`删除“${row.name}”后不可恢复，确认继续吗？`, '删除确认', {type: 'warning'})
        await menuApi.deleteMenu(row.id)
        ElMessage.success('菜单资源已删除')
        await load()
    } catch {
        // 用户取消或请求失败时保持树形列表现状。
    }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-heading">
      <div><h1>菜单管理</h1><p>维护全局目录、页面入口及按钮权限；内置节点仅允许调整展示信息</p></div>
      <el-button
        v-if="hasPermission('system:menu:create')"
        type="primary"
        @click="openCreateRoot"
      >
        创建根目录
      </el-button>
    </header>
    <el-card
      class="table-card"
      shadow="never"
    >
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        default-expand-all
      >
        <el-table-column
          prop="name"
          label="名称"
          min-width="220"
        />
        <el-table-column
          label="类型"
          width="110"
        >
          <template #default="{row}">
            <el-tag :type="typeTag[row.type as MenuType]">
              {{ typeLabels[row.type as MenuType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="路由 / 权限码"
          min-width="220"
        >
          <template #default="{row}">
            <code>{{ row.type === 'BUTTON' ? row.permissionCode : (row.routePath || '-') }}</code>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          width="80"
        />
        <el-table-column
          label="属性"
          width="130"
        >
          <template #default="{row}">
            <el-tag
              v-if="row.builtIn"
              type="warning"
              size="small"
            >
              内置
            </el-tag>
            <span
              v-if="row.type !== 'BUTTON'"
              class="visibility"
            >{{ row.visible ? '显示' : '隐藏' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{row}">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          fixed="right"
          width="285"
        >
          <template #default="{row}">
            <el-button
              v-if="row.type !== 'BUTTON' && hasPermission('system:menu:create')"
              link
              type="primary"
              @click="openCreateChild(row)"
            >
              新增下级
            </el-button>
            <el-button
              v-if="hasPermission('system:menu:update')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="!row.builtIn && row.status === 'ENABLED' && hasPermission('system:menu:disable')"
              link
              type="warning"
              @click="changeStatus(row, 'DISABLED')"
            >
              停用
            </el-button>
            <el-button
              v-if="row.status === 'DISABLED' && hasPermission('system:menu:enable')"
              link
              type="success"
              @click="changeStatus(row, 'ENABLED')"
            >
              启用
            </el-button>
            <el-button
              v-if="!row.builtIn && hasPermission('system:menu:delete')"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="min(620px, 94vw)"
      @closed="formRef?.resetFields()"
    >
      <el-alert
        v-if="isBuiltInEdit"
        title="内置节点仅可修改名称、图标、排序和显示状态，核心标识受保护。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="menu-form"
      >
        <el-form-item
          label="节点类型"
          prop="type"
        >
          <el-select
            v-model="form.type"
            :disabled="Boolean(editingMenu)"
            @change="changeType"
          >
            <el-option
              label="目录"
              value="DIRECTORY"
            />
            <el-option
              label="页面菜单"
              value="MENU"
            />
            <el-option
              label="按钮权限"
              value="BUTTON"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上级节点">
          <el-select
            v-model="form.parentId"
            clearable
            filterable
            :disabled="isBuiltInEdit"
            :placeholder="form.type === 'DIRECTORY' ? '不选择时作为根目录' : '请选择上级节点'"
          >
            <el-option
              v-for="option in selectableParents"
              :key="option.id"
              :label="option.label"
              :value="option.id"
              :disabled="option.status === 'DISABLED'"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="名称"
          prop="name"
        >
          <el-input
            v-model="form.name"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          v-if="form.type === 'MENU'"
          label="路由地址"
          prop="routePath"
        >
          <el-input
            v-model="form.routePath"
            :disabled="isBuiltInEdit"
            placeholder="例如：/system/users"
            maxlength="255"
          />
        </el-form-item>
        <el-form-item
          v-if="form.type === 'MENU'"
          label="组件路径"
          prop="component"
        >
          <el-input
            v-model="form.component"
            :disabled="isBuiltInEdit"
            placeholder="例如：system/user/index"
            maxlength="255"
          />
        </el-form-item>
        <el-form-item
          v-if="form.type !== 'BUTTON'"
          label="图标"
          prop="icon"
        >
          <el-input
            v-model="form.icon"
            placeholder="可选图标标识"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item
          v-if="form.type === 'BUTTON'"
          label="权限码"
          prop="permissionCode"
        >
          <el-input
            v-model="form.permissionCode"
            :disabled="isBuiltInEdit"
            placeholder="例如：system:user:list"
            maxlength="128"
          />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="999999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item
          v-if="form.type !== 'BUTTON'"
          label="菜单显示"
        >
          <el-switch
            v-model="form.visible"
            inline-prompt
            active-text="显示"
            inactive-text="隐藏"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
code { color: var(--app-text-secondary); font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }.visibility { margin-left: 8px; color: var(--app-text-secondary); font-size: 13px; }.menu-form { margin-top: 18px; }:deep(.menu-form .el-select) { width: 100%; }:deep(.menu-form .el-input-number) { width: 180px; }
@media (max-width: 720px) { :deep(.el-table .cell) { white-space: nowrap; } }
</style>
