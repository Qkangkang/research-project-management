<template>
  <div class="output-list-page">
    <PageHeader title="成果管理" subtitle="科研成果列表">
      <template #actions>
        <el-button
          type="primary"
          v-permission="'output:create'"
          @click="handleCreate"
        >
          <el-icon><Plus /></el-icon>
          登记成果
        </el-button>
      </template>
    </PageHeader>

    <SearchBar v-model="searchForm" :loading="loading" @search="handleSearch" @reset="handleReset">
      <el-col :span="6">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.q"
            placeholder="标题/DOI/专利号"
            clearable
          />
        </el-form-item>
      </el-col>
      <el-col :span="6">
        <el-form-item label="成果类型">
          <el-select
            v-model="searchForm.outputType"
            placeholder="请选择"
            clearable
          >
            <el-option label="论文" value="PAPER" />
            <el-option label="专利" value="PATENT" />
            <el-option label="软件" value="SOFTWARE" />
            <el-option label="获奖" value="AWARD" />
            <el-option label="标准" value="STANDARD" />
            <el-option label="其他" value="OTHER" />
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
            <el-option label="待审核" value="SUBMITTED" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="已撤回" value="REVOKED" />
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
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="成果类型" width="120">
          <template #default="{ row }">
            {{ row.outputType ? (outputTypeLabels[row.outputType as OutputType] || row.outputType) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="completedAt" label="完成日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.completedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="output" />
          </template>
        </el-table-column>
        <el-table-column label="登记人" width="120">
          <template #default="{ row }">
            {{ userNames[row.ownerId] || row.ownerName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              text
              size="small"
              @click="handleView(row)"
            >
              查看详情
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              text
              size="small"
              v-permission="'output:submit'"
              @click="handleSubmit(row)"
            >
              提交审核
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
import { outputApi, OutputSummaryVO, OutputSearchQuery, OutputType } from '@/api/output'
import { organizationApi, type UserOption, type OrgUnitOption } from '@/api/organization'

const router = useRouter()

const loading = ref(false)
const tableData = ref<OutputSummaryVO[]>([])
const userNames = ref<Record<string, string>>({})
const deptNames = ref<Record<string, string>>({})

const searchForm = reactive<Omit<OutputSearchQuery, 'page' | 'size'>>({
  q: '',
  outputType: undefined,
  status: undefined
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const outputTypeLabels: Record<OutputType, string> = {
  PAPER: '论文',
  PATENT: '专利',
  SOFTWARE: '软件',
  AWARD: '获奖',
  STANDARD: '标准',
  REPORT: '报告',
  OTHER: '其他'
}

onMounted(async () => {
  await Promise.allSettled([loadLookups(), fetchList()])
})

const loadLookups = async () => {
  const [users, units] = await Promise.all([
    organizationApi.users(),
    organizationApi.orgUnits()
  ])
  userNames.value = Object.fromEntries(users.map((item: UserOption) => [item.id, item.display_name]))
  deptNames.value = Object.fromEntries(units.map((item: OrgUnitOption) => [item.id, item.name]))
}

const fetchList = async () => {
  loading.value = true
  try {
    const params: OutputSearchQuery = {
      ...searchForm,
      page: pagination.page,
      size: pagination.size
    }
    const res = await outputApi.search(params)
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
  router.push('/outputs/create')
}

const handleView = (row: any) => {
  router.push(`/outputs/${row.id}`)
}

const handleSubmit = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认提交该成果审核吗？', '提交确认', {
      type: 'warning'
    })

    await outputApi.submit(row.id)
    ElMessage.success('提交成功')
    fetchList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '提交失败')
    }
  }
}
</script>

<style scoped lang="scss">
.output-list-page {
  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
