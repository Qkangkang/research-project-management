<template>
  <div class="output-create-page">
    <PageHeader :title="isEdit ? '编辑成果' : '登记成果'" subtitle="填写成果信息">
      <template #actions>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="default" @click="handleSaveDraft" :loading="saving">
          保存草稿
        </el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          提交审核
        </el-button>
      </template>
    </PageHeader>

    <el-card shadow="never">
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
        :disabled="loading"
      >
        <el-form-item label="成果类型" prop="outputType" required>
          <el-select
            v-model="formData.outputType"
            @change="handleOutputTypeChange"
            placeholder="请选择成果类型"
            style="width: 300px"
          >
            <el-option label="论文" value="PAPER" />
            <el-option label="专利" value="PATENT" />
            <el-option label="软件" value="SOFTWARE" />
            <el-option label="获奖" value="AWARD" />
            <el-option label="标准" value="STANDARD" />
            <el-option label="报告" value="REPORT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="成果标题" prop="title" required>
          <el-input
            v-model="formData.title"
            placeholder="请输入成果标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="DOI" prop="doi">
          <el-input
            v-model="formData.doi"
            placeholder="如：10.1000/xyz123"
            style="width: 400px"
          />
        </el-form-item>

        <el-form-item label="专利号" prop="patentNo">
          <el-input
            v-model="formData.patentNo"
            placeholder="请输入专利号"
            style="width: 300px"
          />
        </el-form-item>

        <el-form-item label="完成日期" prop="completedAt" required>
          <el-date-picker
            v-model="formData.completedAt"
            type="date"
            placeholder="选择完成日期"
            value-format="YYYY-MM-DD"
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item
          v-for="field in metadataFields"
          :key="field.key"
          :label="field.label"
        >
          <el-input-number
            v-if="field.type === 'NUMBER'"
            v-model="metadataValues[field.key] as number"
            :placeholder="field.placeholder"
            style="width: 300px"
          />
          <el-input
            v-else
            v-model="metadataValues[field.key] as string"
            :placeholder="field.placeholder"
            style="width: 600px"
          />
          <div v-if="field.type === 'LIST'" class="form-tip">
            多个值请用逗号分隔
          </div>
        </el-form-item>

        <el-form-item label="证据附件" prop="evidenceFileIds">
          <FileUpload
            v-model="formData.evidenceFileIds"
            accept=".pdf,.doc,.docx,.jpg,.png"
            :max-size-m-b="50"
            :max-count="10"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 疑似重复提示对话框 -->
    <el-dialog
      v-model="duplicateDialogVisible"
      title="疑似重复成果"
      width="700px"
    >
      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      >
        检测到以下疑似重复的成果，请核实后决定是否继续提交
      </el-alert>

      <el-table :data="duplicateCandidates" border stripe>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="doi" label="DOI" width="150" show-overflow-tooltip />
        <el-table-column prop="similarity" label="相似度" width="100">
          <template #default="{ row }">
            {{ (row.similarity * 100).toFixed(0) }}%
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" width="150" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <el-button @click="duplicateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSubmit">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import FileUpload from '@/components/FileUpload/FileUpload.vue'
import { outputApi, CreateOutputRequest, DuplicateCandidateVO, type OutputType } from '@/api/output'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const duplicateDialogVisible = ref(false)
const duplicateCandidates = ref<DuplicateCandidateVO[]>([])

const isEdit = computed(() => !!route.query.id)
const outputId = ref<string>('')

const formData = reactive({
  outputType: '',
  title: '',
  doi: '',
  patentNo: '',
  completedAt: '',
  evidenceFileIds: [] as string[]
})

const legacyMetadata = ref<Record<string, unknown>>({})
const metadataValues = reactive<Record<string, string | number>>({})

type MetadataFieldConfig = {
  key: string
  label: string
  type: 'TEXT' | 'NUMBER' | 'LIST'
  placeholder?: string
}

const metadataTemplates: Record<OutputType, MetadataFieldConfig[]> = {
  PAPER: [
    { key: 'journal', label: '期刊名称', type: 'TEXT' },
    { key: 'authors', label: '作者', type: 'LIST', placeholder: '如：张三，李四' },
    { key: 'doi', label: '元数据 DOI', type: 'TEXT' }
  ],
  PATENT: [
    { key: 'inventors', label: '发明人', type: 'LIST', placeholder: '如：张三，李四' },
    { key: 'application_no', label: '申请号', type: 'TEXT' }
  ],
  SOFTWARE: [
    { key: 'registration_no', label: '登记号', type: 'TEXT' },
    { key: 'software_version', label: '软件版本', type: 'TEXT' }
  ],
  STANDARD: [
    { key: 'standard_no', label: '标准号', type: 'TEXT' },
    { key: 'standard_level', label: '标准级别', type: 'TEXT' }
  ],
  AWARD: [
    { key: 'award_level', label: '获奖等级', type: 'TEXT' },
    { key: 'award_year', label: '获奖年份', type: 'NUMBER' }
  ],
  REPORT: [
    { key: 'report_type', label: '报告类型', type: 'TEXT' }
  ],
  OTHER: []
}

