#!/usr/bin/env node

import http from 'node:http'
import { URL } from 'node:url'
import * as awarenessProtocol from 'y-protocols/awareness'
import * as syncProtocol from 'y-protocols/sync'
import * as Y from 'yjs'
import * as decoding from 'lib0/decoding'
import * as encoding from 'lib0/encoding'
import WebSocket, { WebSocketServer } from 'ws'

const host = process.env.HOST || '127.0.0.1'
const port = Number.parseInt(process.env.PORT || '1235', 10)
const authApiBaseUrl =
  process.env.WORKFLOW_COLLAB_AUTH_API?.trim() ||
  'http://127.0.0.1:8080/api/v1/process-definitions/collaboration'
const heartbeatIntervalMs = Number.parseInt(
  process.env.WORKFLOW_COLLAB_HEARTBEAT_INTERVAL_MS || '15000',
  10
)
const heartbeatTimeoutMs = Number.parseInt(
  process.env.WORKFLOW_COLLAB_HEARTBEAT_TIMEOUT_MS || '45000',
  10
)
const roomIdleTtlMs = Number.parseInt(
  process.env.WORKFLOW_COLLAB_ROOM_IDLE_TTL_MS || '60000',
  10
)
const maxConnectionsPerRoom = Number.parseInt(
  process.env.WORKFLOW_COLLAB_MAX_CONNECTIONS_PER_ROOM || '12',
  10
)
const maxTotalConnections = Number.parseInt(
  process.env.WORKFLOW_COLLAB_MAX_TOTAL_CONNECTIONS || '200',
  10
)
const messageSync = 0
const messageAwareness = 1
const wsReadyStateConnecting = 0
const wsReadyStateOpen = 1

function serializeError(error) {
  if (error instanceof Error) {
    return {
      name: error.name,
      message: error.message,
      stack: error.stack,
    }
  }
  return {
    message: String(error),
  }
}

function log(level, message, details = undefined) {
  const payload = {
    ts: new Date().toISOString(),
    pid: process.pid,
    level,
    message,
  }
  if (details !== undefined) {
    payload.details = details
  }
  const line = `[workflow-collab] ${JSON.stringify(payload)}`
  if (level === 'error') {
    console.error(line)
    return
  }
  if (level === 'warn') {
    console.warn(line)
    return
  }
  console.log(line)
}

/**
 * 协同房间的内存态文档和连接集合。
 *
 * @type {Map<string, {
 *   name: string,
 *   doc: Y.Doc,
 *   conns: Map<WebSocket, Set<number>>,
 *   awareness: awarenessProtocol.Awareness,
 *   cleanupTimer: ReturnType<typeof setTimeout> | null,
 *   lastActivityAt: number
 * }>}
 */
const docs = new Map()
let activeConnections = 0

function buildAuditUrl() {
  return `${authApiBaseUrl}/audit`
}

function writeJson(response, statusCode, body) {
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
  })
  response.end(JSON.stringify(body))
}

function extractRequestUrl(request) {
  return new URL(request.url || '/', `http://${request.headers.host || `${host}:${port}`}`)
}

function extractRoomName(request) {
  return extractRequestUrl(request).pathname.replace(/^\/+/, '')
}

function extractToken(request) {
  return extractRequestUrl(request).searchParams.get('token')?.trim() || ''
}

function isAllowedRoomName(roomName) {
  return roomName.startsWith('workflow-designer:')
}

function getRoomStats() {
  return Array.from(docs.values()).map((room) => ({
    roomName: room.name,
    connections: room.conns.size,
    idleForMs: room.conns.size === 0 ? Date.now() - room.lastActivityAt : 0,
    cleanupScheduled: room.cleanupTimer !== null,
  }))
}

function getServerStats() {
  return {
    rooms: docs.size,
    connections: activeConnections,
    limits: {
      maxConnectionsPerRoom,
      maxTotalConnections,
      heartbeatIntervalMs,
      heartbeatTimeoutMs,
      roomIdleTtlMs,
    },
    roomStats: getRoomStats(),
  }
}

function writeUpgradeReject(socket, statusCode, body) {
  const statusText =
    statusCode === 401
      ? 'Unauthorized'
      : statusCode === 404
        ? 'Not Found'
        : statusCode === 429
          ? 'Too Many Requests'
          : statusCode === 502
            ? 'Bad Gateway'
            : 'Error'
  socket.write(
    `HTTP/1.1 ${statusCode} ${statusText}\r\nContent-Type: application/json; charset=utf-8\r\nConnection: close\r\n\r\n${JSON.stringify(
      body
    )}`
  )
  socket.destroy()
}

