<template>
  <div class="project-changes">
    <div class="actions">
      <el-button
        type="primary"
        v-permission="'project:change:create'"
        @click="showCreateDialog = true"
      >
        <el-icon><Plus /></el-icon>
        发起变更
      </el-button>
    </div>

    <el-alert
      title="变更记录"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px;"
    >
      变更单提交后将进入审批流程，请在审批中心查看审批进度
    </el-alert>

    <p class="placeholder">变更记录列表待后端提供查询接口</p>

    <!-- 发起变更弹窗 -->
    <el-dialog v-model="showCreateDialog" title="发起项目变更" width="600px">
      <el-form :model="changeForm" label-width="100px">
        <el-form-item label="变更类型" required>
          <el-select v-model="changeForm.changeType" placeholder="请选择">
            <el-option label="预算变更" value="BUDGET" />
            <el-option label="周期变更" value="SCHEDULE" />
            <el-option label="范围变更" value="SCOPE" />
            <el-option label="人员变更" value="PERSONNEL" />
            <el-option label="其他变更" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期" required>
          <el-date-picker
            v-model="changeForm.effectiveDate"
            type="date"
            placeholder="选择生效日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="变更内容">
          <el-input
            v-model="changeForm.payload"
            type="textarea"
            :rows="4"
            placeholder="请输入变更内容（JSON格式）"
          />
        </el-form-item>
        <el-form-item label="变更原因" required>
          <el-input
            v-model="changeForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入变更原因"
          />
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="changeForm.attachmentIds" :max-count="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateChange">
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import FileUpload from '@/components/FileUpload/FileUpload.vue'
import { projectApi } from '@/api/project'

interface Props {
  projectId: string
}

const props = defineProps<Props>()

const showCreateDialog = ref(false)
const submitting = ref(false)

const changeForm = reactive({
  changeType: '',
  effectiveDate: '',
  payload: '',
  reason: '',
  attachmentIds: [] as string[]
})

const handleCreateChange = async () => {
  submitting.value = true
  try {
    let payload = {}
    if (changeForm.payload.trim()) {
      payload = JSON.parse(changeForm.payload)
    }

    await projectApi.createChange(props.projectId, {
      changeType: changeForm.changeType,
      effectiveDate: changeForm.effectiveDate,
      payload,
      reason: changeForm.reason,
      attachmentFileIds: changeForm.attachmentIds
    })

    ElMessage.success('变更申请已提交')
    showCreateDialog.value = false
  } catch (error) {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.project-changes {
  .actions {
    margin-bottom: 16px;
  }

  .placeholder {
    padding: 40px 20px;
    text-align: center;
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}
</style>
