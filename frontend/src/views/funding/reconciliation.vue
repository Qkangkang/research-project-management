<template>
  <div class="reconciliation-page">
    <PageHeader title="对账管理" subtitle="支付导入与对账批次管理">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'finance:payment:import'"
          @click="showImportDialog = true"
        >
          <el-icon><Upload /></el-icon>
          导入支付记录
        </el-button>
        <el-button
          type="success"
          v-permission="'finance:reconcile:run'"
          @click="showReconcileDialog = true"
        >
          <el-icon><Checked /></el-icon>
          运行对账
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      title="对账流程：上传支付文件 → 校验 → 应用 → 运行对账 → 处理差异"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px;"
    />

    <el-card v-loading="loading" shadow="never">
      <el-table :data="batches" border stripe>
        <el-table-column prop="batchNo" label="批次号" width="200" show-overflow-tooltip />
        <el-table-column label="对账期间" width="220">
          <template #default="{ row }">
            {{ formatDate(row.periodStart) }} 至 {{ formatDate(row.periodEnd) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getBatchStatusTagType(row.status)">
              {{ getBatchStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalFinanceAmount" label="财务侧金额" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.totalFinanceAmount || 0) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalSystemAmount" label="系统侧金额" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.totalSystemAmount || 0) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'UPLOADED'"
              type="primary"
              text
              size="small"
              @click="handleValidate(row)"
            >
              校验
            </el-button>
            <el-button
              v-if="row.status === 'READY'"
              type="success"
              text
              size="small"
              @click="handleApply(row)"
            >
              应用
            </el-button>
            <el-button
              v-if="row.diffCount && row.diffCount > 0"
              type="warning"
              text
              size="small"
              @click="viewDiffs(row)"
            >
              查看差异 ({{ row.diffCount }})
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 导入支付记录弹窗 -->
    <el-dialog v-model="showImportDialog" title="导入支付记录" width="500px">
      <el-form label-width="100px">
        <el-form-item label="来源类型" required>
          <el-select v-model="importForm.sourceType" placeholder="请选择">
            <el-option label="CSV 文件" value="CSV" />
            <el-option label="Excel 文件" value="EXCEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件" required>
          <el-upload
            v-model:file-list="fileList"
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
            accept=".xlsx,.xls,.csv"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">
                支持 Excel/CSV 格式
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showImportDialog = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 运行对账弹窗 -->
    <el-dialog v-model="showReconcileDialog" title="运行对账" width="500px">
      <el-form label-width="120px">
        <el-form-item label="对账时间范围" required>
          <el-date-picker
            v-model="reconcileForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="财务批次号">
          <el-input
            v-model="reconcileForm.financeBatchNo"
            placeholder="财务来源批次号（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReconcileDialog = false">取消</el-button>
        <el-button type="primary" :loading="reconciling" @click="handleReconcile">
          运行
        </el-button>
      </template>
    </el-dialog>

    <!-- 差异列表弹窗 -->
    <el-dialog v-model="showDiffsDialog" title="对账差异" width="900px">
      <el-table :data="diffs" border>
        <el-table-column prop="diffType" label="差异类型" width="150" />
        <el-table-column prop="expenseId" label="支出ID" width="200" show-overflow-tooltip />
        <el-table-column prop="systemAmount" label="系统侧金额" width="120" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.systemAmount || 0) }}
          </template>
        </el-table-column>
        <el-table-column prop="financeAmount" label="财务侧金额" width="120" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.financeAmount || 0) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OPEN'"
              type="success"
              text
              size="small"
              @click="closeDiff(row)"
            >
              关闭
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Checked } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import { formatMoney } from '@/utils/number'
import { formatDate } from '@/utils/date'
import { useUserStore } from '@/stores/user'
import { fileApi } from '@/api/file'
import {
  fundingApi,
  ReconciliationBatchView,
  ReconciliationDiffView
} from '@/api/funding'

const userStore = useUserStore()

const loading = ref(false)
const batches = ref<ReconciliationBatchView[]>([])
const showImportDialog = ref(false)
const showReconcileDialog = ref(false)
const showDiffsDialog = ref(false)
const fileList = ref<any[]>([])
const importFile = ref<File | null>(null)
const importing = ref(false)
const reconciling = ref(false)
const diffs = ref<ReconciliationDiffView[]>([])
const currentBatchId = ref('')

const importForm = reactive({
  sourceType: 'CSV'
})

const reconcileForm = reactive({
  dateRange: [] as string[],
  financeBatchNo: ''
})

