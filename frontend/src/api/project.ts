import api, { PageQuery, PageResponse, generateIdempotencyKey } from './base'

// 项目状态（与后端 ProjectStatus 枚举对齐）
export type ProjectStatus =
  | 'PENDING_START'
  | 'EXECUTING'
  | 'SUSPENDED'
  | 'ACCEPTING'
  | 'COMPLETED'
  | 'TERMINATED'

// 里程碑状态
export type MilestoneStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'DELAYED'

// 风险等级
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

// 风险状态
export type RiskStatus = 'OPEN' | 'PROCESSING' | 'ESCALATED' | 'CLOSED' | 'CANCELLED'

// 项目列表项
export interface ProjectListItem {
  id: string
  projectNo: string
  title: string
  projectType: string
  piName: string
  deptName: string
  status: ProjectStatus
  startDate?: string
  endDate?: string
  budget?: number
  createdAt: string
}

interface ProjectListRow {
  project_id: string
  project_no: string
  title: string
  project_type: string
  pi_name?: string
  dept_name?: string
  status: ProjectStatus
  start_date?: string
  end_date?: string
  budget_total?: number
  paid_amount?: number
  risk_level?: string
}

// 项目详情
export interface ProjectDetailSummary {
  id: string
  projectNo: string
  title: string
  projectType: string
  piId: string
  piName: string
  deptId: string
  deptName: string
  status: ProjectStatus
  startDate?: string
  endDate?: string
  totalBudget?: number
  description?: string
  memberCount?: number
  milestoneCount?: number
  riskCount?: number
  outputCount?: number
  createdAt: string
  updatedAt: string
}

interface ProjectDetailRow {
  project_id: string
  project_no: string
  title: string
  project_type: string
  pi_id: string
  pi_name?: string
  dept_id: string
  dept_name?: string
  baseline_id?: string
  baseline_version?: number
  status: ProjectStatus
  start_date?: string
  end_date?: string
  total_budget?: number
  member_count?: number
  created_at: string
  updated_at: string
  summary?: {
    milestone_total?: number
    milestone_completed?: number
    budget_total?: number
    output_count?: number
    open_risk_count?: number
  }
}

// 项目成员
export interface ProjectMemberVO {
  id: string
  userId: string
  userName: string
  projectRole: string
  contributionDesc?: string
  validFrom?: string
  validTo?: string
  status: string
}

interface ProjectMemberRow {
  member_id: string
  user_id: string
  display_name?: string
  project_role: string
  contribution_desc?: string
  valid_from?: string
  valid_to?: string
  status: string
}

// 里程碑视图
export interface MilestoneView {
  id: string
  code: string
  title: string
  planDate: string
  currentPlanDate?: string
  status: MilestoneStatus
}

// 风险视图
export interface ProjectRiskView {
  id: string
  ownerId: string
  ownerName?: string
  riskType: string
  title: string
  description?: string
  riskLevel: RiskLevel
  status: RiskStatus
  dueDate?: string
  closedAt?: string
  resolution?: string
  createdAt: string
}

interface MilestoneRow {
  milestoneId: string
  code: string
  title: string
  planDate: string
  currentPlanDate?: string
  status: MilestoneStatus
}

interface ProjectRiskRow {
  riskId: string
  projectId?: string
  ownerId: string
  riskType: string
  title: string
  description?: string
  riskLevel: RiskLevel
  status: RiskStatus
  dueDate?: string
  closedAt?: string
  resolution?: string
  createdAt: string
}

// 变更单创建结果
export interface ChangeRequestCreated {
  changeRequestId: string
  versionId: string
  status: string
}

// 交接发起结果
export interface ProjectHandoverInitiated {
  handoverId: string
  workflowInstanceId?: string
  status: string
}

// 状态变更结果
export interface ProjectStateChanged {
  newStatus: ProjectStatus
  changedAt: string
}

// 列表查询参数
export interface ProjectListQuery extends PageQuery {
  keyword?: string
  projectType?: string
  deptId?: string
  piId?: string
  status?: ProjectStatus
  year?: number
}

// 新增成员请求
export interface AddMemberRequest {
  userId: string
  projectRole: string
  contributionDesc?: string
  validFrom?: string
  validTo?: string
}

// 创建里程碑请求
export interface CreateMilestonesRequest {
  baselineId: string
  milestones: {
    code: string
    title: string
    planDate: string
    ownerId: string
    completionCriteria?: string
  }[]
}

// 创建风险请求
export interface CreateRiskRequest {
  ownerId: string
  riskType: string
  title: string
  description?: string
  riskLevel: RiskLevel
  dueDate?: string
  idempotencyKey?: string
}

// 关闭风险请求
export interface CloseRiskRequest {
  resolution: string
  evidenceFileId?: string
}

// 状态变更请求
export interface StateChangeRequest {
  reason: string
}

// 创建变更请求
export interface CreateChangeRequest {
  changeType: string
  effectiveDate?: string
  payload: Record<string, unknown>
  reason: string
  attachmentFileIds?: string[]
}

// 交接请求
export interface HandoverRequest {
  toUserId: string
  effectiveAt: string
  pendingItems?: {
    type: string
    id: string
    action: string
  }[]
  reason?: string
}

/**
 * 项目 API
 */
