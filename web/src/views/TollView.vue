<script setup lang="ts">
import { ref } from "vue";
import { http } from "@/api/http";
import { toUserErrorMessage } from "@/utils/userError";

type TollResult = {
  entryTollboothId: string;
  exitTollboothId: string;
  amountCents: number;
  currency: string;
};

const entry = ref("VC_Est");
const exit = ref("MI_Ovest");
const result = ref<TollResult | null>(null);
const error = ref<string | null>(null);
const loading = ref(false);

function formatAmount(cents: number, currency: string): string {
  return new Intl.NumberFormat("it-IT", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

async function calculate() {
  loading.value = true;
  result.value = null;
  error.value = null;
  try {
    const res = await http.get<TollResult>("/api/toll/calculate", {
      params: { entry: entry.value, exit: exit.value },
    });
    result.value = res.data;
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to calculate toll.");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="toll-page">
    <header class="section-head">
      <p class="kicker">Pricing</p>
      <h2>Toll Calculation</h2>
      <p class="lead">Choose entry and exit tollbooths to get the official amount in real time.</p>
    </header>

    <section class="card">
      <div class="grid">
        <label>
          Entry
          <input v-model="entry" />
        </label>
        <label>
          Exit
          <input v-model="exit" />
        </label>
      </div>
      <button class="btn" :disabled="loading" @click="calculate()">
        {{ loading ? "Calculating..." : "Calculate" }}
      </button>
      <p v-if="loading" class="hint-live">Checking route matrix...</p>

      <p v-if="error" class="err">{{ error }}</p>
      <div v-if="result" class="result">
        <p><span>Entry booth</span><strong>{{ result.entryTollboothId }}</strong></p>
        <p><span>Exit booth</span><strong>{{ result.exitTollboothId }}</strong></p>
        <p><span>Amount</span><strong>{{ formatAmount(result.amountCents, result.currency) }}</strong></p>
        <p><span>Currency</span><strong>{{ result.currency }}</strong></p>
      </div>
      <p v-else-if="!error && !loading" class="empty">Run a calculation to display the fare details.</p>
    </section>
  </section>
</template>

<style scoped>
.toll-page {
  display: grid;
  gap: 0.8rem;
  animation: fade-rise 340ms cubic-bezier(0.22, 1, 0.36, 1);
}

.section-head {
  display: grid;
  gap: 0.2rem;
}

.kicker {
  font-size: 0.73rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: color-mix(in oklab, var(--brand-teal) 65%, var(--ink-1) 35%);
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

.card {
  background:
    linear-gradient(142deg, color-mix(in oklab, var(--bg-0) 90%, var(--brand-teal) 10%), color-mix(in oklab, var(--bg-0) 92%, var(--brand-cobalt) 8%));
  border: 1px solid color-mix(in oklab, var(--line-0) 82%, var(--brand-teal) 18%);
  border-radius: 16px;
  padding: clamp(0.9rem, 0.6vw + 0.75rem, 1.15rem);
  box-shadow: 0 14px 28px -28px color-mix(in oklab, var(--brand-cobalt) 55%, transparent);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.34rem;
  font-size: 0.83rem;
  color: var(--ink-1);
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

.btn:disabled {
  opacity: 0.62;
  cursor: wait;
  transform: none;
  box-shadow: none;
}

.hint-live {
  margin-top: 0.48rem;
  color: color-mix(in oklab, var(--ink-1) 78%, var(--brand-teal) 22%);
  font-size: 0.84rem;
}

.err {
  margin-top: 0.7rem;
  color: color-mix(in oklab, var(--state-err) 86%, var(--ink-0) 14%);
  background: color-mix(in oklab, var(--bg-0) 66%, var(--state-err) 34%);
  border: 1px solid color-mix(in oklab, var(--state-err) 55%, var(--line-0) 45%);
  border-radius: 10px;
  padding: 0.5rem 0.68rem;
}

.result {
  margin-top: 0.75rem;
  background: color-mix(in oklab, var(--bg-0) 87%, var(--brand-cobalt) 13%);
  border: 1px solid color-mix(in oklab, var(--line-0) 72%, var(--brand-cobalt) 28%);
  border-radius: 12px;
  padding: 0.7rem;
  display: grid;
  gap: 0.35rem;
}

.result p {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
}

.result span {
  color: var(--ink-1);
}

.empty {
  margin-top: 0.62rem;
  color: var(--ink-1);
  font-size: 0.84rem;
}

@media (prefers-reduced-motion: reduce) {
  .toll-page,
  .btn,
  input {
    animation: none;
    transition: none;
  }
}
</style>