onMounted(() => {
  fetchBatches()
})

const fetchBatches = async () => {
  loading.value = true
  try {
    batches.value = await fundingApi.listReconciliations()
  } catch (error) {
    ElMessage.error('加载批次列表失败')
  } finally {
    loading.value = false
  }
}

const getBatchStatusText = (status: string): string => {
  const map: Record<string, string> = {
    UPLOADED: '已上传',
    READY: '已校验',
    VALIDATED: '已校验',
    APPLYING: '应用中',
    APPLIED: '已应用',
    FAILED: '失败'
  }
  return map[status] || status
}

const getBatchStatusTagType = (status: string) => {
  const map: Record<string, 'info' | 'success' | 'warning' | 'danger'> = {
    UPLOADED: 'info',
    READY: 'warning',
    VALIDATED: 'warning',
    APPLYING: 'warning',
    APPLIED: 'success',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

const handleFileChange = (uploadFile: any) => {
  importFile.value = uploadFile.raw || null
  if (uploadFile.raw) {
    fileList.value = [uploadFile]
  }
}

const submitImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请选择要导入的文件')
    return
  }
  if (!importForm.sourceType) {
    ElMessage.warning('请选择来源类型')
    return
  }
  importing.value = true
  try {
    const uploaded = await fileApi.upload(importFile.value)
    const batch = await fundingApi.uploadPaymentImport(
      uploaded.fileId,
      importForm.sourceType,
      importFile.value
    )
    ElMessage.success(`导入成功，批次号：${batch.batchNo}`)
    showImportDialog.value = false
    fileList.value = []
    importFile.value = null
    await fetchBatches()
  } catch (error: any) {
    ElMessage.error(error?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

const handleValidate = async (batch: ReconciliationBatchView | any) => {
  try {
    await fundingApi.validatePaymentImport(batch.batchId)
    ElMessage.success('校验成功')
    fetchBatches()
  } catch (error: any) {
    ElMessage.error(error?.message || '校验失败')
  }
}

const handleApply = async (batch: ReconciliationBatchView | any) => {
  try {
    await fundingApi.applyPaymentImport(batch.batchId)
    ElMessage.success('应用成功')
    fetchBatches()
  } catch (error: any) {
    ElMessage.error(error?.message || '应用失败')
  }
}

const handleReconcile = async () => {
  if (!reconcileForm.dateRange || reconcileForm.dateRange.length !== 2) {
    ElMessage.warning('请选择对账时间范围')
    return
  }

  reconciling.value = true
  try {
    await fundingApi.runReconciliation({
      period_start: reconcileForm.dateRange[0]!,
      period_end: reconcileForm.dateRange[1]!,
      finance_batch_no: reconcileForm.financeBatchNo || undefined,
      payments: []
    })

    ElMessage.success('对账任务已创建')
    showReconcileDialog.value = false
    fetchBatches()
  } catch (error) {
    ElMessage.error('对账失败')
  } finally {
    reconciling.value = false
  }
}

const viewDiffs = (batch: ReconciliationBatchView | any) => {
  currentBatchId.value = batch.batchId
  diffs.value = batch.diffs || []
  showDiffsDialog.value = true
}

const closeDiff = async (diff: ReconciliationDiffView | any) => {
  if (!userStore.userInfo) {
    ElMessage.warning('登录状态已失效，请重新登录')
    return
  }
  let resolution: string
  try {
    // 差异处理说明必填：作为后续审计追溯的依据，禁止空关闭
    const { value } = await ElMessageBox.prompt(
      '请输入差异处理说明（如：财务补记账 / 科研处更正 / 在途资金）',
      `关闭差异 ${diff.diffType}`,
      {
        confirmButtonText: '确认关闭',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPattern: /^[\s\S]{5,}$/,
        inputErrorMessage: '处理说明不能少于 5 个字符'
      }
    )
    resolution = value.trim()
  } catch {
    return // 用户取消
  }

  try {
    await fundingApi.closeDiff(currentBatchId.value, diff.diffId, {
      resolution,
      ownerId: userStore.userInfo.userId,
      evidenceFileIds: []
    })
    ElMessage.success('差异已关闭')
    await fetchBatches()
    const current = batches.value.find(item => item.batchId === currentBatchId.value)
    diffs.value = current?.diffs || []
  } catch (error: any) {
    ElMessage.error(error?.message || '操作失败')
  }
}
</script>

<style scoped lang="scss">
.reconciliation-page {
  :deep(.el-upload__tip) {
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
