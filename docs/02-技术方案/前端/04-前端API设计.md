# 科研项目管理系统 — 前端API设计

> 按后端已实际开发的 Controller 组织，类型与路径与源码完全对齐
> ⚠️ **acceptance/reporting/notification** 后端模块尚未开发，对应 API 文件留 TODO 占位
> 本文档依据后端代码（2026-11-18 实际开发状态）更新

---

## 1. 模块清单

| 文件 | 对应后端 Controller | 状态 |
|------|-------------------|------|
| `api/base.ts` | — | ✅ |
| `api/types.ts` | — | ✅ |
| `api/application.ts` | ApplicationController | ✅ |
| `api/project.ts` | ProjectController | ✅ |
| `api/workflow.ts` | WorkflowTaskController | ✅ |
| `api/execution.ts` | ExecutionController | ✅ |
| `api/funding.ts` | BudgetController + ExpenseController + FinanceController | ✅ |
| `api/output.ts` | OutputController | ✅ |
| `api/acceptance.ts` | — | ⚠️ TODO |
| `api/reporting.ts` | — | ⚠️ TODO |
| `api/notification.ts` | — | ⚠️ TODO |
| `api/file.ts` | FileController（后续批次）| ⚠️ 待接口 |

---

## 2. api/base.ts — 基础封装

