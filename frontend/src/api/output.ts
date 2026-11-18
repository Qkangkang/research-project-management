import api, { PageQuery, PageResponse } from './base'

// 成果类型
export type OutputType = 'PAPER' | 'PATENT' | 'SOFTWARE' | 'AWARD' | 'STANDARD' | 'REPORT' | 'OTHER'

// 成果状态
export type OutputStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'REVOKED'

// 审核决策
export type ReviewDecision = 'APPROVE' | 'REJECT' | 'RETURN'

// 成果摘要（列表项）
export interface OutputSummaryVO {
  id: string
  outputType: OutputType
  title: string
  doi?: string
  patentNo?: string
  completedAt: string
  ownerId?: string
  deptId?: string
  ownerName: string
  deptName: string
  status: OutputStatus
  currentVersion: number
  createdAt: string
}

interface OutputSummaryRow {
  outputId: string
  outputType: OutputType
  title: string
  doi?: string
  patentNo?: string
  status: OutputStatus
  ownerId: string
  deptId: string
  completedAt: string
}

interface OutputDetailRow extends OutputSummaryRow {
  currentVersionId?: string
  versionNo?: number
  metadata?: Record<string, unknown>
  evidence?: { fileId: string; evidenceType?: string; sha256?: string }[]
  reviews?: { reviewId: string; reviewerId: string; decision: string; comment?: string; reviewedAt?: string }[]
  relations?: {
    relationId: string
    projectId: string
    contributionDesc?: string
    attributionRatio?: number
    status: string
  }[]
}

// 成果详情
export interface OutputDetailVO extends OutputSummaryVO {
  metadata?: Record<string, unknown>
  evidenceFileIds?: string[]
  relatedProjects?: {
    relationId?: string
    projectId: string
    projectNo: string
    projectTitle: string
    contributionDesc?: string
    attributionRatio?: number
  }[]
  reviewComment?: string
  reviewedAt?: string
}

// 成果创建结果
export interface OutputCreated {
  outputId: string
  status: OutputStatus
  version: number
  duplicateCandidates?: DuplicateCandidateVO[]
  warningCode?: string
  warningMessage?: string
}

// 草稿保存结果
export interface OutputDraftSaved {
  outputId: string
  status: OutputStatus
  version: number
}

// 提交结果
export interface OutputSubmission {
  outputId: string
  status: OutputStatus
  fixedVersion: number
  submittedAt: string
}

// 成果关联
export interface OutputRelationVO {
  relationId: string
  outputId: string
  projectId: string
  contributionDesc?: string
  attributionRatio?: number
  status: string
}

// 审核结果
export interface OutputReviewResult {
  outputId: string
  versionId: string
  decision: ReviewDecision
  status: OutputStatus
  reviewedAt: string
}

// 疑似重复候选
export interface DuplicateCandidateVO {
  candidateId: string
  title: string
  doi?: string
  patentNo?: string
  similarity: number
  reason: string
}

// 撤回结果
export interface OutputWithdrawResult {
  outputId: string
  status: OutputStatus
  needsApproval: boolean
}

// 成果库列表项
export interface OutputLibraryItemVO {
  id: string
  outputType: OutputType
  title: string
  doi?: string
  patentNo?: string
  completedAt: string
  ownerName: string
  deptName: string
}

// 成果库详情
export interface OutputLibraryDetailVO extends OutputLibraryItemVO {
  metadata?: Record<string, unknown>
  relatedProjectCount: number
}

// 统计项
export interface OutputStatItem {
  groupKey: string
  groupLabel: string
  count: number
}

// 创建成果请求
export interface CreateOutputRequest {
  outputType: OutputType
  title: string
  doi?: string
  patentNo?: string
  completedAt: string
  ownerId: string
  deptId: string
  metadata?: Record<string, unknown>
  evidenceFileIds?: string[]
}

