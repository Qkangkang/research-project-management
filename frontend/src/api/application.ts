import api, { PageQuery, PageResponse, generateIdempotencyKey } from './base'

// 申报状态
export type ApplicationStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'SUPPLEMENT'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'NOT_ACCEPTED'
  | 'CLOSED'

// 申报列表项
export interface ApplicationSummary {
  id: string
  applicationNo: string
  title: string
  batchId?: string
  batchName?: string
  projectType: string
  applicantId?: string
  applicantName: string
  deptId?: string
  deptName: string
  status: ApplicationStatus
  currentVersionId?: string
  version: number
  createdAt: string
  updatedAt: string
}

// 申报详情
export interface ApplicationDetail extends ApplicationSummary {
  formData: Record<string, unknown>
  attachments: { purpose: string; fileId: string; fileName: string }[]
  workflowInstanceId?: string
  projectId?: string
}

// 附件用途
export interface AttachmentInput {
  purpose: string
  fileId: string
}

// 新建申报选项
export interface ApplicationBatchOption {
  id: string
  batch_no: string
  name: string
  year: number
  status: string
  start_at?: string
  end_at?: string
}

export interface ApplicationProjectTypeOption {
  project_type: string
  template_id: string
  version_no: number
  form_schema?: string
}

export interface ApplicationFormFieldOption {
  label: string
  value: string | number | boolean
}

export interface ApplicationFormField {
  key: string
  title: string
  type: 'string' | 'number' | 'boolean' | 'date' | 'unknown'
  required: boolean
  maxLength?: number
  minimum?: number
  maximum?: number
  enumOptions?: ApplicationFormFieldOption[]
}

interface ApplicationFormFieldSchema {
  type?: string
  title?: string
  required?: boolean
  maxLength?: number
  minimum?: number
  maximum?: number
  enum?: Array<string | number | boolean>
  format?: string
}

export const parseApplicationFormSchema = (schemaText?: string): ApplicationFormField[] => {
  if (!schemaText?.trim()) return []

  let schema: { required?: string[]; properties?: Record<string, ApplicationFormFieldSchema> }
  try {
    schema = JSON.parse(schemaText)
  } catch {
    return []
  }

  const requiredKeys = Array.isArray(schema.required) ? schema.required : []
  return Object.entries(schema.properties || {}).map(([key, fieldSchema]) => {
    const rawType = String(fieldSchema?.type || 'string').toLowerCase()
    const format = String(fieldSchema?.format || '').toLowerCase()
    const type: ApplicationFormField['type'] = rawType === 'integer' || rawType === 'number'
      ? 'number'
      : rawType === 'boolean'
        ? 'boolean'
        : format === 'date' || format === 'date-time'
          ? 'date'
          : rawType === 'string' ? 'string' : 'unknown'

    return {
      key,
      title: fieldSchema?.title || key,
      type,
      required: requiredKeys.includes(key) || fieldSchema?.required === true,
      maxLength: fieldSchema?.maxLength,
      minimum: fieldSchema?.minimum,
      maximum: fieldSchema?.maximum,
      enumOptions: Array.isArray(fieldSchema?.enum)
        ? fieldSchema.enum.map(item => ({ label: String(item), value: item }))
        : undefined
    }
  })
}

export interface ApplicationOptionsView {
  batches: ApplicationBatchOption[]
  project_types: ApplicationProjectTypeOption[]
}

// 创建申报请求
export interface CreateApplicationRequest {
  batchId: string
  projectType: string
  title: string
  deptId: string
}

// 保存草稿请求
export interface UpdateDraftRequest {
  title?: string
  formData: Record<string, unknown>
  expectedVersion: number
}

// 提交申报请求
export interface SubmitApplicationRequest {
  comment?: string
  formData: Record<string, unknown>
  attachments: AttachmentInput[]
  collaborators?: { userId: string; role: string; contributionDesc?: string }[]
  expectedVersion: number
}

// 撤回请求
export interface WithdrawRequest {
  reason: string
}

// 形式审查请求
export interface FormalReviewRequest {
  action: 'ACCEPT' | 'REJECT'
  comment?: string
}

// 批准决策请求
export interface DecisionRequest {
  action: 'APPROVE' | 'REJECT'
  comment?: string
  approvedProjectType?: string
  approvedBudget?: number
}

// 列表查询参数
export interface ApplicationListQuery extends PageQuery {
  keyword?: string
  projectType?: string
  status?: ApplicationStatus
  batchId?: string
}

interface ApplicationListRow {
  application_id: string
  application_no: string
  title?: string
  batch_id?: string
  batch_name?: string
  project_type: string
  applicant_id?: string
  applicant_name?: string
  dept_id?: string
  dept_name?: string
  status: ApplicationStatus
  current_version_id?: string
  version: number
  created_at: string
  updated_at: string
}