function scheduleRoomCleanup(room) {
  if (room.conns.size > 0 || room.cleanupTimer !== null) {
    return
  }

  room.cleanupTimer = setTimeout(() => {
    room.cleanupTimer = null
    if (room.conns.size > 0 || docs.get(room.name) !== room) {
      return
    }
    if (docs.delete(room.name)) {
      room.doc.destroy()
      log('info', 'room-cleanup', {
        room: room.name,
        rooms: docs.size,
        connections: activeConnections,
      })
    }
  }, roomIdleTtlMs)
}

function cancelRoomCleanup(room) {
  if (room.cleanupTimer !== null) {
    clearTimeout(room.cleanupTimer)
    room.cleanupTimer = null
  }
}

function createSharedDoc(name) {
  const doc = new Y.Doc({ gc: true })
  const sharedDoc = Object.assign(doc, {
    name,
    conns: new Map(),
    awareness: new awarenessProtocol.Awareness(doc),
    cleanupTimer: null,
    lastActivityAt: Date.now(),
  })
  sharedDoc.awareness.setLocalState(null)
  sharedDoc.on('update', (update) => {
    const encoder = encoding.createEncoder()
    encoding.writeVarUint(encoder, messageSync)
    syncProtocol.writeUpdate(encoder, update)
    const message = encoding.toUint8Array(encoder)
    sharedDoc.conns.forEach((_, conn) => {
      send(sharedDoc, conn, message)
    })
  })
  sharedDoc.awareness.on('update', ({ added, updated, removed }, conn) => {
    const changedClients = added.concat(updated, removed)
    if (conn) {
      const controlledIds = sharedDoc.conns.get(conn)
      if (controlledIds) {
        added.forEach((clientId) => controlledIds.add(clientId))
        removed.forEach((clientId) => controlledIds.delete(clientId))
      }
    }
    const encoder = encoding.createEncoder()
    encoding.writeVarUint(encoder, messageAwareness)
    encoding.writeVarUint8Array(
      encoder,
      awarenessProtocol.encodeAwarenessUpdate(sharedDoc.awareness, changedClients)
    )
    const payload = encoding.toUint8Array(encoder)
    sharedDoc.conns.forEach((_, currentConn) => {
      send(sharedDoc, currentConn, payload)
    })
  })
  return sharedDoc
}

function getSharedDoc(name) {
  const existingDoc = docs.get(name)
  if (existingDoc) {
    cancelRoomCleanup(existingDoc)
    return existingDoc
  }
  const createdDoc = createSharedDoc(name)
  docs.set(name, createdDoc)
  return createdDoc
}

function detachConnection(room, conn) {
  if (!room.conns.has(conn)) {
    return false
  }

  const controlledIds = room.conns.get(conn) || new Set()
  room.conns.delete(conn)
  activeConnections = Math.max(0, activeConnections - 1)
  room.lastActivityAt = Date.now()
  awarenessProtocol.removeAwarenessStates(room.awareness, Array.from(controlledIds), null)
  if (room.conns.size === 0) {
    scheduleRoomCleanup(room)
  }
  return true
}

function closeConnection(room, conn, reason = 'unknown') {
  const removed = detachConnection(room, conn)
  if (conn.heartbeatTimer !== undefined) {
    clearInterval(conn.heartbeatTimer)
    conn.heartbeatTimer = undefined
  }
  if (removed) {
    log('info', 'connection-close', {
      room: room.name,
      reason,
      rooms: docs.size,
      connections: activeConnections,
    })
  }
  if (conn.readyState === wsReadyStateConnecting || conn.readyState === wsReadyStateOpen) {
    try {
      conn.close()
    } catch {
      // 忽略已关闭连接的 close 异常。
    }
  }
}

function send(doc, conn, payload) {
  if (conn.readyState !== wsReadyStateConnecting && conn.readyState !== wsReadyStateOpen) {
    closeConnection(doc, conn, 'send-failed')
    return
  }
  try {
    conn.send(payload, {}, (error) => {
      if (error) {
        closeConnection(doc, conn, 'send-callback-error')
      }
    })
  } catch {
    closeConnection(doc, conn, 'send-throw')
  }
}

