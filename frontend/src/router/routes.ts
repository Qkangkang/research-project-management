import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', anonymous: true }
  },
  {
    path: '/',
    component: () => import('@/components/Layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'odometer' }
      },
      {
        path: 'projects',
        name: 'Projects',
        meta: { title: '项目管理', icon: 'folder' },
        children: [
          {
            path: '',
            name: 'ProjectList',
            component: () => import('@/views/project/list.vue'),
            meta: { title: '项目列表', permission: 'project:view' }
          },
          {
            path: ':id',
            name: 'ProjectDetail',
            component: () => import('@/views/project/detail/index.vue'),
            meta: { title: '项目详情', permission: 'project:view' }
          }
        ]
      },
      {
        path: 'applications',
        name: 'Applications',
        meta: { title: '申报管理', icon: 'document' },
        children: [
          {
            path: '',
            name: 'ApplicationList',
            component: () => import('@/views/application/list.vue'),
            meta: { title: '申报列表', permission: 'application:view' }
          },
          {
            path: 'create',
            name: 'ApplicationCreate',
            component: () => import('@/views/application/create.vue'),
            meta: { title: '创建申报', permission: 'application:create' }
          },
          {
            path: ':id([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})',
            name: 'ApplicationDetail',
            component: () => import('@/views/application/detail.vue'),
            meta: { title: '申报详情', permission: 'application:view' }
          }
        ]
      },
      {
        path: 'workflow',
        name: 'Workflow',
        meta: { title: '审批中心', icon: 'stamp' },
        children: [
          {
            path: 'todo',
            name: 'WorkflowTodo',
            component: () => import('@/views/workflow/todo.vue'),
            meta: { title: '待办事项' }
          },
          {
            path: 'detail/:taskId',
            name: 'WorkflowDetail',
            component: () => import('@/views/workflow/detail.vue'),
            meta: { title: '审批处理' }
          },
          {
            path: 'trail/:instanceId',
            name: 'WorkflowTrail',
            component: () => import('@/views/workflow/trail.vue'),
            meta: { title: '审批轨迹' }
          },
          {
            path: 'done',
            name: 'WorkflowDone',
            component: () => import('@/views/workflow/done.vue'),
            meta: { title: '已办事项' }
          },
          {
            path: 'initiated',
            name: 'WorkflowInitiated',
            component: () => import('@/views/workflow/initiated.vue'),
            meta: { title: '我发起的' }
          }
        ]
      },
      {
        path: 'funding',
        name: 'Funding',
        meta: { title: '经费管理', icon: 'wallet' },
        children: [
          {
            path: 'budget',
            name: 'Budget',
            component: () => import('@/views/funding/budget.vue'),
            meta: { title: '预算管理', permission: 'fund:view' }
          },
          {
            path: 'expense',
            name: 'Expense',
            component: () => import('@/views/funding/expense.vue'),
            meta: { title: '支出管理', permission: 'fund:view' }
          },
          {
            path: 'expenses/:id',
            name: 'ExpenseDetail',
            component: () => import('@/views/funding/expense/detail.vue'),
            meta: { title: '支出详情', permission: 'fund:view' }
          },
          {
            path: 'reconciliation',
            name: 'Reconciliation',
            component: () => import('@/views/funding/reconciliation.vue'),
            meta: { title: '对账管理', permission: 'finance:reconcile:view' }
          }
        ]
      },
      {
        path: 'outputs',
        name: 'Outputs',
        meta: { title: '成果管理', icon: 'medal' },
        children: [
          {
            path: '',
            name: 'OutputList',
            component: () => import('@/views/output/list.vue'),
            meta: { title: '成果列表', permission: 'output:view' }
          },
          {
            path: 'create',
            name: 'OutputCreate',
            component: () => import('@/views/output/create.vue'),
            meta: { title: '登记成果', permission: 'output:create' }
          },
          {
            path: ':id',
            name: 'OutputDetail',
            component: () => import('@/views/output/detail.vue'),
            meta: { title: '成果详情', permission: 'output:view' }
          }
        ]
      },
      {
        path: 'execution',
        name: 'Execution',
        meta: { title: '执行管理', icon: 'tickets' },
        children: [
          {
            path: 'reports',
            name: 'ExecutionReports',
            component: () => import('@/views/execution/report/list.vue'),
            meta: { title: '周期报告', permission: 'execution:view' }
          },
          {
            path: 'deliverables',
            name: 'ExecutionDeliverables',
            component: () => import('@/views/execution/deliverable/list.vue'),
            meta: { title: '交付物管理', permission: 'execution:view' }
          },
          {
            path: 'tasks',
            name: 'ExecutionTasks',
            component: () => import('@/views/execution/task/list.vue'),
            meta: { title: '轻任务', permission: 'execution:view' }
          }
        ]
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '权限不足' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]
