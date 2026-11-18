<template>
  <div class="application-list-page">
    <PageHeader title="申报管理" subtitle="项目申报列表">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'application:create'"
          @click="handleCreate"
        >
          <el-icon><Plus /></el-icon>
          新建申报
        </el-button>
      </template>
    </PageHeader>

    <SearchBar v-model="searchForm" :loading="loading" @search="handleSearch" @reset="handleReset">
      <el-col :span="6">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="申报编号/标题"
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
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="IN_REVIEW" />
            <el-option label="待补充" value="SUPPLEMENT" />
            <el-option label="已批准" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="已撤回" value="WITHDRAWN" />
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
        <el-table-column prop="applicationNo" label="申报编号" width="180" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="projectType" label="项目类型" width="120" />
        <el-table-column prop="applicantName" label="申请人" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="application" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
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
              v-if="row.status === 'DRAFT'"
              type="warning"
              text
              size="small"
              v-permission="'application:edit'"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              text
              size="small"
              v-permission="'application:submit'"
              @click="handleSubmit(row)"
            >
              提交
            </el-button>
            <el-button
              v-if="row.status === 'IN_REVIEW'"
              type="danger"
              text
              size="small"
              v-permission="'application:withdraw'"
              @click="handleWithdraw(row)"
            >
              撤回
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
import { formatDateTime } from '@/utils/date'
import { applicationApi, ApplicationSummary, ApplicationListQuery } from '@/api/application'

const router = useRouter()

const loading = ref(false)
const tableData = ref<ApplicationSummary[]>([])

const searchForm = reactive<Omit<ApplicationListQuery, 'page' | 'size'>>({
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
    const params: ApplicationListQuery = {
      ...searchForm,
      page: pagination.page,
      size: pagination.size
    }
    const res = await applicationApi.list(params)
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
  router.push('/applications/create')
}

const handleView = (row: any) => {
  router.push(`/applications/${row.id}`)
}

const handleEdit = (row: any) => {
  router.push({
    path: '/applications/create',
    query: { id: row.id, mode: 'edit' }
  })
}

const handleSubmit = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认提交该申报吗？提交后将进入审批流程', '提交确认', {
      type: 'warning'
    })

    // 实际提交需要完整数据，此处简化处理
    ElMessage.warning('请进入编辑页面完整填写后提交')
    handleEdit(row)
  } catch {
    // 用户取消
  }
}

const handleWithdraw = async (row: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入撤回原因', '撤回申报', {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '撤回原因不能为空'
    })

    await applicationApi.withdraw(row.id, { reason })
    ElMessage.success('撤回成功')
    fetchList()
  } catch {
    // 用户取消
  }
}
</script>

<style scoped lang="scss">
.application-list-page {
  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
