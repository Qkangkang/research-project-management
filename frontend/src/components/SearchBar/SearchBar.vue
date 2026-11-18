<template>
  <el-card class="search-bar" shadow="never">
    <el-form
      :model="formData"
      label-position="right"
      label-width="80px"
      @submit.prevent="handleSearch"
    >
      <el-row :gutter="16">
        <slot />
      </el-row>
      <el-row>
        <el-col :span="24" class="search-bar__actions">
          <el-button type="primary" :loading="loading" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
        </el-col>
      </el-row>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { Search, RefreshLeft } from '@element-plus/icons-vue'

interface Props {
  loading?: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  search: []
  reset: []
}>()

// 表单数据由父组件通过 v-model 传入各 form-item
const formData = defineModel<Record<string, any>>({ default: () => ({}) })

const handleSearch = () => {
  emit('search')
}

const handleReset = () => {
  // 重置表单
  Object.keys(formData.value).forEach(key => {
    formData.value[key] = undefined
  })
  emit('reset')
}
</script>

<style scoped lang="scss">
.search-bar {
  margin-bottom: 16px;

  &__actions {
    text-align: right;
  }
}

:deep(.el-card__body) {
  padding: 16px 20px;
}
</style>
