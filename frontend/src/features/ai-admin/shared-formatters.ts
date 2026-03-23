// AI 管理页统一的时间格式，保证注册表和运行记录展示风格一致。
export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

// 将 JSON 字符串尽量转成可读块，坏数据则原样展示。
export function prettyJson(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

// 将毫秒耗时格式化成更容易阅读的中文文案。
export function formatDurationMillis(value: number | null | undefined) {
  if (value == null) {
    return '-'
  }

  if (value < 1000) {
    return `${value} ms`
  }

  const seconds = value / 1000
  return `${seconds.toFixed(seconds < 10 ? 2 : 1)} s`
}

// 把逗号分隔字符串或数组展示成中文标签。
export function renderTags(value: string[] | string | null | undefined) {
  if (!value) {
    return '-'
  }

  if (Array.isArray(value)) {
    return value.length > 0 ? value.join('，') : '-'
  }

  return (
    value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .join('，') || '-'
  )
}
