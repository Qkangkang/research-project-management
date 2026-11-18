<template>
  <div class="report-list-page">
    <PageHeader title="周期报告" subtitle="项目执行报告管理">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'execution:report:edit'"
          :disabled="!projects.length"
          @click="handleCreate"
        >
          <el-icon><Plus /></el-icon>
          创建报告
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="!loadingProjects && projects.length === 0"
      title="暂无项目，请先完成立项后再创建周期报告"
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
            @change="handleProjectChange"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="projectLabel(project)"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="周期类型">
          <el-select v-model="searchForm.periodType" placeholder="请选择" clearable>
            <el-option label="月度" value="MONTH" />
            <el-option label="季度" value="QUARTER" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已退回" value="RETURNED" />
          </el-select>
        </el-form-item>
      </el-col>
    </SearchBar>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe border empty-text="暂无报告">
        <el-table-column prop="periodType" label="周期类型" width="120">
          <template #default="{ row }">
            {{ formatPeriodType(row.periodType) }}
          </template>
        </el-table-column>
        <el-table-column prop="periodKey" label="周期" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="report" />
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="dueDate" label="到期日" width="120">
          <template #default="{ row }">
            {{ row.dueDate ? formatDate(row.dueDate) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              text
              size="small"
              v-permission="'execution:report:submit'"
              @click="handleSubmit(row)"
            >
              提交
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="创建报告" width="520px">
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="100px"
      >
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="createForm.projectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="projectLabel(project)"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="周期类型" prop="periodType">
          <el-select v-model="createForm.periodType" placeholder="请选择周期类型" style="width: 100%">
            <el-option label="月度" value="MONTH" />
            <el-option label="季度" value="QUARTER" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期键" prop="periodKey">
          <el-input
            v-model="createForm.periodKey"
            :placeholder="createForm.periodType === 'QUARTER' ? '如：2026-Q4' : '如：2026-11'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import SearchBar from '@/components/SearchBar/SearchBar.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { formatDate, formatDateTime } from '@/utils/date'
import { projectApi, type ProjectListItem } from '@/api/project'
import {
  executionApi,
  type ProgressReportView,
  type PeriodType,
  type ReportStatus
} from '@/api/execution'

const route = useRoute()

const loading = ref(false)
const loadingProjects = ref(false)
const submitting = ref(false)
const projects = ref<ProjectListItem[]>([])
const tableData = ref<ProgressReportView[]>([])

const searchForm = reactive<{
  projectId: string
  periodType?: PeriodType
  status?: ReportStatus
}>({
  projectId: (route.query.projectId as string) || '',
  periodType: undefined,
  status: undefined
})

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<{
  projectId: string
  periodType: PeriodType
  periodKey: string
}>({
  projectId: '',
  periodType: 'MONTH',
  periodKey: ''
})

const createRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  periodType: [{ required: true, message: '请选择周期类型', trigger: 'change' }],
  periodKey: [{ required: true, message: '请输入周期键', trigger: 'blur' }]
}

const projectLabel = (project: ProjectListItem): string => `${project.projectNo}｜${project.title}`

const formatPeriodType = (type: PeriodType): string => {
  const map: Record<string, string> = { MONTH: '月度', QUARTER: '季度' }
  return map[type] || type
}

onMounted(async () => {
  await loadProjects()
})

const loadProjects = async () => {
  loadingProjects.value = true
  try {
    const result = await projectApi.list({ page: 1, size: 200 })
    projects.value = result.items
    const requested = searchForm.projectId
    const selected = projects.value.find(item => item.id === requested) || projects.value[0]
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
    let rows = await executionApi.listReports(searchForm.projectId)
    if (searchForm.periodType) {
      rows = rows.filter(item => item.periodType === searchForm.periodType)
    }
    if (searchForm.status) {
      rows = rows.filter(item => item.status === searchForm.status)
    }
    tableData.value = rows
  } catch {
    tableData.value = []
    ElMessage.error('加载报告列表失败')
  } finally {
    loading.value = false
  }
}

const handleProjectChange = () => {
  fetchList()
}

const handleSearch = () => {
  fetchList()
}

const handleReset = () => {
  searchForm.periodType = undefined
  searchForm.status = undefined
  fetchList()
}

const handleCreate = () => {
  createForm.projectId = searchForm.projectId || projects.value[0]?.id || ''
  createForm.periodType = 'MONTH'
  createForm.periodKey = ''
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await executionApi.createReport(createForm.projectId, {
      periodType: createForm.periodType,
      periodKey: createForm.periodKey
    })
    ElMessage.success('报告创建成功')
    createDialogVisible.value = false
    if (searchForm.projectId !== createForm.projectId) {
      searchForm.projectId = createForm.projectId
    }
    await fetchList()
  } catch (error: any) {
    ElMessage.error(error?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

const handleSubmit = async (row: any) => {
  const report = row as ProgressReportView
  let summary: string
  try {
    // 报告正文必填：进入审核流程的报告不允许空内容提交
    const { value } = await ElMessageBox.prompt(
      '请输入本周期进展摘要（将随报告一并提交审核）',
      `提交报告 ${report.periodKey}`,
      {
        confirmButtonText: '确认提交',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPattern: /^[\s\S]{10,}$/,
        inputErrorMessage: '进展摘要不能少于 10 个字符'
      }
    )
    summary = value.trim()
  } catch {
    return // 用户取消
  }

  try {
    await executionApi.submitReport(report.reportId, {
      expectedVersion: report.version,
      content: { summary, submittedAt: new Date().toISOString() }
    })
    ElMessage.success('提交成功')
    await fetchList()
  } catch (error: any) {
    ElMessage.error(error?.message || '提交失败')
  }
}
</script>

<style scoped lang="scss">
.report-list-page {
  .empty-project-alert {
    margin-bottom: 16px;
  }
}
</style>
