<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http } from "@/api/http";
import { toUserErrorMessage } from "@/utils/userError";

const loading = ref(true);
const ok = ref(false);
const msg = ref<string | null>(null);

onMounted(async () => {
  try {
    await http.get("/api/admin/ping");
    ok.value = true;
    msg.value = "Admin endpoint reachable.";
  } catch (e: unknown) {
    ok.value = false;
    msg.value = toUserErrorMessage(e, "Unable to reach admin endpoint.");
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="about-page">
    <section class="card">
      <p class="kicker">Diagnostic</p>
      <h1>Admin Health</h1>
      <p v-if="loading" class="neutral">Checking admin endpoint...</p>
      <p v-else-if="ok" class="ok">{{ msg }}</p>
      <p v-else class="err">{{ msg }}</p>
    </section>
  </main>
</template>

<style scoped>
.about-page {
  animation: fade-rise 360ms cubic-bezier(0.22, 1, 0.36, 1);
}

.card {
  background:
    linear-gradient(144deg, color-mix(in oklab, var(--bg-0) 90%, var(--brand-cobalt) 10%), color-mix(in oklab, var(--bg-0) 91%, var(--brand-teal) 9%));
  border: 1px solid color-mix(in oklab, var(--line-0) 82%, var(--brand-cobalt) 18%);
  border-radius: 16px;
  padding: 1rem;
}

h1 {
  margin: 0 0 0.5rem;
  font-family: var(--font-display);
  letter-spacing: -0.01em;
}

.kicker {
  margin-bottom: 0.1rem;
  font-size: 0.73rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: color-mix(in oklab, var(--brand-cobalt) 64%, var(--ink-1) 36%);
  font-weight: 650;
}

.neutral {
  color: var(--ink-1);
}

.ok {
  color: color-mix(in oklab, var(--state-ok) 88%, var(--ink-0) 12%);
  background: color-mix(in oklab, var(--bg-0) 74%, var(--state-ok) 26%);
  border: 1px solid color-mix(in oklab, var(--state-ok) 52%, var(--line-0) 48%);
  border-radius: 10px;
  padding: 0.5rem 0.66rem;
  font-weight: 560;
}

.err {
  color: color-mix(in oklab, var(--state-err) 86%, var(--ink-0) 14%);
  background: color-mix(in oklab, var(--bg-0) 66%, var(--state-err) 34%);
  border: 1px solid color-mix(in oklab, var(--state-err) 55%, var(--line-0) 45%);
  border-radius: 10px;
  padding: 0.5rem 0.66rem;
}
</style>
