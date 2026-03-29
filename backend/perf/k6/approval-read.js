import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.K6_BASE_URL || 'http://host.docker.internal:8080/api/v1'
const USERNAME = __ENV.K6_USERNAME || 'zhangsan'
const PASSWORD = __ENV.K6_PASSWORD || '123456'

export const options = {
  vus: Number(__ENV.K6_VUS || 10),
  duration: __ENV.K6_DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
}

function login() {
  const response = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({
      username: USERNAME,
      password: PASSWORD,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
    }
  )

  check(response, {
    'login ok': (res) => res.status === 200,
  })

  return response.json('data.accessToken')
}

export function setup() {
  return {
    token: login(),
  }
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  }

  const tasksPage = http.post(
    `${BASE_URL}/process-runtime/tasks/page`,
    JSON.stringify({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    }),
    { headers }
  )

  check(tasksPage, {
    'tasks page ok': (res) => res.status === 200,
  })

  const firstTaskId = tasksPage.json('data.records.0.taskId')
  if (firstTaskId) {
    const detail = http.get(`${BASE_URL}/process-runtime/tasks/${firstTaskId}`, {
      headers,
    })
    check(detail, {
      'task detail ok': (res) => res.status === 200,
    })
  }

  const approvalSheets = http.post(
    `${BASE_URL}/process-runtime/approval-sheets/page`,
    JSON.stringify({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
      view: 'TODO',
      businessTypes: [],
    }),
    { headers }
  )

  check(approvalSheets, {
    'approval sheets ok': (res) => res.status === 200,
  })

  sleep(1)
}
