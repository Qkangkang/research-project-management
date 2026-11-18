<template>
  <div class="basic-info">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="项目编号">{{ project.projectNo }}</el-descriptions-item>
      <el-descriptions-item label="项目状态">
        <StatusTag v-if="project.status" :status="project.status" domain="project" />
      </el-descriptions-item>
      <el-descriptions-item label="项目名称" :span="2">{{ project.title }}</el-descriptions-item>
      <el-descriptions-item label="项目类型">{{ project.projectType }}</el-descriptions-item>
      <el-descriptions-item label="负责人">{{ project.piName }}</el-descriptions-item>
      <el-descriptions-item label="所属部门">{{ project.deptName }}</el-descriptions-item>
      <el-descriptions-item label="预算总额">{{ formatMoney(project.totalBudget) }}</el-descriptions-item>
      <el-descriptions-item label="开始日期">{{ formatDate(project.startDate) }}</el-descriptions-item>
      <el-descriptions-item label="结束日期">{{ formatDate(project.endDate) }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatDateTime(project.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ formatDateTime(project.updatedAt) }}</el-descriptions-item>
      <el-descriptions-item v-if="project.description" label="项目描述" :span="2">
        {{ project.description }}
      </el-descriptions-item>
    </el-descriptions>

    <div v-if="project.memberCount || project.milestoneCount || project.riskCount" class="stats">
      <el-statistic title="项目成员" :value="project.memberCount || 0" />
      <el-statistic title="里程碑" :value="project.milestoneCount || 0" />
      <el-statistic title="风险数量" :value="project.riskCount || 0" />
      <el-statistic title="成果数量" :value="project.outputCount || 0" />
    </div>
  </div>
</template>

<script setup lang="ts">
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { formatMoney } from '@/utils/number'
import { formatDate, formatDateTime } from '@/utils/date'
import type { ProjectDetailSummary } from '@/api/project'

interface Props {
  project: Partial<ProjectDetailSummary>
}

defineProps<Props>()
</script>

<style scoped lang="scss">
.basic-info {
  .stats {
    display: flex;
    justify-content: space-around;
    margin-top: 32px;
    padding: 24px;
    background: var(--el-fill-color-light);
    border-radius: 4px;
  }
}
</style>
