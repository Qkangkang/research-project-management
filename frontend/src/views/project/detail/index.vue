<template>
  <div v-loading="loading" class="project-detail-page">
    <PageHeader
      :title="project.title || '项目详情'"
      :subtitle="`编号：${project.projectNo || '-'}`"
    >
      <template #actions>
        <StatusTag v-if="project.status" :status="project.status" domain="project" size="large" />
        <el-button
          v-if="project.status === 'EXECUTING'"
          type="warning"
          v-permission="'project:state:change'"
          @click="handlePause"
        >
          暂停
        </el-button>
        <el-button
          v-if="project.status === 'SUSPENDED'"
          type="success"
          v-permission="'project:state:change'"
          @click="handleResume"
        >
          恢复
        </el-button>
        <el-button
          v-if="['EXECUTING', 'SUSPENDED'].includes(project.status || '')"
          type="danger"
          v-permission="'project:state:change'"
          @click="handleTerminate"
        >
          终止
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="基本信息" name="basic">
        <BasicInfo :project="project" />
      </el-tab-pane>

      <el-tab-pane label="项目成员" name="members">
        <Members :project-id="projectId" />
      </el-tab-pane>

      <el-tab-pane label="里程碑" name="milestones">
        <Milestones :project-id="projectId" />
      </el-tab-pane>

      <el-tab-pane label="项目风险" name="risks">
        <Risks :project-id="projectId" />
      </el-tab-pane>

      <el-tab-pane label="变更记录" name="changes">
        <Changes :project-id="projectId" />
      </el-tab-pane>

      <el-tab-pane label="支出记录" name="expenses">
        <p class="placeholder">支出记录在"经费管理"模块查看</p>
      </el-tab-pane>

      <el-tab-pane label="项目成果" name="outputs">
        <p class="placeholder">成果在"成果管理"模块查看</p>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import BasicInfo from './tabs/BasicInfo.vue'
import Members from './tabs/Members.vue'
import Milestones from './tabs/Milestones.vue'
import Risks from './tabs/Risks.vue'
import Changes from './tabs/Changes.vue'
import { projectApi, ProjectDetailSummary } from '@/api/project'

const route = useRoute()
const projectId = computed(() => route.params.id as string)

const loading = ref(false)
const activeTab = ref('basic')
const project = ref<Partial<ProjectDetailSummary>>({})

onMounted(() => {
  fetchProject()
})

const fetchProject = async () => {
  loading.value = true
  try {
    project.value = await projectApi.detail(projectId.value)
  } catch (error) {
    ElMessage.error('加载项目详情失败')
  } finally {
    loading.value = false
  }
}

const handlePause = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入暂停原因', '暂停项目', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '暂停原因不能为空'
    })

    await projectApi.pause(projectId.value, { reason })
    ElMessage.success('暂停成功')
    fetchProject()
  } catch {
    // 用户取消
  }
}

const handleResume = async () => {
  try {
    await ElMessageBox.confirm('确认恢复该项目吗？', '恢复项目', {
      type: 'warning'
    })

    await projectApi.resume(projectId.value)
    ElMessage.success('恢复成功')
    fetchProject()
  } catch {
    // 用户取消
  }
}

const handleTerminate = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入终止原因', '终止项目', {
      confirmButtonText: '确认终止',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '终止原因不能为空'
    })

    await projectApi.terminate(projectId.value, { reason })
    ElMessage.success('项目已终止')
    fetchProject()
  } catch {
    // 用户取消
  }
}
</script>

<style scoped lang="scss">
.project-detail-page {
  .placeholder {
    padding: 40px 20px;
    text-align: center;
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}
</style>
