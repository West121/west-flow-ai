export type RuntimeStructureLink = {
  linkId: string
  rootInstanceId: string
  parentInstanceId: string
  childInstanceId?: string | null
  parentNodeId?: string | null
  calledProcessKey?: string | null
  calledDefinitionId?: string | null
  calledVersionPolicy?: string | null
  calledVersion?: number | null
  targetBusinessType?: string | null
  targetSceneCode?: string | null
  linkType?: string | null
  runtimeLinkType?: string | null
  triggerMode?: string | null
  appendType?: string | null
  status: string
  callScope?: string | null
  joinMode?: string | null
  childStartStrategy?: string | null
  childStartDecisionReason?: string | null
  parentResumeStrategy?: string | null
  resumeDecisionReason?: string | null
  descendantCount?: number | null
  runningDescendantCount?: number | null
  executionStrategy?: string | null
  fallbackStrategy?: string | null
  maxGeneratedCount?: number | null
  generatedCount?: number | null
  generationTruncated?: boolean | null
  buildMode?: string | null
  sourceMode?: string | null
  sceneCode?: string | null
  ruleExpression?: string | null
  manualTemplateCode?: string | null
  resolvedTargetMode?: string | null
  resolvedSourceMode?: string | null
  resolutionPath?: string | null
  templateSource?: string | null
  terminatePolicy?: string | null
  childFinishPolicy?: string | null
  sourceTaskId?: string | null
  sourceNodeId?: string | null
  targetTaskId?: string | null
  targetInstanceId?: string | null
  targetUserId?: string | null
  operatorUserId?: string | null
  commentText?: string | null
  createdAt: string | null
  finishedAt: string | null
}

export type RuntimeStructureTreeNode = {
  link: RuntimeStructureLink
  targetInstanceId: string | null
  children: RuntimeStructureTreeNode[]
}

export function resolveRuntimeStructureKindLabel(link: RuntimeStructureLink) {
  if (link.triggerMode === 'DYNAMIC_BUILD') {
    return '动态构建'
  }

  if (
    link.triggerMode === 'APPEND' ||
    link.runtimeLinkType === 'ADHOC_TASK' ||
    link.runtimeLinkType === 'ADHOC_SUBPROCESS' ||
    link.appendType === 'TASK' ||
    link.appendType === 'SUBPROCESS'
  ) {
    return link.appendType === 'SUBPROCESS' || link.runtimeLinkType === 'ADHOC_SUBPROCESS'
      ? '追加子流程'
      : '追加任务'
  }

  if (link.linkType === 'CALL_ACTIVITY') {
    return '主子流程'
  }

  return (
    link.runtimeLinkType?.trim() ||
    link.linkType?.trim() ||
    link.triggerMode?.trim() ||
    '--'
  )
}

export function resolveRuntimeStructureTargetInstanceId(
  link: RuntimeStructureLink
) {
  return link.childInstanceId ?? link.targetInstanceId ?? null
}

export function mergeRuntimeStructureLinks(
  ...groups: Array<RuntimeStructureLink[] | null | undefined>
) {
  const deduped = new Map<string, RuntimeStructureLink>()

  for (const group of groups) {
    for (const link of group ?? []) {
      deduped.set(link.linkId, link)
    }
  }

  return Array.from(deduped.values()).sort((left, right) => {
    const leftTime = left.createdAt ?? ''
    const rightTime = right.createdAt ?? ''
    if (leftTime !== rightTime) {
      return leftTime.localeCompare(rightTime)
    }

    return left.linkId.localeCompare(right.linkId)
  })
}

export function buildRuntimeStructureTree(
  links: RuntimeStructureLink[],
  currentInstanceId: string
) {
  const normalizedLinks = mergeRuntimeStructureLinks(links)
  const groupedByParent = new Map<string, RuntimeStructureLink[]>()

  for (const link of normalizedLinks) {
    const parentInstanceId = link.parentInstanceId?.trim()
    if (!parentInstanceId) {
      continue
    }

    const group = groupedByParent.get(parentInstanceId) ?? []
    group.push(link)
    groupedByParent.set(parentInstanceId, group)
  }

  const candidateRootIds = new Set<string>()
  for (const link of normalizedLinks) {
    if (link.rootInstanceId) {
      candidateRootIds.add(link.rootInstanceId)
    }
    if (link.parentInstanceId) {
      candidateRootIds.add(link.parentInstanceId)
    }
  }

  const rootInstanceId = normalizedLinks.find(
    (link) =>
      link.rootInstanceId === currentInstanceId ||
      link.parentInstanceId === currentInstanceId ||
      resolveRuntimeStructureTargetInstanceId(link) === currentInstanceId
  )?.rootInstanceId
    ?? currentInstanceId

  const visited = new Set<string>()

  const buildChildren = (instanceId: string): RuntimeStructureTreeNode[] => {
    const siblings = groupedByParent.get(instanceId) ?? []
    return siblings.map((link) => {
      const targetInstanceId = resolveRuntimeStructureTargetInstanceId(link)
      const visitKey = `${link.linkId}:${targetInstanceId ?? 'leaf'}`
      if (visited.has(visitKey)) {
        return {
          link,
          targetInstanceId,
          children: [],
        }
      }
      visited.add(visitKey)

      return {
        link,
        targetInstanceId,
        children: targetInstanceId ? buildChildren(targetInstanceId) : [],
      }
    })
  }

  return {
    rootInstanceId,
    rootChildren: buildChildren(rootInstanceId),
    hasDetachedLinks: normalizedLinks.some(
      (link) =>
        link.parentInstanceId &&
        !candidateRootIds.has(link.parentInstanceId)
    ),
  }
}
