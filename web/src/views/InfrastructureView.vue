<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";
import type { Tollbooth } from "@/types/tollbooth";
import { formatTollboothLabel } from "@/utils/tollbooths";
import { toUserErrorMessage } from "@/utils/userError";

type Fare = {
  id: number;
  entryTollboothId: string;
  exitTollboothId: string;
  amountCents: number;
  currency: string;
};

type Device = {
  id: number;
  tollboothId: string;
  direction: "entry" | "exit";
  channel: "manual" | "telepass" | "camera";
  enabled: boolean;
  createdAt: string | null;
};

const auth = useAuthStore();
const isAdministrator = computed(() => auth.hasRole("administrators"));

const tollbooths = ref<Tollbooth[]>([]);
const fares = ref<Fare[]>([]);
const devices = ref<Device[]>([]);
const error = ref<string | null>(null);
const msg = ref<string | null>(null);
const msgTone = ref<"ok" | "danger">("ok");

const newTollbooth = ref<Tollbooth>({
  id: "",
  roadCode: "",
  kmMarker: 0,
  region: "",
  description: "",
});
const newFare = ref({
  entryTollboothId: "",
  exitTollboothId: "",
  amountCents: 0,
});
const selectedDeviceTollbooth = ref("");
const newDevice = ref<{
  tollboothId: string;
  direction: "entry" | "exit";
  channel: "manual" | "telepass" | "camera";
  enabled: boolean;
}>({
  tollboothId: "",
  direction: "entry",
  channel: "manual",
  enabled: true,
});
const canCreateFare = computed(
  () =>
    newFare.value.entryTollboothId.trim().length > 0 &&
    newFare.value.exitTollboothId.trim().length > 0 &&
    newFare.value.entryTollboothId !== newFare.value.exitTollboothId &&
    Number.isFinite(newFare.value.amountCents) &&
    newFare.value.amountCents >= 0,
);
const canCreateDevice = computed(
  () =>
    newDevice.value.tollboothId.trim().length > 0 &&
    !!newDevice.value.direction &&
    !!newDevice.value.channel,
);
const canCreateTollbooth = computed(
  () =>
    newTollbooth.value.id.trim().length > 0 &&
    !!newTollbooth.value.roadCode &&
    newTollbooth.value.roadCode.trim().length > 0 &&
    Number.isFinite(newTollbooth.value.kmMarker) &&
    (newTollbooth.value.kmMarker ?? -1) >= 0,
);
const filteredDevices = computed(() => {
  if (!selectedDeviceTollbooth.value) return devices.value;
  return devices.value.filter((d) => d.tollboothId === selectedDeviceTollbooth.value);
});

function canUpdateTollbooth(tb: Tollbooth): boolean {
  return (
    tb.id.trim().length > 0 &&
    !!tb.roadCode &&
    tb.roadCode.trim().length > 0 &&
    Number.isFinite(tb.kmMarker) &&
    (tb.kmMarker ?? -1) >= 0
  );
}

function hasTollbooth(id: string): boolean {
  return tollbooths.value.some((tb) => tb.id === id);
}

function tollboothLabel(id: string): string {
  const tollbooth = tollbooths.value.find((tb) => tb.id === id);
  return tollbooth ? formatTollboothLabel(tollbooth) : id;
}

async function loadData(clearFeedback = true) {
  error.value = null;
  if (clearFeedback) {
    msg.value = null;
  }
  try {
    const [tbRes, fareRes, deviceRes] = await Promise.all([
      http.get<Tollbooth[]>("/api/infrastructure/tollbooths"),
      http.get<Fare[]>("/api/infrastructure/fares"),
      http.get<Device[]>("/api/infrastructure/devices"),
    ]);
    tollbooths.value = tbRes.data;
    fares.value = fareRes.data;
    devices.value = deviceRes.data;
    if (!selectedDeviceTollbooth.value || !hasTollbooth(selectedDeviceTollbooth.value)) {
      selectedDeviceTollbooth.value = tollbooths.value[0]?.id ?? "";
    }
    if (!newDevice.value.tollboothId || !hasTollbooth(newDevice.value.tollboothId)) {
      newDevice.value.tollboothId = selectedDeviceTollbooth.value;
    }
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to load infrastructure data.");
  }
}

async function createTollbooth() {
  msg.value = null;
  error.value = null;
  msgTone.value = "ok";
  try {
    await http.post("/api/infrastructure/tollbooths", newTollbooth.value);
    msg.value = `Tollbooth ${newTollbooth.value.id} created`;
    newTollbooth.value = { id: "", roadCode: "", kmMarker: 0, region: "", description: "" };
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to create tollbooth.");
  }
}

