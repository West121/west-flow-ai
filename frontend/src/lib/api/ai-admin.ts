import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

type BasePageResponse<T> = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: T[]
  groups: Array<{
    field: string
    value: string
  }>
}

export type AiRegistryStatus = 'ENABLED' | 'DISABLED'
export type AiToolActionMode = 'READ' | 'WRITE'
export type AiMcpTransportType = 'INTERNAL' | 'STREAMABLE_HTTP' | 'STDIO'

export type AiRegistryOption = {
  value: string
  label: string
}

export type AiRegistryStatusOption = {
  value: AiRegistryStatus
  label: string
}

export type AiAgentRecord = {
  agentId: string
  agentCode: string
  agentName: string
  capabilityCode: string
  routeMode: string
  supervisor: boolean
  priority: number
  status: AiRegistryStatus
  contextTags: string[]
  systemPrompt: string
  metadataJson: string
  createdAt: string
  updatedAt: string
}

export type AiAgentPageResponse = BasePageResponse<AiAgentRecord>
export type AiAgentDetail = AiAgentRecord

export type AiAgentFormOptions = {
  statusOptions: AiRegistryStatusOption[]
  capabilityOptions: AiRegistryOption[]
  routeModeOptions: AiRegistryOption[]
  domainOptions: AiRegistryOption[]
}

export type SaveAiAgentPayload = {
  agentCode: string
  agentName: string
  capabilityCode: string
  routeMode: string
  supervisor: boolean
  priority: number
  contextTags: string[]
  systemPrompt: string
  metadataJson: string
  enabled: boolean
}

export type AiToolRecord = {
  toolId: string
  toolCode: string
  toolName: string
  toolCategory: string
  actionMode: AiToolActionMode
  requiredCapabilityCode: string | null
  status: AiRegistryStatus
  metadataJson: string
  createdAt: string
  updatedAt: string
}

export type AiToolPageResponse = BasePageResponse<AiToolRecord>
export type AiToolDetail = AiToolRecord & {
  description: string | null
}

export type AiToolFormOptions = {
  statusOptions: AiRegistryStatusOption[]
  capabilityOptions: AiRegistryOption[]
  toolCategoryOptions: AiRegistryOption[]
  actionModeOptions: AiRegistryOption[]
}

export type SaveAiToolPayload = {
  toolCode: string
  toolName: string
  toolCategory: string
  actionMode: AiToolActionMode
  requiredCapabilityCode?: string | null
  metadataJson: string
  enabled: boolean
}

export type AiMcpRecord = {
  mcpId: string
  mcpCode: string
  mcpName: string
  endpointUrl: string | null
  transportType: AiMcpTransportType
  requiredCapabilityCode: string | null
  status: AiRegistryStatus
  metadataJson: string
  createdAt: string
  updatedAt: string
}

export type AiMcpPageResponse = BasePageResponse<AiMcpRecord>
export type AiMcpDetail = AiMcpRecord & {
  description: string | null
}

export type AiMcpFormOptions = {
  statusOptions: AiRegistryStatusOption[]
  capabilityOptions: AiRegistryOption[]
  transportTypeOptions: AiRegistryOption[]
}

export type SaveAiMcpPayload = {
  mcpCode: string
  mcpName: string
  endpointUrl?: string | null
  transportType: AiMcpTransportType
  requiredCapabilityCode?: string | null
  metadataJson: string
  enabled: boolean
}

export type AiSkillRecord = {
  skillId: string
  skillCode: string
  skillName: string
  skillPath: string | null
  requiredCapabilityCode: string | null
  status: AiRegistryStatus
  metadataJson: string
  createdAt: string
  updatedAt: string
}

export type AiSkillPageResponse = BasePageResponse<AiSkillRecord>
export type AiSkillDetail = AiSkillRecord & {
  description: string | null
}

export type AiSkillFormOptions = {
  statusOptions: AiRegistryStatusOption[]
  capabilityOptions: AiRegistryOption[]
  domainOptions: AiRegistryOption[]
}

export type SaveAiSkillPayload = {
  skillCode: string
  skillName: string
  skillPath?: string | null
  requiredCapabilityCode?: string | null
  metadataJson: string
  enabled: boolean
}

export type AiConversationStatus = 'ACTIVE' | 'ARCHIVED'
export type AiConversationRecord = {
  conversationId: string
  title: string
  preview: string | null
  status: AiConversationStatus
  contextTags: string[]
  messageCount: number
  operatorUserId: string
  createdAt: string
  updatedAt: string
}

export type AiConversationPageResponse = BasePageResponse<AiConversationRecord>

export type AiMessageRecord = {
  messageId: string
  conversationId: string
  role: string
  authorName: string
  content: string
  blocks: Array<Record<string, unknown>>
  operatorUserId: string
  createdAt: string
  updatedAt: string
}

export type AiToolCallStatus = 'PENDING' | 'CONFIRMED' | 'EXECUTED' | 'FAILED' | 'REJECTED'
export type AiToolCallRecord = {
  toolCallId: string
  conversationId: string
  toolKey: string
  toolType: string
  toolSource: string
  status: AiToolCallStatus | string
  requiresConfirmation: boolean
  summary: string | null
  confirmationId: string | null
  operatorUserId: string
  createdAt: string
  completedAt: string | null
}

export type AiConfirmationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type AiConfirmationRecord = {
  confirmationId: string
  toolCallId: string
  status: AiConfirmationStatus | string
  approved: boolean
  comment: string | null
  resolvedBy: string | null
  createdAt: string
  resolvedAt: string | null
  updatedAt: string
}

