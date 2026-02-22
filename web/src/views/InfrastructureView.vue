<script setup lang="ts">
import { onMounted, ref } from "vue";
import axios from "axios";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";

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
    if (axios.isAxiosError(e)) {
      error.value = `${e.response?.status ?? "ERR"} ${JSON.stringify(e.response?.data ?? e.message)}`;
    } else {
      error.value = String(e);
    }
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
    if (axios.isAxiosError(e)) {
      error.value = `${e.response?.status ?? "ERR"} ${JSON.stringify(e.response?.data ?? e.message)}`;
    } else {
      error.value = String(e);
    }
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
    if (axios.isAxiosError(e)) {
      error.value = `${e.response?.status ?? "ERR"} ${JSON.stringify(e.response?.data ?? e.message)}`;
    } else {
      error.value = String(e);
    }
  }
}

onMounted(loadData);
</script>

<template>
  <section class="grid">
    <article class="card">
      <h2>Tollbooths</h2>
      <ul>
        <li v-for="tb in tollbooths" :key="tb">{{ tb }}</li>
      </ul>
    </article>

    <article class="card">
      <h2>Fares</h2>
      <ul>
        <li v-for="fare in fares" :key="fare.id">
          {{ fare.entryTollboothId }} -> {{ fare.exitTollboothId }} = {{ fare.amountCents }} {{ fare.currency }}
        </li>
      </ul>
    </article>

    <article v-if="isAdministrator" class="card">
      <h2>Create Tollbooth</h2>
      <div class="inline">
        <input v-model="newTollboothId" placeholder="e.g. TO_Nord" />
        <button class="btn" @click="createTollbooth()">Create</button>
      </div>
    </article>

    <article v-if="isAdministrator" class="card">
      <h2>Create Fare</h2>
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

ul {
  margin: 0;
  padding-left: 1rem;
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

.ok {
  color: #166534;
}

.err {
  color: #a12622;
}
</style>
