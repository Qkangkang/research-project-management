<template>
  <div class="project-list-page">
    <PageHeader title="项目管理" subtitle="项目列表">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'project:create'"
          @click="handleCreate"
        >
          <el-icon><Plus /></el-icon>
          新建项目
        </el-button>
      </template>
    </PageHeader>

    <SearchBar v-model="searchForm" :loading="loading" @search="handleSearch" @reset="handleReset">
      <el-col :span="6">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="项目编号/名称"
            clearable
          />
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="项目类型">
          <el-select
            v-model="searchForm.projectType"
            placeholder="请选择"
            clearable
          >
            <el-option label="重点项目" value="KEY" />
            <el-option label="一般项目" value="GENERAL" />
            <el-option label="青年项目" value="YOUTH" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择"
            clearable
          >
            <el-option label="待启动" value="PENDING_START" />
            <el-option label="执行中" value="EXECUTING" />
            <el-option label="暂停" value="SUSPENDED" />
            <el-option label="验收中" value="ACCEPTING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
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
      >
        <el-table-column prop="projectNo" label="项目编号" width="180" />
        <el-table-column prop="title" label="项目名称" min-width="250" show-overflow-tooltip />
        <el-table-column prop="projectType" label="类型" width="120" />
        <el-table-column prop="piName" label="负责人" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="project" />
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.startDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="endDate" label="结束日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.endDate) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              text
              size="small"
              @click="handleView(row)"
            >
              查看
            </el-button>
            <el-button
              v-if="row.status === 'EXECUTING'"
              type="warning"
              text
              size="small"
              v-permission="'project:state:change'"
              @click="handlePause(row)"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === 'SUSPENDED'"
              type="success"
              text
              size="small"
              v-permission="'project:state:change'"
              @click="handleResume(row)"
            >
              恢复
            </el-button>
            <el-button
              v-if="['EXECUTING', 'SUSPENDED'].includes(row.status)"
              type="danger"
              text
              size="small"
              v-permission="'project:state:change'"
              @click="handleTerminate(row)"
            >
              终止
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import SearchBar from '@/components/SearchBar/SearchBar.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { formatDate } from '@/utils/date'
import { projectApi, ProjectListItem, ProjectListQuery } from '@/api/project'

const router = useRouter()

const loading = ref(false)
const tableData = ref<ProjectListItem[]>([])

const searchForm = reactive<Omit<ProjectListQuery, 'page' | 'size'>>({
  keyword: '',
  projectType: '',
  status: undefined
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

onMounted(() => {
  fetchList()
})

const fetchList = async () => {
  loading.value = true
  try {
    const params: ProjectListQuery = {
      ...searchForm,
      page: pagination.page,
      size: pagination.size
    }
    const res = await projectApi.list(params)
    tableData.value = res.items
    pagination.total = res.total
  } catch (error) {
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchList()
}

const handleReset = () => {
  pagination.page = 1
  fetchList()
}

const handleCreate = () => {
  // 项目由申报批准后自动立项（申报 → 立项状态机），此处引导走申报流程
  ElMessage.info('项目由申报批准后自动立项，请先在申报管理提交申报')
  router.push('/applications')
}

const handleView = (row: any) => {
  router.push(`/projects/${row.id}`)
}

const handlePause = async (row: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入暂停原因', '暂停项目', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '暂停原因不能为空'
    })

    await projectApi.pause(row.id, { reason })
    ElMessage.success('暂停成功')
    fetchList()
  } catch {
    // 用户取消
  }
}

const handleResume = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认恢复该项目吗？', '恢复项目', {
      type: 'warning'
    })

    await projectApi.resume(row.id)
    ElMessage.success('恢复成功')
    fetchList()
  } catch {
    // 用户取消
  }
}

const handleTerminate = async (row: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入终止原因', '终止项目', {
      confirmButtonText: '确认终止',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '终止原因不能为空'
    })

    await projectApi.terminate(row.id, { reason })
    ElMessage.success('项目已终止')
    fetchList()
  } catch {
    // 用户取消
  }
}
</script>

<style scoped lang="scss">
.project-list-page {
  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
