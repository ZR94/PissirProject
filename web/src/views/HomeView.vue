<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http } from "@/api/http";
import axios from "axios";

const msg = ref("...");

onMounted(async () => {
  try {
    const res = await http.get("/api/me");
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
  <main>
    <h1>Home</h1>
    <pre>{{ msg }}</pre>
  </main>
</template>
