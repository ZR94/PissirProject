<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";
import { toUserErrorMessage } from "@/utils/userError";
import { roleLabel, visibleRolesForUi } from "@/utils/roles";

type MeResponse = {
  ok: boolean;
  sub: string;
  username: string;
  roles: string[];
};

const auth = useAuthStore();
const me = ref<MeResponse | null>(null);
const loading = ref(true);
const warning = ref<string | null>(null);
const onboardingDismissed = ref(false);

const ONBOARDING_KEY = "pissir.dashboard.onboarding.dismissed.v1";

const displayUsername = computed(
  () => me.value?.username || auth.username || "-",
);
const displayRoles = computed(
  () => (me.value?.roles.length ? me.value.roles : auth.roles),
);
const visibleRoles = computed(() => visibleRolesForUi(displayRoles.value));
const showOnboarding = computed(() => !onboardingDismissed.value);
const insightLine = computed(() => {
  if (visibleRoles.value.includes("employees")) return "You can monitor toll calculations and operations from this dashboard.";
  if (visibleRoles.value.includes("customers")) return "You can quickly jump to payments and account-related operations.";
  return "Your dashboard is ready. Use quick actions to continue.";
});

const quickActions = computed(() => {
  const roles = displayRoles.value;
  const actions: {
    title: string;
    subtitle: string;
    to: string;
    tone: "cobalt" | "teal" | "moss";
  }[] = [];
  if (roles.includes("administrators")) {
    actions.push({
      title: "Configure Infrastructure",
      subtitle: "Create tollbooths and fares.",
      to: "/infrastructure",
      tone: "cobalt",
    });
  }
  if (roles.includes("employees")) {
    actions.push({
      title: "Run Toll Calculation",
      subtitle: "Check price for entry and exit booths.",
      to: "/toll",
      tone: "teal",
    });
  }
  if (roles.includes("customers") || roles.includes("employees") || roles.includes("administrators")) {
    actions.push({
      title: "Open Payments",
      subtitle: "Inspect debts and payment status.",
      to: "/payments",
      tone: "moss",
    });
  }
  return actions;
});

const displayRoleChips = computed(() =>
  visibleRoles.value.map((role) => {
    if (role === "employees") return { key: role, label: roleLabel(role), tone: "teal" as const };
    if (role === "customers") return { key: role, label: roleLabel(role), tone: "moss" as const };
    return { key: role, label: role, tone: "neutral" as const };
  }),
);

function dismissOnboarding() {
  onboardingDismissed.value = true;
  try {
    localStorage.setItem(ONBOARDING_KEY, "1");
  } catch {
    // ignore storage failures and only dismiss for current session
  }
}

