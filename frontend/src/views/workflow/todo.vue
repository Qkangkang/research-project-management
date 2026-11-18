<template>
  <div class="workflow-todo">
    <PageHeader title="待办事项" />

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="业务类型">
          <el-select
            v-model="queryForm.bizType"
            placeholder="全部"
            clearable
            style="width: 200px"
          >
            <el-option label="申报审批" value="APPLICATION" />
            <el-option label="支出审批" value="EXPENSE" />
            <el-option label="项目变更" value="PROJECT_CHANGE" />
            <el-option label="成果审核" value="OUTPUT_REVIEW" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 待办列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="taskList" stripe>
        <el-table-column prop="taskId" label="任务编号" width="280" show-overflow-tooltip />
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ getBizTypeText(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务对象" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.bizTitle || row.bizId }}
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="审批节点" width="140" />
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">
            <span :class="{ 'text-danger': isOverdue(row.dueDate) }">
              {{ row.dueDate ? formatDateTime(row.dueDate) : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleProcess(row.taskId)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchTasks"
        @current-change="fetchTasks"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import { workflowApi, type WorkflowTaskItem } from '@/api/workflow'
import { applicationApi } from '@/api/application'
import { fundingApi } from '@/api/funding'
import { outputApi } from '@/api/output'
import { formatDateTime } from '@/utils/date'

const router = useRouter()

const loading = ref(false)
const taskList = ref<WorkflowTaskItem[]>([])

const queryForm = reactive({
  bizType: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

onMounted(() => {
  fetchTasks()
})

const fetchTasks = async () => {
  loading.value = true
  try {
    const response = await workflowApi.myTasks({
      page: pagination.page,
      size: pagination.size,
      bizType: queryForm.bizType || undefined
    })
    taskList.value = response.items
    pagination.total = response.total
    await enrichBizTitles(taskList.value)
  } catch (error) {
    ElMessage.error('加载待办列表失败')
  } finally {
    loading.value = false
  }
}

const enrichBizTitles = async (items: WorkflowTaskItem[]) => {
  await Promise.all(items.map(async item => {
    try {
      if (item.bizType === 'APPLICATION') {
        item.bizTitle = (await applicationApi.detail(item.bizId)).title
      } else if (item.bizType === 'EXPENSE') {
        const expense = await fundingApi.expenseDetail(item.bizId)
        item.bizTitle = `${expense.requestNo}｜${expense.purpose}`
      } else if (item.bizType === 'OUTPUT_REVIEW') {
        item.bizTitle = (await outputApi.detail(item.bizId)).title
      } else if (item.bizType === 'PROJECT_CHANGE') {
        item.bizTitle = `项目变更 ${item.bizId.slice(0, 8)}`
      }
    } catch {
      item.bizTitle = undefined
    }
  }))
}

const handleSearch = () => {
  pagination.page = 1
  fetchTasks()
}

const handleReset = () => {
  queryForm.bizType = ''
  pagination.page = 1
  fetchTasks()
}

const handleProcess = (taskId: string) => {
  router.push(`/workflow/detail/${taskId}`)
}

const getBizTypeText = (bizType: string): string => {
  const typeMap: Record<string, string> = {
    APPLICATION: '申报审批',
    EXPENSE: '支出审批',
    PROJECT_CHANGE: '项目变更',
    OUTPUT_REVIEW: '成果审核'
  }
  return typeMap[bizType] || bizType
}

const isOverdue = (dueDate?: string): boolean => {
  if (!dueDate) return false
  return new Date(dueDate) < new Date()
}
</script>

<style scoped lang="scss">
.workflow-todo {
  padding: 20px;

  .search-card {
    margin-bottom: 16px;
  }

  .text-danger {
    color: var(--el-color-danger);
  }
}
</style>