```typescript
import axios, { AxiosInstance, AxiosError, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken } from '@/utils/auth'

/**
 * 统一响应格式（与后端 Result<T> 完全对齐）
 * code === 'OK' 表示成功；其他值为 RPM-xxx 错误码
 */
export interface ApiResponse<T = any> {
  code: string        // "OK" 或 RPM-xxx 错误码
  message?: string    // 可读消息
  data: T
  trace_id: string
  timestamp?: string  // ISO 8601 带时区
}

/**
 * 分页响应（与后端 PageVO<T> 完全对齐）
 * 注意：字段名是 size，不是 pageSize
 */
export interface PageVO<T> {
  items: T[]
  page: number
  size: number    // 注意：后端字段名 size
  total: number
}

/**
 * 分页请求参数
 * 注意：后端接受 size，不是 pageSize
 */
export interface PageQuery {
  page: number
  size: number    // 注意：后端字段名 size
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

instance.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    if (!config.headers['X-Trace-Id']) {
      config.headers['X-Trace-Id'] =
        `web-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    // 判断条件：code === 'OK'（不是 res.success）
    if (res.code === 'OK') return res.data
    handleBusinessError(res.code, res.message)
    return Promise.reject(new Error(res.message || res.code))
  },
  (error: AxiosError<ApiResponse>) => {
    if (error.response) {
      const { status, data } = error.response
      switch (status) {
        case 401: handleUnauthorized(); break
        case 403: ElMessage.error('权限不足'); break
        case 404: ElMessage.error('请求的资源不存在'); break
        case 409: ElMessage.warning(data?.message || '版本冲突，请刷新后重试'); break
        case 422: ElMessage.error(data?.message || '业务规则不满足'); break
        case 500: ElMessage.error(data?.message || '服务器错误'); break
        default:  ElMessage.error(data?.message || `请求失败 (${status})`)
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

function handleBusinessError(code: string, message?: string) {
  // RPM-OUT-001 是 HTTP 200 的业务警告，不是错误，由调用方处理
  const warningCodes = ['RPM-APPLY-002', 'RPM-PROJ-002', 'RPM-WF-004']
  if (warningCodes.includes(code)) {
    ElMessage.warning(message || '操作提示')
  } else {
    ElMessage.error(message || '操作失败')
  }
}

function handleUnauthorized() {
  import('@/stores/user').then(({ useUserStore }) => {
    useUserStore().clearToken()
  })
  router.push('/login')
  ElMessage.warning('登录已过期，请重新登录')
}

export default instance
```

---

## 3. api/types.ts — 通用类型

```typescript
export interface PageQuery {
  page: number
  size: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface PageVO<T> {
  items: T[]
  page: number
  size: number
  total: number
}
```

---

## 4. api/application.ts — 申报模块

> 后端：`ApplicationController` @ `/api/v1/applications`

```typescript
import request from './base'

// ========== 类型定义 ==========

export interface ApplicationCreated {
  application_id: string
  application_no: string
  status: string
  version: number
}

export interface ApplicationDraftSaved {
  application_id: string
  status: string
  version: number
}

export interface ApplicationSubmission {
  application_id: string
  version_id: string
  version_no: number
  status: string
  workflow_instance_id: string
}

export interface ApplicationWithdrawal {
  application_id: string
  status: string
}

export interface FormalReviewResult {
  application_id: string
  status: string
}

export interface DecisionResult {
  application_id: string
  project_id?: string
  project_no?: string
  baseline_id?: string
}

export interface ExternalRegistrationResult {
  application_id: string
  project_id: string
  project_no: string
}

// ========== API 方法 ==========

export const applicationApi = {
  /**
   * 创建申报草稿
   * POST /api/v1/applications
   */
  create(data: {
    batch_id: string
    project_type: string
    title: string
    dept_id: string
  }): Promise<ApplicationCreated> {
    return request.post('/applications', data)
  },

  /**
   * 保存草稿（乐观锁 expected_version）
   * PUT /api/v1/applications/{id}/draft
   */
  saveDraft(
    id: string,
    data: { expected_version: number; title?: string; form_data: Record<string, any> }
  ): Promise<ApplicationDraftSaved> {
    return request.put(`/applications/${id}/draft`, data)
  },

  /**
   * 提交审批（需 Idempotency-Key 请求头）
   * POST /api/v1/applications/{id}/submit
   */
  submit(
    id: string,
    data: {
      expected_version: number
      comment?: string
      form_data: Record<string, any>
      attachments: Array<{ purpose: string; file_id: string }>
      collaborators?: Array<{ user_id: string; role: string; contribution_desc?: string }>
    },
    idempotencyKey?: string
  ): Promise<ApplicationSubmission> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/applications/${id}/submit`, data, { headers })
  },

  /**
   * 撤回申报
   * POST /api/v1/applications/{id}/withdraw
   */
  withdraw(
    id: string,
    data: { reason?: string },
    idempotencyKey?: string
  ): Promise<ApplicationWithdrawal> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/applications/${id}/withdraw`, data, { headers })
  },

  /**
   * 形式审查
   * POST /api/v1/applications/{id}/formal-review
   */
  formalReview(
    id: string,
    data: {
      version_id: string
      task_id?: string
      expected_task_version?: number
      decision: 'PASS' | 'RETURN' | 'NOT_ACCEPT'
      issues?: Array<{ field: string; message: string }>
      comment?: string
    },
    idempotencyKey?: string
  ): Promise<FormalReviewResult> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/applications/${id}/formal-review`, data, { headers })
  },

  /**
   * 立项决策
   * POST /api/v1/applications/{id}/decision
   */
  decision(
    id: string,
    data: {
      version_id: string
      decision: 'APPROVE' | 'REJECT'
      comment?: string
      approved_start_date?: string
      approved_end_date?: string
    },
    idempotencyKey?: string
  ): Promise<DecisionResult> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/applications/${id}/decision`, data, { headers })
  },

  /**
   * 外部项目登记立项
   * POST /api/v1/applications/external-registration
   */
  externalRegistration(
    data: {
      batch_id: string
      project_type: string
      title: string
      dept_id: string
      pi_id: string
      funder_name: string
      approval_no: string
      contract_no: string
      financial_project_no: string
      approved_amount: number
      start_date: string
      end_date: string
      verified_by: string
      evidence_file_ids: string[]
    },
    idempotencyKey?: string
  ): Promise<ExternalRegistrationResult> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post('/applications/external-registration', data, { headers })
  }
}
```

---

## 5. api/project.ts — 项目模块

> 后端：`ProjectController` @ `/api/v1/projects`

```typescript
import request from './base'
import type { PageVO } from './types'

