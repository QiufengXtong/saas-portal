/** 文件作用：创建 Vue 应用并注册路由、状态管理及实际使用的 Element Plus 组件。 */
import {createPinia} from 'pinia'
import {createApp} from 'vue'
import {
    ElAlert,
    ElAside,
    ElButton,
    ElCard,
    ElContainer,
    ElDialog,
    ElForm,
    ElFormItem,
    ElHeader,
    ElInput,
    ElMain,
    ElMenu,
    ElMenuItem,
    ElOption,
    ElPagination,
    ElSelect,
    ElSubMenu,
    ElTable,
    ElTableColumn,
    ElTag,
    ElTree,
} from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router from './router'
import './styles/index.css'
import {initializeTheme} from './utils/theme'

initializeTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)
const elementComponents = [
    ElAlert, ElAside, ElButton, ElCard, ElContainer, ElDialog, ElForm, ElFormItem,
    ElHeader, ElInput, ElMain, ElMenu, ElMenuItem, ElOption, ElPagination, ElSelect,
    ElSubMenu, ElTable, ElTableColumn, ElTag, ElTree,
]
elementComponents.forEach(component => app.use(component))
app.mount('#app')
