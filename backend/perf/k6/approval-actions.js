import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.K6_BASE_URL || 'http://host.docker.internal:8080/api/v1'
const ADMIN_USERNAME = __ENV.K6_ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = __ENV.K6_ADMIN_PASSWORD || 'admin123'
const APPROVER_USERNAME = __ENV.K6_APPROVER_USERNAME || 'zhangsan'
const APPROVER_PASSWORD = __ENV.K6_APPROVER_PASSWORD || '123456'

export const options = {
  vus: Number(__ENV.K6_VUS || 3),
  iterations: Number(__ENV.K6_ITERATIONS || 12),
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1200'],
  },
}

function login(username, password) {
  const response = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: { 'Content-Type': 'application/json' },
    }
  )

  check(response, {
    [`login ${username} ok`]: (res) => res.status === 200,
  })

  return response.json('data.accessToken')
}

export function setup() {
  return {
    adminToken: login(ADMIN_USERNAME, ADMIN_PASSWORD),
    approverToken: login(APPROVER_USERNAME, APPROVER_PASSWORD),
  }
}

export default function (data) {
  const timestamp = `${Date.now()}-${__VU}-${__ITER}`
  const adminHeaders = {
    Authorization: `Bearer ${data.adminToken}`,
    'Content-Type': 'application/json',
  }
  const approverHeaders = {
    Authorization: `Bearer ${data.approverToken}`,
    'Content-Type': 'application/json',
  }

  const createResponse = http.post(
    `${BASE_URL}/oa/leaves`,
    JSON.stringify({
      leaveType: 'PERSONAL',
      days: 1,
      urgent: false,
      reason: `k6-${timestamp}`,
      managerUserId: 'usr_002',
    }),
    { headers: adminHeaders }
  )

  check(createResponse, {
    'create leave ok': (res) => res.status === 200,
  })

  const taskId = createResponse.json('data.firstActiveTask.taskId')
  if (!taskId) {
    return
  }

  const claimResponse = http.post(
    `${BASE_URL}/process-runtime/tasks/${taskId}/claim`,
    JSON.stringify({ comment: 'k6 claim' }),
    { headers: approverHeaders }
  )

  check(claimResponse, {
    'claim ok': (res) => res.status === 200,
  })

  const completeResponse = http.post(
    `${BASE_URL}/process-runtime/tasks/${taskId}/complete`,
    JSON.stringify({
      action: 'APPROVE',
      comment: 'k6 approve',
      taskFormData: {},
    }),
    { headers: approverHeaders }
  )

  check(completeResponse, {
    'approve ok': (res) => res.status === 200,
  })

  sleep(1)
}
