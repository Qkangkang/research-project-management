<template>
  <div class="expense-page">
    <PageHeader title="支出管理" subtitle="项目支出列表与申请">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'fund:expense:create'"
          :disabled="!projects.length"
          @click="openCreateDrawer"
        >
          <el-icon><Plus /></el-icon>
          新建支出
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="!loadingProjects && projects.length === 0"
      title="暂无项目，请先完成立项后再创建支出"
      type="warning"
      :closable="false"
      show-icon
      class="empty-project-alert"
    />

    <SearchBar v-model="searchForm" :loading="loading" @search="handleSearch" @reset="handleReset">
      <el-col :span="8">
        <el-form-item label="项目">
          <el-select
            v-model="searchForm.projectId"
            placeholder="请选择项目"
            filterable
            clearable
            :loading="loadingProjects"
            style="width: 100%"
            @change="handleSearchProjectChange"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="`${project.projectNo}｜${project.title}`"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="IN_REVIEW" />
            <el-option label="待支付" value="APPROVED_UNPAID" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
      </el-col>
    </SearchBar>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe border empty-text="暂无支出">
        <el-table-column prop="requestNo" label="支出编号" width="200" show-overflow-tooltip />
        <el-table-column prop="purpose" label="用途" min-width="200" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额（元）" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="payeeName" label="收款方" width="150" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="expense" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              text
              size="small"
              v-permission="'fund:expense:submit'"
              @click="handleSubmit(row)"
            >
              提交
            </el-button>
            <el-button
              v-if="row.status === 'IN_REVIEW'"
              type="warning"
              text
              size="small"
              v-permission="'fund:expense:withdraw'"
              @click="handleWithdraw(row)"
            >
              撤回
            </el-button>
            <el-button
              v-if="['DRAFT', 'IN_REVIEW'].includes(row.status)"
              type="danger"
              text
              size="small"
              v-permission="'fund:expense:cancel'"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="showCreateDrawer" title="新建支出申请" size="620px">
      <el-form :model="createForm" label-width="140px">
        <el-form-item label="项目" required>
          <el-select
            v-model="createForm.projectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
            @change="handleCreateProjectChange"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="`${project.projectNo}｜${project.title}`"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预算版本" required>
          <el-select
            v-model="createForm.budgetVersionId"
            placeholder="请选择预算版本"
            :loading="loadingBudgets"
            :disabled="!createForm.projectId"
            style="width: 100%"
            @change="handleBudgetVersionChange"
          >
            <el-option
              v-for="version in budgetVersions"
              :key="version.budgetVersionId"
              :label="`V${version.versionNo}（${version.status}，${formatMoney(version.totalAmount)} 元）`"
              :value="version.budgetVersionId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预算科目" required>
          <el-select
            v-model="createForm.budgetLineId"
            placeholder="请选择预算科目"
            :disabled="!createForm.budgetVersionId"
            :loading="loadingBudgets"
            style="width: 100%"
          >
            <el-option
              v-for="line in budgetLines"
              :key="line.budgetLineId"
              :label="`${line.subjectName}（可用 ${formatMoney(line.availableAmount)} 元）`"
              :value="line.budgetLineId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="金额（元）" required>
          <el-input-number
            v-model="createForm.amount"
            :precision="2"
            :min="0"
            :max="999999999"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="用途说明" required>
          <el-input v-model="createForm.purpose" type="textarea" :rows="3" placeholder="请输入支出用途" />
        </el-form-item>
        <el-form-item label="收款方名称" required>
          <el-input v-model="createForm.payeeName" placeholder="请输入收款方名称" />
        </el-form-item>
        <el-form-item label="收款账号" required>
          <el-input v-model="createForm.payeeBankAccountEncrypted" placeholder="请输入收款账号" />
        </el-form-item>
        <el-form-item label="收款银行" required>
          <el-input v-model="createForm.payeeBankName" placeholder="请输入收款银行" />
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="createForm.attachmentIds" :max-count="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDrawer = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">保存草稿</el-button>
        <el-button type="success" :loading="creating" @click="handleCreateAndSubmit">直接提交</el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="showSubmitDialog"
      title="提交支出"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="支出提交前必须上传合同、事项说明等证明附件"
        type="warning"
        show-icon
        :closable="false"
        class="submit-attachment-alert"
      />
      <el-descriptions :column="1" border class="submit-expense-summary">
        <el-descriptions-item label="支出编号">{{ submitForm.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="用途说明">{{ submitForm.purpose }}</el-descriptions-item>
        <el-descriptions-item label="金额（元）">{{ formatMoney(submitForm.amount) }}</el-descriptions-item>
      </el-descriptions>
      <el-form-item label="证明附件" required>
        <FileUpload
          v-model="submitForm.attachmentIds"
          :max-count="5"
          accept=".pdf,.doc,.docx,.jpg,.png,.xlsx,.zip"
        />
      </el-form-item>
      <template #footer>
        <el-button @click="showSubmitDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleConfirmSubmit">
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import SearchBar from '@/components/SearchBar/SearchBar.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import FileUpload from '@/components/FileUpload/FileUpload.vue'
import { formatMoney } from '@/utils/number'
import { formatDateTime } from '@/utils/date'
import { projectApi, type ProjectListItem } from '@/api/project'
import {
  fundingApi,
  type ExpenseView,
  type BudgetVersionSummary,
  type BudgetLineDetail
} from '@/api/funding'

const router = useRouter()

const loading = ref(false)
const loadingProjects = ref(false)
const loadingBudgets = ref(false)
const creating = ref(false)
const projects = ref<ProjectListItem[]>([])
const tableData = ref<ExpenseView[]>([])
const showCreateDrawer = ref(false)
const showSubmitDialog = ref(false)
const submitting = ref(false)
const budgetVersions = ref<BudgetVersionSummary[]>([])
const budgetLines = ref<BudgetLineDetail[]>([])

const searchForm = reactive<{ projectId: string; status?: string }>({
  projectId: '',
  status: undefined
})

const createForm = reactive({
  projectId: '',
  budgetVersionId: '',
  budgetLineId: '',
  amount: null as number | null,
  purpose: '',
  payeeName: '',
  payeeBankAccountMasked: '',
  payeeBankAccountEncrypted: '',
  payeeBankName: '',
  attachmentIds: [] as string[]
})

const submitForm = reactive({
  expenseId: '',
  requestNo: '',
  version: 0,
  budgetVersionId: '',
  budgetLineId: '',
  amount: 0,
  purpose: '',
  attachmentIds: [] as string[]
})

onMounted(() => {
  loadProjects()
})

const loadProjects = async () => {
  loadingProjects.value = true
  try {
    const result = await projectApi.list({ page: 1, size: 200 })
    projects.value = result.items
    const selected = projects.value.find(item => item.id === searchForm.projectId) || projects.value[0]
    if (selected) {
      searchForm.projectId = selected.id
      await fetchList()
    }
  } catch {
    projects.value = []
  } finally {
    loadingProjects.value = false
  }
}

const fetchList = async () => {
  if (!searchForm.projectId) {
    tableData.value = []
    return
  }
  loading.value = true
  try {
    const res = await fundingApi.listExpenses(searchForm.projectId)
    tableData.value = searchForm.status
      ? res.filter(item => item.status === searchForm.status)
      : res
  } catch {
    tableData.value = []
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearchProjectChange = () => {
  fetchList()
}

const handleSearch = () => {
  fetchList()
}

const handleReset = () => {
  searchForm.status = undefined
  fetchList()
}

const openCreateDrawer = async () => {
  createForm.projectId = searchForm.projectId || projects.value[0]?.id || ''
  createForm.budgetVersionId = ''
  createForm.budgetLineId = ''
  createForm.amount = null
  createForm.purpose = ''
  createForm.payeeName = ''
  createForm.payeeBankAccountMasked = ''
  createForm.payeeBankAccountEncrypted = ''
  createForm.payeeBankName = ''
  createForm.attachmentIds = []
  showCreateDrawer.value = true
  await loadBudgetOptions(createForm.projectId)
}

const loadBudgetOptions = async (projectId: string) => {
  if (!projectId) {
    budgetVersions.value = []
    budgetLines.value = []
    return
  }
  loadingBudgets.value = true
  try {
    const overview = await fundingApi.budgets(projectId)
    budgetVersions.value = overview.versions || []
    const effective = overview.effectiveBudget
    const selected = budgetVersions.value.find(item => item.status === 'EFFECTIVE') || budgetVersions.value[0]
    createForm.budgetVersionId = selected?.budgetVersionId || ''
    budgetLines.value = effective && effective.budgetVersionId === createForm.budgetVersionId
      ? effective.lines || []
      : []
    createForm.budgetLineId = budgetLines.value[0]?.budgetLineId || ''
  } catch {
    budgetVersions.value = []
    budgetLines.value = []
  } finally {
    loadingBudgets.value = false
  }
}

const handleCreateProjectChange = async () => {
  createForm.budgetVersionId = ''
  createForm.budgetLineId = ''
  await loadBudgetOptions(createForm.projectId)
}

const handleBudgetVersionChange = async () => {
  createForm.budgetLineId = ''
  if (!createForm.projectId) return
  try {
    const overview = await fundingApi.budgets(createForm.projectId)
    const effective = overview.effectiveBudget
    budgetLines.value = effective && effective.budgetVersionId === createForm.budgetVersionId
      ? effective.lines || []
      : []
    createForm.budgetLineId = budgetLines.value[0]?.budgetLineId || ''
  } catch {
    budgetLines.value = []
  }
}

const validateCreateForm = (requireAttachments = false): boolean => {
  if (!createForm.projectId) return fail('请选择项目')
  if (!createForm.budgetVersionId) return fail('请选择预算版本')
  if (!createForm.budgetLineId) return fail('请选择预算科目')
  if (!createForm.amount || createForm.amount <= 0) return fail('请输入正确的支出金额')
  if (!createForm.purpose.trim()) return fail('请输入用途说明')
  if (!createForm.payeeName.trim()) return fail('请输入收款方名称')
  if (!createForm.payeeBankAccountEncrypted.trim()) return fail('请输入收款账号')
  if (!createForm.payeeBankName.trim()) return fail('请输入收款银行')
  if (requireAttachments && createForm.attachmentIds.length === 0) {
    return fail('请上传合同、事项说明等必填附件')
  }
  return true
}

const fail = (message: string): false => {
  ElMessage.warning(message)
  return false
}

const buildCreatePayload = () => ({
  budgetVersionId: createForm.budgetVersionId,
  budgetLineId: createForm.budgetLineId,
  amount: createForm.amount || 0,
  purpose: createForm.purpose,
  payeeName: createForm.payeeName,
  payeeBankAccountMasked: maskAccount(createForm.payeeBankAccountEncrypted),
  payeeBankAccountEncrypted: createForm.payeeBankAccountEncrypted,
  payeeBankName: createForm.payeeBankName
})

const handleCreate = async () => {
  if (!validateCreateForm()) return
  creating.value = true
  try {
    await fundingApi.createExpense(createForm.projectId, buildCreatePayload())
    ElMessage.success('草稿保存成功')
    showCreateDrawer.value = false
    if (searchForm.projectId !== createForm.projectId) {
      searchForm.projectId = createForm.projectId
    }
    await fetchList()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    creating.value = false
  }
}

const handleCreateAndSubmit = async () => {
  if (!validateCreateForm(true)) return
  creating.value = true
  try {
    const res = await fundingApi.createExpense(createForm.projectId, buildCreatePayload())
    await fundingApi.submitExpense(res.expenseId, {
      expectedVersion: res.version,
      budgetVersionId: createForm.budgetVersionId,
      budgetLineId: createForm.budgetLineId,
      amount: createForm.amount || 0,
      purpose: createForm.purpose,
      attachmentFileIds: createForm.attachmentIds
    })
    ElMessage.success('提交成功')
    showCreateDrawer.value = false
    if (searchForm.projectId !== createForm.projectId) {
      searchForm.projectId = createForm.projectId
    }
    await fetchList()
  } catch (error: any) {
    ElMessage.error(error?.message || '提交失败')
  } finally {
    creating.value = false
  }
}

const handleView = (row: any) => {
  router.push(`/funding/expenses/${row.expenseId}`)
}

const handleSubmit = async (row: any) => {
  submitForm.expenseId = row.expenseId
  submitForm.requestNo = row.requestNo
  submitForm.version = row.version
  submitForm.budgetVersionId = row.budgetVersionId
  submitForm.budgetLineId = row.budgetLineId
  submitForm.amount = row.amount
  submitForm.purpose = row.purpose
  submitForm.attachmentIds = []
  showSubmitDialog.value = true
}

const handleConfirmSubmit = async () => {
  if (submitForm.attachmentIds.length === 0) {
    ElMessage.warning('请上传合同、事项说明等必填附件')
    return
  }

  submitting.value = true
  try {
    await fundingApi.submitExpense(submitForm.expenseId, {
      expectedVersion: submitForm.version,
      budgetVersionId: submitForm.budgetVersionId,
      budgetLineId: submitForm.budgetLineId,
      amount: submitForm.amount,
      purpose: submitForm.purpose,
      attachmentFileIds: submitForm.attachmentIds
    })
    ElMessage.success('提交成功')
    showSubmitDialog.value = false
    await fetchList()
  } catch (error: any) {
    ElMessage.error(error?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

const handleWithdraw = async (row: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入撤回原因', '撤回支出', {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '撤回原因不能为空'
    })
    await fundingApi.withdrawExpense(row.expenseId, { reason, expectedVersion: row.version })
    ElMessage.success('撤回成功')
    await fetchList()
  } catch {
    // 用户取消
  }
}

const handleCancel = async (row: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消支出', {
      confirmButtonText: '确认取消',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '取消原因不能为空'
    })
    await fundingApi.cancelExpense(row.expenseId, { reason, expectedVersion: row.version })
    ElMessage.success('已取消')
    await fetchList()
  } catch {
    // 用户取消
  }
}

const maskAccount = (account: string): string => {
  if (!account) return ''
  if (account.length <= 4) return '*'.repeat(account.length)
  return `${'*'.repeat(Math.max(account.length - 4, 4))}${account.slice(-4)}`
}
</script>

<style scoped lang="scss">
.expense-page {
  .empty-project-alert {
    margin-bottom: 16px;
  }
}

.submit-attachment-alert {
  margin-bottom: 16px;
}

.submit-expense-summary {
  margin-bottom: 16px;
}
</style>
