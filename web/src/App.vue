<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, RouterView } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();

const isEmployeeOrAdmin = computed(() =>
  auth.hasAnyRole(["employees", "administrators"]),
);
const isAdmin = computed(() => auth.hasRole("administrators"));
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="badge">Pissir</span>
        <h1>Control Panel</h1>
      </div>
      <div class="session">
        <div class="identity">
          <strong>{{ auth.username }}</strong>
          <small>{{ auth.roles.join(", ") || "no-roles" }}</small>
        </div>
        <button class="btn" @click="auth.logout()">Logout</button>
      </div>
    </header>

    <nav class="nav">
      <RouterLink to="/">Dashboard</RouterLink>
      <RouterLink v-if="isEmployeeOrAdmin" to="/toll">Toll</RouterLink>
      <RouterLink v-if="isEmployeeOrAdmin" to="/infrastructure">Infrastructure</RouterLink>
      <RouterLink to="/payments">Payments</RouterLink>
      <RouterLink v-if="isAdmin" to="/admin">Admin</RouterLink>
    </nav>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: linear-gradient(150deg, #f5f9ff 0%, #f0f5f0 45%, #fff9f2 100%);
  color: #123;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #d5e0ea;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
}

.brand h1 {
  margin: 0;
  font-size: 1.1rem;
}

.badge {
  background: #164863;
  color: #fff;
  font-weight: 700;
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
}

.session {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.identity {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  text-align: right;
}

.identity strong {
  font-size: 0.92rem;
}

.identity small {
  font-size: 0.73rem;
  color: #4d667a;
}

.btn {
  border: 0;
  background: #1d5b79;
  color: #fff;
  border-radius: 8px;
  padding: 0.4rem 0.7rem;
  font-weight: 600;
  cursor: pointer;
}

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
}

.nav a {
  text-decoration: none;
  border: 1px solid #c8d9e8;
  border-radius: 8px;
  padding: 0.3rem 0.6rem;
  color: #13354e;
  background: #ffffff;
}

.nav a.router-link-exact-active {
  background: #d9edf8;
  border-color: #86b7d4;
}

.content {
  padding: 1rem 1.25rem 1.5rem;
}

@media (max-width: 768px) {
  .topbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .session {
    width: 100%;
    justify-content: space-between;
  }

  .identity {
    text-align: left;
  }
}
</style>