onMounted(async () => {
  try {
    onboardingDismissed.value = localStorage.getItem(ONBOARDING_KEY) === "1";
  } catch {
    onboardingDismissed.value = false;
  }

  try {
    const res = await http.get<MeResponse>("/api/me");
    me.value = res.data;
  } catch (e: unknown) {
    warning.value = `${toUserErrorMessage(e, "Unable to refresh profile.")} Showing token data.`;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="dashboard-view">
    <section v-if="showOnboarding" class="onboarding">
      <div class="onboarding-copy">
        <p class="kicker">Getting Started</p>
        <h2>Welcome, {{ displayUsername }}</h2>
        <p class="hint">{{ insightLine }}</p>
      </div>
      <div class="onboarding-actions">
        <RouterLink
          v-for="action in quickActions"
          :key="action.to"
          :to="action.to"
          class="quick-action"
          :class="`quick-action--${action.tone}`"
        >
          <strong>{{ action.title }}</strong>
          <span>{{ action.subtitle }}</span>
        </RouterLink>
        <p v-if="quickActions.length === 0" class="hint">
          No enabled actions for your role. Ask an administrator to assign permissions.
        </p>
      </div>
      <button class="dismiss" @click="dismissOnboarding()">Skip for now</button>
    </section>

    <section class="dashboard">
      <article class="card">
        <h2>Profile</h2>
        <p class="label">Username</p>
        <p class="value">{{ displayUsername }}</p>
        <p v-if="loading" class="hint">Refreshing profile...</p>
      </article>

      <article class="card">
        <h2>Roles</h2>
        <p v-if="displayRoleChips.length === 0" class="hint">No roles assigned</p>
        <div v-else class="chips">
          <span
            v-for="role in displayRoleChips"
            :key="role.key"
            class="chip"
            :class="`chip--${role.tone}`"
          >
            {{ role.label }}
          </span>
        </div>
      </article>
    </section>

    <p v-if="warning" class="warn">{{ warning }}</p>
  </section>
</template>

<style scoped>
.dashboard-view {
  --ink-strong: oklch(0.28 0.045 246);
  --ink-muted: oklch(0.48 0.028 238);
  --paper: oklch(0.987 0.004 236);
  --paper-strong: oklch(0.965 0.014 234);
  --line: oklch(0.83 0.03 236);
  --cobalt: oklch(0.6 0.14 252);
  --teal: oklch(0.64 0.12 208);
  --moss: oklch(0.67 0.12 152);
  position: relative;
  display: grid;
  gap: clamp(0.9rem, 0.7vw + 0.7rem, 1.35rem);
  color: var(--ink-strong);
  isolation: isolate;
}

.dashboard-view::before {
  content: "";
  position: absolute;
  inset: -0.55rem -0.25rem auto;
  height: min(28vw, 180px);
  background:
    radial-gradient(45% 85% at 3% 0%, color-mix(in oklab, var(--cobalt) 23%, transparent), transparent 72%),
    radial-gradient(36% 80% at 92% 8%, color-mix(in oklab, var(--moss) 25%, transparent), transparent 74%),
    radial-gradient(52% 90% at 55% 0%, color-mix(in oklab, var(--teal) 16%, transparent), transparent 74%);
  pointer-events: none;
  z-index: -1;
}

.onboarding {
  display: grid;
  gap: clamp(0.8rem, 0.5vw + 0.65rem, 1.05rem);
  grid-template-columns: 1.2fr 1fr auto;
  background:
    linear-gradient(132deg, color-mix(in oklab, var(--paper) 85%, var(--teal) 15%), color-mix(in oklab, var(--paper) 88%, var(--cobalt) 12%));
  border: 1px solid color-mix(in oklab, var(--line) 82%, var(--cobalt) 18%);
  border-radius: 18px;
  padding: clamp(0.9rem, 0.6vw + 0.75rem, 1.2rem);
  align-items: start;
  box-shadow: 0 12px 30px -26px color-mix(in oklab, var(--cobalt) 35%, transparent);
  animation: fade-rise 380ms cubic-bezier(0.22, 1, 0.36, 1);
}

.onboarding-copy h2 {
  margin-bottom: 0.35rem;
  font-size: clamp(1.1rem, 1.4vw + 0.75rem, 1.55rem);
  line-height: 1.12;
}

.kicker {
  margin-bottom: 0.25rem;
  color: color-mix(in oklab, var(--ink-muted) 82%, var(--cobalt) 18%);
  font-size: 0.73rem;
  letter-spacing: 0.085em;
  text-transform: uppercase;
  font-weight: 650;
}

.onboarding-actions {
  display: grid;
  gap: 0.55rem;
}

.quick-action {
  display: grid;
  gap: 0.22rem;
  text-decoration: none;
  color: var(--ink-strong);
  border: 1px solid color-mix(in oklab, var(--line) 78%, var(--teal) 22%);
  background: color-mix(in oklab, var(--paper) 88%, var(--teal) 12%);
  border-radius: 12px;
  padding: 0.62rem 0.75rem;
  transition:
    transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    background-color 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.quick-action:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 18px -15px color-mix(in oklab, var(--teal) 45%, transparent);
}

.quick-action:focus-visible {
  outline: 2px solid color-mix(in oklab, var(--teal) 72%, white 28%);
  outline-offset: 2px;
}

.quick-action--cobalt {
  border-color: color-mix(in oklab, var(--line) 70%, var(--cobalt) 30%);
  background: color-mix(in oklab, var(--paper) 84%, var(--cobalt) 16%);
}

.quick-action--teal {
  border-color: color-mix(in oklab, var(--line) 68%, var(--teal) 32%);
  background: color-mix(in oklab, var(--paper) 82%, var(--teal) 18%);
}

.quick-action--moss {
  border-color: color-mix(in oklab, var(--line) 66%, var(--moss) 34%);
  background: color-mix(in oklab, var(--paper) 82%, var(--moss) 18%);
}

.quick-action strong {
  font-size: clamp(0.87rem, 0.4vw + 0.78rem, 0.97rem);
  line-height: 1.24;
}

.quick-action span {
  font-size: 0.78rem;
  color: color-mix(in oklab, var(--ink-muted) 88%, var(--teal) 12%);
  line-height: 1.33;
}

.dismiss {
  border: 1px solid color-mix(in oklab, var(--line) 74%, var(--cobalt) 26%);
  background: color-mix(in oklab, var(--paper-strong) 70%, var(--cobalt) 30%);
  color: var(--ink-strong);
  border-radius: 11px;
  padding: 0.48rem 0.72rem;
  font-weight: 620;
  cursor: pointer;
  transition:
    transform 200ms cubic-bezier(0.16, 1, 0.3, 1),
    background-color 200ms cubic-bezier(0.16, 1, 0.3, 1);
}

.dismiss:hover {
  transform: translateY(-1px);
  background: color-mix(in oklab, var(--paper-strong) 63%, var(--cobalt) 37%);
}

.dashboard {
  display: grid;
  gap: clamp(0.75rem, 0.6vw + 0.6rem, 1rem);
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

.card {
  background: color-mix(in oklab, var(--paper) 94%, var(--cobalt) 6%);
  border: 1px solid color-mix(in oklab, var(--line) 84%, var(--cobalt) 16%);
  border-radius: 16px;
  padding: clamp(0.9rem, 0.7vw + 0.75rem, 1.15rem);
  box-shadow: 0 10px 22px -24px color-mix(in oklab, var(--cobalt) 34%, transparent);
  transition: transform 180ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
  animation: fade-rise 460ms cubic-bezier(0.22, 1, 0.36, 1);
}

.card:nth-child(2n) {
  background: color-mix(in oklab, var(--paper) 92%, var(--teal) 8%);
  border-color: color-mix(in oklab, var(--line) 84%, var(--teal) 16%);
}

.card:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 24px -22px color-mix(in oklab, var(--teal) 38%, transparent);
}

h2 {
  margin-top: 0;
  margin-bottom: 0.58rem;
  font-size: clamp(1rem, 0.45vw + 0.9rem, 1.2rem);
  letter-spacing: -0.01em;
}

.label {
  font-size: 0.78rem;
  color: var(--ink-muted);
  letter-spacing: 0.02em;
}

.value {
  font-size: clamp(1.05rem, 0.65vw + 0.86rem, 1.34rem);
  font-weight: 700;
  line-height: 1.15;
}

.hint {
  margin-top: 0.5rem;
  color: color-mix(in oklab, var(--ink-muted) 90%, var(--teal) 10%);
  font-size: 0.84rem;
  line-height: 1.4;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.42rem;
}

.chip {
  border: 1px solid color-mix(in oklab, var(--line) 84%, var(--cobalt) 16%);
  background: color-mix(in oklab, var(--paper) 86%, var(--cobalt) 14%);
  color: var(--ink-strong);
  border-radius: 999px;
  padding: 0.24rem 0.62rem;
  font-size: 0.82rem;
  font-weight: 650;
  letter-spacing: 0.01em;
}

.chip--cobalt {
  border-color: color-mix(in oklab, var(--line) 68%, var(--cobalt) 32%);
  background: color-mix(in oklab, var(--paper) 76%, var(--cobalt) 24%);
}

.chip--teal {
  border-color: color-mix(in oklab, var(--line) 68%, var(--teal) 32%);
  background: color-mix(in oklab, var(--paper) 76%, var(--teal) 24%);
}

.chip--moss {
  border-color: color-mix(in oklab, var(--line) 67%, var(--moss) 33%);
  background: color-mix(in oklab, var(--paper) 76%, var(--moss) 24%);
}

.chip--neutral {
  border-color: var(--line);
  background: var(--paper-strong);
}

.warn {
  color: color-mix(in oklab, oklch(0.43 0.1 55) 90%, var(--ink-strong) 10%);
  background: color-mix(in oklab, var(--paper) 68%, oklch(0.85 0.11 84) 32%);
  border: 1px solid color-mix(in oklab, oklch(0.81 0.1 84) 70%, var(--line) 30%);
  border-radius: 12px;
  padding: 0.58rem 0.75rem;
  font-weight: 560;
}

@media (max-width: 860px) {
  .onboarding {
    grid-template-columns: 1fr;
  }

  .dismiss {
    justify-self: start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .onboarding,
  .card,
  .quick-action,
  .dismiss {
    animation: none;
    transition: none;
  }
}
</style>
