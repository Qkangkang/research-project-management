import api, { PageQuery, PageResponse } from './base'

// 审批动作（与后端 ApprovalAction 枚举一致）
export type ApprovalAction = 'APPROVED' | 'RETURNED' | 'REJECTED'

// 任务状态
export type TaskStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'APPROVED'
  | 'RETURNED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'TIMEOUT'
  | 'EXCEPTION'

// 工作流任务列表项
export interface WorkflowTaskItem {
  taskId: string
  instanceId: string
  nodeKey: string
  nodeName: string
  assigneeId?: string
  version: number
  taskName: string
  bizType: string
  bizId: string
  bizTitle?: string
  assigneeName: string
  status: TaskStatus
  createdAt: string
  dueDate?: string
}

interface WorkflowTaskRow {
  task_id: string
  instance_id: string
  biz_type: string
  biz_id: string
  node_key: string
  node_name: string
  assignee_id?: string
  status: TaskStatus
  due_at?: string
  round_no?: number
  version: number
}

interface TaskActionRow {
  task_id: string
  instance_id: string
  task_status: TaskStatus
  instance_status: string
  next_task_ids?: string[]
}

interface WorkflowTrailTaskRow {
  task_id: string
  node_name: string
  assignee_id?: string
  status: TaskStatus
  due_at?: string
}

interface WorkflowInstanceRow {
  instance_id: string
  biz_type: string
  biz_id: string
  status: string
  started_at: string
  ended_at?: string
  tasks: WorkflowTrailTaskRow[]
}

// 我发起的实例行（GET /workflow/instances/initiated）
interface InitiatedInstanceRow {
  instance_id: string
  biz_type: string
  biz_id: string
  status: string
  started_at: string
  ended_at?: string
}

// 我发起的实例项（前端视图）
export interface InitiatedInstanceItem {
  instanceId: string
  bizType: string
  bizId: string
  status: string
  startedAt: string
  endedAt?: string
  bizTitle?: string
}

// 任务处理请求
export interface TaskActionRequest {
  requestId: string
  expectedVersion?: number
  action: ApprovalAction
  comment?: string
  onBehalfOf?: string
  reassignTo?: string
}

// 任务处理响应
export interface TaskActionResponse {
  taskId: string
  newStatus: TaskStatus
  instanceStatus: string
  nextTaskIds?: string[]
}

// 审批轨迹节点
export interface ApprovalTrailNode {
  taskId: string
  taskName: string
  assigneeName: string
  action?: ApprovalAction
  comment?: string
  completedAt?: string
  status: TaskStatus
}

// 工作流实例响应
export interface WorkflowInstanceResponse {
  instanceId: string
  bizType: string
  bizId: string
  instanceStatus: string
  startedAt: string
  completedAt?: string
  trail: ApprovalTrailNode[]
}

// 任务查询参数
export interface TaskQuery extends PageQuery {
  bizType?: string
  status?: TaskStatus
}

/**
 * 审批工作流 API
 */
export const workflowApi = {
  /**
   * 查询我的待办
   */
  async myTasks(params: TaskQuery): Promise<PageResponse<WorkflowTaskItem>> {
    const result = await api.get<PageResponse<WorkflowTaskRow>>('/workflow/tasks', { params })
    return {
      ...result,
      items: result.items.map(row => ({
        taskId: row.task_id,
        instanceId: row.instance_id,
        nodeKey: row.node_key,
        nodeName: row.node_name,
        assigneeId: row.assignee_id,
        version: row.version,
        taskName: row.node_name,
        bizType: row.biz_type,
        bizId: row.biz_id,
        assigneeName: '',
        status: row.status,
        createdAt: '',
        dueDate: row.due_at
      }))
    }
  },

  /**
   * 查询单个审批任务（详情页/已办轨迹入口）
   */
  async task(taskId: string): Promise<WorkflowTaskItem> {
    const row = await api.get<WorkflowTaskRow>(`/workflow/tasks/${taskId}`)
    return {
      taskId: row.task_id,
      instanceId: row.instance_id,
      nodeKey: row.node_key,
      nodeName: row.node_name,
      assigneeId: row.assignee_id,
      version: row.version,
      taskName: row.node_name,
      bizType: row.biz_type,
      bizId: row.biz_id,
      assigneeName: '',
      status: row.status,
      createdAt: '',
      dueDate: row.due_at
    }
  },

  /**
   * 处理审批任务（通过/退回/拒绝）
   */
  async act(taskId: string, data: TaskActionRequest): Promise<TaskActionResponse> {
    const row = await api.post<TaskActionRow>(`/workflow/tasks/${taskId}/actions`, {
      request_id: data.requestId,
      expected_version: data.expectedVersion,
      action: data.action,
      comment: data.comment,
      on_behalf_of: data.onBehalfOf,
      reassign_to: data.reassignTo
    })
    return {
      taskId: row.task_id,
      newStatus: row.task_status,
      instanceStatus: row.instance_status,
      nextTaskIds: row.next_task_ids
    }
  },

  /**
   * 查询审批轨迹
   */
  async trail(instanceId: string): Promise<WorkflowInstanceResponse> {
    const row = await api.get<WorkflowInstanceRow>(`/workflow/instances/${instanceId}`)
    return {
      instanceId: row.instance_id,
      bizType: row.biz_type,
      bizId: row.biz_id,
      instanceStatus: row.status,
      startedAt: row.started_at,
      completedAt: row.ended_at,
      trail: (row.tasks || []).map(task => ({
        taskId: task.task_id,
        taskName: task.node_name,
        assigneeName: '',
        action: toAction(task.status),
        status: task.status
      }))
    }
  },

  /**
   * 查询我发起的审批实例
   */
  async myInitiated(params: PageQuery): Promise<PageResponse<InitiatedInstanceItem>> {
    const result = await api.get<PageResponse<InitiatedInstanceRow>>(
      '/workflow/instances/initiated', { params })
    return {
      ...result,
      items: result.items.map(row => ({
        instanceId: row.instance_id,
        bizType: row.biz_type,
        bizId: row.biz_id,
        status: row.status,
        startedAt: row.started_at,
        endedAt: row.ended_at
      }))
    }
  }
}

const toAction = (status: TaskStatus): ApprovalAction | undefined => {
  if (status === 'APPROVED' || status === 'RETURNED' || status === 'REJECTED') {
    return status
  }
  return undefined
}
