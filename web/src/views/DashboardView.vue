<script setup lang="ts">
import { onMounted, ref } from "vue";
import axios from "axios";
import { http } from "@/api/http";

type MeResponse = {
  ok: boolean;
  sub: string;
  username: string;
  roles: string[];
};

const me = ref<MeResponse | null>(null);
const error = ref<string | null>(null);

onMounted(async () => {
  try {
    const res = await http.get<MeResponse>("/api/me");
    me.value = res.data;
  } catch (e: unknown) {
    if (axios.isAxiosError(e)) {
      error.value = `${e.response?.status ?? "ERR"} ${JSON.stringify(e.response?.data ?? e.message)}`;
    } else {
      error.value = String(e);
    }
  }
});
</script>

<template>
  <section class="card">
    <h2>Dashboard</h2>
    <p v-if="!me && !error">Loading profile...</p>
    <p v-if="error" class="err">{{ error }}</p>
    <pre v-if="me">{{ me }}</pre>
  </section>
</template>

<style scoped>
.card {
  background: #fff;
  border: 1px solid #d4dee8;
  border-radius: 12px;
  padding: 1rem;
}

h2 {
  margin-top: 0;
}

.err {
  color: #a12622;
}

pre {
  background: #f6f8fb;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  padding: 0.7rem;
  overflow: auto;
}
</style>