const metadataFields = computed<MetadataFieldConfig[]>(
  () => metadataTemplates[formData.outputType as OutputType] || []
)

const clearMetadataValues = () => {
  Object.keys(metadataValues).forEach(key => delete metadataValues[key])
}

const handleOutputTypeChange = () => {
  clearMetadataValues()
  legacyMetadata.value = {}
}

const loadMetadataValues = (metadata?: Record<string, unknown>) => {
  clearMetadataValues()
  const knownKeys = new Set(metadataFields.value.map(field => field.key))
  legacyMetadata.value = Object.fromEntries(
    Object.entries(metadata || {}).filter(([key]) => !knownKeys.has(key))
  )

  metadataFields.value.forEach(field => {
    const value = metadata?.[field.key]
    if (value === null || value === undefined || value === '') return
    if (field.type === 'NUMBER' && typeof value === 'number' && Number.isFinite(value)) {
      metadataValues[field.key] = value
      return
    }
    if (field.type === 'LIST' && Array.isArray(value)) {
      metadataValues[field.key] = value.join('，')
      return
    }
    metadataValues[field.key] = String(value)
  })
}

const formRules: FormRules = {
  outputType: [
    { required: true, message: '请选择成果类型', trigger: 'change' }
  ],
  title: [
    { required: true, message: '请输入成果标题', trigger: 'blur' },
    { min: 2, max: 200, message: '标题长度为 2-200 个字符', trigger: 'blur' }
  ],
  completedAt: [
    { required: true, message: '请选择完成日期', trigger: 'change' }
  ]
}

onMounted(() => {
  if (isEdit.value) {
    loadOutputDetail()
  }
})

const loadOutputDetail = async () => {
  const id = route.query.id as string
  if (!id) return

  loading.value = true
  try {
    const detail = await outputApi.detail(id)
    outputId.value = detail.id
    formData.outputType = detail.outputType
    formData.title = detail.title
    formData.doi = detail.doi || ''
    formData.patentNo = detail.patentNo || ''
    formData.completedAt = detail.completedAt
    loadMetadataValues(detail.metadata)
    formData.evidenceFileIds = detail.evidenceFileIds || []
  } catch (error) {
    ElMessage.error('加载成果详情失败')
    router.back()
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  router.back()
}

const handleSaveDraft = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请填写必填项')
    return
  }

  saving.value = true
  try {
    const request = buildRequest()

    if (isEdit.value && outputId.value) {
      await outputApi.saveDraft(outputId.value, request)
    } else {
      const result = await outputApi.create(request)
      outputId.value = result.outputId
    }

    ElMessage.success('保存成功')
    router.push('/outputs')
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请填写必填项')
    return
  }

  submitting.value = true
  try {
    const request = buildRequest()

    // 先创建或保存草稿，拿到 outputId
    let candidates: DuplicateCandidateVO[] | undefined
    if (!outputId.value) {
      const result = await outputApi.create(request)
      outputId.value = result.outputId
      candidates = result.duplicateCandidates
    } else {
      await outputApi.saveDraft(outputId.value, request)
    }

    // 提交前查重（BR-21：只提示，由人决策是否继续）
    if (!candidates) {
      const duplicatesRes = await outputApi.duplicates(outputId.value)
      candidates = duplicatesRes.items
    }
    if (candidates && candidates.length > 0) {
      duplicateCandidates.value = candidates
      duplicateDialogVisible.value = true
      return
    }

    await doSubmit()
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

const doSubmit = async () => {
  await outputApi.submit(outputId.value)
  ElMessage.success('提交成功')
  router.push('/outputs')
}

const confirmSubmit = async () => {
  submitting.value = true
  try {
    await doSubmit()
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

const buildMetadata = (): Record<string, unknown> | undefined => {
  const metadata: Record<string, unknown> = { ...legacyMetadata.value }

  metadataFields.value.forEach(field => {
    const rawValue = metadataValues[field.key]
    if (rawValue === undefined || rawValue === null || rawValue === '') return

    if (field.type === 'NUMBER') {
      metadata[field.key] = Number(rawValue)
      return
    }
    if (field.type === 'LIST') {
      metadata[field.key] = String(rawValue)
        .split(/[，,]/)
        .map(item => item.trim())
        .filter(Boolean)
      return
    }
    metadata[field.key] = String(rawValue).trim()
  })

  return Object.keys(metadata).length > 0 ? metadata : undefined
}

const buildRequest = (): CreateOutputRequest => {
  const metadata = buildMetadata()

  return {
    outputType: formData.outputType as OutputType,
    title: formData.title,
    doi: formData.doi || undefined,
    patentNo: formData.patentNo || undefined,
    completedAt: formData.completedAt,
    ownerId: userStore.userId,
    deptId: userStore.departmentId,
    metadata,
    evidenceFileIds: formData.evidenceFileIds.length > 0 ? formData.evidenceFileIds : undefined
  }
}
</script>

<style scoped lang="scss">
.output-create-page {
  .form-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
  }
}
</style>
