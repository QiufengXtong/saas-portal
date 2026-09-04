import {createPinia} from 'pinia'
import {createApp} from 'vue'
import {ElAlert, ElButton, ElCard, ElTag} from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElAlert)
app.use(ElButton)
app.use(ElCard)
app.use(ElTag)
app.mount('#app')
