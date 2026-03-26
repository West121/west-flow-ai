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
const messageSync = 0
const messageAwareness = 1
const wsReadyStateConnecting = 0
const wsReadyStateOpen = 1
const pingTimeout = 30_000

/** @type {Map<string, Y.Doc & { name: string, conns: Map<WebSocket, Set<number>>, awareness: awarenessProtocol.Awareness }>} */
const docs = new Map()

function buildAuditUrl() {
  return `${authApiBaseUrl}/audit`
}

function writeJson(response, statusCode, body) {
  response.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' })
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

async function authorizeUpgrade(request) {
  const roomName = extractRoomName(request)
  if (!roomName || !isAllowedRoomName(roomName)) {
    return { ok: false, statusCode: 404, body: { message: 'Unknown collaboration room' } }
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
    console.error('[workflow-collab] audit failed', error)
  }
}

function createSharedDoc(name) {
  const doc = new Y.Doc({ gc: true })
  const sharedDoc = Object.assign(doc, {
    name,
    conns: new Map(),
    awareness: new awarenessProtocol.Awareness(doc),
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
    return existingDoc
  }
  const createdDoc = createSharedDoc(name)
  docs.set(name, createdDoc)
  return createdDoc
}

function closeConnection(doc, conn) {
  if (doc.conns.has(conn)) {
    const controlledIds = doc.conns.get(conn) || new Set()
    doc.conns.delete(conn)
    awarenessProtocol.removeAwarenessStates(doc.awareness, Array.from(controlledIds), null)
    if (doc.conns.size === 0) {
      docs.delete(doc.name)
      doc.destroy()
    }
  }
  conn.close()
}

function send(doc, conn, payload) {
  if (conn.readyState !== wsReadyStateConnecting && conn.readyState !== wsReadyStateOpen) {
    closeConnection(doc, conn)
    return
  }
  try {
    conn.send(payload, {}, (error) => {
      if (error) {
        closeConnection(doc, conn)
      }
    })
  } catch {
    closeConnection(doc, conn)
  }
}

function handleMessage(conn, doc, message) {
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
    }
  } catch (error) {
    console.error('[workflow-collab] message handling failed', error)
  }
}

function setupWsConnection(conn, request, roomName, auth = null) {
  conn.binaryType = 'arraybuffer'
  const doc = getSharedDoc(roomName)
  doc.conns.set(conn, new Set())
  conn.auth = auth
  conn.authToken = extractToken(request)
  conn.connectionId = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

  if (auth) {
    console.info(
      `[workflow-collab] join room=${roomName} user=${auth.userId} name=${auth.displayName} processDefinitionId=${auth.processDefinitionId || '-'}`
    )
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

  let pongReceived = true
  const pingInterval = setInterval(() => {
    if (!pongReceived) {
      if (doc.conns.has(conn)) {
        closeConnection(doc, conn)
      }
      clearInterval(pingInterval)
      return
    }
    if (doc.conns.has(conn)) {
      pongReceived = false
      try {
        conn.ping()
      } catch {
        closeConnection(doc, conn)
        clearInterval(pingInterval)
      }
    }
  }, pingTimeout)

  conn.on('close', () => {
    if (conn.auth) {
      console.info(
        `[workflow-collab] leave room=${roomName} user=${conn.auth.userId} name=${conn.auth.displayName}`
      )
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
    closeConnection(doc, conn)
    clearInterval(pingInterval)
  })
  conn.on('pong', () => {
    pongReceived = true
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
      awarenessEncoder,
      awarenessProtocol.encodeAwarenessUpdate(doc.awareness, Array.from(awarenessStates.keys()))
    )
    send(doc, conn, encoding.toUint8Array(awarenessEncoder))
  }
}

const wss = new WebSocketServer({ noServer: true })

const server = http.createServer((request, response) => {
  if ((request.url || '').startsWith('/health')) {
    writeJson(response, 200, {
      status: 'UP',
      mode: 'auth',
      host,
      port,
      rooms: docs.size,
    })
    return
  }

  writeJson(response, 200, {
    service: 'workflow-designer-collaboration',
    transport: 'y-websocket-compatible',
    mode: 'auth',
  })
})

wss.on('connection', (conn, request, roomName, auth) => {
  setupWsConnection(conn, request, roomName, auth)
})

server.on('upgrade', async (request, socket, head) => {
  const authResult = await authorizeUpgrade(request)
  if (!authResult.ok) {
    socket.write(
      `HTTP/1.1 ${authResult.statusCode} Unauthorized\r\nContent-Type: application/json\r\n\r\n${JSON.stringify(
        authResult.body
      )}`
    )
    socket.destroy()
    return
  }

  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request, authResult.roomName, authResult.auth)
  })
})

server.listen(port, host, () => {
  console.log(
    `[workflow-collab] listening on ws://${host}:${port} (auth)`
  )
})

function shutdown() {
  wss.clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.close()
    }
  })
  wss.close(() => {
    server.close(() => {
      process.exit(0)
    })
  })
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
