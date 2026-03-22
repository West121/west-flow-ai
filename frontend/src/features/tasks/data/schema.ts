import { z } from 'zod'

// 这里保留一个简单的非关系型示例 schema。
// 实际项目里，这里通常会替换成真实数据模型对应的校验结构。
export const taskSchema = z.object({
  id: z.string(),
  title: z.string(),
  status: z.string(),
  label: z.string(),
  priority: z.string(),
})

export type Task = z.infer<typeof taskSchema>
