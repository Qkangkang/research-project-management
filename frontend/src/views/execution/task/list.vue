<template>
  <div class="task-list-page">
    <PageHeader title="轻任务管理" subtitle="项目任务协作">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'exec:task:assign'"
          :disabled="!projects.length"
          @click="handleCreate"
        >
          <el-icon><Plus /></el-icon>
          新建任务
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="!loadingProjects && projects.length === 0"
      title="暂无项目，请先完成立项后再创建轻任务"
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
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
            <el-option label="待开始" value="TODO" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="DONE" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
      </el-col>
    </SearchBar>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        empty-text="暂无任务"
      >
        <el-table-column prop="title" label="任务标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="负责人" width="140">
          <template #default="{ row }">
            {{ assigneeName(row.assigneeId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="task" />
          </template>
        </el-table-column>
        <el-table-column label="截止日期" width="120">
          <template #default="{ row }">
            {{ row.dueDate ? formatDate(row.dueDate) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="关联交付物" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ deliverableName(row.deliverableId) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button
              v-if="row.status === 'TODO'"
              type="success"
              text
              size="small"
              v-permission="'exec:task:assign'"
              @click="handleStart(row)"
            >
              开始
            </el-button>
            <el-button
              v-if="['TODO', 'IN_PROGRESS'].includes(row.status)"
              type="success"
              text
              size="small"
              v-permission="'exec:task:assign'"
              @click="handleComplete(row)"
            >
              完成
            </el-button>
            <el-button
              v-if="['TODO', 'IN_PROGRESS'].includes(row.status)"
              type="danger"
              text
              size="small"
              v-permission="'exec:task:assign'"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建任务" width="620px">
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="110px"
      >
        <el-form-item label="项目" prop="projectId">
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
              :label="projectLabel(project)"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务标题" prop="title">
          <el-input v-model="createForm.title" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item label="任务描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="负责人" prop="assigneeId">
          <el-select
            v-model="createForm.assigneeId"
            placeholder="请选择项目成员"
            :loading="formResourceLoading"
            :disabled="!createForm.projectId"
            style="width: 100%"
          >
            <el-option
              v-for="member in formMembers"
              :key="member.userId"
              :label="`${member.userName}（${member.projectRole}）`"
              :value="member.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期" prop="dueDate">
          <el-date-picker
            v-model="createForm.dueDate"
            type="date"
            placeholder="选择截止日期（可选）"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="关联交付物" prop="deliverableId">
          <el-select
            v-model="createForm.deliverableId"
            placeholder="选择交付物（可选）"
            clearable
            :loading="formResourceLoading"
            :disabled="!createForm.projectId"
            style="width: 100%"
          >
            <el-option
              v-for="deliverable in formDeliverables"
              :key="deliverable.deliverableId"
              :label="deliverable.title"
              :value="deliverable.deliverableId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">
          确认创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeDialogVisible" title="完成任务" width="520px">
      <el-form label-width="110px">
        <el-form-item label="关联交付物">
          <el-select
            v-model="completeForm.deliverableId"
            placeholder="选择交付物（可选）"
            clearable
            :loading="formResourceLoading"
            style="width: 100%"
          >
            <el-option
              v-for="deliverable in completeDeliverables"
              :key="deliverable.deliverableId"
              :label="deliverable.title"
              :value="deliverable.deliverableId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitComplete">
          确认完成
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawerVisible" title="任务详情" size="480px">
      <el-descriptions v-if="selectedTask" :column="1" border>
        <el-descriptions-item label="任务标题">{{ selectedTask.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :status="selectedTask.status" domain="task" />
        </el-descriptions-item>
        <el-descriptions-item label="负责人">
          {{ assigneeName(selectedTask.assigneeId) }}
        </el-descriptions-item>
        <el-descriptions-item label="截止日期">
          {{ selectedTask.dueDate ? formatDate(selectedTask.dueDate) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="关联交付物">
          {{ deliverableName(selectedTask.deliverableId) }}
        </el-descriptions-item>
        <el-descriptions-item label="任务描述">
          {{ selectedTask.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDateTime(selectedTask.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="完成时间">
          {{ selectedTask.completedAt ? formatDateTime(selectedTask.completedAt) : '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
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
import { projectApi, ProjectListItem, ProjectMemberVO } from '@/api/project'
import {
  executionApi,
  DeliverableView,
  ProjectTaskView,
  TaskStatus,
  CreateTaskRequest
} from '@/api/execution'

const route = useRoute()

const loading = ref(false)
const loadingProjects = ref(false)
const formResourceLoading = ref(false)
const submitting = ref(false)
const projects = ref<ProjectListItem[]>([])
const tableData = ref<ProjectTaskView[]>([])
const projectMembers = ref<ProjectMemberVO[]>([])
const projectDeliverables = ref<DeliverableView[]>([])
const formMembers = ref<ProjectMemberVO[]>([])
const formDeliverables = ref<DeliverableView[]>([])
const completeDeliverables = ref<DeliverableView[]>([])

const searchForm = reactive<{
  projectId: string
  status?: TaskStatus
}>({
  projectId: (route.query.projectId as string) || '',
  status: undefined
})

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<{
  projectId: string
  title: string
  description: string
  assigneeId: string
  dueDate: string
  deliverableId: string
}>({
  projectId: '',
  title: '',
  description: '',
  assigneeId: '',
  dueDate: '',
  deliverableId: ''
})

const createRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
  assigneeId: [{ required: true, message: '请选择负责人', trigger: 'change' }]
}

const completeDialogVisible = ref(false)
const completeForm = reactive({
  taskId: '',
  projectId: '',
  deliverableId: ''
})

const detailDrawerVisible = ref(false)
const selectedTask = ref<ProjectTaskView | null>(null)

const projectLabel = (project: ProjectListItem): string => {
  return `${project.projectNo}｜${project.title}`
}

const assigneeName = (assigneeId: string): string => {
  const member = projectMembers.value.find(item => item.userId === assigneeId)
  return member?.userName || assigneeId
}

const deliverableName = (deliverableId?: string): string => {
  if (!deliverableId) return '-'
  const source = projectDeliverables.value.length > 0
    ? projectDeliverables.value
    : formDeliverables.value
  return source.find(item => item.deliverableId === deliverableId)?.title || deliverableId
}

const loadProjects = async () => {
  loadingProjects.value = true
  try {
    const result = await projectApi.list({ page: 1, size: 200 })
    projects.value = result.items
    const requestedId = route.query.projectId as string | undefined
    const selected = projects.value.find(item => item.id === requestedId) || projects.value[0]
    if (selected) {
      searchForm.projectId = selected.id
      await loadProjectContext(selected.id)
      await fetchList()
    }
  } catch {
    projects.value = []
  } finally {
    loadingProjects.value = false
  }
}

onMounted(() => {
  loadProjects()
})

const loadProjectContext = async (projectId: string) => {
  const [members, deliverables] = await Promise.all([
    projectApi.members(projectId),
    executionApi.listDeliverables(projectId, { page: 1, size: 200 })
  ])
  projectMembers.value = members
  projectDeliverables.value = deliverables.items
}

const fetchList = async () => {
  if (!searchForm.projectId) {
    tableData.value = []
    return
  }

  loading.value = true
  try {
    tableData.value = await executionApi.listTasks(searchForm.projectId, searchForm.status)
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleProjectChange = async () => {
  searchForm.status = undefined
  if (!searchForm.projectId) {
    tableData.value = []
    return
  }
  try {
    await loadProjectContext(searchForm.projectId)
  } catch {
    projectMembers.value = []
    projectDeliverables.value = []
  }
  await fetchList()
}

const handleSearch = () => {
  fetchList()
}

const handleReset = () => {
  searchForm.status = undefined
  fetchList()
}

const handleCreate = async () => {
  if (projects.value.length === 0) {
    ElMessage.warning('暂无项目，请先完成立项')
    return
  }
  createForm.projectId = searchForm.projectId || projects.value[0]?.id || ''
  createForm.title = ''
  createForm.description = ''
  createForm.assigneeId = ''
  createForm.dueDate = ''
  createForm.deliverableId = ''
  await loadCreateContext(createForm.projectId)
  createDialogVisible.value = true
}

const loadCreateContext = async (projectId: string) => {
  if (!projectId) {
    formMembers.value = []
    formDeliverables.value = []
    return
  }
  formResourceLoading.value = true
  try {
    const [members, deliverables] = await Promise.all([
      projectApi.members(projectId),
      executionApi.listDeliverables(projectId, { page: 1, size: 200 })
    ])
    formMembers.value = members
    formDeliverables.value = deliverables.items
  } catch {
    formMembers.value = []
    formDeliverables.value = []
  } finally {
    formResourceLoading.value = false
  }
}

const handleCreateProjectChange = async () => {
  createForm.assigneeId = ''
  createForm.deliverableId = ''
  await loadCreateContext(createForm.projectId)
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const request: CreateTaskRequest = {
      title: createForm.title,
      description: createForm.description || undefined,
      assigneeId: createForm.assigneeId,
      dueDate: createForm.dueDate || undefined,
      deliverableId: createForm.deliverableId || undefined
    }
    await executionApi.createTask(createForm.projectId, request)
    ElMessage.success('任务创建成功')
    createDialogVisible.value = false
    if (searchForm.projectId !== createForm.projectId) {
      searchForm.projectId = createForm.projectId
      await loadProjectContext(createForm.projectId)
    }
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleView = (row: any) => {
  selectedTask.value = row as ProjectTaskView
  detailDrawerVisible.value = true
}

const handleStart = async (row: any) => {
  const task = row as ProjectTaskView
  try {
    await ElMessageBox.confirm('确认开始该任务吗？', '开始任务', { type: 'info' })
    await executionApi.startTask(task.taskId)
    ElMessage.success('任务已开始')
    await fetchList()
  } catch {
    // 用户取消或业务错误已由请求拦截器提示
  }
}

const handleComplete = async (row: any) => {
  const task = row as ProjectTaskView
  completeForm.taskId = task.taskId
  completeForm.projectId = task.projectId
  completeForm.deliverableId = ''
  formResourceLoading.value = true
  try {
    const result = await executionApi.listDeliverables(task.projectId, { page: 1, size: 200 })
    completeDeliverables.value = result.items
  } catch {
    completeDeliverables.value = []
  } finally {
    formResourceLoading.value = false
  }
  completeDialogVisible.value = true
}

const submitComplete = async () => {
  submitting.value = true
  try {
    await executionApi.completeTask(completeForm.taskId, {
      deliverableId: completeForm.deliverableId || undefined
    })
    ElMessage.success('任务已完成')
    completeDialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleCancel = async (row: any) => {
  const task = row as ProjectTaskView
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消任务', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPattern: /.+/,
      inputErrorMessage: '取消原因不能为空'
    })
    await executionApi.cancelTask(task.taskId, { reason })
    ElMessage.success('任务已取消')
    await fetchList()
  } catch {
    // 用户取消或业务错误已由请求拦截器提示
  }
}
</script>

<style scoped lang="scss">
.task-list-page {
  .empty-project-alert {
    margin-bottom: 16px;
  }
}
</style>
