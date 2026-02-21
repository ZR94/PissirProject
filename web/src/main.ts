import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { initKeycloak } from "@/auth/keycloak";
import { useAuthStore } from "@/stores/auth";

async function bootstrap() {
    const app = createApp(App);

    const pinia = createPinia();
    app.use(pinia);

    await initKeycloak();

    const auth = useAuthStore();
    auth.syncFromKeycloak();

    app.use(router);
    app.mount('#app');

}

bootstrap();