export interface ProjectListItem {
  id: string
  projectNo: string
  projectName: string
  projectType: string
  piId: string
  piName: string
  deptId: string
  deptName: string
  status: string
  startDate: string
  endDate: string
}

export interface ProjectDetailSummary {
  id: string
  projectNo: string
  projectName: string
  projectType: string
  piId: string
  piName: string
  status: string
  startDate: string
  endDate: string
  baselineId: string
  // 其他页签摘要字段由后端返回
}

export interface ProjectMemberVO {
  id: string
  userId: string
  userName: string
  projectRole: string
  contributionDesc?: string
  validFrom: string
  validTo?: string
  status: string
}

export interface MilestoneView {
  milestoneId: string
  code: string
  title: string
  planDate: string
  currentPlanDate: string
  status: string
}

export interface ProjectRiskView {
  id: string
  title: string
  riskType: string
  riskLevel: string
  ownerId: string
  ownerName: string
  dueDate: string
  status: string
  resolution?: string
}

export interface ProjectStateChanged {
  projectId: string
  status: string
}

export interface ChangeRequestCreated {
  changeId: string
  projectId: string
  changeType: string
  status: string
}

export interface ProjectHandoverInitiated {
  handoverId: string
  projectId: string
  workflowInstanceId: string
}

export const projectApi = {
  /**
   * 项目列表
   * GET /api/v1/projects
   * 注意：分页参数用 size，不是 pageSize
   */
  list(params: {
    keyword?: string
    projectType?: string
    deptId?: string
    piId?: string
    status?: string
    year?: number
    page: number
    size: number
  }): Promise<PageVO<ProjectListItem>> {
    return request.get('/projects', { params })
  },

  /**
   * 项目详情
   * GET /api/v1/projects/{id}
   */
  detail(id: string): Promise<ProjectDetailSummary> {
    return request.get(`/projects/${id}`)
  },

  // --- 成员 ---
  listMembers(projectId: string): Promise<ProjectMemberVO[]> {
    return request.get(`/projects/${projectId}/members`)
  },
  addMember(projectId: string, data: {
    userId: string; projectRole: string; contributionDesc?: string
    validFrom?: string; validTo?: string
  }): Promise<string> {
    return request.post(`/projects/${projectId}/members`, data)
  },

  // --- 里程碑 ---
  listMilestones(projectId: string): Promise<MilestoneView[]> {
    return request.get(`/projects/${projectId}/milestones`)
  },
  createMilestones(projectId: string, data: {
    baselineId: string
    milestones: Array<{ code: string; title: string; planDate: string; ownerId: string; completionCriteria?: string }>
  }): Promise<MilestoneView[]> {
    return request.post(`/projects/${projectId}/milestones`, data)
  },
  /**
   * 标记里程碑完成
   * POST /api/v1/projects/{id}/milestones/{milestoneId}/complete?actualDate=
   */
  completeMilestone(projectId: string, milestoneId: string, actualDate?: string): Promise<MilestoneView> {
    return request.post(
      `/projects/${projectId}/milestones/${milestoneId}/complete`,
      null,
      { params: actualDate ? { actualDate } : {} }
    )
  },

  // --- 风险 ---
  listRisks(projectId: string): Promise<ProjectRiskView[]> {
    return request.get(`/projects/${projectId}/risks`)
  },
  createRisk(projectId: string, data: {
    ownerId: string; riskType: string; title: string; description?: string
    riskLevel: string; dueDate?: string; idempotencyKey?: string
  }): Promise<ProjectRiskView> {
    return request.post(`/projects/${projectId}/risks`, data)
  },
  closeRisk(projectId: string, riskId: string, data: {
    resolution: string; evidenceFileId?: string
  }): Promise<ProjectRiskView> {
    return request.post(`/projects/${projectId}/risks/${riskId}/close`, data)
  },

  // --- 生命周期 ---
  pause(projectId: string, reason: string): Promise<ProjectStateChanged> {
    return request.post(`/projects/${projectId}/pause`, { reason })
  },
  resume(projectId: string): Promise<ProjectStateChanged> {
    return request.post(`/projects/${projectId}/resume`)
  },
  terminate(projectId: string, reason: string): Promise<ProjectStateChanged> {
    return request.post(`/projects/${projectId}/terminate`, { reason })
  },

  // --- 变更 ---
  createChange(projectId: string, data: {
    changeType: string; effectiveDate: string; payload: Record<string, any>
    reason: string; attachmentFileIds?: string[]
  }, idempotencyKey?: string): Promise<ChangeRequestCreated> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/projects/${projectId}/changes`, data, { headers })
  },

  // --- 交接 ---
  handover(projectId: string, data: {
    toUserId: string; effectiveAt: string
    pendingItems?: Array<{ type: string; id: string; action: string }>
    reason: string
  }, idempotencyKey?: string): Promise<ProjectHandoverInitiated> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/projects/${projectId}/handover`, data, { headers })
  }
}
```

---

## 6. api/workflow.ts — 审批模块

> 后端：`WorkflowTaskController` @ `/api/v1/workflow`

```typescript
import request from './base'
import type { PageVO } from './types'

