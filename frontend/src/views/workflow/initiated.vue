<template>
  <div class="workflow-initiated">
    <PageHeader title="我发起的" subtitle="本人发起的审批实例及当前进展" />

    <el-card shadow="never" v-loading="loading">
      <el-table :data="instanceList" stripe>
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
        <el-table-column label="实例状态" width="110">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="workflow" />
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.startedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">
            {{ row.endedAt ? formatDateTime(row.endedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewTrail(row.instanceId)">轨迹</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="fetchInstances"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { workflowApi, type InitiatedInstanceItem } from '@/api/workflow'
import { applicationApi } from '@/api/application'
import { fundingApi } from '@/api/funding'
import { outputApi } from '@/api/output'
import { formatDateTime } from '@/utils/date'

const router = useRouter()

const loading = ref(false)
const instanceList = ref<InitiatedInstanceItem[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

onMounted(() => {
  fetchInstances()
})

const fetchInstances = async () => {
  loading.value = true
  try {
    const response = await workflowApi.myInitiated({
      page: pagination.page,
      size: pagination.size
    })
    instanceList.value = response.items
    pagination.total = response.total
    await enrichBizTitles(instanceList.value)
  } catch (error) {
    ElMessage.error('加载发起列表失败')
  } finally {
    loading.value = false
  }
}

// 与待办/已办页同一套业务标题回填口径
const enrichBizTitles = async (items: InitiatedInstanceItem[]) => {
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
  fetchInstances()
}

const handleViewTrail = (instanceId: string) => {
  router.push(`/workflow/trail/${instanceId}`)
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
</script>

<style scoped lang="scss">
.workflow-initiated {
  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
