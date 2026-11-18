<template>
  <div class="deliverable-list-page">
    <PageHeader title="交付物管理" subtitle="项目交付物列表">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'execution:deliverable:upload'"
          :disabled="!projects.length"
          @click="handleUpload"
        >
          <el-icon><Upload /></el-icon>
          上传交付物
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="!loadingProjects && projects.length === 0"
      title="暂无项目，请先完成立项后再上传交付物"
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
              :label="projectLabel(project)"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="关联里程碑">
          <el-select
            v-model="searchForm.milestoneId"
            placeholder="全部里程碑"
            clearable
            :disabled="!searchForm.projectId"
            :loading="loadingMilestones"
            style="width: 100%"
          >
            <el-option
              v-for="milestone in searchMilestones"
              :key="milestone.id"
              :label="milestoneLabel(milestone)"
              :value="milestone.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </SearchBar>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe border empty-text="暂无交付物">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="关联里程碑" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ milestoneName(row.milestoneId) }}
          </template>
        </el-table-column>
        <el-table-column label="上传人" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ uploaderName(row.uploadedBy) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="success" text size="small" @click="handleDownload(row)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="uploadDialogVisible" title="上传交付物" width="620px">
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadRules"
        label-width="120px"
      >
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="uploadForm.projectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
            @change="handleUploadProjectChange"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="projectLabel(project)"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入交付物标题" />
        </el-form-item>
        <el-form-item label="关联里程碑" prop="milestoneId">
          <el-select
            v-model="uploadForm.milestoneId"
            placeholder="选择里程碑（可选）"
            clearable
            :disabled="!uploadForm.projectId"
            :loading="loadingUploadMilestones"
            style="width: 100%"
          >
            <el-option
              v-for="milestone in uploadMilestones"
              :key="milestone.id"
              :label="milestoneLabel(milestone)"
              :value="milestone.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="文件" prop="fileIds">
          <FileUpload
            v-model="uploadForm.fileIds"
            @update:files="handleUploadedFiles"
            :max-count="1"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.zip"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUpload">确认上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import SearchBar from '@/components/SearchBar/SearchBar.vue'
import FileUpload from '@/components/FileUpload/FileUpload.vue'
import { formatDateTime } from '@/utils/date'
import { projectApi, type ProjectListItem, type MilestoneView, type ProjectMemberVO } from '@/api/project'
import { executionApi, type DeliverableView } from '@/api/execution'
import { fileApi, type UploadedFileView } from '@/api/file'

const route = useRoute()

const loading = ref(false)
const loadingProjects = ref(false)
const loadingMilestones = ref(false)
const loadingUploadMilestones = ref(false)
const submitting = ref(false)
const projects = ref<ProjectListItem[]>([])
const tableData = ref<DeliverableView[]>([])
const searchMilestones = ref<MilestoneView[]>([])
const uploadMilestones = ref<MilestoneView[]>([])
const projectMembers = ref<ProjectMemberVO[]>([])