export interface WorkflowTaskItem {
  taskId: string
  bizType: string
  bizId: string
  bizTitle: string
  nodeName: string
  assigneeId: string
  assigneeName: string
  status: string
  arrivedAt: string
  dueDate?: string
}

export interface TaskActionResponse {
  taskId: string
  instanceId: string
  action: string
  status: string
  nextTaskIds?: string[]
}

export interface WorkflowInstanceResponse {
  instanceId: string
  bizType: string
  bizId: string
  status: string
  tasks: WorkflowTaskHistory[]
}

export interface WorkflowTaskHistory {
  taskId: string
  nodeName: string
  assigneeId: string
  assigneeName: string
  status: string
  decision?: string
  comment?: string
  arrivedAt: string
  completedAt?: string
  version: number
}

export const workflowApi = {
  /**
   * 我的待办
   * GET /api/v1/workflow/tasks
   * 注意：分页参数 size（不是 pageSize）
   */
  myTasks(params: {
    bizType?: string; status?: string; page: number; size: number
  }): Promise<PageVO<WorkflowTaskItem>> {
    return request.get('/workflow/tasks', { params })
  },

  /**
   * 处理审批任务（通过/退回/拒绝）
   * POST /api/v1/workflow/tasks/{taskId}/actions
   */
  actTask(taskId: string, data: {
    requestId: string            // UUID，幂等键
    expectedVersion?: number
    action: 'APPROVE' | 'RETURN' | 'REJECT'
    comment?: string
    onBehalfOf?: string          // 代理时的被代理人 UUID
    reassignTo?: string          // 转派时的目标人 UUID
  }): Promise<TaskActionResponse> {
    return request.post(`/workflow/tasks/${taskId}/actions`, data)
  },

  /**
   * 审批轨迹
   * GET /api/v1/workflow/instances/{instanceId}
   */
  getTrail(instanceId: string): Promise<WorkflowInstanceResponse> {
    return request.get(`/workflow/instances/${instanceId}`)
  }
}
```

---

## 7. api/execution.ts — 执行模块

> 后端：`ExecutionController` @ `/api/v1`

```typescript
import request from './base'
import type { PageVO } from './types'

export interface ProgressReportView {
  id: string
  projectId: string
  periodType: string
  periodKey: string
  status: string
  submittedAt?: string
  dueDate?: string
  isLate?: boolean
}

export interface ProgressReportCreated {
  reportId: string
  periodType: string
  periodKey: string
  dueDate: string
}

export interface ProgressReportSubmitted {
  reportId: string
  versionNo: number
  status: string
}

export interface ProgressReportReviewed {
  reportId: string
  status: string
}

