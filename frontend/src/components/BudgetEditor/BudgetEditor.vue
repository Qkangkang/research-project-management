<template>
  <div class="budget-editor">
    <el-table :data="lines" border stripe>
      <el-table-column label="科目代码" prop="subjectCode" width="150">
        <template #default="{ row }">
          <el-input
            v-model="row.subjectCode"
            placeholder="请输入科目代码"
            :disabled="disabled"
            @blur="validateDuplicate"
          />
        </template>
      </el-table-column>

      <el-table-column label="科目名称" prop="subjectName" min-width="200">
        <template #default="{ row }">
          <el-input
            v-model="row.subjectName"
            placeholder="请输入科目名称"
            :disabled="disabled"
          />
        </template>
      </el-table-column>

      <el-table-column label="金额（元）" prop="amount" width="180">
        <template #default="{ row }">
          <el-input-number
            v-model="row.amount"
            :precision="2"
            :min="0"
            :max="999999999.99"
            :disabled="disabled"
            controls-position="right"
            style="width: 100%"
            @change="handleAmountChange"
          />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ $index }">
          <el-button
            type="danger"
            size="small"
            text
            :disabled="disabled || lines.length === 1"
            @click="removeLine($index)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="budget-editor__footer">
      <el-button
        type="primary"
        plain
        :disabled="disabled"
        @click="addLine"
      >
        <el-icon><Plus /></el-icon>
        新增科目
      </el-button>

      <div class="total">
        <span class="label">预算总额：</span>
        <span class="value">{{ formatMoney(totalAmount) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { formatMoney, addMoney } from '@/utils/number'

export interface BudgetLineInput {
  subjectCode: string
  subjectName: string
  amount: number | null
}

interface Props {
  modelValue: BudgetLineInput[]
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: BudgetLineInput[]]
  'total-change': [total: number]
}>()

const lines = ref<BudgetLineInput[]>([])

// 监听 modelValue 变化
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal && newVal.length > 0) {
      lines.value = JSON.parse(JSON.stringify(newVal))
    } else {
      lines.value = [createEmptyLine()]
    }
  },
  { immediate: true, deep: true }
)

// 计算总额
const totalAmount = computed(() => {
  return lines.value.reduce((sum, line) => {
    return addMoney(sum, line.amount || 0)
  }, 0)
})

// 监听总额变化
watch(totalAmount, (newTotal) => {
  emit('total-change', newTotal)
})

const createEmptyLine = (): BudgetLineInput => ({
  subjectCode: '',
  subjectName: '',
  amount: null
})

const addLine = () => {
  lines.value.push(createEmptyLine())
  emitChange()
}

const removeLine = (index: number) => {
  if (lines.value.length === 1) {
    ElMessage.warning('至少保留一行')
    return
  }
  lines.value.splice(index, 1)
  emitChange()
}

const handleAmountChange = () => {
  emitChange()
}

const validateDuplicate = () => {
  const codes = lines.value.map(line => line.subjectCode).filter(Boolean)
  const uniqueCodes = new Set(codes)

  if (codes.length !== uniqueCodes.size) {
    ElMessage.warning('科目代码不能重复')
  }
}

const emitChange = () => {
  emit('update:modelValue', lines.value)
}
</script>

<style scoped lang="scss">
.budget-editor {
  &__footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 16px;

    .total {
      font-size: 16px;
      font-weight: 600;

      .label {
        color: var(--el-text-color-secondary);
      }

      .value {
        color: var(--el-color-primary);
        margin-left: 8px;
      }
    }
  }
}
</style>
