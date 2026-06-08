<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AdminSeatMapEditor from '../../components/AdminSeatMapEditor.vue'
import { subscribeToSeatUpdates } from '../../api/seatRealtime'
import {
  getAdminScheduleInventory,
  updateAdminScheduleAreaInventory,
  updateAdminScheduleSeatStatus
} from '../../api/admin'
import type {
  AdminScheduleInventory,
  AdminScheduleInventoryArea,
  AdminScheduleInventorySeat,
  SeatStatus
} from '../../api/admin'

const route = useRoute()
const { t } = useI18n()

const scheduleId = computed(() => route.params.id as string)
const inventory = ref<AdminScheduleInventory | null>(null)
const loading = ref(false)
const operatingSeat = ref('')
const operatingArea = ref('')
const areaDialogVisible = ref(false)
const areaForm = reactive({
  inventoryId: '',
  name: '',
  totalCount: 0,
  availableCount: 0,
  status: 'AVAILABLE'
})

const seats = computed(() => inventory.value?.seats || [])
const areas = computed(() => inventory.value?.areas || [])

const loadInventory = async () => {
  loading.value = true
  try {
    inventory.value = await getAdminScheduleInventory(scheduleId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

let disconnectRealtime: (() => void) | undefined
let refreshTimer: ReturnType<typeof setTimeout> | undefined

const scheduleLiveRefresh = () => {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
  refreshTimer = setTimeout(() => {
    void loadInventory()
  }, 400)
}

onMounted(() => {
  void loadInventory()
  disconnectRealtime = subscribeToSeatUpdates(scheduleId.value, {
    onEvent: () => scheduleLiveRefresh()
  })
})

onBeforeUnmount(() => {
  disconnectRealtime?.()
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
})

const money = (value: number | string) => Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 })

const seatCanToggle = (seat: AdminScheduleInventorySeat) => {
  return seat.status === 'AVAILABLE' || seat.status === 'DISABLED'
}

const toggleSeat = async (seat: AdminScheduleInventorySeat) => {
  if (!seatCanToggle(seat)) return

  operatingSeat.value = seat.id
  try {
    const nextStatus: SeatStatus = seat.status === 'AVAILABLE' ? 'DISABLED' : 'AVAILABLE'
    inventory.value = await updateAdminScheduleSeatStatus(scheduleId.value, seat.id, nextStatus)
    ElMessage.success(t('admin.statusUpdated'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingSeat.value = ''
  }
}

const toggleSeatFromMap = (seat: unknown) => {
  return toggleSeat(seat as AdminScheduleInventorySeat)
}

const openAreaEdit = (area: AdminScheduleInventoryArea) => {
  Object.assign(areaForm, {
    inventoryId: area.id,
    name: area.name,
    totalCount: area.totalCount,
    availableCount: area.availableCount,
    status: area.status
  })
  areaDialogVisible.value = true
}

const saveArea = async () => {
  if (areaForm.totalCount < 0 || areaForm.availableCount < 0) {
    ElMessage.error(t('admin.formRequired'))
    return
  }

  operatingArea.value = areaForm.inventoryId
  try {
    inventory.value = await updateAdminScheduleAreaInventory(
      scheduleId.value,
      areaForm.inventoryId,
      areaForm.totalCount,
      areaForm.availableCount,
      areaForm.status
    )
    areaDialogVisible.value = false
    ElMessage.success(t('admin.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingArea.value = ''
  }
}
</script>

<template>
  <div class="inventory-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.scheduleInventory') }}</h1>
        <p v-if="inventory">{{ inventory.showTitle }} · {{ inventory.theaterName }} · {{ scheduleId }}</p>
      </div>
      <el-button type="primary" plain :loading="loading" @click="loadInventory">{{ t('admin.refresh') }}</el-button>
    </div>

    <template v-if="inventory">
      <div class="stats-strip">
        <div class="stat-cell">
          <span>{{ t('admin.totalSeats') }}</span>
          <strong>{{ inventory.totalSeats }}</strong>
        </div>
        <div class="stat-cell success">
          <span>{{ t('admin.availableSeats') }}</span>
          <strong>{{ inventory.availableSeats }}</strong>
        </div>
        <div class="stat-cell warning">
          <span>{{ t('admin.lockedSeats') }}</span>
          <strong>{{ inventory.lockedSeats }}</strong>
        </div>
        <div class="stat-cell danger">
          <span>{{ t('admin.soldSeats') }}</span>
          <strong>{{ inventory.soldSeats }}</strong>
        </div>
        <div class="stat-cell muted">
          <span>{{ t('admin.disabledSeats') }}</span>
          <strong>{{ inventory.disabledSeats }}</strong>
        </div>
      </div>

      <div class="inventory-grid">
        <section class="panel">
          <div class="panel-head">
            <h2>{{ t('admin.seatSnapshot') }}</h2>
            <span>{{ t('admin.seatSnapshotHint') }}</span>
          </div>
          <AdminSeatMapEditor
            v-if="seats.length > 0"
            :seats="seats"
            :operating-seat-id="operatingSeat"
            :hint="t('admin.seatSnapshotHint')"
            :stage-label="t('seat.stage')"
            @toggle="toggleSeatFromMap"
          />
          <el-empty v-else :description="t('admin.noSeatSnapshot')" />
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>{{ t('admin.areaInventory') }}</h2>
          </div>
          <el-table :data="areas" :empty-text="t('admin.empty')" v-loading="loading">
            <el-table-column prop="name" :label="t('admin.areaName')" min-width="130" />
            <el-table-column prop="price" :label="t('admin.price')" width="90">
              <template #default="{ row }">{{ money(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="totalCount" :label="t('admin.totalSeats')" width="90" />
            <el-table-column prop="availableCount" :label="t('admin.availableSeats')" width="90" />
            <el-table-column prop="lockedCount" :label="t('admin.lockedSeats')" width="90" />
            <el-table-column prop="soldCount" :label="t('admin.soldSeats')" width="80" />
            <el-table-column :label="t('admin.action')" width="90">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="operatingArea === row.id" @click="openAreaEdit(row)">
                  {{ t('admin.edit') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </template>
    <el-empty v-else-if="!loading" :description="t('admin.empty')" />

    <el-dialog v-model="areaDialogVisible" :title="`${t('admin.areaInventory')} · ${areaForm.name}`" width="520px">
      <el-form label-position="top">
        <el-form-item :label="t('admin.totalSeats')" required>
          <el-input-number v-model="areaForm.totalCount" :min="0" class="full-control" />
        </el-form-item>
        <el-form-item :label="t('admin.availableSeats')" required>
          <el-input-number v-model="areaForm.availableCount" :min="0" class="full-control" />
        </el-form-item>
        <el-form-item :label="t('admin.scheduleStatus')" required>
          <el-select v-model="areaForm.status" class="full-control">
            <el-option value="AVAILABLE" label="AVAILABLE" />
            <el-option value="DISABLED" label="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="!!operatingArea" @click="areaDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="!!operatingArea" @click="saveArea">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.inventory-page {
  width: 100%;
}

.page-header {
  margin-bottom: var(--spacing-6);
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-4);

  h1 {
    font-family: var(--font-family-display);
    font-size: 32px;
  }

  p {
    margin-top: var(--spacing-2);
    color: var(--color-text-secondary);
  }
}

.stats-strip {
  margin-bottom: var(--spacing-4);
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: var(--spacing-3);
}

.stat-cell {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--spacing-3);
  background: var(--color-bg-elevated);

  span {
    display: block;
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: var(--spacing-1);
    font-size: 22px;
  }

  &.success strong {
    color: var(--color-success);
  }

  &.warning strong {
    color: var(--color-warning);
  }

  &.danger strong {
    color: var(--color-error);
  }

  &.muted strong {
    color: var(--color-text-secondary);
  }
}

.inventory-grid {
  display: grid;
  grid-template-columns: minmax(520px, 1fr) minmax(420px, 0.8fr);
  gap: var(--spacing-4);
}

.panel {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-elevated);
  padding: var(--spacing-4);
}

.panel-head {
  margin-bottom: var(--spacing-3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);

  h2 {
    font-size: 16px;
  }

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}

:deep(.el-table) {
  background-color: transparent;
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.02);
  --el-table-header-text-color: var(--color-text-secondary);
  --el-table-text-color: var(--color-text-primary);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
}

.full-control {
  width: 100%;
}

@media (max-width: 1180px) {
  .inventory-grid,
  .stats-strip {
    grid-template-columns: 1fr;
  }
}
</style>
