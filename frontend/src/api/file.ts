import api from './base'

export interface UploadedFileView {
  fileId: string
  originalName: string
  sizeBytes: number
  sha256?: string
}

export interface FileDownloadView {
  token: string
  downloadUrl: string
  expiresAt: string
}

interface UploadedFileRow {
  file_id: string
  original_name: string
  size_bytes: number
  sha256?: string
}

interface FileDownloadRow {
  token: string
  download_url: string
  expires_at: string
}

export const fileApi = {
  /**
   * 上传单个文件
   */
  async upload(file: File): Promise<UploadedFileView> {
    const form = new FormData()
    form.append('file', file)
    const row = await api.post<UploadedFileRow>('/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return {
      fileId: row.file_id,
      originalName: row.original_name,
      sizeBytes: row.size_bytes,
      sha256: row.sha256
    }
  },

  /**
   * 获取短期下载地址
   */
  async downloadUrl(fileId: string): Promise<FileDownloadView> {
    const row = await api.get<FileDownloadRow>(`/files/${fileId}/download-url`)
    return {
      token: row.token,
      downloadUrl: row.download_url,
      expiresAt: row.expires_at
    }
  },

  /**
   * 打开短期下载地址
   */
  async download(fileId: string): Promise<void> {
    const result = await this.downloadUrl(fileId)
    window.open(result.downloadUrl, '_blank')
  }
}
