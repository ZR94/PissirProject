<script setup lang="ts">
import { ref } from "vue";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";
import { toUserErrorMessage } from "@/utils/userError";

type Debt = {
  id: number;
  telepassId: string;
  amountCents: number;
  currency: string;
  status: string;
};

type Summary = {
  collectedCents: number;
  openDebtCents: number;
  currency: string;
};

const auth = useAuthStore();
const canSeeSummary = auth.hasAnyRole(["employees", "administrators"]);

const telepassId = ref("");
const debts = ref<Debt[]>([]);
const summary = ref<Summary | null>(null);
const payDebtId = ref<number | null>(null);
const msg = ref<string | null>(null);
const error = ref<string | null>(null);

function setError(e: unknown) {
  error.value = toUserErrorMessage(e, "Payments request failed.");
}

function formatAmount(cents: number, currency: string): string {
  return new Intl.NumberFormat("it-IT", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

async function loadDebts() {
  error.value = null;
  msg.value = null;
  debts.value = [];
  try {
    const res = await http.get<Debt[]>(`/api/payments/telepass/${telepassId.value}/debts`);
    debts.value = res.data;
    msg.value = `Loaded ${res.data.length} debts`;
  } catch (e: unknown) {
    setError(e);
  }
}

async function payDebt() {
  if (!payDebtId.value) return;
  error.value = null;
  msg.value = null;
  try {
    await http.post(`/api/payments/debts/${payDebtId.value}/pay`);
    msg.value = `Debt #${payDebtId.value} paid successfully.`;
    await loadDebts();
  } catch (e: unknown) {
    setError(e);
  }
}

async function loadSummary() {
  error.value = null;
  msg.value = null;
  summary.value = null;
  try {
    const res = await http.get<Summary>("/api/payments/summary");
    summary.value = res.data;
  } catch (e: unknown) {
    setError(e);
  }
}
</script>

<template>
  <section class="payments-page">
    <header class="section-head">
      <p class="kicker">Collections</p>
      <h2>Payments Control</h2>
      <p class="lead">Inspect telepass debts, execute payments, and monitor the revenue summary.</p>
    </header>

    <section class="grid">
      <article class="card">
        <h3>Debts By Telepass</h3>
        <div class="inline">
          <input v-model="telepassId" placeholder="Telepass ID" />
          <button class="btn" @click="loadDebts()">Load</button>
        </div>
        <ul v-if="debts.length > 0">
          <li v-for="d in debts" :key="d.id">
            #{{ d.id }} {{ d.telepassId }} - {{ formatAmount(d.amountCents, d.currency) }} - {{ d.status }}
          </li>
        </ul>
        <p v-else class="empty">No debts loaded. Search by telepass to start.</p>
      </article>

      <article class="card">
        <h3>Pay Debt</h3>
        <div class="inline">
          <input v-model.number="payDebtId" type="number" min="1" placeholder="Debt ID" />
          <button class="btn" @click="payDebt()">Pay</button>
        </div>
      </article>

      <article v-if="canSeeSummary" class="card">
        <h3>Summary</h3>
        <button class="btn" @click="loadSummary()">Refresh Summary</button>
        <div v-if="summary" class="summary">
          <p><span>Collected</span><strong>{{ formatAmount(summary.collectedCents, summary.currency) }}</strong></p>
          <p><span>Open debt</span><strong>{{ formatAmount(summary.openDebtCents, summary.currency) }}</strong></p>
          <p><span>Currency</span><strong>{{ summary.currency }}</strong></p>
        </div>
        <p v-else class="empty">Refresh to load the latest financial snapshot.</p>
      </article>
    </section>

    <p v-if="msg" class="ok">{{ msg }}</p>
    <p v-if="error" class="err">{{ error }}</p>
  </section>
</template>

<style scoped>
.payments-page {
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
  color: color-mix(in oklab, var(--brand-moss) 64%, var(--ink-1) 36%);
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
    linear-gradient(142deg, color-mix(in oklab, var(--bg-0) 90%, var(--brand-moss) 10%), color-mix(in oklab, var(--bg-0) 91%, var(--brand-cobalt) 9%));
  border: 1px solid color-mix(in oklab, var(--line-0) 82%, var(--brand-moss) 18%);
  border-radius: 16px;
  padding: clamp(0.9rem, 0.6vw + 0.75rem, 1.12rem);
  box-shadow: 0 14px 28px -30px color-mix(in oklab, var(--brand-moss) 48%, transparent);
}

h3 {
  margin-top: 0;
  margin-bottom: 0.42rem;
  font-size: 0.98rem;
  letter-spacing: -0.01em;
}

.inline {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

input {
  border: 1px solid color-mix(in oklab, var(--line-0) 80%, var(--brand-moss) 20%);
  border-radius: 8px;
  padding: 0.45rem 0.55rem;
  flex: 1;
  background: color-mix(in oklab, var(--bg-0) 94%, white 6%);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

input:focus-visible {
  border-color: color-mix(in oklab, var(--line-0) 60%, var(--brand-moss) 40%);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--brand-moss) 20%, transparent);
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

ul {
  margin: 0;
  padding-left: 1rem;
}

li {
  color: color-mix(in oklab, var(--ink-0) 85%, var(--brand-moss) 15%);
}

.summary {
  margin-top: 0.6rem;
  background: color-mix(in oklab, var(--bg-0) 84%, var(--brand-moss) 16%);
  border: 1px solid color-mix(in oklab, var(--line-0) 70%, var(--brand-moss) 30%);
  border-radius: 12px;
  padding: 0.7rem;
  display: grid;
  gap: 0.35rem;
}

.summary p {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
}

.summary span {
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

.empty {
  color: var(--ink-1);
  font-size: 0.84rem;
}

@media (prefers-reduced-motion: reduce) {
  .payments-page,
  .btn,
  input {
    animation: none;
    transition: none;
  }
}
</style>
