<template>
  <div class="application-create-page">
    <PageHeader
      :title="isEdit ? '编辑申报' : '新建申报'"
      :subtitle="formData.title || ''"
    />

    <el-card shadow="never" v-loading="loading">
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="申报批次" prop="batchId">
          <el-select
            v-model="formData.batchId"
            placeholder="请选择开放批次"
            filterable
            :disabled="isEdit"
            style="width: 100%"
            @change="handleProjectTypeChange"
          >
            <el-option
              v-for="batch in options.batches"
              :key="batch.id"
              :label="`${batch.name}（${batch.batch_no}）`"
              :value="batch.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="项目类型" prop="projectType">
          <el-select
            v-model="formData.projectType"
            placeholder="请选择项目类型"
            :disabled="isEdit"
            style="width: 100%"
          >
            <el-option
              v-for="item in options.project_types"
              :key="item.project_type"
              :label="projectTypeLabel(item.project_type)"
              :value="item.project_type"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="所属部门" prop="deptId">
          <el-select
            v-model="formData.deptId"
            placeholder="请选择所属部门"
            filterable
            :disabled="isEdit"
            style="width: 100%"
          >
            <el-option
              v-for="unit in orgUnits"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="formData.title"
            placeholder="请输入申报标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item
          v-for="field in formFields"
          :key="field.key"
          :label="field.title"
          :prop="`formData.formData.${field.key}`"
          :required="field.required"
        >
          <el-select
            v-if="field.enumOptions?.length"
            :model-value="formValue(field.key)"
            placeholder="请选择"
            clearable
            style="width: 100%"
            @update:model-value="value => setSelectFormValue(field.key, value)"
          >
            <el-option
              v-for="option in field.enumOptions"
              :key="String(option.value)"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input-number
            v-else-if="field.type === 'number'"
            :model-value="numberFormValue(field.key)"
            :min="field.minimum"
            :max="field.maximum"
            :precision="2"
            controls-position="right"
            style="width: 100%"
            @update:model-value="value => setNumberFormValue(field.key, value)"
          />
          <el-switch
            v-else-if="field.type === 'boolean'"
            :model-value="booleanFormValue(field.key)"
            @update:model-value="value => setBooleanFormValue(field.key, value)"
          />
          <el-date-picker
            v-else-if="field.type === 'date'"
            :model-value="dateFormValue(field.key)"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
            style="width: 100%"
            @update:model-value="value => setDateFormValue(field.key, value)"
          />
          <el-input
            v-else-if="field.type === 'string'"
            :model-value="textFormValue(field.key)"
            :type="(field.maxLength || 0) >= 200 ? 'textarea' : 'text'"
            :rows="(field.maxLength || 0) >= 200 ? 5 : undefined"
            :maxlength="field.maxLength"
            :show-word-limit="Boolean(field.maxLength)"
            :placeholder="`请输入${field.title}`"
            @update:model-value="value => setTextFormValue(field.key, value)"
          />
          <el-alert
            v-else
            :title="`字段 ${field.title} 的模板类型暂不支持在线录入`"
            type="warning"
            :closable="false"
          />
        </el-form-item>
        <el-form-item v-if="!loading && !formFields.length" label="表单数据">
          <el-alert
            title="当前项目类型模板未配置表单字段"
            description="请联系管理员发布包含表单字段的申报模板。"
            type="warning"
            :closable="false"
            show-icon
          />
        </el-form-item>

        <el-form-item label="附件">
          <div v-for="(item, index) in formData.attachments" :key="index" class="attachment-item">
            <el-select v-model="item.purpose" placeholder="用途" style="width: 180px; margin-right: 8px;">
              <el-option label="伦理审查" value="ETHICS" />
              <el-option label="盖章封面" value="COVER" />
              <el-option label="其他材料" value="OTHER" />
            </el-select>
            <FileUpload
              v-model="item.fileIds"
              :max-count="1"
              style="display: inline-block; margin-right: 8px;"
            />
            <el-button type="danger" text @click="removeAttachment(index)">删除</el-button>
          </div>
          <el-button type="primary" plain @click="addAttachment">
            <el-icon><Plus /></el-icon>
            添加附件
          </el-button>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
            提交审核
          </el-button>
          <el-button :loading="draftLoading" @click="handleSaveDraft">
            保存草稿
          </el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import FileUpload from '@/components/FileUpload/FileUpload.vue'
import {
  applicationApi,
  parseApplicationFormSchema,
  type ApplicationFormField,
  type ApplicationOptionsView,
  type SubmitApplicationRequest
} from '@/api/application'
import { organizationApi, type OrgUnitOption } from '@/api/organization'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitLoading = ref(false)
const draftLoading = ref(false)
const options = reactive<ApplicationOptionsView>({ batches: [], project_types: [] })
const orgUnits = ref<OrgUnitOption[]>([])

const isEdit = computed(() => route.query.mode === 'edit' && !!route.query.id)
const applicationId = ref<string>((route.query.id as string) || '')

interface AttachmentItem {
  purpose: string
  fileIds: string[]
}

const formData = reactive({
  batchId: '',
  title: '',
  projectType: '',
  deptId: '',
  formData: {} as Record<string, unknown>,
  attachments: [] as AttachmentItem[],
  version: 0
})

const currentTemplate = computed(() => options.project_types.find(
  item => item.project_type === formData.projectType
))
const formFields = computed(() => parseApplicationFormSchema(currentTemplate.value?.form_schema))

const formFieldRules = computed(() => {
  const result: FormRules = {}
  formFields.value.filter(field => field.required).forEach(field => {
    result[`formData.formData.${field.key}`] = [{
      required: true,
      message: `请填写${field.title}`,
      trigger: field.type === 'number' || field.type === 'boolean' ? 'change' : 'blur'
    }]
  })
  return result
})