export const projectApi = {
  /**
   * 查询项目列表
   */
  async list(params: ProjectListQuery): Promise<PageResponse<ProjectListItem>> {
    const result = await api.get<PageResponse<ProjectListRow>>('/projects', { params })
    return {
      ...result,
      items: result.items.map(row => ({
        id: row.project_id,
        projectNo: row.project_no,
        title: row.title,
        projectType: row.project_type,
        piName: row.pi_name || '-',
        deptName: row.dept_name || '-',
        status: row.status,
        startDate: row.start_date,
        endDate: row.end_date,
        budget: row.budget_total,
        createdAt: ''
      }))
    }
  },

  /**
   * 项目详情
   */
  async detail(projectId: string): Promise<ProjectDetailSummary> {
    const row = await api.get<ProjectDetailRow>(`/projects/${projectId}`)
    return {
      id: row.project_id,
      projectNo: row.project_no,
      title: row.title,
      projectType: row.project_type,
      piId: row.pi_id,
      piName: row.pi_name || '-',
      deptId: row.dept_id,
      deptName: row.dept_name || '-',
      status: row.status,
      startDate: row.start_date,
      endDate: row.end_date,
      totalBudget: row.total_budget ?? row.summary?.budget_total,
      memberCount: row.member_count,
      milestoneCount: row.summary?.milestone_total,
      riskCount: row.summary?.open_risk_count,
      outputCount: row.summary?.output_count,
      createdAt: row.created_at,
      updatedAt: row.updated_at
    }
  },

  /**
   * 项目成员列表
   */
  async members(projectId: string): Promise<ProjectMemberVO[]> {
    const rows = await api.get<ProjectMemberRow[]>(`/projects/${projectId}/members`)
    return rows.map(row => ({
      id: row.member_id,
      userId: row.user_id,
      userName: row.display_name || row.user_id,
      projectRole: row.project_role,
      contributionDesc: row.contribution_desc,
      validFrom: row.valid_from,
      validTo: row.valid_to,
      status: row.status
    }))
  },

  /**
   * 新增项目成员
   */
  addMember(projectId: string, data: AddMemberRequest): Promise<string> {
    return api.post(`/projects/${projectId}/members`, data)
  },

  /**
   * 里程碑列表
   */
  async milestones(projectId: string): Promise<MilestoneView[]> {
    const rows = await api.get<MilestoneRow[]>(`/projects/${projectId}/milestones`)
    return rows.map(row => ({
      id: row.milestoneId,
      code: row.code,
      title: row.title,
      planDate: row.planDate,
      currentPlanDate: row.currentPlanDate,
      status: row.status
    }))
  },

  /**
   * 创建里程碑计划
   */
  createMilestones(projectId: string, data: CreateMilestonesRequest): Promise<MilestoneView[]> {
    return api.post(`/projects/${projectId}/milestones`, data)
  },

  /**
   * 标记里程碑完成
   */
  completeMilestone(
    projectId: string,
    milestoneId: string,
    actualDate?: string
  ): Promise<MilestoneView> {
    return api.post(`/projects/${projectId}/milestones/${milestoneId}/complete`, null, {
      params: { actualDate }
    })
  },

  /**
   * 项目风险列表
   */
  async risks(projectId: string): Promise<ProjectRiskView[]> {
    const rows = await api.get<ProjectRiskRow[]>(`/projects/${projectId}/risks`)
    return rows.map(row => ({
      id: row.riskId,
      ownerId: row.ownerId,
      riskType: row.riskType,
      title: row.title,
      description: row.description,
      riskLevel: row.riskLevel,
      status: row.status,
      dueDate: row.dueDate,
      closedAt: row.closedAt,
      resolution: row.resolution,
      createdAt: row.createdAt
    }))
  },

  /**
   * 登记项目风险
   */
  async createRisk(projectId: string, data: CreateRiskRequest): Promise<ProjectRiskView> {
    const row = await api.post<ProjectRiskRow>(`/projects/${projectId}/risks`, data)
    return {
      id: row.riskId,
      ownerId: row.ownerId,
      riskType: row.riskType,
      title: row.title,
      description: row.description,
      riskLevel: row.riskLevel,
      status: row.status,
      dueDate: row.dueDate,
      closedAt: row.closedAt,
      resolution: row.resolution,
      createdAt: row.createdAt
    }
  },

  /**
   * 关闭项目风险
   */
  async closeRisk(projectId: string, riskId: string, data: CloseRiskRequest): Promise<ProjectRiskView> {
    const row = await api.post<ProjectRiskRow>(`/projects/${projectId}/risks/${riskId}/close`, data)
    return {
      id: row.riskId,
      ownerId: row.ownerId,
      riskType: row.riskType,
      title: row.title,
      description: row.description,
      riskLevel: row.riskLevel,
      status: row.status,
      dueDate: row.dueDate,
      closedAt: row.closedAt,
      resolution: row.resolution,
      createdAt: row.createdAt
    }
  },

  /**
   * 暂停项目
   */
  pause(projectId: string, data: StateChangeRequest): Promise<ProjectStateChanged> {
    return api.post(`/projects/${projectId}/pause`, data)
  },

  /**
   * 恢复项目
   */
  resume(projectId: string): Promise<ProjectStateChanged> {
    return api.post(`/projects/${projectId}/resume`)
  },

  /**
   * 终止项目
   */
  terminate(projectId: string, data: StateChangeRequest): Promise<ProjectStateChanged> {
    return api.post(`/projects/${projectId}/terminate`, data)
  },

  /**
   * 发起项目变更
   */
  createChange(projectId: string, data: CreateChangeRequest): Promise<ChangeRequestCreated> {
    return api.post(`/projects/${projectId}/changes`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  /**
   * 发起负责人交接
   */
  handover(projectId: string, data: HandoverRequest): Promise<ProjectHandoverInitiated> {
    return api.post(`/projects/${projectId}/handover`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  }
}