export interface DeliverableView {
  id: string
  projectId: string
  fileId: string
  title: string
  milestoneId?: string
  reportVersionId?: string
  sha256: string
  uploadedAt: string
}

export interface DeliverableCreated {
  deliverableId: string
  fileId: string
  sha256: string
}

export interface ProjectTaskView {
  id: string
  projectId: string
  title: string
  description?: string
  assigneeId: string
  assigneeName: string
  dueDate?: string
  status: string
  deliverableId?: string
}

export interface ProjectTaskCreated {
  taskId: string
  projectId: string
  status: string
}

export interface ProjectTaskStateChanged {
  taskId: string
  status: string
}

export const executionApi = {
  // --- 周期报告 ---
  listReports(projectId: string): Promise<ProgressReportView[]> {
    return request.get(`/projects/${projectId}/progress-reports`)
  },
  createReport(projectId: string, data: {
    periodType: string; periodKey: string
  }): Promise<ProgressReportCreated> {
    return request.post(`/projects/${projectId}/progress-reports`, data)
  },
  submitReport(reportId: string, data: {
    expectedVersion: number
    content: Record<string, any>
    deliverableFileIds?: string[]
  }, idempotencyKey?: string): Promise<ProgressReportSubmitted> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/progress-reports/${reportId}/submit`, data, { headers })
  },
  reviewReport(reportId: string, data: {
    action: 'RETURN' | 'APPROVE'; comment?: string
  }, idempotencyKey?: string): Promise<ProgressReportReviewed> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/progress-reports/${reportId}/review`, data, { headers })
  },

  // --- 交付物 ---
  listDeliverables(projectId: string, params: {
    milestoneId?: string; page: number; size: number
  }): Promise<PageVO<DeliverableView>> {
    return request.get(`/projects/${projectId}/deliverables`, { params })
  },
  uploadDeliverable(projectId: string, data: {
    fileId: string; title: string; milestoneId?: string
    reportVersionId?: string; sha256: string
  }): Promise<DeliverableCreated> {
    return request.post(`/projects/${projectId}/deliverables`, data)
  },

  // --- 轻任务 ---
  listTasks(projectId: string, status?: string): Promise<ProjectTaskView[]> {
    return request.get(`/projects/${projectId}/tasks`, {
      params: status ? { status } : {}
    })
  },
  createTask(projectId: string, data: {
    title: string; description?: string; assigneeId: string
    dueDate?: string; deliverableId?: string
  }): Promise<ProjectTaskCreated> {
    return request.post(`/projects/${projectId}/tasks`, data)
  },
  startTask(taskId: string): Promise<ProjectTaskStateChanged> {
    return request.post(`/tasks/${taskId}/start`)
  },
  completeTask(taskId: string, data: { deliverableId?: string }): Promise<ProjectTaskStateChanged> {
    return request.post(`/tasks/${taskId}/complete`, data)
  },
  cancelTask(taskId: string, data: { reason?: string }): Promise<ProjectTaskStateChanged> {
    return request.post(`/tasks/${taskId}/cancel`, data)
  }
}
```

---

## 8. api/funding.ts — 经费模块

> 后端：BudgetController + ExpenseController + FinanceController

```typescript
import request from './base'
import type { PageVO } from './types'

export interface BudgetOverview {
  projectId: string
  versions: BudgetVersionDetail[]
  currentEffectiveVersionId?: string
}

export interface BudgetVersionDetail {
  id: string
  sourceType: string
  controlLevel: string
  effectiveFrom: string
  effectiveTo: string
  totalAmount: number
  status: string
  lines: BudgetLineDetail[]
}

export interface BudgetLineDetail {
  id: string
  subjectCode: string
  subjectName: string
  approvedAmount: number
  reservedAmount: number
  approvedUnpaidAmount: number
  paidAmount: number
  availableAmount: number
}

export interface FundSummary {
  projectId: string
  approvedTotal: number
  reservedTotal: number
  approvedUnpaidTotal: number
  paidTotal: number
  availableTotal: number
  lines: BudgetLineDetail[]
}

