/// <reference types="vite/client" />

// 环境变量声明：仅声明实际读取的键（见 src/api/base.ts），未定义键走代码内默认值
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
