<template>
  <div class="budget-page">
    <PageHeader title="预算管理" subtitle="项目预算版本与资金汇总">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'fund:budget:edit'"
          @click="showCreateDialog = true"
        >
          <el-icon><Plus /></el-icon>
          创建预算
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" style="margin-bottom: 16px;">
      <el-form inline>
        <el-form-item label="项目">
          <el-select
            v-model="projectId"
            placeholder="请选择项目"
            filterable
            clearable
            :loading="loadingProjects"
            style="width: 360px;"
            @change="handleProjectChange"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="`${project.projectNo}｜${project.title}`"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadBudget">
            查询
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="fundSummary" v-loading="loading" shadow="never" style="margin-bottom: 16px;">
      <template #header>
        <span>资金汇总</span>
      </template>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-statistic title="批准额" :value="fundSummary.totalApprovedAmount" :precision="2" suffix="元" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="在审占用" :value="fundSummary.totalReservedAmount" :precision="2" suffix="元" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已支付" :value="fundSummary.totalPaidAmount" :precision="2" suffix="元" />
        </el-col>
        <el-col :span="6">
          <el-statistic
            title="可用余额"
            :value="fundSummary.totalAvailableAmount"
            :precision="2"
            suffix="元"
            :value-style="{ color: fundSummary.totalAvailableAmount < 0 ? '#F56C6C' : '#67C23A' }"
          />
        </el-col>
      </el-row>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <template #header>
        <span>预算版本列表</span>
      </template>

      <el-table v-if="budgetVersions.length > 0" :data="budgetVersions" border stripe>
        <el-table-column prop="budgetVersionId" label="版本ID" width="200" show-overflow-tooltip />
        <el-table-column prop="versionNo" label="版本号" width="100" />
        <el-table-column prop="controlLevel" label="控制级别" width="120" />
        <el-table-column prop="effectiveFrom" label="生效开始" width="120">
          <template #default="{ row }">
            {{ formatDate(row.effectiveFrom) }}
          </template>
        </el-table-column>
        <el-table-column prop="effectiveTo" label="生效结束" width="120">
          <template #default="{ row }">
            {{ row.effectiveTo ? formatDate(row.effectiveTo) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="预算总额" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.totalAmount) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              text
              size="small"
              @click="viewBudgetLines(row)"
            >
              查看科目
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              text
              size="small"
              v-permission="'fund:budget:submit'"
              @click="submitBudget(row)"
            >
              提交
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="暂无预算版本" />
    </el-card>

    <!-- 预算科目明细弹窗 -->
    <el-dialog v-model="showLinesDialog" title="预算科目明细" width="900px">
      <el-table :data="currentLines" border>
        <el-table-column prop="subjectCode" label="科目代码" width="150" />
        <el-table-column prop="subjectName" label="科目名称" min-width="200" />
        <el-table-column prop="amount" label="预算金额" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="approvedAmount" label="批准金额" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.approvedAmount) }}
          </template>
        </el-table-column>
        <el-table-column prop="reservedAmount" label="在审占用" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.reservedAmount) }}
          </template>
        </el-table-column>
        <el-table-column prop="approvedUnpaidAmount" label="已批未付" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.approvedUnpaidAmount) }}
          </template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已支付" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.paidAmount) }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 创建预算弹窗 -->
    <el-dialog v-model="showCreateDialog" title="创建预算版本" width="800px">
      <el-form label-width="120px">
        <el-form-item label="来源类型" required>
          <el-select v-model="createForm.sourceType" placeholder="请选择">
            <el-option label="拨款" value="GRANT" />
            <el-option label="合同" value="CONTRACT" />
            <el-option label="配套" value="MATCHING" />
            <el-option label="调整" value="ADJUSTMENT" />
            <el-option label="迁移" value="MIGRATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="控制级别" required>
          <el-select v-model="createForm.controlLevel" placeholder="请选择">
            <el-option label="阻断" value="BLOCK" />
            <el-option label="警告" value="WARN" />
            <el-option label="审批" value="APPROVAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效开始" required>
          <el-date-picker
            v-model="createForm.effectiveFrom"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="生效结束">
          <el-date-picker
            v-model="createForm.effectiveTo"
            type="date"
            placeholder="选择日期（可选）"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="预算科目" required>
          <BudgetEditor v-model="createForm.lines" @total-change="handleTotalChange" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateBudget">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import BudgetEditor from '@/components/BudgetEditor/BudgetEditor.vue'