interface ApplicationDetailRow extends ApplicationListRow {
  form_data?: Record<string, unknown>
  attachments?: { purpose: string; file_id: string; file_name?: string }[]
  workflow_instance_id?: string
  project_id?: string
}

interface ApplicationCreatedRow {
  application_id: string
  application_no: string
  status: ApplicationStatus
  version: number
}

interface ApplicationDraftSavedRow {
  application_id: string
  status: ApplicationStatus
  version: number
  draft_version_id: string
  draft_version_no: number
}

interface ApplicationSubmittedRow {
  application_id: string
  version_id: string
  version_no: number
  status: ApplicationStatus
  workflow_instance_id?: string
}

const toSummary = (row: ApplicationListRow): ApplicationSummary => ({
  id: row.application_id,
  applicationNo: row.application_no,
  title: row.title || '',
  batchId: row.batch_id,
  batchName: row.batch_name,
  projectType: row.project_type,
  applicantId: row.applicant_id,
  applicantName: row.applicant_name || '',
  deptId: row.dept_id,
  deptName: row.dept_name || '',
  status: row.status,
  currentVersionId: row.current_version_id,
  version: row.version ?? 0,
  createdAt: row.created_at,
  updatedAt: row.updated_at
})

/**
 * 申报 API
 */
export const applicationApi = {
  /**
   * 查询申报列表
   */
  async list(params: ApplicationListQuery): Promise<PageResponse<ApplicationSummary>> {
    const result = await api.get<PageResponse<ApplicationListRow>>('/applications', {
      params: {
        keyword: params.keyword || undefined,
        projectType: params.projectType || undefined,
        status: params.status || undefined,
        batchId: params.batchId || undefined,
        page: params.page,
        size: params.size
      }
    })
    return { ...result, items: result.items.map(toSummary) }
  },

  /**
   * 新建申报选项（开放批次、有效项目类型）
   */
  options(): Promise<ApplicationOptionsView> {
    return api.get('/applications/options')
  },

  /**
   * 查询申报详情
   */
  async detail(id: string): Promise<ApplicationDetail> {
    const row = await api.get<ApplicationDetailRow>(`/applications/${id}`)
    return {
      ...toSummary(row),
      formData: row.form_data || {},
      attachments: (row.attachments || []).map(item => ({
        purpose: item.purpose,
        fileId: item.file_id,
        fileName: item.file_name || item.file_id
      })),
      workflowInstanceId: row.workflow_instance_id,
      projectId: row.project_id
    }
  },

  /**
   * 创建申报草稿
   */
  async create(data: CreateApplicationRequest): Promise<{ id: string; applicationNo: string; status: ApplicationStatus; version: number }> {
    const row = await api.post<ApplicationCreatedRow>('/applications', {
      batch_id: data.batchId,
      project_type: data.projectType,
      title: data.title,
      dept_id: data.deptId
    })
    return {
      id: row.application_id,
      applicationNo: row.application_no,
      status: row.status,
      version: row.version
    }
  },

  /**
   * 保存草稿
   */
  async saveDraft(id: string, data: UpdateDraftRequest): Promise<{ version: number; draftVersionId: string; draftVersionNo: number }> {
    const row = await api.put<ApplicationDraftSavedRow>(`/applications/${id}/draft`, {
      expected_version: data.expectedVersion,
      title: data.title,
      form_data: data.formData
    })
    return {
      version: row.version,
      draftVersionId: row.draft_version_id,
      draftVersionNo: row.draft_version_no
    }
  },

  /**
   * 提交申报
   */
  async submit(id: string, data: SubmitApplicationRequest): Promise<{ versionId: string; versionNo: number; status: ApplicationStatus; workflowInstanceId?: string }> {
    const row = await api.post<ApplicationSubmittedRow>(`/applications/${id}/submit`, {
      expected_version: data.expectedVersion,
      comment: data.comment,
      form_data: data.formData,
      attachments: data.attachments.map(item => ({
        purpose: item.purpose,
        file_id: item.fileId
      })),
      collaborators: (data.collaborators || []).map(item => ({
        user_id: item.userId,
        role: item.role,
        contribution_desc: item.contributionDesc
      }))
    }, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
    return {
      versionId: row.version_id,
      versionNo: row.version_no,
      status: row.status,
      workflowInstanceId: row.workflow_instance_id
    }
  },

  /**
   * 撤回申报
   */
  withdraw(id: string, data: WithdrawRequest): Promise<void> {
    return api.post(`/applications/${id}/withdraw`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  /**
   * 形式审查
   */
  formalReview(id: string, data: FormalReviewRequest): Promise<void> {
    return api.post(`/applications/${id}/formal-review`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  /**
   * 批准决策
   */
  decision(id: string, data: DecisionRequest): Promise<{ projectId?: string }> {
    return api.post(`/applications/${id}/decision`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  }
}