const rules = computed<FormRules>(() => ({
  batchId: [{ required: true, message: '请选择申报批次', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  ...formFieldRules.value
}))

const projectTypeLabel = (type: string): string => {
  const labels: Record<string, string> = {
    GENERAL: '一般项目',
    KEY: '重点项目',
    YOUTH: '青年项目'
  }
  return labels[type] || type
}

const defaultFormValue = (field: ApplicationFormField) => {
  if (field.type === 'number') return undefined
  if (field.type === 'boolean') return false
  if (field.type === 'string') {
    return field.enumOptions?.length ? field.enumOptions[0]?.value : ''
  }
  if (field.type === 'date') return ''
  return null
}

const withFormDefaults = (values: Record<string, unknown> = {}) => {
  const result: Record<string, unknown> = { ...values }
  formFields.value.forEach(field => {
    if (result[field.key] === undefined || result[field.key] === null) {
      result[field.key] = defaultFormValue(field)
    }
  })
  return result
}

const formValue = (key: string) => {
  const value = formData.formData[key]
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
    ? value
    : undefined
}

const setSelectFormValue = (
  key: string,
  value: string | number | boolean | undefined | null
) => {
  formData.formData[key] = value ?? ''
}

const numberFormValue = (key: string): number | undefined => {
  const value = formData.formData[key]
  return typeof value === 'number' ? value : undefined
}

const setNumberFormValue = (key: string, value: number | undefined | null) => {
  formData.formData[key] = value ?? undefined
}

const booleanFormValue = (key: string): boolean => formData.formData[key] === true

const setBooleanFormValue = (key: string, value: boolean | string | number | undefined) => {
  formData.formData[key] = value === true
}

const dateFormValue = (key: string): string | undefined => {
  const value = formData.formData[key]
  return typeof value === 'string' && value ? value : undefined
}

const setDateFormValue = (key: string, value: string | undefined | null) => {
  formData.formData[key] = value || ''
}

const textFormValue = (key: string): string | number | undefined => {
  const value = formData.formData[key]
  return typeof value === 'string' || typeof value === 'number' ? value : undefined
}

const setTextFormValue = (key: string, value: string | number | undefined | null) => {
  formData.formData[key] = value ?? ''
}

const handleProjectTypeChange = () => {
  formData.formData = withFormDefaults({})
}

onMounted(async () => {
  loading.value = true
  try {
    const [applicationOptions, units] = await Promise.all([
      applicationApi.options(),
      organizationApi.orgUnits()
    ])
    options.batches = applicationOptions.batches || []
    options.project_types = applicationOptions.project_types || []
    orgUnits.value = units || []

    if (isEdit.value) {
      await loadApplication()
    } else {
      formData.batchId = options.batches[0]?.id || ''
      formData.projectType = options.project_types[0]?.project_type || ''
      formData.deptId = userStore.departmentId || orgUnits.value[0]?.id || ''
      await nextTick()
      formData.formData = withFormDefaults({})
    }
  } catch {
    ElMessage.error('加载申报选项失败')
  } finally {
    loading.value = false
  }
})

const loadApplication = async () => {
  if (!applicationId.value) return
  const detail = await applicationApi.detail(applicationId.value)
  formData.batchId = detail.batchId || ''
  formData.title = detail.title
  formData.projectType = detail.projectType
  formData.deptId = detail.deptId || ''
  formData.version = detail.version
  formData.formData = detail.formData || {}
  await nextTick()
  formData.formData = withFormDefaults(detail.formData)
  formData.attachments = detail.attachments.length
    ? detail.attachments.map(item => ({ purpose: item.purpose, fileIds: [item.fileId] }))
    : []
}

const addAttachment = () => {
  formData.attachments.push({ purpose: 'OTHER', fileIds: [] })
}

const removeAttachment = (index: number) => {
  formData.attachments.splice(index, 1)
}

const attachmentInputs = () => formData.attachments.flatMap(item => {
  const fileId = item.fileIds[0]
  return fileId ? [{ purpose: item.purpose || 'OTHER', fileId }] : []
})

const ensureCreated = async (): Promise<boolean> => {
  if (applicationId.value) return true
  if (!formRef.value) return false
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return false

  const created = await applicationApi.create({
    batchId: formData.batchId,
    projectType: formData.projectType,
    title: formData.title,
    deptId: formData.deptId
  })
  applicationId.value = created.id
  formData.version = created.version
  await router.replace({
    path: '/applications/create',
    query: { id: created.id, mode: 'edit' }
  })
  return true
}

const saveDraftInternal = async () => {
  formData.formData = withFormDefaults(formData.formData)
  const saved = await applicationApi.saveDraft(applicationId.value, {
    title: formData.title,
    formData: formData.formData,
    expectedVersion: formData.version
  })
  formData.version = saved.version
}

const handleSaveDraft = async () => {
  draftLoading.value = true
  try {
    if (!(await ensureCreated())) return
    await saveDraftInternal()
    ElMessage.success('草稿保存成功')
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    draftLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (!(await ensureCreated())) return
    await saveDraftInternal()

    const submitData: SubmitApplicationRequest = {
      formData: formData.formData,
      attachments: attachmentInputs(),
      expectedVersion: formData.version,
      comment: '本地提交'
    }
    await applicationApi.submit(applicationId.value, submitData)
    ElMessage.success('提交成功，已进入审批流程')
    router.push('/applications')
  } catch (error: any) {
    ElMessage.error(error?.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}

const handleCancel = () => {
  router.push('/applications')
}
</script>

<style scoped lang="scss">
.application-create-page {
  .attachment-item {
    display: flex;
    align-items: center;
    margin-bottom: 8px;
  }

  .form-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
  }
}
</style>