function handleMessage(conn, doc, message) {
  doc.lastActivityAt = Date.now()
  try {
    const decoder = decoding.createDecoder(message)
    const encoder = encoding.createEncoder()
    const messageType = decoding.readVarUint(decoder)
    switch (messageType) {
      case messageSync:
        encoding.writeVarUint(encoder, messageSync)
        syncProtocol.readSyncMessage(decoder, encoder, doc, conn)
        if (encoding.length(encoder) > 1) {
          send(doc, conn, encoding.toUint8Array(encoder))
        }
        break
      case messageAwareness:
        awarenessProtocol.applyAwarenessUpdate(
          doc.awareness,
          decoding.readVarUint8Array(decoder),
          conn
        )
        break
      default:
        closeConnection(doc, conn, 'unsupported-message')
    }
  } catch (error) {
    log('error', 'message-handling-failed', serializeError(error))
    closeConnection(doc, conn, 'message-error')
  }
}

function authorizeRoomAccess(roomName) {
  if (!roomName || !isAllowedRoomName(roomName)) {
    return { ok: false, statusCode: 404, body: { message: 'Unknown collaboration room' } }
  }

  const room = docs.get(roomName)
  if (room && room.conns.size >= maxConnectionsPerRoom) {
    return {
      ok: false,
      statusCode: 429,
      body: {
        message: 'Collaboration room is at capacity',
        roomName,
        limit: maxConnectionsPerRoom,
      },
    }
  }

  if (activeConnections >= maxTotalConnections) {
    return {
      ok: false,
      statusCode: 429,
      body: {
        message: 'Collaboration service reached its connection limit',
        limit: maxTotalConnections,
      },
    }
  }

  return { ok: true }
}

async function authorizeUpgrade(request) {
  const roomName = extractRoomName(request)
  const roomAccess = authorizeRoomAccess(roomName)
  if (!roomAccess.ok) {
    return roomAccess
  }

  const token = extractToken(request)
  if (!token) {
    return { ok: false, statusCode: 401, body: { message: 'Missing collaboration token' } }
  }

  try {
    const response = await fetch(
      `${authApiBaseUrl}/authorize?roomName=${encodeURIComponent(roomName)}`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    )
    if (!response.ok) {
      return { ok: false, statusCode: 401, body: { message: 'Collaboration authentication failed' } }
    }
    const json = await response.json().catch(() => ({}))
    return { ok: true, roomName, auth: json?.data || null }
  } catch (error) {
    log('warn', 'authorize-upgrade-failed', {
      roomName,
      authApiBaseUrl,
      error: serializeError(error),
    })
    return {
      ok: false,
      statusCode: 502,
      body: {
        message: 'Collaboration auth service unavailable',
        error: error instanceof Error ? error.message : 'Unknown error',
      },
    }
  }
}

async function postCollaborationAuditEvent(authToken, payload) {
  if (!authToken) {
    return
  }

  try {
    await fetch(buildAuditUrl(), {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    })
  } catch (error) {
    log('warn', 'audit-failed', serializeError(error))
  }
}

