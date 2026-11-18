<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>科研项目管理系统</h1>
        <p>Research Project Management System</p>
      </div>

      <el-form ref="formRef" :model="formData" :rules="rules" class="login-form">
        <el-form-item prop="employeeNo">
          <el-input
            v-model="formData.employeeNo"
            placeholder="请输入工号"
            size="large"
            clearable
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <p>科研项目全生命周期管理系统 v1.0</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import type { LoginCmd } from '@/api/types'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive<LoginCmd>({
  employeeNo: '',
  password: ''
})

const rules: FormRules = {
  employeeNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await userStore.login(formData)
      ElMessage.success('登录成功')

      // 跳转到重定向地址或首页；仅接受站内相对路径，防止开放重定向
      const redirect = (route.query.redirect as string) || '/'
      const safeRedirect = redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/'
      router.push(safeRedirect)
    } catch (error: any) {
      ElMessage.error(error?.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 400px;
  padding: 40px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);

  .login-header {
    text-align: center;
    margin-bottom: 32px;

    h1 {
      font-size: 24px;
      font-weight: 600;
      color: var(--color-text-primary);
      margin-bottom: 8px;
    }

    p {
      font-size: 14px;
      color: var(--color-text-secondary);
    }
  }

  .login-form {
    .login-button {
      width: 100%;
    }
  }

  .login-footer {
    text-align: center;
    margin-top: 24px;

    p {
      font-size: 12px;
      color: var(--color-text-secondary);
    }
  }
}
</style>