export interface ExpenseView {
  id: string
  projectId: string
  budgetVersionId: string
  budgetLineId: string
  amount: number
  purpose: string
  payeeName: string
  payeeBankAccountMasked: string  // 脱敏账号
  payeeBankName: string
  status: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface ExpenseSubmitted {
  expenseId: string
  status: string
  fundEventId: string
  reservedAmount: number
  availableAfter: number
  workflowInstanceId: string
}

export interface ExpenseStatusResult {
  expenseId: string
  status: string
}

export interface PaymentImportBatchView {
  batchId: string
  sourceType: string
  status: string
  totalCount: number
  successCount?: number
  errorCount?: number
}

export interface ReconciliationBatchView {
  batchId: string
  periodStart: string
  periodEnd: string
  financeBatchNo: string
  status: string
  diffCount: number
  createdAt: string
}

export interface ReconciliationDiffView {
  diffId: string
  batchId: string
  externalPaymentNo: string
  externalExpenseNo?: string
  amount: number
  diffType: string
  status: string
  resolution?: string
}

export const fundingApi = {
  // --- 预算 (BudgetController) ---
  listBudgets(projectId: string): Promise<BudgetOverview> {
    return request.get(`/projects/${projectId}/budgets`)
  },
  createBudget(projectId: string, data: {
    sourceType: string; controlLevel: string
    effectiveFrom: string; effectiveTo: string
    lines: Array<{ subjectCode: string; subjectName: string; amount: number }>
    totalAmount: number
  }): Promise<BudgetVersionDetail> {
    return request.post(`/projects/${projectId}/budgets`, data)
  },
  /**
   * 提交预算生效（无请求体）
   * POST /api/v1/projects/{id}/budgets/{budgetId}/submit
   */
  submitBudget(projectId: string, budgetId: string): Promise<BudgetVersionDetail> {
    return request.post(`/projects/${projectId}/budgets/${budgetId}/submit`)
  },
  getFundSummary(projectId: string): Promise<FundSummary> {
    return request.get(`/projects/${projectId}/funds/summary`)
  },

  // --- 支出 (ExpenseController) ---
  createExpense(projectId: string, data: {
    budgetVersionId: string; budgetLineId: string; amount: number; purpose: string
    payeeName: string
    payeeBankAccountMasked: string      // 脱敏账号（前端输入时用户填写的显示值）
    payeeBankAccountEncrypted: string   // 加密账号（前端加密后传输）
    payeeBankName: string
    idempotencyKey?: string
  }): Promise<ExpenseView> {
    return request.post(`/projects/${projectId}/expenses`, data)
  },
  listExpenses(projectId: string): Promise<ExpenseView[]> {
    return request.get(`/projects/${projectId}/expenses`)
  },
  getExpense(expenseId: string): Promise<ExpenseView> {
    return request.get(`/expenses/${expenseId}`)
  },
  submitExpense(expenseId: string, data: {
    expectedVersion: number; budgetVersionId: string; budgetLineId: string
    amount: number; purpose: string; attachmentFileIds: string[]; specialReason?: string
  }, idempotencyKey?: string): Promise<ExpenseSubmitted> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/expenses/${expenseId}/submit`, data, { headers })
  },
  withdrawExpense(expenseId: string, data: {
    expectedVersion: number; reason?: string
  }, idempotencyKey?: string): Promise<ExpenseStatusResult> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/expenses/${expenseId}/withdraw`, data, { headers })
  },
  cancelExpense(expenseId: string, data: {
    expectedVersion: number; reason?: string
  }, idempotencyKey?: string): Promise<ExpenseStatusResult> {
    const headers: Record<string, string> = {}
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
    return request.post(`/expenses/${expenseId}/cancel`, data, { headers })
  },

  // --- 财务导入 (FinanceController) ---
  // 注意：财务回调 POST /integration/finance/payments/callback 是后端专用接口，前端不调用
  uploadPaymentImport(file: File, sourceType = 'CSV'): Promise<PaymentImportBatchView> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('fileId', crypto.randomUUID())
    formData.append('sourceType', sourceType)
    return request.post('/finance/payments/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  validatePaymentImport(batchId: string): Promise<PaymentImportBatchView> {
    return request.post(`/finance/payments/import/${batchId}/validate`)
  },
  applyPaymentImport(batchId: string): Promise<PaymentImportBatchView> {
    return request.post(`/finance/payments/import/${batchId}/apply`)
  },

  // --- 对账 (FinanceController) ---
  listReconciliationBatches(): Promise<ReconciliationBatchView[]> {
    return request.get('/finance/reconciliations')
  },
  createReconciliation(data: {
    period_start: string; period_end: string; finance_batch_no: string
    payments: Array<{ external_payment_no: string; external_expense_no: string; amount: number }>
  }): Promise<ReconciliationBatchView> {
    return request.post('/finance/reconciliations', data)
  },
  closeReconciliationDiff(batchId: string, diffId: string, data: {
    resolution: string; ownerId: string; evidence_file_ids?: string[]
  }): Promise<ReconciliationDiffView> {
    return request.post(`/finance/reconciliations/${batchId}/diffs/${diffId}/close`, data)
  }
}
```

---

## 9. api/output.ts — 成果模块

> 后端：`OutputController` @ `/api/v1`

```typescript
import request from './base'
import type { PageVO } from './types'

