<template>
  <div class="error-page">
    <el-result icon="warning" title="403" sub-title="抱歉，您没有访问权限">
      <template #extra>
        <el-button type="primary" @click="goBack">返回</el-button>
        <el-button @click="goHome">回到首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.back()
}

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
