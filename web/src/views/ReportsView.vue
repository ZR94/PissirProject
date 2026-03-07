<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";
import type { Tollbooth } from "@/types/tollbooth";
import { formatTollboothLabel } from "@/utils/tollbooths";
import { toUserErrorMessage } from "@/utils/userError";

type Trip = {
  id: number;
  entryTollboothId: string;
  exitTollboothId: string | null;
  ticketId: string | null;
  telepassId: string | null;
  plate: string | null;
  entryAt: string;
  exitAt: string | null;
  amountCents: number | null;
  currency: string | null;
  avgSpeedKmh: number | null;
  speeding: boolean;
  paid: boolean;
};

type RouteStats = {
  entryTollboothId: string;
  exitTollboothId: string;
  tripsCount: number;
  paidTripsCount: number;
  unpaidTripsCount: number;
  totalAmountCents: number;
  avgAmountCents: number;
  currency: string;
};

type ActiveTrip = {
  id: number;
  entryTollboothId: string;
  passId: string;
  channel: "manual" | "telepass";
  plate: string | null;
  entryAt: string;
  minutesInNetwork: number;
};

type Fault = {
  id: number;
  tollboothId: string;
  direction: "entry" | "exit";
  channel: "manual" | "telepass" | "camera";
  code: string;
  message: string;
  severity: "WARN" | "ERROR";
  status: "OPEN" | "RESPONDED";
  backendAction: string | null;
  createdAt: string;
  respondedAt: string | null;
};

type FaultAction = "ACK" | "TECHNICIAN_DISPATCHED" | "RESET_REQUESTED";

const defaultFilters = {
  from: "",
  to: "",
  entryTollboothId: "",
  exitTollboothId: "",
  channel: "all",
  paid: "all",
  limit: 100,
};

const filters = ref({ ...defaultFilters });
const activeFilters = ref({
  entryTollboothId: "",
  channel: "all",
  limit: 100,
});
const auth = useAuthStore();

const trips = ref<Trip[]>([]);
const routeStats = ref<RouteStats[]>([]);
const activeTrips = ref<ActiveTrip[]>([]);
const faults = ref<Fault[]>([]);
const tollbooths = ref<Tollbooth[]>([]);
const loading = ref(false);
const tollboothsLoading = ref(false);
const faultsLoading = ref(false);
const error = ref<string | null>(null);
const tollboothsError = ref<string | null>(null);
const faultsError = ref<string | null>(null);
const msg = ref<string | null>(null);
const faultsMsg = ref<string | null>(null);
const respondingFaultId = ref<number | null>(null);
const faultActions = ref<Record<number, FaultAction>>({});
const faultMessages = ref<Record<number, string>>({});

const isAdministrator = computed(() => auth.hasRole("administrators"));

const kpis = computed(() => {
  const total = trips.value.length;
  const completed = trips.value.filter((t) => !!t.exitAt).length;
  const open = total - completed;
  const paid = trips.value.filter((t) => t.paid).length;
  return { total, completed, open, paid };
});

function buildQueryParams(): Record<string, string | number> {
  const q: Record<string, string | number> = {};
  if (filters.value.from.trim()) q.from = toIsoInstantFromLocalInput(filters.value.from.trim());
  if (filters.value.to.trim()) q.to = toIsoInstantFromLocalInput(filters.value.to.trim());
  if (filters.value.entryTollboothId.trim()) q.entryTollboothId = filters.value.entryTollboothId.trim();
  if (filters.value.exitTollboothId.trim()) q.exitTollboothId = filters.value.exitTollboothId.trim();
  if (filters.value.channel !== "all") q.channel = filters.value.channel;
  if (filters.value.paid !== "all") q.paid = filters.value.paid;
  q.limit = filters.value.limit;
  return q;
}

function buildActiveTripsQueryParams(): Record<string, string | number> {
  const q: Record<string, string | number> = {};
  if (activeFilters.value.entryTollboothId.trim()) {
    q.entryTollboothId = activeFilters.value.entryTollboothId.trim();
  }
  if (activeFilters.value.channel !== "all") {
    q.channel = activeFilters.value.channel;
  }
  q.limit = activeFilters.value.limit;
  return q;
}