async function updateTollbooth(tollbooth: Tollbooth) {
  msg.value = null;
  error.value = null;
  msgTone.value = "ok";
  try {
    await http.put(`/api/infrastructure/tollbooths/${encodeURIComponent(tollbooth.id)}`, {
      roadCode: tollbooth.roadCode,
      kmMarker: tollbooth.kmMarker,
      region: tollbooth.region,
      description: tollbooth.description,
    });
    msg.value = `Tollbooth ${tollbooth.id} updated`;
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to update tollbooth.");
  }
}

async function deleteTollbooth(tollboothId: string) {
  const confirmed = window.confirm(
    `Delete tollbooth ${tollboothId}? Related fares using this tollbooth will be removed.`,
  );
  if (!confirmed) return;

  msg.value = null;
  error.value = null;
  try {
    await http.delete(`/api/infrastructure/tollbooths/${encodeURIComponent(tollboothId)}`);
    msg.value = `Tollbooth ${tollboothId} deleted`;
    msgTone.value = "danger";
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to delete tollbooth.");
  }
}

async function createFare() {
  msg.value = null;
  error.value = null;
  msgTone.value = "ok";
  try {
    await http.post("/api/infrastructure/fares", newFare.value);
    msg.value = `Fare ${newFare.value.entryTollboothId} -> ${newFare.value.exitTollboothId} created`;
    newFare.value = { entryTollboothId: "", exitTollboothId: "", amountCents: 0 };
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to create fare.");
  }
}

async function updateFare(fare: Fare) {
  msg.value = null;
  error.value = null;
  msgTone.value = "ok";
  try {
    await http.put(`/api/infrastructure/fares/${fare.id}`, { amountCents: fare.amountCents });
    msg.value = `Fare ${fare.entryTollboothId} -> ${fare.exitTollboothId} updated`;
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to update fare.");
  }
}

async function deleteFare(fare: Fare) {
  const confirmed = window.confirm(
    `Delete fare ${fare.entryTollboothId} -> ${fare.exitTollboothId}?`,
  );
  if (!confirmed) return;

  msg.value = null;
  error.value = null;
  try {
    await http.delete(`/api/infrastructure/fares/${fare.id}`);
    msg.value = `Fare ${fare.entryTollboothId} -> ${fare.exitTollboothId} deleted`;
    msgTone.value = "danger";
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to delete fare.");
  }
}

async function createDevice() {
  msg.value = null;
  error.value = null;
  msgTone.value = "ok";
  try {
    await http.post("/api/infrastructure/devices", newDevice.value);
    msg.value = `Device ${newDevice.value.direction}/${newDevice.value.channel} created on ${newDevice.value.tollboothId}`;
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to create device.");
  }
}

async function setDeviceEnabled(device: Device, enabled: boolean) {
  msg.value = null;
  error.value = null;
  msgTone.value = "ok";
  try {
    await http.put(`/api/infrastructure/devices/${device.id}/enabled`, { enabled });
    msg.value = `Device #${device.id} ${enabled ? "enabled" : "disabled"}`;
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to update device state.");
  }
}

