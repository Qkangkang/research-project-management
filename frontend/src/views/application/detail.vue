<template>
  <div class="application-detail-page" v-loading="loading">
    <PageHeader :title="application.title || '申报详情'" :subtitle="`编号：${application.applicationNo || '-'}`">
      <template #actions>
        <el-button
          v-if="application.status === 'DRAFT'"
          type="warning"
          v-permission="'application:edit'"
          @click="handleEdit"
        >
          编辑
        </el-button>
        <el-button
          v-if="application.status === 'DRAFT'"
          type="primary"
          v-permission="'application:submit'"
          @click="handleSubmit"
        >
          提交审核
        </el-button>
        <el-button
          v-if="application.status === 'IN_REVIEW'"
          type="danger"
          v-permission="'application:withdraw'"
          @click="handleWithdraw"
        >
          撤回
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="info-card">
      <template #header>
        <div class="card-header">
          <span>基本信息</span>
          <StatusTag :status="application.status" domain="application" />
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="申报编号">{{ application.applicationNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :status="application.status" domain="application" />
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ application.title }}</el-descriptions-item>
        <el-descriptions-item label="申报批次">{{ application.batchName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目类型">{{ projectTypeLabel(application.projectType) }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ application.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ application.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(application.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(application.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="application.workflowInstanceId" label="审批实例" :span="2">
          {{ application.workflowInstanceId }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="application.formData" shadow="never" class="info-card">
      <template #header>
        <span>表单数据</span>
      </template>
      <el-tree
        :data="formDataTree"
        :props="{ label: 'label', children: 'children' }"
        default-expand-all
      />
    </el-card>

    <el-card v-if="application.attachments.length > 0" shadow="never" class="info-card">
      <template #header>
        <span>附件列表</span>
      </template>
      <el-table :data="application.attachments" border>
        <el-table-column prop="purpose" label="用途" width="200" />
        <el-table-column prop="fileName" label="文件名" min-width="300" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="handleDownload(row.fileId)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <ApprovalTimeline v-if="application.workflowInstanceId" :instance-id="application.workflowInstanceId" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import ApprovalTimeline from '@/components/ApprovalTimeline/ApprovalTimeline.vue'
import { formatDateTime } from '@/utils/date'
import { applicationApi, type ApplicationDetail } from '@/api/application'
import { fileApi } from '@/api/file'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const application = ref<ApplicationDetail>({
  id: '',
  applicationNo: '',
  title: '',
  projectType: '',
  applicantName: '',
  deptName: '',
  status: 'DRAFT',
  createdAt: '',
  updatedAt: '',
  version: 0,
  formData: {},
  attachments: []
})

const UUID_PATTERN = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

const projectTypeLabel = (type: string): string => {
  const labels: Record<string, string> = {
    GENERAL: '一般项目',
    KEY: '重点项目',
    YOUTH: '青年项目'
  }
  return labels[type] || type
}

const formDataTree = computed(() => {
  return Object.entries(application.value.formData || {}).map(([key, value]) => ({
    label: `${key}: ${JSON.stringify(value)}`,
    children: []
  }))
})

onMounted(() => {
  loadDetail()
})

const loadDetail = async () => {
  const id = route.params.id as string
  if (!id || !UUID_PATTERN.test(id)) {
    router.replace('/404')
    return
  }
  loading.value = true
  try {
    application.value = await applicationApi.detail(id)
  } catch {
    ElMessage.error('加载申报详情失败')
  } finally {
    loading.value = false
  }
}

const handleEdit = () => {
  router.push({
    path: '/applications/create',
    query: { id: application.value.id, mode: 'edit' }
  })
}

const handleSubmit = async () => {
  try {
    await ElMessageBox.confirm('确认提交该申报吗？提交后将进入审批流程', '提交确认', {
      type: 'warning'
    })

    await applicationApi.submit(application.value.id, {
      expectedVersion: application.value.version,
      comment: '详情页提交',
      formData: application.value.formData,
      attachments: application.value.attachments.map(item => ({
        purpose: item.purpose,
        fileId: item.fileId
      }))
    })
    ElMessage.success('提交成功')
    await loadDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.message || '提交失败')
    }
  }
}

const handleWithdraw = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入撤回原因', '撤回申报', {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '撤回原因不能为空'
    })

    await applicationApi.withdraw(application.value.id, { reason })
    ElMessage.success('撤回成功')
    await loadDetail()
  } catch {
    // 用户取消
  }
}

const handleDownload = async (fileId: string) => {
  try {
    await fileApi.download(fileId)
  } catch {
    ElMessage.error('获取下载地址失败')
  }
}
</script>

<style scoped lang="scss">
.application-detail-page {
  .info-card {
    margin-bottom: 16px;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }
}
</style>