const searchForm = reactive({
  projectId: (route.query.projectId as string) || '',
  milestoneId: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const uploadDialogVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadForm = reactive({
  projectId: '',
  fileId: '',
  title: '',
  milestoneId: '',
  fileIds: [] as string[]
})
const uploadedFiles = ref<UploadedFileView[]>([])

const uploadRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  fileIds: [{
    validator: (_rule, value, callback) => {
      if (!value || value.length === 0) {
        callback(new Error('请上传文件'))
      } else if (!uploadedFiles.value.find(item => item.fileId === value[0])?.sha256) {
        callback(new Error('文件摘要缺失，请重新上传文件'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }]
}

const projectLabel = (project: ProjectListItem): string => `${project.projectNo}｜${project.title}`
const milestoneLabel = (milestone: MilestoneView): string => `${milestone.code}｜${milestone.title}`
const milestoneName = (milestoneId?: string): string => {
  if (!milestoneId) return '-'
  return searchMilestones.value.find(item => item.id === milestoneId)?.title || milestoneId
}
const uploaderName = (userId?: string): string => {
  if (!userId) return '-'
  return projectMembers.value.find(item => item.userId === userId)?.userName || userId
}

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
      await loadMembers(searchForm.projectId)
      await loadMilestones(searchForm.projectId, 'search')
      await fetchList()
    }
  } catch {
    projects.value = []
  } finally {
    loadingProjects.value = false
  }
}

const loadMembers = async (projectId: string) => {
  try {
    projectMembers.value = await projectApi.members(projectId)
  } catch {
    projectMembers.value = []
  }
}

const loadMilestones = async (projectId: string, target: 'search' | 'upload') => {
  if (!projectId) {
    if (target === 'search') searchMilestones.value = []
    else uploadMilestones.value = []
    return
  }
  if (target === 'search') loadingMilestones.value = true
  else loadingUploadMilestones.value = true
  try {
    const rows = await projectApi.milestones(projectId)
    if (target === 'search') searchMilestones.value = rows
    else uploadMilestones.value = rows
  } catch {
    if (target === 'search') searchMilestones.value = []
    else uploadMilestones.value = []
  } finally {
    if (target === 'search') loadingMilestones.value = false
    else loadingUploadMilestones.value = false
  }
}

const fetchList = async () => {
  if (!searchForm.projectId) {
    tableData.value = []
    return
  }
  loading.value = true
  try {
    const res = await executionApi.listDeliverables(searchForm.projectId, {
      milestoneId: searchForm.milestoneId || undefined,
      page: pagination.page,
      size: pagination.size
    })
    tableData.value = res.items
    pagination.total = res.total
  } catch {
    tableData.value = []
    ElMessage.error('加载交付物失败')
  } finally {
    loading.value = false
  }
}

const handleSearchProjectChange = async () => {
  searchForm.milestoneId = ''
  await loadMembers(searchForm.projectId)
  await loadMilestones(searchForm.projectId, 'search')
  pagination.page = 1
  await fetchList()
}

const handleSearch = () => {
  pagination.page = 1
  fetchList()
}

const handleReset = () => {
  searchForm.milestoneId = ''
  pagination.page = 1
  fetchList()
}

const handleUpload = () => {
  uploadForm.projectId = searchForm.projectId || projects.value[0]?.id || ''
  uploadForm.title = ''
  uploadForm.milestoneId = ''
  uploadForm.fileIds = []
  uploadedFiles.value = []
  loadMilestones(uploadForm.projectId, 'upload')
  uploadDialogVisible.value = true
}

const handleUploadProjectChange = async () => {
  uploadForm.milestoneId = ''
  await loadMilestones(uploadForm.projectId, 'upload')
}

const handleUploadedFiles = (files: UploadedFileView[]) => {
  uploadedFiles.value = files
}

const submitUpload = async () => {
  if (!uploadFormRef.value) return
  const valid = await uploadFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await executionApi.uploadDeliverable(uploadForm.projectId, {
      fileId: uploadForm.fileIds[0]!,
      sha256: uploadedFiles.value.find(item => item.fileId === uploadForm.fileIds[0])!.sha256!,
      title: uploadForm.title,
      milestoneId: uploadForm.milestoneId || undefined
    })
    ElMessage.success('交付物上传成功')
    uploadDialogVisible.value = false
    if (searchForm.projectId !== uploadForm.projectId) {
      searchForm.projectId = uploadForm.projectId
      await loadMembers(searchForm.projectId)
      await loadMilestones(searchForm.projectId, 'search')
    }
    await fetchList()
  } catch (error: any) {
    ElMessage.error(error?.message || '上传失败')
  } finally {
    submitting.value = false
  }
}

const handleDownload = async (row: any) => {
  const deliverable = row as DeliverableView
  try {
    await fileApi.download(deliverable.fileId)
  } catch {
    ElMessage.error('获取下载地址失败')
  }
}
</script>

<style scoped lang="scss">
.deliverable-list-page {
  .empty-project-alert {
    margin-bottom: 16px;
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
