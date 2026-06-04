<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  createAdminHall,
  createAdminVenue,
  getAdminHalls,
  getAdminVenues,
  updateAdminHall,
  updateAdminVenue
} from '../../api/admin'
import type { AdminHall, AdminHallPayload, AdminVenue, AdminVenuePayload, HallType } from '../../api/admin'

const { t } = useI18n()

type DialogMode = 'create' | 'edit'

const venues = ref<AdminVenue[]>([])
const halls = ref<AdminHall[]>([])
const loading = ref(false)
const saving = ref(false)
const selectedVenueId = ref('')
const venueDialogVisible = ref(false)
const hallDialogVisible = ref(false)
const venueDialogMode = ref<DialogMode>('create')
const hallDialogMode = ref<DialogMode>('create')

const venueForm = reactive({
  id: '',
  name: '',
  city: '',
  address: '',
  status: 'ACTIVE'
})

const hallForm = reactive({
  id: '',
  venueId: '',
  name: '',
  hallType: 'THEATER' as HallType,
  capacity: 150,
  clearanceMinutes: 30,
  defaultLayoutId: '',
  status: 'ACTIVE'
})

const filteredHalls = computed(() => {
  if (!selectedVenueId.value) return halls.value
  return halls.value.filter(hall => hall.venueId === selectedVenueId.value)
})

const selectedVenueName = computed(() => venues.value.find(venue => venue.id === selectedVenueId.value)?.name || t('admin.allVenues'))

const selectVenue = (venue: AdminVenue) => {
  selectedVenueId.value = venue.id
}

const resetVenueForm = () => {
  Object.assign(venueForm, {
    id: '',
    name: '',
    city: '',
    address: '',
    status: 'ACTIVE'
  })
}

const resetHallForm = () => {
  Object.assign(hallForm, {
    id: '',
    venueId: selectedVenueId.value || venues.value[0]?.id || '',
    name: '',
    hallType: 'THEATER',
    capacity: 150,
    clearanceMinutes: 30,
    defaultLayoutId: '',
    status: 'ACTIVE'
  })
}

