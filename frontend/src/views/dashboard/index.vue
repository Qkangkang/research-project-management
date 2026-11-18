<template>
  <div class="dashboard">
    <h1 class="page-title">工作台</h1>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon primary">
              <el-icon><Folder /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">项目总数</div>
              <div class="stat-value">{{ stats.projects }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon warning">
              <el-icon><Bell /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">待办审批</div>
              <div class="stat-value">{{ stats.tasks }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon success">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">申报总数</div>
              <div class="stat-value">{{ stats.applications }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon danger">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">成果总数</div>
              <div class="stat-value">{{ stats.outputs }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-card shadow="never" class="quick-actions">
      <template #header>
        <span>快捷操作</span>
      </template>
      <el-row :gutter="20">
        <el-col :xs="12" :sm="8" :md="6">
          <div class="action-item" @click="handleAction('/applications/create')">
            <el-icon><Plus /></el-icon>
            <span>新建申报</span>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="action-item" @click="handleAction('/outputs/create')">
            <el-icon><Medal /></el-icon>
            <span>登记成果</span>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="action-item" @click="handleAction('/workflow/todo')">
            <el-icon><Stamp /></el-icon>
            <span>待办审批</span>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="action-item" @click="handleAction('/funding/expense')">
            <el-icon><Wallet /></el-icon>
            <span>经费支出</span>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 最近项目 -->
    <el-card shadow="never" class="recent-projects">
      <template #header>
        <div class="flex-between">
          <span>最近访问的项目</span>
          <el-button link type="primary" @click="handleAction('/projects')">查看全部</el-button>
        </div>
      </template>
      <el-empty v-if="!loading && recentProjects.length === 0" description="暂无项目" />
      <el-table v-else :data="recentProjects" stripe>
        <el-table-column prop="projectNo" label="项目编号" width="150" />
        <el-table-column prop="title" label="项目名称" min-width="200" />
        <el-table-column prop="piName" label="负责人" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewProject(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Folder,
  Bell,
  Document,
  Warning,
  Plus,
  Medal,
  Stamp,
  Wallet
} from '@element-plus/icons-vue'
import { projectApi, type ProjectListItem, type ProjectStatus } from '@/api/project'
import { workflowApi } from '@/api/workflow'
import { applicationApi } from '@/api/application'
import { outputApi } from '@/api/output'

const router = useRouter()

const loading = ref(false)
const recentProjects = ref<ProjectListItem[]>([])
const stats = reactive({
  projects: 0,
  tasks: 0,
  applications: 0,
  outputs: 0
})

onMounted(loadDashboard)

async function loadDashboard() {
  loading.value = true
  try {
    const [projects, tasks, applications, outputs] = await Promise.allSettled([
      projectApi.list({ page: 1, size: 5 }),
      workflowApi.myTasks({ page: 1, size: 1 }),
      applicationApi.list({ page: 1, size: 1 }),
      outputApi.search({ page: 1, size: 1 })
    ])
    if (projects.status === 'fulfilled') {
      recentProjects.value = projects.value.items
      stats.projects = projects.value.total
    }
    if (tasks.status === 'fulfilled') {
      stats.tasks = tasks.value.total
    }
    if (applications.status === 'fulfilled') {
      stats.applications = applications.value.total
    }
    if (outputs.status === 'fulfilled') {
      stats.outputs = outputs.value.total
    }
  } finally {
    loading.value = false
  }
}

const handleAction = (path: string) => {
  router.push(path)
}

const handleViewProject = (id: string) => {
  router.push(`/projects/${id}`)
}

const getStatusText = (status: ProjectStatus): string => {
  const map: Record<ProjectStatus, string> = {
    PENDING_START: '待启动',
    EXECUTING: '执行中',
    SUSPENDED: '暂停',
    ACCEPTING: '验收中',
    COMPLETED: '已完成',
    TERMINATED: '已终止'
  }
  return map[status] || status
}

const getStatusType = (status: ProjectStatus) => {
  const map: Record<ProjectStatus, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    PENDING_START: 'info',
    EXECUTING: 'primary',
    SUSPENDED: 'warning',
    ACCEPTING: 'warning',
    COMPLETED: 'success',
    TERMINATED: 'danger'
  }
  return map[status] || 'info'
}
</script>

<style scoped lang="scss">
.dashboard {
  .page-title {
    font-size: 24px;
    font-weight: 500;
    color: var(--color-text-primary);
    margin-bottom: 20px;
  }

  .stat-cards {
    margin-bottom: 20px;

    .stat-card {
      cursor: pointer;
      transition: transform 0.3s;

      &:hover {
        transform: translateY(-4px);
      }

      .stat-content {
        display: flex;
        align-items: center;
        gap: 16px;

        .stat-icon {
          width: 56px;
          height: 56px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 8px;
          font-size: 24px;
          color: #fff;

          &.primary {
            background-color: var(--color-primary);
          }

          &.warning {
            background-color: var(--color-warning);
          }

          &.success {
            background-color: var(--color-success);
          }

          &.danger {
            background-color: var(--color-danger);
          }
        }

        .stat-info {
          flex: 1;

          .stat-label {
            font-size: 14px;
            color: var(--color-text-secondary);
            margin-bottom: 4px;
          }

          .stat-value {
            font-size: 28px;
            font-weight: 600;
            color: var(--color-text-primary);
          }
        }
      }
    }
  }

  .quick-actions {
    margin-bottom: 20px;

    .action-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 20px;
      border: 1px solid var(--color-border-lighter);
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        border-color: var(--color-primary);
        background-color: var(--color-bg-light);

        .el-icon {
          color: var(--color-primary);
        }
      }

      .el-icon {
        font-size: 32px;
        color: var(--color-text-secondary);
      }

      span {
        font-size: 14px;
        color: var(--color-text-regular);
      }
    }
  }

  .recent-projects {
    .flex-between {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  }
}
</style>
