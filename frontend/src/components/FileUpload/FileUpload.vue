<template>
  <div class="file-upload">
    <el-upload
      :file-list="fileList"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :accept="accept"
      :before-upload="handleBeforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :on-preview="handlePreview"
      :disabled="disabled"
      :list-type="listType"
      :limit="maxCount"
      :on-exceed="handleExceed"
    >
      <template v-if="listType === 'picture-card'" #default>
        <el-icon><Plus /></el-icon>
      </template>
      <template v-else #default>
        <el-button type="primary" :disabled="disabled">
          <el-icon><Upload /></el-icon>
          点击上传
        </el-button>
      </template>
      <template #tip>
        <div class="el-upload__tip">
          {{ uploadTip }}
        </div>
      </template>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import type { UploadFile, UploadUserFile } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fileApi, type UploadedFileView } from '@/api/file'

interface Props {
  modelValue: string[]        // 文件 ID 数组
  accept?: string             // 如 '.pdf,.docx'
  maxSizeMB?: number          // 默认 200
  maxCount?: number           // 默认 5
  disabled?: boolean
  listType?: 'text' | 'picture-card'
}

const props = withDefaults(defineProps<Props>(), {
  accept: '',
  maxSizeMB: 200,
  maxCount: 5,
  disabled: false,
  listType: 'text'
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  'update:files': [files: UploadedFileView[]]
}>()

const userStore = useUserStore()

const uploadUrl = computed(() => {
  return `${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/files/upload`
})

const uploadHeaders = computed(() => {
  return {
    Authorization: `Bearer ${userStore.token}`
  }
})

const uploadTip = computed(() => {
  const accepts = props.accept || '所有文件'
  return `支持 ${accepts}，单个文件不超过 ${props.maxSizeMB}MB，最多上传 ${props.maxCount} 个文件`
})

// 文件列表（用于 UI 展示）
const fileList = ref<UploadUserFile[]>([])

const uploadedNames = new Map<string, string>()
const uploadedFiles = ref<Record<string, UploadedFileView>>({})

const currentUploadedFiles = (fileIds: string[]): UploadedFileView[] => {
  return fileIds.flatMap(fileId => {
    const file = uploadedFiles.value[fileId]
    return file ? [file] : []
  })
}

// 监听 modelValue 变化，初始化文件列表
watch(
  () => props.modelValue,
  (newVal) => {
    Object.keys(uploadedFiles.value).forEach(fileId => {
      if (!newVal.includes(fileId)) delete uploadedFiles.value[fileId]
    })
    if (newVal && newVal.length > 0) {
      fileList.value = newVal.map((fileId, index) => ({
        name: uploadedNames.get(fileId) || `文件 ${index + 1}`,
        url: '',
        uid: Date.now() + index,
        status: 'success'
      }))
    } else {
      fileList.value = []
    }
  },
  { immediate: true }
)

const handleBeforeUpload = (file: File): boolean => {
  const sizeMB = file.size / 1024 / 1024
  if (sizeMB > props.maxSizeMB) {
    ElMessage.error(`文件大小不能超过 ${props.maxSizeMB}MB`)
    return false
  }
  return true
}

const handleSuccess = (response: any, uploadFile: UploadFile) => {
  const payload = response?.data ?? response
  const fileId = payload?.file_id || payload?.fileId || payload?.id
  if (!fileId) {
    ElMessage.error('上传成功但未返回文件 ID')
    return
  }

  uploadedNames.set(fileId, payload?.original_name || uploadFile.name)
  uploadedFiles.value[fileId] = {
    fileId,
    originalName: payload?.original_name || uploadFile.name,
    sizeBytes: payload?.size_bytes || payload?.sizeBytes || 0,
    sha256: payload?.sha256
  }

  // 更新 modelValue
  const newFileIds = [...props.modelValue, fileId]
  emit('update:modelValue', newFileIds)
  emit('update:files', currentUploadedFiles(newFileIds))
  fileList.value = newFileIds.map((id, index) => ({
    name: uploadedNames.get(id) || `文件 ${index + 1}`,
    uid: Date.now() + index,
    status: 'success'
  }))

  ElMessage.success('上传成功')
}

const handleError = () => {
  ElMessage.error('上传失败')
}

const handleRemove = (uploadFile: UploadFile) => {
  // 根据索引移除对应的文件 ID
  const index = fileList.value.findIndex(f => f.uid === uploadFile.uid)
  if (index !== -1) {
    const newFileIds = [...props.modelValue]
    const fileId = newFileIds[index]
    newFileIds.splice(index, 1)
    if (fileId) delete uploadedFiles.value[fileId]
    emit('update:modelValue', newFileIds)
    emit('update:files', currentUploadedFiles(newFileIds))
  }
}

const handleExceed = () => {
  ElMessage.warning(`最多上传 ${props.maxCount} 个文件`)
}

const handlePreview = async (uploadFile: UploadFile) => {
  const index = fileList.value.findIndex(f => f.uid === uploadFile.uid)
  const fileId = index >= 0 ? props.modelValue[index] : undefined
  if (!fileId) {
    ElMessage.warning('未找到文件标识')
    return
  }
  try {
    await fileApi.download(fileId)
  } catch {
    ElMessage.error('获取下载地址失败')
  }
}
</script>

<style scoped lang="scss">
.file-upload {
  :deep(.el-upload__tip) {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 8px;
  }
}
</style>