async function deleteDevice(device: Device) {
  const confirmed = window.confirm(
    `Delete device #${device.id} (${device.tollboothId} ${device.direction}/${device.channel})?`,
  );
  if (!confirmed) return;

  msg.value = null;
  error.value = null;
  msgTone.value = "danger";
  try {
    await http.delete(`/api/infrastructure/devices/${device.id}`);
    msg.value = `Device #${device.id} deleted`;
    await loadData(false);
  } catch (e: unknown) {
    error.value = toUserErrorMessage(e, "Unable to delete device.");
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
        <p v-if="isAdministrator" class="hint">Deleting a tollbooth also removes related fares.</p>
        <ul v-if="tollbooths.length > 0" class="tb-list">
          <li v-for="tb in tollbooths" :key="tb.id" class="tb-row">
            <template v-if="isAdministrator">
              <div class="tb-fields">
                <span class="tb-id">{{ tb.id }}</span>
                <input v-model="tb.roadCode" placeholder="Road code" />
                <input v-model.number="tb.kmMarker" type="number" min="0" step="0.1" placeholder="km marker" />
                <input v-model="tb.region" placeholder="Region" />
                <input v-model="tb.description" placeholder="Description" />
              </div>
              <div class="tb-actions">
                <button class="btn btn-secondary" :disabled="!canUpdateTollbooth(tb)" @click="updateTollbooth(tb)">Update</button>
                <button class="btn btn-danger" @click="deleteTollbooth(tb.id)">Delete</button>
              </div>
            </template>
            <template v-else>
              <div class="tb-summary">
                <strong>{{ formatTollboothLabel(tb) }}</strong>
                <span v-if="tb.region">{{ tb.region }}</span>
                <span v-if="tb.description">{{ tb.description }}</span>
              </div>
            </template>
          </li>
        </ul>
        <p v-else class="empty">No tollbooths registered yet.</p>
      </article>

      <article class="card">
        <h3>Fares</h3>
        <ul v-if="fares.length > 0">
          <li v-for="fare in fares" :key="fare.id" class="fare-row">
            <template v-if="isAdministrator">
              <span class="fare-route">{{ tollboothLabel(fare.entryTollboothId) }} -> {{ tollboothLabel(fare.exitTollboothId) }}</span>
              <div class="fare-actions">
                <input v-model.number="fare.amountCents" type="number" min="0" />
                <span class="fare-currency">{{ fare.currency }}</span>
                <button class="btn btn-secondary" @click="updateFare(fare)">Update</button>
                <button class="btn btn-danger" @click="deleteFare(fare)">Delete</button>
              </div>
            </template>
            <template v-else>
              {{ tollboothLabel(fare.entryTollboothId) }} -> {{ tollboothLabel(fare.exitTollboothId) }} = {{ fare.amountCents }} {{ fare.currency }}
            </template>
          </li>
        </ul>
        <p v-else class="empty">No fares configured yet.</p>
      </article>

      <article class="card">
        <h3>Devices</h3>
        <div class="form">
          <select v-model="selectedDeviceTollbooth" :disabled="tollbooths.length === 0">
            <option value="" disabled>Select tollbooth</option>
            <option v-for="tb in tollbooths" :key="`device-filter-${tb.id}`" :value="tb.id">{{ formatTollboothLabel(tb) }}</option>
          </select>
        </div>
        <ul v-if="filteredDevices.length > 0" class="tb-list">
          <li v-for="device in filteredDevices" :key="device.id" class="device-row">
            <div class="device-main">
              <strong>#{{ device.id }}</strong>
              <span>{{ tollboothLabel(device.tollboothId) }} {{ device.direction }}/{{ device.channel }}</span>
              <span class="fare-currency">{{ device.enabled ? "enabled" : "disabled" }}</span>
            </div>
            <div v-if="isAdministrator" class="device-actions">
              <button
                class="btn btn-secondary"
                @click="setDeviceEnabled(device, !device.enabled)"
              >
                {{ device.enabled ? "Disable" : "Enable" }}
              </button>
              <button class="btn btn-danger" @click="deleteDevice(device)">Delete</button>
            </div>
          </li>
        </ul>
        <p v-else class="empty">No devices for selected tollbooth.</p>
      </article>

      <article v-if="isAdministrator" class="card">
        <h3>Create Tollbooth</h3>
        <div class="form">
          <input v-model="newTollbooth.id" placeholder="e.g. TO_Nord" />
          <input v-model="newTollbooth.roadCode" placeholder="Road code, e.g. A4" />
          <input v-model.number="newTollbooth.kmMarker" type="number" min="0" step="0.1" placeholder="km marker" />
          <input v-model="newTollbooth.region" placeholder="Region" />
          <input v-model="newTollbooth.description" placeholder="Description" />
          <button class="btn" :disabled="!canCreateTollbooth" @click="createTollbooth()">Create tollbooth</button>
        </div>
      </article>

      <article v-if="isAdministrator" class="card">
        <h3>Create Fare</h3>
        <div class="form">
          <select
            v-model="newFare.entryTollboothId"
            :disabled="tollbooths.length === 0"
          >
            <option value="" disabled>Select entry tollbooth</option>
            <option v-for="tb in tollbooths" :key="`new-fare-entry-${tb.id}`" :value="tb.id">{{ formatTollboothLabel(tb) }}</option>
          </select>
          <select
            v-model="newFare.exitTollboothId"
            :disabled="tollbooths.length === 0"
          >
            <option value="" disabled>Select exit tollbooth</option>
            <option v-for="tb in tollbooths" :key="`new-fare-exit-${tb.id}`" :value="tb.id">{{ formatTollboothLabel(tb) }}</option>
          </select>
          <input v-model.number="newFare.amountCents" type="number" min="0" placeholder="Amount cents" />
          <button class="btn" :disabled="!canCreateFare" @click="createFare()">Create fare</button>
        </div>
      </article>

      <article v-if="isAdministrator" class="card">
        <h3>Create Device</h3>
        <div class="form">
          <select
            v-model="newDevice.tollboothId"
            :disabled="tollbooths.length === 0"
          >
            <option value="" disabled>Select tollbooth</option>
            <option v-for="tb in tollbooths" :key="`new-device-toll-${tb.id}`" :value="tb.id">{{ formatTollboothLabel(tb) }}</option>
          </select>
          <select v-model="newDevice.direction">
            <option value="entry">entry</option>
            <option value="exit">exit</option>
          </select>
          <select v-model="newDevice.channel">
            <option value="manual">manual</option>
            <option value="telepass">telepass</option>
            <option value="camera">camera</option>
          </select>
          <label class="toggle">
            <input v-model="newDevice.enabled" type="checkbox" />
            Enabled
          </label>
          <button class="btn" :disabled="!canCreateDevice" @click="createDevice()">Create device</button>
        </div>
      </article>
    </section>

    <p v-if="msg" :class="msgTone === 'danger' ? 'warn' : 'ok'">{{ msg }}</p>
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

.fare-row {
  display: grid;
  gap: 0.35rem;
}

.device-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.6rem;
  padding: 0.56rem 0.68rem;
  border: 1px solid color-mix(in oklab, var(--line-0) 84%, var(--brand-cobalt) 16%);
  border-radius: 11px;
  background: color-mix(in oklab, var(--bg-0) 90%, var(--brand-cobalt) 10%);
}

.device-main {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  align-items: center;
}

.device-actions {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.tb-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.95rem;
  padding: 0.56rem 0.68rem;
  border: 1px solid color-mix(in oklab, var(--line-0) 84%, var(--brand-cobalt) 16%);
  border-radius: 11px;
  background: color-mix(in oklab, var(--bg-0) 90%, var(--brand-cobalt) 10%);
}

.tb-list {
  list-style: none;
  padding-left: 0;
  margin-top: 0.5rem;
  display: grid;
  gap: 0.62rem;
}

.tb-id {
  font-weight: 560;
  letter-spacing: 0.01em;
}

.tb-fields {
  display: grid;
  gap: 0.45rem;
  flex: 1;
}

.tb-actions {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
  align-items: flex-start;
}

.tb-summary {
  display: grid;
  gap: 0.18rem;
}

.tb-row .btn-danger {
  min-width: 92px;
  margin-left: auto;
}

.fare-route {
  font-weight: 600;
}

.fare-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
}

