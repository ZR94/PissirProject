<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";
import { toUserErrorMessage } from "@/utils/userError";

type Fare = {
  id: number;
  entryTollboothId: string;
  exitTollboothId: string;
  amountCents: number;
  currency: string;
};

const auth = useAuthStore();
const isAdministrator = auth.hasRole("administrators");

const tollbooths = ref<string[]>([]);
const fares = ref<Fare[]>([]);
const error = ref<string | null>(null);
const msg = ref<string | null>(null);

const newTollboothId = ref("");
const newFare = ref({
  entryTollboothId: "",
  exitTollboothId: "",
  amountCents: 0,
});

async function loadData() {
  error.value = null;
  msg.value = null;
  try {
    const [tbRes, fareRes] = await Promise.all([
      http.get<string[]>("/api/infrastructure/tollbooths"),
      http.get<Fare[]>("/api/infrastructure/fares"),
    ]);
    tollbooths.value = tbRes.data;
    fares.value = fareRes.data;
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to load infrastructure data.");
  }
}

async function createTollbooth() {
  msg.value = null;
  error.value = null;
  try {
    await http.post("/api/infrastructure/tollbooths", { id: newTollboothId.value });
    msg.value = `Tollbooth ${newTollboothId.value} created`;
    newTollboothId.value = "";
    await loadData();
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to create tollbooth.");
  }
}

async function createFare() {
  msg.value = null;
  error.value = null;
  try {
    await http.post("/api/infrastructure/fares", newFare.value);
    msg.value = `Fare ${newFare.value.entryTollboothId} -> ${newFare.value.exitTollboothId} created`;
    newFare.value = { entryTollboothId: "", exitTollboothId: "", amountCents: 0 };
    await loadData();
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to create fare.");
  }
}

onMounted(loadData);
</script>

<template>
  <section class="infrastructure-page">
    <header class="section-head">
      <p class="kicker">Operations</p>
      <h2>Infrastructure Registry</h2>
      <p class="lead">Manage tollbooths and fares for the network configuration.</p>
    </header>

    <section class="grid">
      <article class="card">
        <h3>Tollbooths</h3>
        <ul v-if="tollbooths.length > 0">
          <li v-for="tb in tollbooths" :key="tb">{{ tb }}</li>
        </ul>
        <p v-else class="empty">No tollbooths registered yet.</p>
      </article>

      <article class="card">
        <h3>Fares</h3>
        <ul v-if="fares.length > 0">
          <li v-for="fare in fares" :key="fare.id">
            {{ fare.entryTollboothId }} -> {{ fare.exitTollboothId }} = {{ fare.amountCents }} {{ fare.currency }}
          </li>
        </ul>
        <p v-else class="empty">No fares configured yet.</p>
      </article>

      <article v-if="isAdministrator" class="card">
        <h3>Create Tollbooth</h3>
        <div class="inline">
          <input v-model="newTollboothId" placeholder="e.g. TO_Nord" />
          <button class="btn" @click="createTollbooth()">Create</button>
        </div>
      </article>

      <article v-if="isAdministrator" class="card">
        <h3>Create Fare</h3>
        <div class="form">
          <input v-model="newFare.entryTollboothId" placeholder="Entry" />
          <input v-model="newFare.exitTollboothId" placeholder="Exit" />
          <input v-model.number="newFare.amountCents" type="number" min="0" placeholder="Amount cents" />
          <button class="btn" @click="createFare()">Create</button>
        </div>
      </article>
    </section>

    <p v-if="msg" class="ok">{{ msg }}</p>
    <p v-if="error" class="err">{{ error }}</p>
  </section>
</template>

<style scoped>
.infrastructure-page {
  display: grid;
  gap: 0.8rem;
  animation: fade-rise 360ms cubic-bezier(0.22, 1, 0.36, 1);
}

.section-head {
  display: grid;
  gap: 0.2rem;
}

.kicker {
  font-size: 0.73rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: color-mix(in oklab, var(--brand-cobalt) 64%, var(--ink-1) 36%);
  font-weight: 650;
}

h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.12rem, 0.5vw + 1rem, 1.34rem);
  letter-spacing: -0.01em;
}

.lead {
  color: var(--ink-1);
  font-size: 0.9rem;
}

.grid {
  display: grid;
  gap: 0.82rem;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.card {
  background:
    linear-gradient(142deg, color-mix(in oklab, var(--bg-0) 90%, var(--brand-cobalt) 10%), color-mix(in oklab, var(--bg-0) 91%, var(--brand-moss) 9%));
  border: 1px solid color-mix(in oklab, var(--line-0) 82%, var(--brand-cobalt) 18%);
  border-radius: 16px;
  padding: clamp(0.9rem, 0.6vw + 0.75rem, 1.12rem);
  box-shadow: 0 14px 28px -30px color-mix(in oklab, var(--brand-cobalt) 52%, transparent);
}

h3 {
  margin-top: 0;
  margin-bottom: 0.42rem;
  font-size: 0.98rem;
  letter-spacing: -0.01em;
}

ul {
  margin: 0;
  padding-left: 1rem;
}

li {
  color: color-mix(in oklab, var(--ink-0) 84%, var(--brand-cobalt) 16%);
}

.inline {
  display: flex;
  gap: 0.5rem;
}

.form {
  display: grid;
  gap: 0.5rem;
}

input {
  border: 1px solid color-mix(in oklab, var(--line-0) 80%, var(--brand-cobalt) 20%);
  border-radius: 8px;
  padding: 0.45rem 0.55rem;
  background: color-mix(in oklab, var(--bg-0) 94%, white 6%);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

input:focus-visible {
  border-color: color-mix(in oklab, var(--line-0) 60%, var(--brand-cobalt) 40%);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--brand-cobalt) 18%, transparent);
  outline: none;
}

.btn {
  border: 0;
  background: linear-gradient(135deg, color-mix(in oklab, var(--brand-cobalt) 84%, black 16%), color-mix(in oklab, var(--brand-teal) 72%, black 28%));
  color: oklch(0.985 0.003 240);
  border-radius: 10px;
  padding: 0.5rem 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 180ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 200ms cubic-bezier(0.16, 1, 0.3, 1);
}

.btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px -16px color-mix(in oklab, var(--brand-cobalt) 70%, transparent);
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

.empty {
  color: var(--ink-1);
  font-size: 0.84rem;
}

@media (prefers-reduced-motion: reduce) {
  .infrastructure-page,
  .btn,
  input {
    animation: none;
    transition: none;
  }
}
</style>
