<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";

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
  if (axios.isAxiosError(e)) {
    error.value = `${e.response?.status ?? "ERR"} ${JSON.stringify(e.response?.data ?? e.message)}`;
  } else {
    error.value = String(e);
  }
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
    const res = await http.post(`/api/payments/debts/${payDebtId.value}/pay`);
    msg.value = `Payment result: ${JSON.stringify(res.data)}`;
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
  <section class="grid">
    <article class="card">
      <h2>Debts By Telepass</h2>
      <div class="inline">
        <input v-model="telepassId" placeholder="Telepass ID" />
        <button class="btn" @click="loadDebts()">Load</button>
      </div>
      <ul>
        <li v-for="d in debts" :key="d.id">
          #{{ d.id }} {{ d.telepassId }} - {{ d.amountCents }} {{ d.currency }} - {{ d.status }}
        </li>
      </ul>
    </article>

    <article class="card">
      <h2>Pay Debt</h2>
      <div class="inline">
        <input v-model.number="payDebtId" type="number" min="1" placeholder="Debt ID" />
        <button class="btn" @click="payDebt()">Pay</button>
      </div>
    </article>

    <article v-if="canSeeSummary" class="card">
      <h2>Summary</h2>
      <button class="btn" @click="loadSummary()">Refresh Summary</button>
      <pre v-if="summary">{{ summary }}</pre>
    </article>
  </section>

  <p v-if="msg" class="ok">{{ msg }}</p>
  <p v-if="error" class="err">{{ error }}</p>
</template>

<style scoped>
.grid {
  display: grid;
  gap: 0.9rem;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.card {
  background: #fff;
  border: 1px solid #d4dee8;
  border-radius: 12px;
  padding: 1rem;
}

h2 {
  margin-top: 0;
}

.inline {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

input {
  border: 1px solid #bfcedc;
  border-radius: 8px;
  padding: 0.45rem 0.55rem;
  flex: 1;
}

.btn {
  border: 0;
  background: #1d5b79;
  color: #fff;
  border-radius: 8px;
  padding: 0.5rem 0.75rem;
  font-weight: 600;
  cursor: pointer;
}

ul {
  margin: 0;
  padding-left: 1rem;
}

pre {
  margin-top: 0.6rem;
  background: #f6f8fb;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  padding: 0.7rem;
  overflow: auto;
}

.ok {
  color: #166534;
}

.err {
  color: #a12622;
}
</style>
