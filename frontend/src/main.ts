import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import './styles/global.css'
import './styles/premium.css'
import './styles/ui-enhancements.css'
import './styles/form-row.css'
import './styles/explorescape.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