.fare-actions input {
  width: 120px;
}

.fare-currency {
  color: var(--ink-1);
  font-size: 0.86rem;
}

.hint {
  color: var(--ink-1);
  font-size: 0.84rem;
  margin-bottom: 0.35rem;
}

.inline {
  display: flex;
  gap: 0.5rem;
}

.form {
  display: grid;
  gap: 0.5rem;
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.84rem;
  color: var(--ink-1);
}

input,
select {
  border: 1px solid color-mix(in oklab, var(--line-0) 80%, var(--brand-cobalt) 20%);
  border-radius: 8px;
  padding: 0.45rem 0.55rem;
  background: color-mix(in oklab, var(--bg-0) 94%, white 6%);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

input:focus-visible,
select:focus-visible {
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
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btn-secondary {
  background: linear-gradient(135deg, color-mix(in oklab, var(--brand-cobalt) 66%, black 34%), color-mix(in oklab, var(--brand-moss) 60%, black 40%));
}

.btn-danger {
  background: linear-gradient(135deg, color-mix(in oklab, var(--state-err) 82%, black 18%), color-mix(in oklab, var(--state-err) 62%, black 38%));
}

.ok {
  color: color-mix(in oklab, var(--state-ok) 88%, var(--ink-0) 12%);
  background: color-mix(in oklab, var(--bg-0) 74%, var(--state-ok) 26%);
  border: 1px solid color-mix(in oklab, var(--state-ok) 52%, var(--line-0) 48%);
  border-radius: 10px;
  padding: 0.5rem 0.66rem;
  font-weight: 560;
}

.warn {
  color: color-mix(in oklab, var(--state-err) 86%, var(--ink-0) 14%);
  background: color-mix(in oklab, var(--bg-0) 66%, var(--state-err) 34%);
  border: 1px solid color-mix(in oklab, var(--state-err) 55%, var(--line-0) 45%);
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
