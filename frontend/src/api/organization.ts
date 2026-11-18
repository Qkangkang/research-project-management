import api from './base'

export interface OrgUnitOption {
  id: string
  parent_id?: string
  code: string
  name: string
  unit_type: string
}

export interface UserOption {
  id: string
  employee_no: string
  display_name: string
  dept_id?: string
  dept_name?: string
  status: string
}

export const organizationApi = {
  /**
   * 组织单元下拉选项
   */
  orgUnits(): Promise<OrgUnitOption[]> {
    return api.get('/org-units/options')
  },

  /**
   * 用户下拉选项
   */
  users(keyword?: string): Promise<UserOption[]> {
    return api.get('/users/options', { params: { keyword: keyword || undefined } })
  }
}