export interface OutputCreated {
  outputId: string
  status: string
  versionNo: number
  duplicateWarning?: boolean  // RPM-OUT-001：疑似重复（HTTP 200，非错误）
}

export interface OutputDetailVO {
  id: string
  outputType: string
  title: string
  doi?: string
  patentNo?: string
  completedAt: string
  ownerId: string
  ownerName: string
  deptId: string
  deptName: string
  status: string
  currentVersionNo: number
  evidenceFileIds: string[]
  relations: OutputRelationVO[]
  metadata?: Record<string, any>
}

export interface OutputDraftSaved {
  outputId: string
  status: string
  versionNo: number
}

export interface OutputSubmission {
  outputId: string
  versionId: string
  status: string
}

export interface OutputRelationVO {
  relationId: string
  outputId: string
  projectId: string
  projectName: string
  contributionDesc: string
  attributionRatio?: number
  status: string
}

export interface OutputReviewResult {
  outputId: string
  versionId: string
  status: string
}

export interface OutputWithdrawResult {
  outputId: string
  status: string
  requiresReview: boolean
}

export interface OutputSummaryVO {
  id: string
  outputType: string
  title: string
  doi?: string
  patentNo?: string
  completedAt: string
  ownerName: string
  status: string
}

export interface DuplicateCandidateVO {
  candidateId: string
  title: string
  score: number
  matchFields: string[]
}

export interface OutputLibraryItemVO {
  id: string
  outputType: string
  title: string
  doi?: string
  completedAt: string
  ownerName: string
  deptName: string
}

export interface OutputLibraryDetailVO extends OutputLibraryItemVO {
  metadata?: Record<string, any>
}

export interface OutputStatItem {
  group: string
  count: number
}

