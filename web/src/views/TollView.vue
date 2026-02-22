<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";
import { http } from "@/api/http";

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
    if (axios.isAxiosError(e)) {
      error.value = `${e.response?.status ?? "ERR"} ${JSON.stringify(e.response?.data ?? e.message)}`;
    } else {
      error.value = String(e);
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="card">
    <h2>Toll Calculation</h2>
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

    <p v-if="error" class="err">{{ error }}</p>
    <pre v-if="result">{{ result }}</pre>
  </section>
</template>

<style scoped>
.card {
  background: #fff;
  border: 1px solid #d4dee8;
  border-radius: 12px;
  padding: 1rem;
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
  gap: 0.3rem;
}

input {
  border: 1px solid #bfcedc;
  border-radius: 8px;
  padding: 0.45rem 0.55rem;
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

.btn:disabled {
  opacity: 0.6;
  cursor: wait;
}

.err {
  color: #a12622;
}

pre {
  margin-top: 0.75rem;
  background: #f6f8fb;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  padding: 0.7rem;
  overflow: auto;
}
</style>
