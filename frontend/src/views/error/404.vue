<template>
  <div class="error-page">
    <el-result icon="warning" title="404" sub-title="抱歉，页面不存在">
      <template #extra>
        <el-button type="primary" @click="goHome">回到首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}
</script>

<style scoped lang="scss">
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
