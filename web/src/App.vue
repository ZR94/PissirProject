<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, RouterView } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { roleLabel, visibleRolesForUi } from "@/utils/roles";

const auth = useAuthStore();

const isEmployeeOrAdmin = computed(() =>
  auth.hasAnyRole(["employees", "administrators"]),
);
const visibleRoleText = computed(() => {
  const roles = visibleRolesForUi(auth.roles).map(roleLabel);
  return roles.join(", ");
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
});
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
          <strong>{{ auth.username || "User" }}</strong>
          <small class="welcome">{{ greeting }}</small>
          <small v-if="visibleRoleText" class="roles">{{ visibleRoleText }}</small>
        </div>
        <button class="btn" @click="auth.logout()">Logout</button>
      </div>
    </header>

    <nav class="nav">
      <RouterLink to="/">Dashboard</RouterLink>
      <RouterLink v-if="isEmployeeOrAdmin" to="/toll">Toll</RouterLink>
      <RouterLink v-if="isEmployeeOrAdmin" to="/infrastructure">Infrastructure</RouterLink>
      <RouterLink to="/payments">Payments</RouterLink>
    </nav>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: transparent;
  color: oklch(0.27 0.045 244);
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0.95rem 1rem 0.5rem;
  padding: 0.95rem 1.05rem;
  border: 1px solid oklch(0.83 0.03 236);
  border-radius: 16px;
  background:
    linear-gradient(135deg, oklch(0.985 0.01 236), oklch(0.965 0.016 232));
  box-shadow: 0 18px 34px -34px color-mix(in oklab, oklch(0.58 0.14 252) 52%, transparent);
  animation: fade-rise 380ms cubic-bezier(0.22, 1, 0.36, 1);
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
}

.brand h1 {
  margin: 0;
  font-size: clamp(1.04rem, 0.5vw + 0.92rem, 1.22rem);
  font-family: var(--font-display);
  letter-spacing: -0.02em;
}

.badge {
  background:
    linear-gradient(140deg, color-mix(in oklab, var(--brand-cobalt) 84%, black 16%), color-mix(in oklab, var(--brand-teal) 74%, black 26%));
  color: oklch(0.98 0.004 240);
  font-weight: 700;
  font-size: 0.75rem;
  letter-spacing: 0.065em;
  padding: 0.32rem 0.58rem;
  border-radius: 999px;
  box-shadow: 0 10px 20px -16px color-mix(in oklab, var(--brand-cobalt) 72%, transparent);
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
  line-height: 1.2;
}

.welcome {
  font-size: 0.72rem;
  color: oklch(0.49 0.03 236);
  letter-spacing: 0.02em;
}

.roles {
  font-size: 0.72rem;
  color: color-mix(in oklab, oklch(0.49 0.03 236) 84%, var(--brand-teal) 16%);
}

.btn {
  border: 0;
  background:
    linear-gradient(135deg, color-mix(in oklab, var(--brand-cobalt) 86%, black 14%), color-mix(in oklab, var(--brand-teal) 74%, black 26%));
  color: oklch(0.985 0.003 240);
  border-radius: 10px;
  padding: 0.43rem 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 180ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px -16px color-mix(in oklab, var(--brand-cobalt) 72%, transparent);
}

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.52rem;
  padding: 0.15rem 1rem 0.35rem;
  animation: fade-rise 430ms cubic-bezier(0.22, 1, 0.36, 1);
}

.nav a {
  text-decoration: none;
  border: 1px solid color-mix(in oklab, oklch(0.82 0.03 236) 84%, var(--brand-cobalt) 16%);
  border-radius: 999px;
  padding: 0.28rem 0.7rem;
  color: oklch(0.31 0.05 244);
  background: color-mix(in oklab, oklch(0.985 0.008 236) 90%, var(--brand-teal) 10%);
  font-size: 0.87rem;
  transition:
    transform 180ms cubic-bezier(0.16, 1, 0.3, 1),
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    background-color 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.nav a:hover {
  transform: translateY(-1px);
  border-color: color-mix(in oklab, oklch(0.82 0.03 236) 70%, var(--brand-cobalt) 30%);
  background: color-mix(in oklab, oklch(0.985 0.008 236) 80%, var(--brand-teal) 20%);
}

.content {
  padding: 0.85rem 1rem 1.5rem;
  animation: fade-rise 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

@media (max-width: 768px) {
  .topbar {
    margin: 0.7rem 0.75rem 0.45rem;
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

  .nav {
    padding: 0.15rem 0.75rem 0.35rem;
  }

  .content {
    padding: 0.8rem 0.75rem 1.25rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .topbar,
  .nav,
  .content,
  .btn,
  .nav a {
    animation: none;
    transition: none;
  }
}
</style>
