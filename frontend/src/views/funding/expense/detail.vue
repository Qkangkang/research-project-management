<template>
  <div v-loading="loading" class="expense-detail-page">
    <PageHeader
      :title="`支出详情 - ${expense.purpose || ''}`"
      :subtitle="`支出编号：${expense.requestNo || '-'}`"
    >
      <template #actions>
        <StatusTag v-if="expense.status" :status="expense.status" domain="expense" size="large" />
        <el-button
          v-if="expense.status === 'IN_REVIEW'"
          type="warning"
          v-permission="'fund:expense:withdraw'"
          @click="handleWithdraw"
        >
          撤回
        </el-button>
        <el-button
          v-if="expense.status === 'DRAFT' || expense.status === 'IN_REVIEW'"
          type="danger"
          v-permission="'fund:expense:cancel'"
          @click="handleCancel"
        >
          取消
        </el-button>
        <el-button @click="goBack">
          <el-icon><Back /></el-icon>
          返回
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never" style="margin-bottom: 16px;">
          <template #header>
            <span>基本信息</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="支出单号" :span="2">
              {{ expense.requestNo || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="项目">
              {{ projectDisplay }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <StatusTag v-if="expense.status" :status="expense.status" domain="expense" />
            </el-descriptions-item>
            <el-descriptions-item label="金额" :span="2">
              <span class="amount">¥ {{ formatMoney(expense.amount || 0) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="用途说明" :span="2">
              {{ expense.purpose || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="预算版本">
              {{ budgetVersionDisplay }}
            </el-descriptions-item>
            <el-descriptions-item label="预算科目">
              {{ budgetLineDisplay }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <span>收款方信息</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="收款方名称" :span="2">
              {{ expense.payeeName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="收款账号" :span="2">
              {{ expense.accountMasked || '-' }}
              <el-tag size="small" type="info" style="margin-left: 8px;">已脱敏</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" style="margin-bottom: 16px;">
          <template #header>
            <span>处理记录</span>
          </template>
          <el-timeline>
            <el-timeline-item v-if="expense.createdAt" timestamp="创建时间" :hollow="true">
              {{ formatDateTime(expense.createdAt) }}
            </el-timeline-item>
            <el-timeline-item v-if="expense.submittedAt" timestamp="提交时间" type="primary">
              {{ formatDateTime(expense.submittedAt) }}
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <span>版本信息</span>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="当前版本">
              v{{ expense.version || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDateTime(expense.createdAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="expense.status === 'DRAFT'"
            title="草稿状态，请完善支出信息后提交审核"
            type="info"
            :closable="false"
            class="status-alert"
          />
          <el-alert
            v-if="expense.status === 'IN_REVIEW'"
            title="当前支出正在审批流程中"
            type="warning"
            :closable="false"
            class="status-alert"
          />
          <el-alert
            v-if="expense.status === 'APPROVED_UNPAID'"
            title="审批已通过，等待财务支付"
            type="success"
            :closable="false"
            class="status-alert"
          />
          <el-alert
            v-if="expense.status === 'PAID'"
            title="支付已完成"
            type="success"
            :closable="false"
            class="status-alert"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { formatMoney } from '@/utils/number'
import { formatDateTime } from '@/utils/date'
import { fundingApi, ExpenseView, BudgetOverview } from '@/api/funding'
import { projectApi, type ProjectListItem } from '@/api/project'

const route = useRoute()
const router = useRouter()

const expenseId = computed(() => route.params.id as string)
const loading = ref(false)
const projects = ref<ProjectListItem[]>([])
const budgetOverview = ref<BudgetOverview | null>(null)
const expense = ref<Partial<ExpenseView>>({
  expenseId: '',
  requestNo: '',
  projectId: '',
  budgetVersionId: '',
  amount: 0,
  purpose: '',
  payeeName: '',
  accountMasked: '',
  status: 'DRAFT',
  version: 0,
  createdAt: ''
})

onMounted(async () => {
  await fetchExpense()
  await loadContexts()
})

const loadContexts = async () => {
  try {
    const result = await projectApi.list({ page: 1, size: 200 })
    projects.value = result.items
  } catch {
    projects.value = []
  }
  if (!expense.value.projectId) return
  try {
    budgetOverview.value = await fundingApi.budgets(expense.value.projectId)
  } catch {
    budgetOverview.value = null
  }
}

const projectDisplay = computed(() => {
  const project = projects.value.find(item => item.id === expense.value.projectId)
  return project ? `${project.projectNo}｜${project.title}` : (expense.value.projectId || '-')
})

const budgetVersionDisplay = computed(() => {
  const version = budgetOverview.value?.versions?.find(
    item => item.budgetVersionId === expense.value.budgetVersionId
  )
  return version
    ? `V${version.versionNo}（${version.status}）`
    : (expense.value.budgetVersionId || '-')
})

const budgetLineDisplay = computed(() => {
  const line = budgetOverview.value?.effectiveBudget?.lines?.find(
    item => item.budgetLineId === expense.value.budgetLineId
  )
  return line ? `${line.subjectCode}｜${line.subjectName}` : (expense.value.budgetLineId || '-')
})

async function fetchExpense() {
  loading.value = true
  try {
    expense.value = await fundingApi.expenseDetail(expenseId.value)
  } finally {
    loading.value = false
  }
}

const handleWithdraw = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入撤回原因', '撤回支出', {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '撤回原因不能为空'
    })
    await fundingApi.withdrawExpense(expenseId.value, {
      expectedVersion: expense.value.version || 0,
      reason
    })
    ElMessage.success('撤回成功')
    await fetchExpense()
  } catch {
    // 用户取消
  }
}

const handleCancel = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消支出', {
      confirmButtonText: '确认取消',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '取消原因不能为空'
    })
    await fundingApi.cancelExpense(expenseId.value, {
      expectedVersion: expense.value.version || 0,
      reason
    })
    ElMessage.success('已取消')
    router.back()
  } catch {
    // 用户取消
  }
}

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="scss">
.expense-detail-page {
  .amount {
    font-size: 18px;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  .status-alert {
    margin-top: 16px;
  }
}
</style>
