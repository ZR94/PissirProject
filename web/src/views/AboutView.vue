<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http } from "@/api/http";
import axios from "axios";

const msg = ref("...");

onMounted(async () => {
  try {
    const res = await http.get("/api/admin/ping");
    msg.value = JSON.stringify(res.data, null, 2);
  } catch (e: unknown) {
  if (axios.isAxiosError(e)) {
    msg.value = JSON.stringify(
      {
        message: e.message,
        status: e.response?.status,
        data: e.response?.data,
      },
      null,
      2
    );
  } else {
    msg.value = JSON.stringify(
      { message: e instanceof Error ? e.message : String(e) },
      null,
      2
    );
  }
}

});
</script>

<template>
  <main class="card">
    <h1>Admin Health</h1>
    <pre>{{ msg }}</pre>
  </main>
</template>

<style scoped>
.card {
  background: #fff;
  border: 1px solid #d4dee8;
  border-radius: 12px;
  padding: 1rem;
}

h1 {
  margin-top: 0;
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
