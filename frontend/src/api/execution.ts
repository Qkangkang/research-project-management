import api, { PageResponse, generateIdempotencyKey } from './base'

// 报告周期类型（与后端 ProgressReportEntity 对齐）
export type PeriodType = 'MONTH' | 'QUARTER'

// 报告状态
export type ReportStatus = 'DRAFT' | 'SUBMITTED' | 'RETURNED' | 'APPROVED'

// 任务状态（与后端 ProjectTaskEntity 对齐）
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'

// 周期报告视图
export interface ProgressReportView {
  reportId: string
  projectId: string
  periodType: PeriodType
  periodKey: string
  status: ReportStatus
  version: number
  submittedAt?: string
  reviewedAt?: string
  dueDate?: string
  createdAt: string
}

// 报告创建结果
export interface ProgressReportCreated {
  reportId: string
  dueDate?: string
}

// 报告提交结果
export interface ProgressReportSubmitted {
  reportId: string
  newVersion: number
  status: ReportStatus
}

// 报告审核结果
export interface ProgressReportReviewed {
  reportId: string
  status: ReportStatus
  reviewedAt: string
}

// 交付物视图
export interface DeliverableView {
  deliverableId: string
  projectId: string
  fileId: string
  title: string
  milestoneId?: string
  reportVersionId?: string
  sha256?: string
  status: string
  uploadedBy: string
  createdAt: string
}

// 交付物创建结果
export interface DeliverableCreated {
  deliverableId: string
  fileId: string
  sha256: string
  status: string
  version: number
}

// 轻任务视图
export interface ProjectTaskView {
  taskId: string
  projectId: string
  title: string
  description?: string
  assigneeId: string
  status: TaskStatus
  dueDate?: string
  deliverableId?: string
  completedAt?: string
  createdAt: string
  version: number
}

// 任务创建结果
export interface ProjectTaskCreated {
  taskId: string
  status: TaskStatus
  assigneeId: string
  version: number
}

// 任务状态变更结果
export interface ProjectTaskStateChanged {
  taskId: string
  status: TaskStatus
  version: number
}

// 创建报告请求
export interface CreateReportRequest {
  periodType: PeriodType
  periodKey: string
}

// 提交报告请求
export interface SubmitReportRequest {
  expectedVersion: number
  content: Record<string, unknown>
  deliverableFileIds?: string[]
}

// 审核报告请求
export interface ReviewReportRequest {
  action: 'RETURN' | 'APPROVE'
  comment?: string
}

// 上传交付物请求
export interface UploadDeliverableRequest {
  fileId: string
  sha256: string
  title: string
  milestoneId?: string
  reportVersionId?: string
}

// 创建任务请求
export interface CreateTaskRequest {
  title: string
  description?: string
  assigneeId: string
  dueDate?: string
  deliverableId?: string
}

// 完成任务请求
export interface CompleteTaskRequest {
  deliverableId?: string
}

// 取消任务请求
export interface CancelTaskRequest {
  reason: string
}

/**
 * 执行与协作 API
 */
export const executionApi = {
  /**
   * 报告列表
   */
  listReports(projectId: string): Promise<ProgressReportView[]> {
    return api.get(`/projects/${projectId}/progress-reports`)
  },

  /**
   * 创建报告草稿
   */
  createReport(projectId: string, data: CreateReportRequest): Promise<ProgressReportCreated> {
    return api.post(`/projects/${projectId}/progress-reports`, data)
  },

  /**
   * 提交报告
   */
  submitReport(reportId: string, data: SubmitReportRequest): Promise<ProgressReportSubmitted> {
    return api.post(`/progress-reports/${reportId}/submit`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  /**
   * 审核报告（退回或通过）
   */
  reviewReport(reportId: string, data: ReviewReportRequest): Promise<ProgressReportReviewed> {
    return api.post(`/progress-reports/${reportId}/review`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  /**
   * 交付物列表
   */
  listDeliverables(
    projectId: string,
    params?: { milestoneId?: string; page?: number; size?: number }
  ): Promise<PageResponse<DeliverableView>> {
    return api.get(`/projects/${projectId}/deliverables`, { params })
  },

  /**
   * 上传交付物
   */
  uploadDeliverable(
    projectId: string,
    data: UploadDeliverableRequest
  ): Promise<DeliverableCreated> {
    return api.post(`/projects/${projectId}/deliverables`, data)
  },

  /**
   * 创建并分派轻任务
   */
  createTask(projectId: string, data: CreateTaskRequest): Promise<ProjectTaskCreated> {
    return api.post(`/projects/${projectId}/tasks`, data)
  },

  /**
   * 项目任务列表
   */
  listTasks(projectId: string, status?: TaskStatus): Promise<ProjectTaskView[]> {
    return api.get(`/projects/${projectId}/tasks`, {
      params: { status }
    })
  },

  /**
   * 开始任务
   */
  startTask(taskId: string): Promise<ProjectTaskStateChanged> {
    return api.post(`/tasks/${taskId}/start`)
  },

  /**
   * 完成任务
   */
  completeTask(taskId: string, data: CompleteTaskRequest): Promise<ProjectTaskStateChanged> {
    return api.post(`/tasks/${taskId}/complete`, data)
  },

  /**
   * 取消任务
   */
  cancelTask(taskId: string, data: CancelTaskRequest): Promise<ProjectTaskStateChanged> {
    return api.post(`/tasks/${taskId}/cancel`, data)
  }
}