function toIsoInstantFromLocalInput(raw: string): string {
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toISOString();
}

function formatAmount(cents: number | null, currency: string | null): string {
  if (cents == null || currency == null) return "-";
  return new Intl.NumberFormat("it-IT", { style: "currency", currency }).format(cents / 100);
}

function formatSpeed(avgSpeedKmh: number | null): string {
  if (avgSpeedKmh == null) return "-";
  return `${avgSpeedKmh.toFixed(2)} km/h`;
}

function formatDateTime(raw: string | null): string {
  if (!raw) return "-";
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleString("it-IT");
}

function tripPassId(row: Trip): string {
  return row.ticketId || row.telepassId || "-";
}

function tripChannel(row: Trip): string {
  if (row.ticketId) return "manual";
  if (row.telepassId) return "telepass";
  return "-";
}

function tollboothLabel(id: string | null): string {
  if (!id) return "-";
  const tollbooth = tollbooths.value.find((tb) => tb.id === id);
  return tollbooth ? formatTollboothLabel(tollbooth) : id;
}

function faultDevice(row: Fault): string {
  return `${row.direction} ${row.channel}`;
}

function faultStatusClass(status: Fault["status"]): string {
  return status === "RESPONDED" ? "pill--ok" : "pill--warn";
}

function faultSeverityClass(severity: Fault["severity"]): string {
  return severity === "ERROR" ? "pill--err" : "pill--warn";
}

function actionLabel(action: string | null): string {
  if (!action) return "-";
  return action.split("_").join(" ").toLowerCase();
}

function ensureFaultAction(id: number): FaultAction {
  if (!faultActions.value[id]) {
    faultActions.value[id] = "ACK";
  }
  return faultActions.value[id];
}

async function loadReports() {
  loading.value = true;
  error.value = null;
  msg.value = null;
  const params = buildQueryParams();
  try {
    const [tripRes, statsRes] = await Promise.all([
      http.get<Trip[]>("/api/reports/trips", { params }),
      http.get<RouteStats[]>("/api/reports/routes", { params }),
    ]);
    trips.value = tripRes.data;
    routeStats.value = statsRes.data;
    msg.value = `Loaded ${trips.value.length} trips and ${routeStats.value.length} route stats rows.`;
  } catch (e: unknown) {
    trips.value = [];
    routeStats.value = [];
    error.value = toUserErrorMessage(e, "Unable to load reports.");
  } finally {
    loading.value = false;
  }
}

async function loadActiveTrips() {
  loading.value = true;
  error.value = null;
  msg.value = null;
  const params = buildActiveTripsQueryParams();
  try {
    const res = await http.get<ActiveTrip[]>("/api/reports/active-trips", { params });
    activeTrips.value = res.data;
    msg.value = `Loaded ${activeTrips.value.length} active vehicles.`;
  } catch (e: unknown) {
    activeTrips.value = [];
    error.value = toUserErrorMessage(e, "Unable to load active vehicles.");
  } finally {
    loading.value = false;
  }
}

async function loadFaults() {
  faultsLoading.value = true;
  faultsError.value = null;
  faultsMsg.value = null;
  try {
    const res = await http.get<Fault[]>("/api/faults");
    faults.value = res.data;
    for (const fault of faults.value) {
      ensureFaultAction(fault.id);
      if (faultMessages.value[fault.id] == null) {
        faultMessages.value[fault.id] = "";
      }
    }
  } catch (e: unknown) {
    faults.value = [];
    faultsError.value = toUserErrorMessage(e, "Unable to load faults.");
  } finally {
    faultsLoading.value = false;
  }
}

async function respondToFault(faultId: number) {
  respondingFaultId.value = faultId;
  faultsError.value = null;
  faultsMsg.value = null;
  try {
    const action = ensureFaultAction(faultId);
    const message = (faultMessages.value[faultId] || "").trim();
    if (!message) {
      faultsError.value = "Response message is required.";
      return;
    }
    await http.post(`/api/faults/${faultId}/respond`, { action, message });
    faultsMsg.value = `Fault ${faultId} responded with ${actionLabel(action)}.`;
    await loadFaults();
  } catch (e: unknown) {
    faultsError.value = toUserErrorMessage(e, "Unable to send fault response.");
  } finally {
    respondingFaultId.value = null;
  }
}

