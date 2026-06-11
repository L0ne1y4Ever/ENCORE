<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AdminTableScroller from '../../components/AdminTableScroller.vue'
import {
  createAdminStaffUser,
  getAdminStaffUsers,
  resetAdminStaffPassword,
  updateAdminStaffUser
} from '../../api/admin'
import type { AdminStaffUser, StaffStatus } from '../../api/admin'
import { validateDisplayName, validatePassword, validateUsername } from '../../utils/credentialPolicy'

const { t } = useI18n()

type DialogMode = 'create' | 'edit'

const users = ref<AdminStaffUser[]>([])
const loading = ref(false)
const saving = ref(false)
const operatingId = ref('')
const dialogVisible = ref(false)
const resetDialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')

const form = reactive({
  id: '',
  username: '',
  password: '',
  displayName: '',
  role: 'checker' as 'admin' | 'checker',
  status: 'ACTIVE' as StaffStatus
})

const resetForm = reactive({
  id: '',
  username: '',
  password: ''
})

const credentialMessages = computed(() => ({
  usernameRule: t('login.usernameRule'),
  passwordLength: t('login.passwordLength'),
  passwordWhitespace: t('login.passwordWhitespace'),
  passwordLettersNumbers: t('login.passwordLettersNumbers'),
  passwordContainsUsername: t('login.passwordContainsUsername'),
  passwordCommon: t('login.passwordCommon'),
  displayNameRule: t('login.displayNameRule'),
  displayNameControl: t('login.displayNameControl')
}))

const formUsernameError = computed(() => (
  dialogMode.value === 'create'
    ? validateUsername(form.username, credentialMessages.value, { allowReserved: true })
    : ''
))
const formPasswordError = computed(() => (
  dialogMode.value === 'create'
    ? validatePassword(form.password, form.username, credentialMessages.value)
    : ''
))
const formDisplayNameError = computed(() => validateDisplayName(form.displayName, credentialMessages.value))
const resetPasswordError = computed(() => (
  validatePassword(resetForm.password, resetForm.username, credentialMessages.value)
))

const loadUsers = async () => {
  loading.value = true
  try {
    users.value = await getAdminStaffUsers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)

const resetEditor = () => {
  Object.assign(form, {
    id: '',
    username: '',
    password: '',
    displayName: '',
    role: 'checker',
    status: 'ACTIVE'
  })
}

const openCreate = () => {
  resetEditor()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

const openEdit = (row: AdminStaffUser) => {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    displayName: row.displayName,
    role: row.role === 'admin' ? 'admin' : 'checker',
    status: row.status as StaffStatus
  })
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

const replaceUser = (user: AdminStaffUser) => {
  const index = users.value.findIndex(item => item.id === user.id)
  if (index >= 0) {
    users.value[index] = user
  } else {
    users.value.unshift(user)
  }
}

const saveUser = async () => {
  if (!form.username.trim() || !form.displayName.trim() || (dialogMode.value === 'create' && !form.password.trim())) {
    ElMessage.error(t('admin.formRequired'))
    return
  }
  const validationError = formUsernameError.value || formPasswordError.value || formDisplayNameError.value
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  saving.value = true
  try {
    const user = dialogMode.value === 'create'
      ? await createAdminStaffUser({
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        role: form.role,
        status: form.status
      })
      : await updateAdminStaffUser(form.id, {
        displayName: form.displayName.trim(),
        role: form.role,
        status: form.status
      })
    replaceUser(user)
    dialogVisible.value = false
    ElMessage.success(t('admin.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}

const openResetPassword = (row: AdminStaffUser) => {
  Object.assign(resetForm, {
    id: row.id,
    username: row.username,
    password: ''
  })
  resetDialogVisible.value = true
}

const savePassword = async () => {
  if (!resetForm.password.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return
  }
  if (resetPasswordError.value) {
    ElMessage.error(resetPasswordError.value)
    return
  }
  operatingId.value = resetForm.id
  try {
    replaceUser(await resetAdminStaffPassword(resetForm.id, resetForm.password))
    resetDialogVisible.value = false
    ElMessage.success(t('admin.passwordResetDone'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}

const statusType = (status: string) => status === 'ACTIVE' ? 'success' : 'info'
</script>

<template>
  <div class="staff-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.staffUsers') }}</h1>
        <p>{{ t('admin.staffUsersSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain :loading="loading" @click="loadUsers">{{ t('admin.refresh') }}</el-button>
        <el-button type="primary" @click="openCreate">{{ t('admin.addStaffUser') }}</el-button>
      </div>
    </div>

    <section class="panel">
      <AdminTableScroller :label="t('admin.tableHorizontalScroll')">
        <el-table :data="users" :empty-text="t('admin.empty')" v-loading="loading" row-key="id">
          <el-table-column prop="username" :label="t('common.username')" min-width="140" />
          <el-table-column prop="displayName" :label="t('common.nickname')" min-width="150" />
          <el-table-column prop="role" :label="t('admin.staffRole')" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.role }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="t('common.status')" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.action')" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!row.editable" @click="openEdit(row)">
                {{ t('admin.edit') }}
              </el-button>
              <el-button link type="primary" :disabled="!row.editable || operatingId === row.id" @click="openResetPassword(row)">
                {{ t('admin.resetPassword') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </AdminTableScroller>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('admin.addStaffUser') : t('admin.editStaffUser')"
      width="560px"
      @closed="resetEditor"
    >
      <el-form label-position="top" class="staff-form">
        <el-form-item :label="t('common.username')" required>
          <el-input v-model="form.username" maxlength="32" :disabled="dialogMode === 'edit'" />
          <p v-if="dialogMode === 'create'" class="form-hint" :class="{ invalid: formUsernameError }">
            {{ formUsernameError || t('admin.usernamePolicyHint') }}
          </p>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" :label="t('common.password')" required>
          <el-input v-model="form.password" maxlength="64" show-password />
          <p class="form-hint" :class="{ invalid: formPasswordError }">
            {{ formPasswordError || t('admin.passwordPolicyHint') }}
          </p>
        </el-form-item>
        <el-form-item :label="t('common.nickname')" required>
          <el-input v-model="form.displayName" maxlength="64" />
          <p class="form-hint" :class="{ invalid: formDisplayNameError }">
            {{ formDisplayNameError || t('login.nicknameHint') }}
          </p>
        </el-form-item>
        <el-form-item :label="t('admin.staffRole')" required>
          <el-select v-model="form.role" class="full-control">
            <el-option value="checker" :label="t('admin.roleChecker')" />
            <el-option value="admin" :label="t('admin.roleAdmin')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')" required>
          <el-select v-model="form.status" class="full-control">
            <el-option value="ACTIVE" label="ACTIVE" />
            <el-option value="INACTIVE" label="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialogVisible" :title="`${t('admin.resetPassword')} · ${resetForm.username}`" width="460px">
      <el-form label-position="top">
        <el-form-item :label="t('admin.newPassword')" required>
          <el-input v-model="resetForm.password" maxlength="64" show-password />
          <p class="form-hint" :class="{ invalid: resetPasswordError }">
            {{ resetPasswordError || t('admin.passwordPolicyHint') }}
          </p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="!!operatingId" @click="resetDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="!!operatingId" @click="savePassword">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.staff-page {
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

.panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-elevated);
  padding: var(--spacing-4);
}

.staff-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--spacing-4);
}

.full-control {
  width: 100%;
}

.form-hint {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.45;

  &.invalid {
    color: #ffb1a8;
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

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
  }

  .staff-form {
    grid-template-columns: 1fr;
  }
}
</style>