// 保存草稿请求
export interface SaveOutputDraftRequest {
  title: string
  doi?: string
  patentNo?: string
  completedAt: string
  metadata?: Record<string, unknown>
  evidenceFileIds?: string[]
}

// 关联成果请求
export interface RelateOutputRequest {
  projectId: string
  contributionDesc?: string
  attributionRatio?: number
}

// 审核成果请求
export interface ReviewOutputRequest {
  versionId: string
  decision: ReviewDecision
  comment?: string
}

// 撤回成果请求
export interface WithdrawOutputRequest {
  reason: string
}

// 撤回审核请求
export interface WithdrawReviewRequest {
  approved: boolean
  comment?: string
}

// 成果检索查询
export interface OutputSearchQuery extends PageQuery {
  q?: string
  outputType?: OutputType
  status?: OutputStatus
  deptId?: string
  year?: number
}

// 成果库检索查询
export interface LibrarySearchQuery extends PageQuery {
  q?: string
  outputType?: OutputType
  deptId?: string
  year?: number
}

// 统计查询
export interface OutputStatsQuery {
  groupBy: 'TYPE' | 'YEAR' | 'DEPT'
  deptId?: string
  year?: number
}

const toSummary = (row: OutputSummaryRow): OutputSummaryVO => ({
  id: row.outputId,
  outputType: row.outputType,
  title: row.title,
  doi: row.doi,
  patentNo: row.patentNo,
  completedAt: row.completedAt,
  ownerId: row.ownerId,
  deptId: row.deptId,
  ownerName: '',
  deptName: '',
  status: row.status,
  currentVersion: 0,
  createdAt: ''
})

// 成果写操作响应行（output 模块 DTO 为 camelCase 序列化）
interface OutputMutationRow {
  outputId: string
  status: OutputStatus
  versionNo: number
  warningCode?: string
  warningMessage?: string
}

/**
 * 成果管理 API
 */