function resetFilters() {
  filters.value = { ...defaultFilters };
  activeFilters.value = { entryTollboothId: "", channel: "all", limit: 100 };
  msg.value = "Filters reset. Load reports to refresh data.";
  error.value = null;
}

async function loadTollbooths() {
  tollboothsLoading.value = true;
  tollboothsError.value = null;
  try {
    const res = await http.get<Tollbooth[]>("/api/infrastructure/tollbooths");
    tollbooths.value = res.data;
  } catch (e: unknown) {
    tollbooths.value = [];
    tollboothsError.value = toUserErrorMessage(e, "Unable to load tollbooths.");
  } finally {
    tollboothsLoading.value = false;
  }
}

onMounted(async () => {
  await loadTollbooths();
  await loadReports();
  await loadActiveTrips();
  await loadFaults();
});
</script>

<template>
  <section class="reports-page">
    <header class="section-head">
      <p class="kicker">Analytics</p>
      <h2>Trips &amp; Route Stats</h2>
      <p class="lead">Filter trip history and inspect aggregate traffic and revenue by route.</p>
    </header>

    <section class="card">
      <h3>Filters</h3>
      <div class="filters">
        <label>
          From
          <input v-model="filters.from" type="datetime-local" />
        </label>
        <label>
          To
          <input v-model="filters.to" type="datetime-local" />
        </label>
        <label>
          Entry tollbooth
          <select
            v-model="filters.entryTollboothId"
            :disabled="tollboothsLoading || tollbooths.length === 0"
          >
            <option value="">All</option>
            <option v-for="tb in tollbooths" :key="`entry-${tb.id}`" :value="tb.id">{{ formatTollboothLabel(tb) }}</option>
          </select>
        </label>
        <label>
          Exit tollbooth
          <select
            v-model="filters.exitTollboothId"
            :disabled="tollboothsLoading || tollbooths.length === 0"
          >
            <option value="">All</option>
            <option v-for="tb in tollbooths" :key="`exit-${tb.id}`" :value="tb.id">{{ formatTollboothLabel(tb) }}</option>
          </select>
        </label>
        <label>
          Channel
          <select v-model="filters.channel">
            <option value="all">All</option>
            <option value="manual">Manual</option>
            <option value="telepass">Telepass</option>
          </select>
        </label>
        <label>
          Paid
          <select v-model="filters.paid">
            <option value="all">All</option>
            <option value="true">Paid</option>
            <option value="false">Unpaid</option>
          </select>
        </label>
        <label>
          Limit
          <input v-model.number="filters.limit" type="number" min="1" max="500" />
        </label>
      </div>
      <div class="actions">
        <button class="btn" :disabled="loading" @click="loadReports()">
          {{ loading ? "Loading..." : "Apply filters" }}
        </button>
        <button class="btn btn-ghost" :disabled="loading" @click="resetFilters()">Reset</button>
      </div>
      <p v-if="tollboothsLoading" class="hint">Loading tollbooths...</p>
      <p v-else-if="tollboothsError" class="err">{{ tollboothsError }}</p>
    </section>

    <section class="kpis">
      <article class="kpi">
        <span>Trips loaded</span>
        <strong>{{ kpis.total }}</strong>
      </article>
      <article class="kpi">
        <span>Completed trips</span>
        <strong>{{ kpis.completed }}</strong>
      </article>
      <article class="kpi">
        <span>Open trips</span>
        <strong>{{ kpis.open }}</strong>
      </article>
      <article class="kpi">
        <span>Paid trips</span>
        <strong>{{ kpis.paid }}</strong>
      </article>
    </section>

    <section class="card">
      <h3>Active Vehicles</h3>
      <div class="filters">
        <label>
          Entry tollbooth
          <select
            v-model="activeFilters.entryTollboothId"
            :disabled="tollboothsLoading || tollbooths.length === 0"
          >
            <option value="">All</option>
            <option v-for="tb in tollbooths" :key="`active-entry-${tb.id}`" :value="tb.id">{{ formatTollboothLabel(tb) }}</option>
          </select>
        </label>
        <label>
          Channel
          <select v-model="activeFilters.channel">
            <option value="all">All</option>
            <option value="manual">Manual</option>
            <option value="telepass">Telepass</option>
          </select>
        </label>
        <label>
          Limit
          <input v-model.number="activeFilters.limit" type="number" min="1" max="500" />
        </label>
      </div>
      <div class="actions">
        <button class="btn" :disabled="loading" @click="loadActiveTrips()">
          {{ loading ? "Loading..." : "Load active vehicles" }}
        </button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Entry</th>
              <th>Channel</th>
              <th>Pass ID</th>
              <th>Plate</th>
              <th>Entry At</th>
              <th>Minutes In Network</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in activeTrips" :key="`active-${row.id}`">
              <td>{{ row.id }}</td>
              <td>{{ tollboothLabel(row.entryTollboothId) }}</td>
              <td>
                <span class="pill" :class="row.channel === 'telepass' ? 'pill--info' : 'pill--neutral'">
                  {{ row.channel }}
                </span>
              </td>
              <td>{{ row.passId }}</td>
              <td>{{ row.plate || "-" }}</td>
              <td>{{ formatDateTime(row.entryAt) }}</td>
              <td>{{ row.minutesInNetwork }}</td>
            </tr>
            <tr v-if="activeTrips.length === 0">
              <td colspan="7" class="empty-row">No active vehicles for current filters.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <h3>Trip History</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Entry</th>
              <th>Exit</th>
              <th>Channel</th>
              <th>Pass ID</th>
              <th>Plate</th>
              <th>Entry At</th>
              <th>Exit At</th>
              <th>Amount</th>
              <th>Avg Speed</th>
              <th>Speeding</th>
              <th>Paid</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in trips" :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ tollboothLabel(row.entryTollboothId) }}</td>
              <td>{{ tollboothLabel(row.exitTollboothId) }}</td>
              <td>
                <span
                  class="pill"
                  :class="tripChannel(row) === 'telepass' ? 'pill--info' : tripChannel(row) === 'manual' ? 'pill--neutral' : ''"
                >
                  {{ tripChannel(row) }}
                </span>
              </td>
              <td>{{ tripPassId(row) }}</td>
              <td>{{ row.plate || "-" }}</td>
              <td>{{ formatDateTime(row.entryAt) }}</td>
              <td>{{ formatDateTime(row.exitAt) }}</td>
              <td>{{ formatAmount(row.amountCents, row.currency) }}</td>
              <td>{{ formatSpeed(row.avgSpeedKmh) }}</td>
              <td>
                <span class="pill" :class="row.speeding ? 'pill--err' : 'pill--ok'">
                  {{ row.speeding ? "yes" : "no" }}
                </span>
              </td>
              <td>
                <span class="pill" :class="row.paid ? 'pill--ok' : 'pill--warn'">
                  {{ row.paid ? "paid" : "unpaid" }}
                </span>
              </td>
            </tr>
            <tr v-if="trips.length === 0">
              <td colspan="12" class="empty-row">No trips found for current filters.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <h3>Route Statistics</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Route</th>
              <th>Trips</th>
              <th>Paid</th>
              <th>Unpaid</th>
              <th>Total</th>
              <th>Average</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in routeStats" :key="`${row.entryTollboothId}-${row.exitTollboothId}`">
              <td>{{ tollboothLabel(row.entryTollboothId) }} -> {{ tollboothLabel(row.exitTollboothId) }}</td>
              <td>{{ row.tripsCount }}</td>
              <td>{{ row.paidTripsCount }}</td>
              <td>{{ row.unpaidTripsCount }}</td>
              <td>{{ formatAmount(row.totalAmountCents, row.currency) }}</td>
              <td>{{ formatAmount(row.avgAmountCents, row.currency) }}</td>
            </tr>
            <tr v-if="routeStats.length === 0">
              <td colspan="6" class="empty-row">No route statistics found for current filters.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <div class="section-row">
        <h3>Faults</h3>
        <button class="btn btn-ghost" :disabled="faultsLoading" @click="loadFaults()">
          {{ faultsLoading ? "Loading..." : "Refresh faults" }}
        </button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Tollbooth</th>
              <th>Device</th>
              <th>Code</th>
              <th>Message</th>
              <th>Severity</th>
              <th>Status</th>
              <th>Created At</th>
              <th>Backend Action</th>
              <th v-if="isAdministrator">Respond</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in faults" :key="`fault-${row.id}`">
              <td>{{ row.id }}</td>
              <td>{{ tollboothLabel(row.tollboothId) }}</td>
              <td>{{ faultDevice(row) }}</td>
              <td>{{ row.code }}</td>
              <td>{{ row.message }}</td>
              <td>
                <span class="pill" :class="faultSeverityClass(row.severity)">
                  {{ row.severity.toLowerCase() }}
                </span>
              </td>
              <td>
                <span class="pill" :class="faultStatusClass(row.status)">
                  {{ row.status.toLowerCase() }}
                </span>
              </td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
              <td>{{ actionLabel(row.backendAction) }}</td>
              <td v-if="isAdministrator">
                <div v-if="row.status === 'OPEN'" class="respond-box">
                  <select v-model="faultActions[row.id]">
                    <option value="ACK">Ack</option>
                    <option value="TECHNICIAN_DISPATCHED">Technician dispatched</option>
                    <option value="RESET_REQUESTED">Reset requested</option>
                  </select>
                  <input
                    v-model="faultMessages[row.id]"
                    type="text"
                    maxlength="160"
                    placeholder="Short response message"
                  />
                  <button
                    class="btn"
                    :disabled="respondingFaultId === row.id"
                    @click="respondToFault(row.id)"
                  >
                    {{ respondingFaultId === row.id ? "Sending..." : "Send response" }}
                  </button>
                </div>
                <span v-else class="hint">Already responded</span>
              </td>
            </tr>
            <tr v-if="faults.length === 0">
              <td :colspan="isAdministrator ? 10 : 9" class="empty-row">No device faults recorded.</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="faultsMsg" class="ok">{{ faultsMsg }}</p>
      <p v-if="faultsError" class="err">{{ faultsError }}</p>
    </section>

    <p v-if="msg" class="ok">{{ msg }}</p>
    <p v-if="error" class="err">{{ error }}</p>
  </section>