export type AiAuditRecord = {
  auditId: string
  conversationId: string | null
  toolCallId: string | null
  actionType: string
  summary: string | null
  operatorUserId: string
  occurredAt: string
}

export type AiConversationDetail = AiConversationRecord & {
  messages: AiMessageRecord[]
  toolCalls: AiToolCallRecord[]
  confirmations: AiConfirmationRecord[]
  audits: AiAuditRecord[]
}

export type AiToolCallDetail = AiToolCallRecord & {
  argumentsJson: string
  resultJson: string
}

export type AiConfirmationDetail = AiConfirmationRecord

export async function listAiAgents(
  search: ListQuerySearch
): Promise<AiAgentPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<AiAgentPageResponse>>(
    '/system/ai/agents/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getAiAgentDetail(agentId: string): Promise<AiAgentDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AiAgentDetail>>(
    `/system/ai/agents/${agentId}`
  )

  return unwrapResponse(response)
}

export async function getAiAgentFormOptions(): Promise<AiAgentFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<AiAgentFormOptions>>(
    '/system/ai/agents/options'
  )

  return unwrapResponse(response)
}

export async function createAiAgent(
  payload: SaveAiAgentPayload
): Promise<{ agentId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ agentId: string }>>(
    '/system/ai/agents',
    payload
  )

  return unwrapResponse(response)
}

export async function updateAiAgent(
  agentId: string,
  payload: SaveAiAgentPayload
): Promise<{ agentId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ agentId: string }>>(
    `/system/ai/agents/${agentId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function listAiTools(
  search: ListQuerySearch
): Promise<AiToolPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<AiToolPageResponse>>(
    '/system/ai/tools/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getAiToolDetail(toolId: string): Promise<AiToolDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AiToolDetail>>(
    `/system/ai/tools/${toolId}`
  )

  return unwrapResponse(response)
}

export async function getAiToolFormOptions(): Promise<AiToolFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<AiToolFormOptions>>(
    '/system/ai/tools/options'
  )

  return unwrapResponse(response)
}

export async function createAiTool(
  payload: SaveAiToolPayload
): Promise<{ toolId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ toolId: string }>>(
    '/system/ai/tools',
    payload
  )

  return unwrapResponse(response)
}

export async function updateAiTool(
  toolId: string,
  payload: SaveAiToolPayload
): Promise<{ toolId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ toolId: string }>>(
    `/system/ai/tools/${toolId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function listAiMcps(
  search: ListQuerySearch
): Promise<AiMcpPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<AiMcpPageResponse>>(
    '/system/ai/mcps/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getAiMcpDetail(mcpId: string): Promise<AiMcpDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AiMcpDetail>>(
    `/system/ai/mcps/${mcpId}`
  )

  return unwrapResponse(response)
}

export async function getAiMcpFormOptions(): Promise<AiMcpFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<AiMcpFormOptions>>(
    '/system/ai/mcps/options'
  )

  return unwrapResponse(response)
}

export async function createAiMcp(
  payload: SaveAiMcpPayload
): Promise<{ mcpId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ mcpId: string }>>(
    '/system/ai/mcps',
    payload
  )

  return unwrapResponse(response)
}

export async function updateAiMcp(
  mcpId: string,
  payload: SaveAiMcpPayload
): Promise<{ mcpId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ mcpId: string }>>(
    `/system/ai/mcps/${mcpId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function listAiSkills(
  search: ListQuerySearch
): Promise<AiSkillPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<AiSkillPageResponse>>(
    '/system/ai/skills/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getAiSkillDetail(skillId: string): Promise<AiSkillDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AiSkillDetail>>(
    `/system/ai/skills/${skillId}`
  )

  return unwrapResponse(response)
}

export async function getAiSkillFormOptions(): Promise<AiSkillFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<AiSkillFormOptions>>(
    '/system/ai/skills/options'
  )

  return unwrapResponse(response)
}

export async function createAiSkill(
  payload: SaveAiSkillPayload
): Promise<{ skillId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ skillId: string }>>(
    '/system/ai/skills',
    payload
  )

  return unwrapResponse(response)
}

export async function updateAiSkill(
  skillId: string,
  payload: SaveAiSkillPayload
): Promise<{ skillId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ skillId: string }>>(
    `/system/ai/skills/${skillId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function listAiConversations(
  search: ListQuerySearch
): Promise<AiConversationPageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<AiConversationPageResponse>
  >('/system/ai/conversations/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getAiConversationDetail(
  conversationId: string
): Promise<AiConversationDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AiConversationDetail>>(
    `/system/ai/conversations/${conversationId}`
  )

  return unwrapResponse(response)
}

export async function listAiToolCalls(
  search: ListQuerySearch
): Promise<BasePageResponse<AiToolCallRecord>> {
  const response = await apiClient.post<
    ApiSuccessResponse<BasePageResponse<AiToolCallRecord>>
  >('/system/ai/tool-calls/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getAiToolCallDetail(
  toolCallId: string
): Promise<AiToolCallDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AiToolCallDetail>>(
    `/system/ai/tool-calls/${toolCallId}`
  )

  return unwrapResponse(response)
}

export async function listAiConfirmations(
  search: ListQuerySearch
): Promise<BasePageResponse<AiConfirmationRecord>> {
  const response = await apiClient.post<
    ApiSuccessResponse<BasePageResponse<AiConfirmationRecord>>
  >('/system/ai/confirmations/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getAiConfirmationDetail(
  confirmationId: string
): Promise<AiConfirmationDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AiConfirmationDetail>>(
    `/system/ai/confirmations/${confirmationId}`
  )

  return unwrapResponse(response)
}
