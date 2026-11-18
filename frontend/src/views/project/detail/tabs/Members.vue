<template>
  <div class="project-members">
    <div class="actions">
      <el-button
        type="primary"
        v-permission="'project:member:manage'"
        @click="openAddDialog"
      >
        <el-icon><Plus /></el-icon>
        新增成员
      </el-button>
    </div>

    <el-table v-loading="loading" :data="members" border stripe>
      <el-table-column prop="userName" label="姓名" width="120" />
      <el-table-column prop="projectRole" label="项目角色" width="150" />
      <el-table-column prop="contributionDesc" label="贡献描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="validFrom" label="加入日期" width="120">
        <template #default="{ row }">
          {{ formatDate(row.validFrom) }}
        </template>
      </el-table-column>
      <el-table-column prop="validTo" label="离开日期" width="120">
        <template #default="{ row }">
          {{ row.validTo ? formatDate(row.validTo) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" />
    </el-table>

    <!-- 新增成员弹窗 -->
    <el-dialog v-model="showAddDialog" title="新增项目成员" width="500px">
      <el-form :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="用户" prop="userId">
          <el-select
            v-model="addForm.userId"
            placeholder="输入姓名或工号搜索"
            filterable
            remote
            clearable
            :remote-method="searchUsers"
            :loading="loadingUsers"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.display_name}（${user.employee_no}${user.dept_name ? '，' + user.dept_name : ''}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目角色" prop="projectRole">
          <el-select v-model="addForm.projectRole" placeholder="请选择">
            <el-option label="项目负责人" value="PI" />
            <el-option label="核心成员" value="CORE_MEMBER" />
            <el-option label="普通成员" value="MEMBER" />
            <el-option label="外部专家" value="EXTERNAL_EXPERT" />
          </el-select>
        </el-form-item>
        <el-form-item label="贡献描述" prop="contributionDesc">
          <el-input
            v-model="addForm.contributionDesc"
            type="textarea"
            :rows="3"
            placeholder="请输入成员贡献描述"
          />
        </el-form-item>
        <el-form-item label="生效日期" prop="validFrom">
          <el-date-picker
            v-model="addForm.validFrom"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAddMember">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/date'
import { projectApi, ProjectMemberVO } from '@/api/project'
import { organizationApi, type UserOption } from '@/api/organization'

interface Props {
  projectId: string
}

const props = defineProps<Props>()

const loading = ref(false)
const members = ref<ProjectMemberVO[]>([])
const showAddDialog = ref(false)
const submitting = ref(false)
const loadingUsers = ref(false)
const userOptions = ref<UserOption[]>([])

const addForm = reactive({
  userId: '',
  projectRole: '',
  contributionDesc: '',
  validFrom: ''
})

const addRules = {
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }],
  projectRole: [{ required: true, message: '请选择项目角色', trigger: 'change' }]
}

onMounted(() => {
  fetchMembers()
})

const fetchMembers = async () => {
  loading.value = true
  try {
    members.value = await projectApi.members(props.projectId)
  } catch (error) {
    ElMessage.error('加载成员列表失败')
  } finally {
    loading.value = false
  }
}

const handleAddMember = async () => {
  submitting.value = true
  try {
    await projectApi.addMember(props.projectId, {
      ...addForm,
      validFrom: addForm.validFrom
        ? new Date(`${addForm.validFrom}T00:00:00+08:00`).toISOString()
        : undefined
    })
    ElMessage.success('添加成功')
    showAddDialog.value = false
    fetchMembers()
  } catch (error) {
    ElMessage.error('添加失败')
  } finally {
    submitting.value = false
  }
}

const searchUsers = async (keyword: string) => {
  loadingUsers.value = true
  try {
    userOptions.value = await organizationApi.users(keyword)
  } catch {
    userOptions.value = []
  } finally {
    loadingUsers.value = false
  }
}

const openAddDialog = () => {
  addForm.userId = ''
  addForm.projectRole = ''
  addForm.contributionDesc = ''
  addForm.validFrom = ''
  showAddDialog.value = true
  searchUsers('')
}
</script>

<style scoped lang="scss">
.project-members {
  .actions {
    margin-bottom: 16px;
  }
}
</style>