import { formatMoney } from '@/utils/number'
import { formatDate } from '@/utils/date'
import { projectApi, type ProjectListItem } from '@/api/project'
import {
  fundingApi,
  BudgetLineDetail,
  BudgetVersionDetail,
  BudgetVersionSummary,
  ControlLevel,
  FundSummary
} from '@/api/funding'

const loading = ref(false)
const loadingProjects = ref(false)
const projects = ref<ProjectListItem[]>([])
const projectId = ref('')
const budgetVersions = ref<BudgetVersionSummary[]>([])
const effectiveBudget = ref<BudgetVersionDetail | null>(null)
const fundSummary = ref<FundSummary | null>(null)
const showLinesDialog = ref(false)
const currentLines = ref<BudgetLineDetail[]>([])
const showCreateDialog = ref(false)
const creating = ref(false)
const createTotal = ref(0)

const createForm = reactive({
  sourceType: '',
  controlLevel: 'BLOCK' as ControlLevel,
  effectiveFrom: '',
  effectiveTo: '',
  lines: [] as any[]
})

onMounted(async () => {
  loadingProjects.value = true
  try {
    const result = await projectApi.list({ page: 1, size: 200 })
    projects.value = result.items
    const requested = projectId.value
    const selected = projects.value.find(item => item.id === requested) || projects.value[0]
    if (selected) {
      projectId.value = selected.id
      await loadBudget()
    }
  } catch {
    projects.value = []
  } finally {
    loadingProjects.value = false
  }
})

const handleProjectChange = () => {
  if (projectId.value) {
    loadBudget()
  } else {
    budgetVersions.value = []
    effectiveBudget.value = null
    fundSummary.value = null
  }
}

const loadBudget = async () => {
  if (!projectId.value) {
    ElMessage.warning('请选择项目')
    return
  }

  loading.value = true
  try {
    const overview = await fundingApi.budgets(projectId.value)
    budgetVersions.value = overview.versions || []
    effectiveBudget.value = overview.effectiveBudget || null

    fundSummary.value = await fundingApi.fundSummary(projectId.value)
  } catch (error) {
    ElMessage.error('加载预算失败')
  } finally {
    loading.value = false
  }
}

const viewBudgetLines = (budget: any) => {
  const effective = effectiveBudget.value
  if (!effective || effective.budgetVersionId !== budget.budgetVersionId) {
    ElMessage.info('仅当前生效版本提供科目明细')
    return
  }
  currentLines.value = effective.lines || []
  showLinesDialog.value = true
}

const submitBudget = async (budget: any) => {
  try {
    await ElMessageBox.confirm('确认提交该预算版本吗？', '提交确认', {
      type: 'warning'
    })

    await fundingApi.submitBudget(projectId.value, budget.budgetVersionId)
    ElMessage.success('提交成功')
    loadBudget()
  } catch {
    // 用户取消
  }
}

const handleTotalChange = (total: number) => {
  createTotal.value = total
}

const handleCreateBudget = async () => {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  creating.value = true
  try {
    await fundingApi.createBudget(projectId.value, {
      sourceType: createForm.sourceType,
      controlLevel: createForm.controlLevel,
      effectiveFrom: createForm.effectiveFrom,
      effectiveTo: createForm.effectiveTo,
      totalAmount: createTotal.value,
      lines: createForm.lines.map(line => ({
        subjectCode: line.subjectCode,
        subjectName: line.subjectName,
        amount: line.amount || 0
      }))
    })

    ElMessage.success('预算创建成功')
    showCreateDialog.value = false
    loadBudget()
  } catch (error) {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped lang="scss">
.budget-page {
  :deep(.el-statistic__content) {
    font-size: 24px;
  }
}
</style>