const loadData = async () => {
  loading.value = true
  try {
    const [venueRows, hallRows] = await Promise.all([getAdminVenues(), getAdminHalls()])
    venues.value = venueRows
    halls.value = hallRows
    if (!selectedVenueId.value && venueRows.length > 0) {
      selectedVenueId.value = venueRows[0].id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

const openCreateVenue = () => {
  resetVenueForm()
  venueDialogMode.value = 'create'
  venueDialogVisible.value = true
}

const openEditVenue = (venue: AdminVenue) => {
  Object.assign(venueForm, {
    id: venue.id,
    name: venue.name,
    city: venue.city || '',
    address: venue.address || '',
    status: venue.status || 'ACTIVE'
  })
  venueDialogMode.value = 'edit'
  venueDialogVisible.value = true
}

const openCreateHall = () => {
  resetHallForm()
  hallDialogMode.value = 'create'
  hallDialogVisible.value = true
}

const openEditHall = (hall: AdminHall) => {
  Object.assign(hallForm, {
    id: hall.id,
    venueId: hall.venueId,
    name: hall.name,
    hallType: hall.hallType || 'THEATER',
    capacity: hall.capacity,
    clearanceMinutes: hall.clearanceMinutes,
    defaultLayoutId: hall.defaultLayoutId || '',
    status: hall.status || 'ACTIVE'
  })
  hallDialogMode.value = 'edit'
  hallDialogVisible.value = true
}

const saveVenue = async () => {
  if (!venueForm.name.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return
  }

  const payload: AdminVenuePayload = {
    name: venueForm.name.trim(),
    city: venueForm.city.trim() || null,
    address: venueForm.address.trim() || null,
    status: venueForm.status
  }

  saving.value = true
  try {
    const updated = venueDialogMode.value === 'create'
      ? await createAdminVenue(payload)
      : await updateAdminVenue(venueForm.id, payload)
    const index = venues.value.findIndex(venue => venue.id === updated.id)
    if (index >= 0) venues.value[index] = updated
    else venues.value.unshift(updated)
    selectedVenueId.value = updated.id
    venueDialogVisible.value = false
    ElMessage.success(t('admin.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}

const saveHall = async () => {
  if (!hallForm.venueId || !hallForm.name.trim() || hallForm.capacity < 0 || hallForm.clearanceMinutes < 0) {
    ElMessage.error(t('admin.formRequired'))
    return
  }

  const payload: AdminHallPayload = {
    venueId: hallForm.venueId,
    name: hallForm.name.trim(),
    hallType: hallForm.hallType,
    capacity: hallForm.capacity,
    clearanceMinutes: hallForm.clearanceMinutes,
    defaultLayoutId: hallForm.defaultLayoutId || null,
    status: hallForm.status
  }

  saving.value = true
  try {
    const updated = hallDialogMode.value === 'create'
      ? await createAdminHall(payload)
      : await updateAdminHall(hallForm.id, payload)
    const index = halls.value.findIndex(hall => hall.id === updated.id)
    if (index >= 0) halls.value[index] = updated
    else halls.value.unshift(updated)
    selectedVenueId.value = updated.venueId
    hallDialogVisible.value = false
    ElMessage.success(t('admin.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="venues-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.venues') }}</h1>
        <p>{{ t('admin.venuesSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain :loading="loading" @click="loadData">{{ t('admin.refresh') }}</el-button>
        <el-button type="primary" @click="openCreateVenue">{{ t('admin.addVenue') }}</el-button>
        <el-button type="primary" @click="openCreateHall">{{ t('admin.addHall') }}</el-button>
      </div>
    </div>

    <div class="workspace-grid">
      <section class="panel">
        <div class="panel-head">
          <h2>{{ t('admin.venueList') }}</h2>
          <el-button link type="primary" @click="selectedVenueId = ''">{{ t('admin.allVenues') }}</el-button>
        </div>
        <el-table :data="venues" :empty-text="t('admin.empty')" v-loading="loading" row-key="id" @row-click="selectVenue">
          <el-table-column prop="name" :label="t('admin.venueName')" min-width="160" />
          <el-table-column prop="city" :label="t('admin.city')" width="90" />
          <el-table-column :label="t('admin.halls')" width="80">
            <template #default="{ row }">{{ row.hallCount }}</template>
          </el-table-column>
          <el-table-column :label="t('admin.action')" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEditVenue(row)">{{ t('admin.edit') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>{{ t('admin.hallList') }} · {{ selectedVenueName }}</h2>
        </div>
        <el-table :data="filteredHalls" :empty-text="t('admin.empty')" v-loading="loading" row-key="id">
          <el-table-column prop="name" :label="t('admin.hallName')" min-width="160" />
          <el-table-column prop="hallType" :label="t('admin.hallType')" width="100" />
          <el-table-column prop="capacity" :label="t('admin.capacity')" width="90" />
          <el-table-column prop="clearanceMinutes" :label="t('admin.clearanceMinutes')" width="110" />
          <el-table-column prop="layoutCount" :label="t('admin.layouts')" width="90" />
          <el-table-column :label="t('admin.action')" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditHall(row)">{{ t('admin.edit') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-dialog v-model="venueDialogVisible" :title="venueDialogMode === 'create' ? t('admin.addVenue') : t('admin.editVenue')" width="520px">
      <el-form label-position="top">
        <el-form-item :label="t('admin.venueName')" required>
          <el-input v-model="venueForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('admin.city')">
          <el-input v-model="venueForm.city" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('admin.address')">
          <el-input v-model="venueForm.address" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="venueDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveVenue">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="hallDialogVisible" :title="hallDialogMode === 'create' ? t('admin.addHall') : t('admin.editHall')" width="640px">
      <el-form label-position="top" class="hall-form">
        <el-form-item :label="t('admin.venueName')" required>
          <el-select v-model="hallForm.venueId" class="full-control" filterable>
            <el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.hallName')" required>
          <el-input v-model="hallForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('admin.hallType')" required>
          <el-select v-model="hallForm.hallType" class="full-control">
            <el-option label="THEATER" value="THEATER" />
            <el-option label="CINEMA" value="CINEMA" />
            <el-option label="STADIUM" value="STADIUM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.capacity')" required>
          <el-input-number v-model="hallForm.capacity" :min="0" class="full-control" />
        </el-form-item>
        <el-form-item :label="t('admin.clearanceMinutes')" required>
          <el-input-number v-model="hallForm.clearanceMinutes" :min="0" :max="180" class="full-control" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="hallDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveHall">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.venues-page {
  width: 100%;
}

.page-header {
  margin-bottom: var(--spacing-6);
  display: flex;
  align-items: flex-start;
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

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(420px, 1.3fr);
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
}

:deep(.el-table) {
  background-color: transparent;
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.02);
  --el-table-header-text-color: var(--color-text-secondary);
  --el-table-text-color: var(--color-text-primary);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
}

.hall-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--spacing-4);
}

.full-control {
  width: 100%;
}

@media (max-width: 1100px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}
</style>
