import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import DiaryView from './views/DiaryView.vue'
import AnniversaryView from './views/AnniversaryView.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ left: 0, top: 0 }),
  routes: [
    { path: '/', component: DiaryView },
    { path: '/anniversaries', component: AnniversaryView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
createApp(App).use(router).mount('#app')