</template>

<style scoped>
.reports-page {
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
  color: color-mix(in oklab, var(--brand-cobalt) 62%, var(--ink-1) 38%);
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
    linear-gradient(142deg, color-mix(in oklab, var(--bg-0) 90%, var(--brand-cobalt) 10%), color-mix(in oklab, var(--bg-0) 91%, var(--brand-teal) 9%));
  border: 1px solid color-mix(in oklab, var(--line-0) 82%, var(--brand-cobalt) 18%);
  border-radius: 16px;
  padding: clamp(0.9rem, 0.6vw + 0.75rem, 1.12rem);
  box-shadow: 0 14px 28px -30px color-mix(in oklab, var(--brand-cobalt) 48%, transparent);
}

h3 {
  margin-top: 0;
  margin-bottom: 0.5rem;
  font-size: 0.98rem;
}

.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 0.55rem;
  margin-bottom: 0.7rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
  font-size: 0.82rem;
  color: var(--ink-1);
}

input,
select {
  border: 1px solid color-mix(in oklab, var(--line-0) 80%, var(--brand-cobalt) 20%);
  border-radius: 8px;
  padding: 0.45rem 0.55rem;
  background: color-mix(in oklab, var(--bg-0) 94%, white 6%);
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
}

.btn:disabled {
  opacity: 0.6;
  cursor: wait;
}