export const outputApi = {
  /**
   * 登记成果草稿
   * POST /api/v1/outputs
   * 注意：RPM-OUT-001 是 HTTP 200 的业务警告，不是错误，需检查 duplicateWarning
   */
  create(data: {
    outputType: string; title: string; doi?: string; patentNo?: string
    completedAt: string; ownerId: string; deptId: string
    metadata?: Record<string, any>; evidenceFileIds: string[]
  }): Promise<OutputCreated> {
    return request.post('/outputs', data)
  },

  detail(outputId: string): Promise<OutputDetailVO> {
    return request.get(`/outputs/${outputId}`)
  },

  /**
   * 保存草稿（已审核成果自动进入修订）
   * PUT /api/v1/outputs/{id}/draft
   */
  saveDraft(outputId: string, data: {
    title?: string; doi?: string; patentNo?: string; completedAt?: string
    metadata?: Record<string, any>; evidenceFileIds?: string[]
  }): Promise<OutputDraftSaved> {
    return request.put(`/outputs/${outputId}/draft`, data)
  },

  /**
   * 显式修订（生成新版本，旧版本与旧审核意见保留）
   * POST /api/v1/outputs/{id}/revisions
   */
  revise(outputId: string, data: {
    title?: string; doi?: string; patentNo?: string; completedAt?: string
    metadata?: Record<string, any>; evidenceFileIds?: string[]
  }): Promise<OutputDraftSaved> {
    return request.post(`/outputs/${outputId}/revisions`, data)
  },

  submit(outputId: string): Promise<OutputSubmission> {
    return request.post(`/outputs/${outputId}/submit`)
  },

  relate(outputId: string, data: {
    projectId: string; contributionDesc: string; attributionRatio?: number
  }): Promise<OutputRelationVO> {
    return request.post(`/outputs/${outputId}/relations`, data)
  },

  revokeRelation(outputId: string, relationId: string): Promise<OutputRelationVO> {
    return request.delete(`/outputs/${outputId}/relations/${relationId}`)
  },

  review(outputId: string, data: {
    versionId: string; decision: 'APPROVE' | 'REJECT'; comment?: string
  }): Promise<OutputReviewResult> {
    return request.post(`/outputs/${outputId}/review`, data)
  },

  /**
   * 疑似重复候选
   * GET /api/v1/outputs/duplicates?outputId=
   */
  duplicates(outputId: string): Promise<PageVO<DuplicateCandidateVO>> {
    return request.get('/outputs/duplicates', { params: { outputId } })
  },

  withdraw(outputId: string, data: { reason: string }): Promise<OutputWithdrawResult> {
    return request.post(`/outputs/${outputId}/withdraw`, data)
  },

  reviewWithdraw(outputId: string, data: {
    approved: boolean; comment?: string
  }): Promise<OutputWithdrawResult> {
    return request.post(`/outputs/${outputId}/withdraw/review`, data)
  },

  /**
   * 成果检索（我的 + 已公开）
   * GET /api/v1/outputs
   */
  search(params: {
    q?: string; outputType?: string; status?: string
    deptId?: string; year?: number; page: number; size: number
  }): Promise<PageVO<OutputSummaryVO>> {
    return request.get('/outputs', { params })
  },

  librarySearch(params: {
    q?: string; outputType?: string; deptId?: string
    year?: number; page: number; size: number
  }): Promise<PageVO<OutputLibraryItemVO>> {
    return request.get('/outputs/library/search', { params })
  },

  libraryDetail(outputId: string): Promise<OutputLibraryDetailVO> {
    return request.get(`/outputs/${outputId}/library-detail`)
  },

  stats(params: {
    groupBy: 'TYPE' | 'YEAR' | 'DEPT'; deptId?: string; year?: number
  }): Promise<OutputStatItem[]> {
    return request.get('/outputs/library/stats', { params })
  },

  projectOutputs(projectId: string): Promise<OutputSummaryVO[]> {
    return request.get(`/projects/${projectId}/outputs`)
  }
}
```

---

## 10. 后端尚未开发的模块（TODO 占位）

```typescript
// api/acceptance.ts — rpm-acceptance 仅有空 pom.xml，尚未开发
// 预留类型，待后端开发后补充 API 方法
export const acceptanceApi: Record<string, never> = {} // TODO: 后端待开发

// api/reporting.ts — rpm-reporting 仅有空 pom.xml，尚未开发
export const reportingApi: Record<string, never> = {} // TODO: 后端待开发

// api/notification.ts — rpm-notification 仅有空 pom.xml，尚未开发
export const notificationApi: Record<string, never> = {} // TODO: 后端待开发
```

---

> 本文档依据后端代码（2026-11-18 实际开发状态）更新