function setupWsConnection(conn, request, roomName, auth = null) {
  conn.binaryType = 'arraybuffer'
  const doc = getSharedDoc(roomName)
  if (doc.conns.size >= maxConnectionsPerRoom) {
    try {
      conn.close(1008, 'Room at capacity')
    } catch {
      // 忽略拒绝连接时的 close 异常。
    }
    return
  }

  doc.conns.set(conn, new Set())
  activeConnections += 1
  doc.lastActivityAt = Date.now()
  conn.auth = auth
  conn.authToken = extractToken(request)
  conn.connectionId = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  conn.lastPongAt = Date.now()

  if (auth) {
    log('info', 'join', {
      room: roomName,
      userId: auth.userId,
      displayName: auth.displayName,
      processDefinitionId: auth.processDefinitionId || null,
      rooms: docs.size,
      connections: activeConnections,
      connectionId: conn.connectionId,
    })
    void postCollaborationAuditEvent(conn.authToken, {
      roomName,
      eventType: 'DESIGNER_COLLAB_JOIN',
      eventName: '加入协同房间',
      details: {
        connectionId: conn.connectionId,
        processDefinitionId: auth.processDefinitionId,
      },
    })
  }

  conn.on('message', (message) => {
    handleMessage(conn, doc, new Uint8Array(message))
  })

  conn.on('pong', () => {
    conn.lastPongAt = Date.now()
  })

  conn.heartbeatTimer = setInterval(() => {
    if (!doc.conns.has(conn)) {
      clearInterval(conn.heartbeatTimer)
      conn.heartbeatTimer = undefined
      return
    }
    if (Date.now() - conn.lastPongAt > heartbeatTimeoutMs) {
      closeConnection(doc, conn, 'heartbeat-timeout')
      return
    }
    try {
      conn.ping()
    } catch {
      closeConnection(doc, conn, 'heartbeat-ping-error')
    }
  }, heartbeatIntervalMs)

  conn.on('close', () => {
    if (conn.auth) {
      log('info', 'leave', {
        room: roomName,
        userId: conn.auth.userId,
        displayName: conn.auth.displayName,
        rooms: docs.size,
        connections: activeConnections,
        connectionId: conn.connectionId,
      })
      void postCollaborationAuditEvent(conn.authToken, {
        roomName,
        eventType: 'DESIGNER_COLLAB_LEAVE',
        eventName: '离开协同房间',
        details: {
          connectionId: conn.connectionId,
          processDefinitionId: conn.auth.processDefinitionId,
        },
      })
    }
    closeConnection(doc, conn, 'socket-close')
  })

  const syncEncoder = encoding.createEncoder()
  encoding.writeVarUint(syncEncoder, messageSync)
  syncProtocol.writeSyncStep1(syncEncoder, doc)
  send(doc, conn, encoding.toUint8Array(syncEncoder))

  const awarenessStates = doc.awareness.getStates()
  if (awarenessStates.size > 0) {
    const awarenessEncoder = encoding.createEncoder()
    encoding.writeVarUint(awarenessEncoder, messageAwareness)
    encoding.writeVarUint8Array(
      awarenessProtocol.encodeAwarenessUpdate(doc.awareness, Array.from(awarenessStates.keys()))
    )
    send(doc, conn, encoding.toUint8Array(awarenessEncoder))
  }
}

const wss = new WebSocketServer({ noServer: true, perMessageDeflate: false })
process.on('warning', (warning) => {
  log('warn', 'process-warning', {
    name: warning.name,
    message: warning.message,
    stack: warning.stack,
  })
})
process.on('unhandledRejection', (reason) => {
  log('error', 'unhandled-rejection', serializeError(reason))
})
process.on('uncaughtException', (error) => {
  log('error', 'uncaught-exception', serializeError(error))
})
process.on('beforeExit', (code) => {
  log('warn', 'before-exit', { code, ...getServerStats() })
})
process.on('exit', (code) => {
  log('warn', 'exit', { code, ...getServerStats() })
})
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    log('warn', 'signal-received', { signal, ...getServerStats() })
  })
}
wss.on('error', (error) => {
  log('error', 'websocket-server-error', serializeError(error))
})

const server = http.createServer((request, response) => {
  if ((request.url || '').startsWith('/health')) {
    writeJson(response, 200, {
      status: 'UP',
      mode: 'auth',
      host,
      port,
      ...getServerStats(),
    })
    return
  }

  writeJson(response, 200, {
    service: 'workflow-designer-collaboration',
    transport: 'y-websocket-compatible',
    mode: 'auth',
    ...getServerStats(),
  })
})
server.on('error', (error) => {
  log('error', 'http-server-error', serializeError(error))
})
server.on('clientError', (error, socket) => {
  log('warn', 'http-client-error', serializeError(error))
  socket.destroy()
})

wss.on('connection', (conn, request, roomName, auth) => {
  setupWsConnection(conn, request, roomName, auth)
})

server.on('upgrade', async (request, socket, head) => {
  const authResult = await authorizeUpgrade(request)
  if (!authResult.ok) {
    log('warn', 'upgrade-rejected', {
      roomName: extractRoomName(request),
      statusCode: authResult.statusCode,
      body: authResult.body,
    })
    writeUpgradeReject(socket, authResult.statusCode, authResult.body)
    return
  }

  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request, authResult.roomName, authResult.auth)
  })
})

server.listen(port, host, () => {
  log('info', 'listening', {
    url: `ws://${host}:${port}`,
    mode: 'auth',
    ...getServerStats(),
  })
})
