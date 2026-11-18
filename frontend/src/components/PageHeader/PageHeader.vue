<template>
  <div class="page-header">
    <div class="page-header__title">
      <h2>{{ title }}</h2>
      <span v-if="subtitle" class="subtitle">{{ subtitle }}</span>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title: string
  subtitle?: string
}

defineProps<Props>()
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color);

  &__title {
    display: flex;
    align-items: baseline;
    gap: 12px;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .subtitle {
      font-size: 14px;
      color: var(--el-text-color-secondary);
    }
  }

  &__actions {
    display: flex;
    gap: 8px;
  }
}
</style>
