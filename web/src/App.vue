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
        <div class="brand-copy">
          <h1>Control Panel</h1>
          <small>Highway Operations Grid</small>
        </div>
      </div>
      <div class="session">
        <div class="identity">
          <strong><span class="presence" />{{ auth.username || "User" }}</strong>
          <small class="welcome">{{ greeting }}</small>
          <small v-if="visibleRoleText" class="roles">{{ visibleRoleText }}</small>
        </div>
        <button class="btn" @click="auth.logout()">Logout</button>
      </div>
    </header>

    <nav class="nav" aria-label="Primary navigation">
      <RouterLink class="nav-link" to="/">Dashboard</RouterLink>
      <RouterLink v-if="isEmployeeOrAdmin" class="nav-link" to="/toll">Toll</RouterLink>
      <RouterLink v-if="isEmployeeOrAdmin" class="nav-link" to="/infrastructure">Infrastructure</RouterLink>
      <RouterLink class="nav-link" to="/payments">Payments</RouterLink>
      <RouterLink v-if="isEmployeeOrAdmin" class="nav-link" to="/reports">Reports</RouterLink>
    </nav>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  width: min(1240px, 100% - 1rem);
  margin-inline: auto;
  min-height: 100vh;
  background: var(--bg-0);
  color: oklch(0.27 0.045 244);
  position: relative;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0.95rem 1rem 0.5rem;
  padding: 0.95rem 1.05rem;
  border: 1px solid oklch(0.83 0.03 236);
  border-radius: 16px;
  background: color-mix(in oklab, var(--bg-0) 94%, var(--brand-cobalt) 6%);
  box-shadow: 0 18px 34px -34px color-mix(in oklab, oklch(0.58 0.14 252) 52%, transparent);
  animation: fade-rise 380ms cubic-bezier(0.22, 1, 0.36, 1);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand-copy {
  display: grid;
  line-height: 1.05;
}

.brand h1 {
  margin: 0;
  font-size: clamp(1.04rem, 0.5vw + 0.92rem, 1.22rem);
  font-family: var(--font-display);
  letter-spacing: -0.02em;
}

.brand-copy small {
  color: oklch(0.49 0.03 236);
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
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
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
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

.presence {
  width: 0.48rem;
  height: 0.48rem;
  border-radius: 999px;
  background: color-mix(in oklab, var(--state-ok) 85%, white 15%);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--state-ok) 20%, transparent);
  animation: soft-pulse 2.2s ease-out infinite;
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
  margin: 0 1rem;
  padding: 0.55rem;
  border-radius: 14px;
  border: 1px solid color-mix(in oklab, oklch(0.82 0.03 236) 86%, var(--brand-cobalt) 14%);
  background: color-mix(in oklab, oklch(0.985 0.008 236) 93%, var(--brand-cobalt) 7%);
  animation: fade-rise 430ms cubic-bezier(0.22, 1, 0.36, 1);
}

.nav-link {
  text-decoration: none;
  border: 1px solid color-mix(in oklab, oklch(0.82 0.03 236) 84%, var(--brand-cobalt) 16%);
  border-radius: 999px;
  padding: 0.3rem 0.74rem;
  color: oklch(0.31 0.05 244);
  background: color-mix(in oklab, oklch(0.985 0.008 236) 90%, var(--brand-teal) 10%);
  font-size: 0.87rem;
  font-weight: 560;
  transition:
    transform 180ms cubic-bezier(0.16, 1, 0.3, 1),
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    background-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.nav-link:hover {
  transform: translateY(-1px);
  border-color: color-mix(in oklab, oklch(0.82 0.03 236) 70%, var(--brand-cobalt) 30%);
  background: color-mix(in oklab, oklch(0.985 0.008 236) 80%, var(--brand-teal) 20%);
  box-shadow: 0 10px 16px -14px color-mix(in oklab, var(--brand-cobalt) 55%, transparent);
}

.nav-link[aria-current="page"] {
  border-color: color-mix(in oklab, oklch(0.82 0.03 236) 62%, var(--brand-cobalt) 38%);
  background: color-mix(in oklab, oklch(0.985 0.008 236) 74%, var(--brand-cobalt) 26%);
  box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--brand-cobalt) 30%, transparent);
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
    margin: 0 0.75rem;
    padding: 0.5rem;
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
  .nav-link,
  .presence {
    animation: none;
    transition: none;
  }
}
</style>