export const outputApi = {
  /**
   * 登记成果草稿
   */
  async create(data: CreateOutputRequest): Promise<OutputCreated> {
    const row = await api.post<OutputMutationRow>('/outputs', {
      output_type: data.outputType,
      title: data.title,
      doi: data.doi,
      patent_no: data.patentNo,
      completed_at: data.completedAt,
      owner_id: data.ownerId,
      dept_id: data.deptId,
      metadata: data.metadata,
      evidence_file_ids: data.evidenceFileIds
    })
    return {
      outputId: row.outputId,
      status: row.status,
      version: row.versionNo,
      warningCode: row.warningCode,
      warningMessage: row.warningMessage
    }
  },

  /**
   * 成果详情
   */
  async detail(outputId: string): Promise<OutputDetailVO> {
    const row = await api.get<OutputDetailRow>(`/outputs/${outputId}`)
    const reviews = row.reviews || []
    const latestReview = reviews.length ? reviews[reviews.length - 1] : undefined
    return {
      ...toSummary(row),
      currentVersion: row.versionNo || 0,
      ownerId: row.ownerId,
      deptId: row.deptId,
      metadata: row.metadata,
      evidenceFileIds: (row.evidence || []).map(item => item.fileId),
      relatedProjects: (row.relations || [])
        .filter(item => item.status === 'ACTIVE')
        .map(item => ({
          relationId: item.relationId,
          projectId: item.projectId,
          projectNo: '',
          projectTitle: item.projectId,
          contributionDesc: item.contributionDesc,
          attributionRatio: item.attributionRatio
        })),
      reviewComment: latestReview?.comment,
      reviewedAt: latestReview?.reviewedAt
    }
  },

  /**
   * 保存成果草稿（非草稿状态自动进入修订）
   */
  async saveDraft(outputId: string, data: SaveOutputDraftRequest): Promise<OutputDraftSaved> {
    const row = await api.put<OutputMutationRow>(`/outputs/${outputId}/draft`, {
      title: data.title,
      doi: data.doi,
      patent_no: data.patentNo,
      completed_at: data.completedAt,
      metadata: data.metadata,
      evidence_file_ids: data.evidenceFileIds
    })
    return { outputId: row.outputId, status: row.status, version: row.versionNo }
  },

  /**
   * 修订成果（生成新版本）
   */
  async revise(outputId: string, data: SaveOutputDraftRequest): Promise<OutputDraftSaved> {
    const row = await api.post<OutputMutationRow>(`/outputs/${outputId}/revisions`, {
      title: data.title,
      doi: data.doi,
      patent_no: data.patentNo,
      completed_at: data.completedAt,
      metadata: data.metadata,
      evidence_file_ids: data.evidenceFileIds
    })
    return { outputId: row.outputId, status: row.status, version: row.versionNo }
  },

  /**
   * 提交成果审核
   */
  async submit(outputId: string): Promise<OutputSubmission> {
    const row = await api.post<OutputMutationRow>(`/outputs/${outputId}/submit`)
    return {
      outputId: row.outputId,
      status: row.status,
      fixedVersion: row.versionNo,
      submittedAt: ''
    }
  },

  /**
   * 关联成果到项目
   */
  relate(outputId: string, data: RelateOutputRequest): Promise<OutputRelationVO> {
    return api.post(`/outputs/${outputId}/relations`, {
      project_id: data.projectId,
      contribution_desc: data.contributionDesc,
      attribution_ratio: data.attributionRatio
    })
  },

  /**
   * 撤销成果-项目关联
   */
  revokeRelation(outputId: string, relationId: string): Promise<OutputRelationVO> {
    return api.delete(`/outputs/${outputId}/relations/${relationId}`)
  },

  /**
   * 审核成果
   */
  review(outputId: string, data: ReviewOutputRequest): Promise<OutputReviewResult> {
    return api.post(`/outputs/${outputId}/review`, {
      version_id: data.versionId,
      decision: data.decision,
      comment: data.comment
    })
  },

  /**
   * 查询疑似重复候选
   */
  duplicates(outputId: string): Promise<PageResponse<DuplicateCandidateVO>> {
    return api.get('/outputs/duplicates', {
      params: { outputId }
    })
  },

  /**
   * 发起成果撤回申请
   */
  withdraw(outputId: string, data: WithdrawOutputRequest): Promise<OutputWithdrawResult> {
    return api.post(`/outputs/${outputId}/withdraw`, data)
  },

  /**
   * 科研管理审核撤回申请
   */
  reviewWithdraw(outputId: string, data: WithdrawReviewRequest): Promise<OutputWithdrawResult> {
    return api.post(`/outputs/${outputId}/withdraw/review`, data)
  },

  /**
   * 「我的成果 + 已公开成果」检索
   */
  async search(params: OutputSearchQuery): Promise<PageResponse<OutputSummaryVO>> {
    const result = await api.get<PageResponse<OutputSummaryRow>>('/outputs', { params })
    return { ...result, items: result.items.map(toSummary) }
  },

  /**
   * 全院成果库检索
   */
  librarySearch(params: LibrarySearchQuery): Promise<PageResponse<OutputLibraryItemVO>> {
    return api.get('/outputs/library/search', { params })
  },

  /**
   * 全院成果库详情（仅元数据）
   */
  libraryDetail(outputId: string): Promise<OutputLibraryDetailVO> {
    return api.get(`/outputs/${outputId}/library-detail`)
  },

  /**
   * 全院成果统计（按类型/年度/部门分组）
   */
  stats(params: OutputStatsQuery): Promise<OutputStatItem[]> {
    return api.get('/outputs/library/stats', { params })
  },

  /**
   * 项目成果列表
   */
  projectOutputs(projectId: string): Promise<OutputSummaryVO[]> {
    return api.get(`/projects/${projectId}/outputs`)
  }
}