.btn-ghost {
  background: color-mix(in oklab, var(--bg-0) 90%, var(--brand-cobalt) 10%);
  color: var(--ink-0);
  border: 1px solid color-mix(in oklab, var(--line-0) 82%, var(--brand-cobalt) 18%);
}

.actions {
  display: flex;
  gap: 0.45rem;
  align-items: center;
}

.section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  margin-bottom: 0.6rem;
}

.hint {
  margin-top: 0.48rem;
  color: var(--ink-1);
  font-size: 0.84rem;
}

.kpis {
  display: grid;
  gap: 0.65rem;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.kpi {
  border: 1px solid color-mix(in oklab, var(--line-0) 80%, var(--brand-teal) 20%);
  border-radius: 12px;
  background: color-mix(in oklab, var(--bg-0) 93%, var(--brand-teal) 7%);
  padding: 0.65rem 0.75rem;
  display: grid;
  gap: 0.2rem;
}

.kpi span {
  color: var(--ink-1);
  font-size: 0.8rem;
}

.kpi strong {
  font-size: 1.2rem;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 700px;
}

tbody tr:nth-child(even) {
  background: color-mix(in oklab, var(--bg-0) 92%, var(--brand-cobalt) 8%);
}

th,
td {
  text-align: left;
  padding: 0.42rem 0.48rem;
  border-bottom: 1px solid color-mix(in oklab, var(--line-0) 84%, var(--brand-cobalt) 16%);
  font-size: 0.84rem;
}

th {
  font-weight: 650;
  color: var(--ink-1);
}

.empty-row {
  text-align: center;
  color: var(--ink-1);
}

.pill {
  display: inline-flex;
  align-items: center;
  border: 1px solid color-mix(in oklab, var(--line-0) 84%, var(--brand-cobalt) 16%);
  border-radius: 999px;
  padding: 0.08rem 0.5rem;
  font-size: 0.74rem;
  font-weight: 640;
  letter-spacing: 0.01em;
  background: color-mix(in oklab, var(--bg-0) 90%, var(--brand-cobalt) 10%);
}

.pill--ok {
  border-color: color-mix(in oklab, var(--state-ok) 48%, var(--line-0) 52%);
  background: color-mix(in oklab, var(--bg-0) 78%, var(--state-ok) 22%);
  color: color-mix(in oklab, var(--state-ok) 80%, var(--ink-0) 20%);
}

.pill--warn {
  border-color: color-mix(in oklab, var(--state-warn) 56%, var(--line-0) 44%);
  background: color-mix(in oklab, var(--bg-0) 74%, var(--state-warn) 26%);
  color: color-mix(in oklab, var(--ink-0) 80%, var(--state-warn) 20%);
}

.pill--info {
  border-color: color-mix(in oklab, var(--brand-teal) 44%, var(--line-0) 56%);
  background: color-mix(in oklab, var(--bg-0) 80%, var(--brand-teal) 20%);
  color: color-mix(in oklab, var(--ink-0) 82%, var(--brand-teal) 18%);
}

.pill--neutral {
  border-color: color-mix(in oklab, var(--brand-cobalt) 32%, var(--line-0) 68%);
  background: color-mix(in oklab, var(--bg-0) 84%, var(--brand-cobalt) 16%);
}

.pill--err {
  border-color: color-mix(in oklab, var(--state-err) 52%, var(--line-0) 48%);
  background: color-mix(in oklab, var(--bg-0) 72%, var(--state-err) 28%);
  color: color-mix(in oklab, var(--state-err) 84%, var(--ink-0) 16%);
}

.respond-box {
  display: grid;
  gap: 0.35rem;
  min-width: 240px;
}

.ok {
  color: color-mix(in oklab, var(--state-ok) 88%, var(--ink-0) 12%);
  background: color-mix(in oklab, var(--bg-0) 74%, var(--state-ok) 26%);
  border: 1px solid color-mix(in oklab, var(--state-ok) 52%, var(--line-0) 48%);
  border-radius: 10px;
  padding: 0.5rem 0.66rem;
}

.err {
  color: color-mix(in oklab, var(--state-err) 86%, var(--ink-0) 14%);
  background: color-mix(in oklab, var(--bg-0) 66%, var(--state-err) 34%);
  border: 1px solid color-mix(in oklab, var(--state-err) 55%, var(--line-0) 45%);
  border-radius: 10px;
  padding: 0.5rem 0.66rem;
}

@media (prefers-reduced-motion: reduce) {
  .reports-page {
    animation: none;
  }
}
</style>
