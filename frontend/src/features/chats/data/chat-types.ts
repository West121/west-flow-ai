import { type conversations } from './convo.json'

// 这里直接从示例会话 JSON 推导聊天记录类型，避免手写结构与数据不一致。
export type ChatUser = (typeof conversations)[number]
export type Convo = ChatUser['messages'][number]
