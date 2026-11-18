import api, { generateIdempotencyKey } from './base'

// 预算控制级别（与后端 budget_version.control_level 对齐）
export type ControlLevel = 'BLOCK' | 'WARN' | 'APPROVAL'

// 支出状态
export type ExpenseStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'SUPPLEMENT'
  | 'APPROVED_UNPAID'
  | 'PARTIALLY_PAID'
  | 'PAID'
  | 'CANCELLED'
  | 'REJECTED'
  | 'REVERSED'

// 对账差异状态
export type DiffStatus = 'OPEN' | 'PROCESSING' | 'CLOSED' | 'IGNORED'

export interface BudgetLine {
  subjectCode: string
  subjectName: string
  amount: number
}

export interface BudgetLineDetail {
  budgetLineId: string
  subjectCode: string
  subjectName: string
  amount: number
  approvedAmount: number
  reservedAmount: number
  approvedUnpaidAmount: number
  paidAmount: number
  availableAmount: number
}

export interface BudgetVersionSummary {
  budgetVersionId: string
  versionNo: number
  totalAmount: number
  status: string
  controlLevel: ControlLevel
  effectiveFrom?: string
  effectiveTo?: string
}

export interface BudgetVersionDetail extends BudgetVersionSummary {
  projectId: string
  lines: BudgetLineDetail[]
}

export interface BudgetOverview {
  versions: BudgetVersionSummary[]
  effectiveBudget?: BudgetVersionDetail
}

export interface FundLineSummary {
  budgetLineId: string
  subjectCode: string
  subjectName: string
  approvedAmount: number
  reservedAmount: number
  approvedUnpaidAmount: number
  paidAmount: number
  availableAmount: number
}

export interface FundSummary {
  dataAsOf: string
  totalApprovedAmount: number
  totalReservedAmount: number
  totalApprovedUnpaidAmount: number
  totalPaidAmount: number
  totalAvailableAmount: number
  lines: FundLineSummary[]
}

export interface ExpenseView {
  expenseId: string
  requestNo: string
  projectId: string
  budgetVersionId: string
  budgetLineId?: string
  amount: number
  purpose: string
  payeeName: string
  accountMasked: string
  status: ExpenseStatus
  currentVersionId?: string
  version: number
  submittedAt?: string
  createdAt: string
}

export interface ExpenseSubmitted {
  expenseId: string
  fundEventId: string
  versionNo: number
  availableAmount: number
  riskWarning?: string
  workflowInstanceId?: string
}

export interface ExpenseStatusResult {
  expenseId: string
  status: ExpenseStatus
  releasedAmount?: number
}

export interface PaymentImportBatchView {
  batchId: string
  batchNo: string
  status: string
  totalRows: number
  successRows: number
  errorRows: number
  appliedBy?: string
  idempotent: boolean
}

export interface ReconciliationDiffView {
  diffId: string
  batchId: string
  projectId?: string
  diffType: string
  externalPaymentNo?: string
  systemAmount?: number
  financeAmount?: number
  ownerId?: string
  status: DiffStatus
  resolution?: string
  evidenceFileIds?: string[]
  closedBy?: string
  closedAt?: string
}

export interface ReconciliationBatchView {
  batchId: string
  batchNo: string
  periodStart: string
  periodEnd: string
  status: string
  totalFinanceAmount?: number
  totalSystemAmount?: number
  diffCount: number
  diffs: ReconciliationDiffView[]
}

export interface CreateBudgetRequest {
  sourceType: string
  controlLevel: ControlLevel
  effectiveFrom?: string
  effectiveTo?: string
  totalAmount: number
  lines: BudgetLine[]
}

export interface CreateExpenseRequest {
  budgetVersionId: string
  budgetLineId?: string
  amount: number
  purpose: string
  payeeName: string
  payeeBankAccountMasked: string
  payeeBankAccountEncrypted: string
  payeeBankName: string
  idempotencyKey?: string
}

export interface SubmitExpenseRequest {
  expectedVersion: number
  budgetVersionId: string
  budgetLineId?: string
  amount: number
  purpose: string
  attachmentFileIds: string[]
  specialReason?: string
}

export interface ExpenseActionRequest {
  expectedVersion: number
  reason: string
}

export interface ReconciliationRunRequest {
  period_start: string
  period_end: string
  finance_batch_no?: string
  payments: {
    external_payment_no: string
    external_expense_no: string
    amount: number
  }[]
}

export interface CloseDiffRequest {
  resolution: string
  ownerId: string
  evidenceFileIds: string[]
}

export const fundingApi = {
  budgets(projectId: string): Promise<BudgetOverview> {
    return api.get(`/projects/${projectId}/budgets`)
  },

  createBudget(projectId: string, data: CreateBudgetRequest): Promise<BudgetVersionDetail> {
    return api.post(`/projects/${projectId}/budgets`, data)
  },

  submitBudget(projectId: string, budgetId: string): Promise<BudgetVersionDetail> {
    return api.post(`/projects/${projectId}/budgets/${budgetId}/submit`)
  },

  fundSummary(projectId: string): Promise<FundSummary> {
    return api.get(`/projects/${projectId}/funds/summary`)
  },

  createExpense(projectId: string, data: CreateExpenseRequest): Promise<ExpenseView> {
    return api.post(`/projects/${projectId}/expenses`, data)
  },

  listExpenses(projectId: string): Promise<ExpenseView[]> {
    return api.get(`/projects/${projectId}/expenses`)
  },

  expenseDetail(expenseId: string): Promise<ExpenseView> {
    return api.get(`/expenses/${expenseId}`)
  },

  submitExpense(expenseId: string, data: SubmitExpenseRequest): Promise<ExpenseSubmitted> {
    return api.post(`/expenses/${expenseId}/submit`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  withdrawExpense(expenseId: string, data: ExpenseActionRequest): Promise<ExpenseStatusResult> {
    return api.post(`/expenses/${expenseId}/withdraw`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  cancelExpense(expenseId: string, data: ExpenseActionRequest): Promise<ExpenseStatusResult> {
    return api.post(`/expenses/${expenseId}/cancel`, data, {
      headers: {
        'Idempotency-Key': generateIdempotencyKey()
      }
    })
  },

  uploadPaymentImport(
    fileId: string,
    sourceType: string,
    file: File
  ): Promise<PaymentImportBatchView> {
    const formData = new FormData()
    formData.append('fileId', fileId)
    formData.append('sourceType', sourceType)
    formData.append('file', file)

    return api.post('/finance/payments/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  validatePaymentImport(batchId: string): Promise<PaymentImportBatchView> {
    return api.post(`/finance/payments/import/${batchId}/validate`)
  },

  applyPaymentImport(batchId: string): Promise<PaymentImportBatchView> {
    return api.post(`/finance/payments/import/${batchId}/apply`)
  },

  listReconciliations(): Promise<ReconciliationBatchView[]> {
    return api.get('/finance/reconciliations')
  },

  runReconciliation(data: ReconciliationRunRequest): Promise<ReconciliationBatchView> {
    return api.post('/finance/reconciliations', data)
  },

  closeDiff(
    batchId: string,
    diffId: string,
    data: CloseDiffRequest
  ): Promise<ReconciliationDiffView> {
    return api.post(`/finance/reconciliations/${batchId}/diffs/${diffId}/close`, data)
  }
}
