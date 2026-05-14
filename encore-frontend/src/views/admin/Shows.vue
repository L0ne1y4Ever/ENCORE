<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getShowList } from '../../api/show'
import type { Show } from '../../mock/shows'
import { useI18n } from 'vue-i18n'

const tableData = ref<Show[]>([])
const loading = ref(true)
const { t } = useI18n()

onMounted(async () => {
  tableData.value = await getShowList()
  loading.value = false
})
</script>

<template>
  <div class="admin-shows">
    <div class="toolbar">
      <h2>{{ t('admin.showsManagement') }}</h2>
      <el-button type="primary" class="custom-btn">{{ t('admin.addNewShow') }}</el-button>
    </div>

    <el-table 
      :data="tableData" 
      v-loading="loading" 
      style="width: 100%"
      class="custom-table"
    >
      <el-table-column prop="id" label="ID" width="100" />
      <el-table-column prop="title" :label="t('admin.title')" min-width="200" />
      <el-table-column prop="category" :label="t('admin.category')" width="120" />
      <el-table-column prop="duration" :label="t('admin.durationMinutes')" width="120" />
      <el-table-column :label="t('admin.actions')" width="150" fixed="right">
        <template #default>
          <el-button link type="primary" class="action-btn">{{ t('admin.edit') }}</el-button>
          <el-button link type="danger" class="action-btn">{{ t('admin.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.admin-shows {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--spacing-5);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-5);

  h2 {
    font-size: 18px;
    font-weight: 500;
    font-family: var(--font-family-sans);
  }
}

.custom-btn {
  background-color: var(--color-text-primary);
  border-color: var(--color-text-primary);
  color: var(--color-bg-base);
  border-radius: var(--radius-sm);
  font-family: var(--font-family-sans);
  font-weight: 600;

  &:hover {
    background-color: var(--color-accent);
    border-color: var(--color-accent);
  }
}

.action-btn {
  font-family: var(--font-family-sans);
  font-weight: 500;
}

/* 覆盖 Element Plus 表格的默认样式，使其符合 Linear 风格 */
:deep(.el-table) {
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.02);
  --el-table-tr-bg-color: transparent;
  --el-table-bg-color: transparent;
  --el-table-header-text-color: var(--color-text-secondary);
  --el-table-text-color: var(--color-text-primary);
  
  font-family: var(--font-family-sans);

  th.el-table__cell {
    font-weight: 500;
    border-bottom: 1px solid var(--color-border-strong);
  }

  td.el-table__cell {
    border-bottom: 1px solid var(--color-border);
    padding: var(--spacing-3) 0; /* 宽松的行高 */
  }

  &::before {
    display: none; /* 去除底部的虚假边框 */
  }
}
</style>
