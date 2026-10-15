import { createId } from '@/utils/id'
import { Client, IMessage, StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { ref } from 'vue'

export type MessageHandler<T = unknown> = (message: T) => void
const connected = ref(false)
const sessionId = ref(createId())
const subscriptions = new Set<{ destination: string; handler: MessageHandler<unknown>; active?: StompSubscription }>()
const joins = new Map<string, { body: unknown; roomId: string }>()
let client: Client | null = null
let connection: Promise<void> | null = null
let hasConnected = false
const readyListeners = new Set<() => void>()

function attach(entry: typeof subscriptions extends Set<infer T> ? T : never) {
  entry.active = client!.subscribe(entry.destination, (message: IMessage) => {
    try { entry.handler(JSON.parse(message.body)) }
    catch (error) { console.error('WebSocket message error', error) }
  })
}

export function useStompClient() {
  function connect(): Promise<void> {
    if (client?.connected) return Promise.resolve()
    if (connection) return connection
    connection = new Promise<void>((resolve, reject) => {
      const finish = () => { clearTimeout(timeout); readyListeners.delete(finish); resolve() }
      const timeout = window.setTimeout(() => {
        readyListeners.delete(finish)
        reject(new Error('WebSocket connection timeout'))
      }, 10000)
      readyListeners.add(finish)
      if (client?.active) return
      client = new Client({
        webSocketFactory: () => new SockJS('/api/ws/game') as unknown as WebSocket,
        reconnectDelay: 2000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          connected.value = true
          subscriptions.forEach(attach)
          if (hasConnected) {
            // Restore membership only for screens that are still subscribed.
            joins.forEach((join, destination) => {
              if ([...subscriptions].some(entry => entry.destination.includes(`/${join.roomId}/`))) {
                client!.publish({ destination, body: JSON.stringify(join.body) })
              } else joins.delete(destination)
            })
          }
          hasConnected = true
          readyListeners.forEach(finish => finish())
        },
        onWebSocketClose: () => { connected.value = false },
        onStompError: frame => { connected.value = false; console.error(frame.headers.message) }
      })
      client.activate()
    }).finally(() => { connection = null })
    return connection
  }

  function subscribe<T>(destination: string, handler: MessageHandler<T>): () => void {
    const entry = { destination, handler: handler as MessageHandler<unknown>, active: undefined as StompSubscription | undefined }
    subscriptions.add(entry)
    if (client?.connected) attach(entry)
    return () => {
      if (client?.connected) entry.active?.unsubscribe()
      subscriptions.delete(entry)
      joins.forEach((join, destination) => {
        if (![...subscriptions].some(item => item.destination.includes(`/${join.roomId}/`))) joins.delete(destination)
      })
    }
  }

  function publish(destination: string, body: unknown): void {
    const match = destination.match(/^\/app\/(?:snake|tank|turn)\/([^/]+)\/join$/)
    if (match) joins.set(destination, { body, roomId: match[1] })
    // Inputs are replaceable: never buffer and replay old steering after an outage.
    if (!client?.connected) return
    client.publish({ destination, body: JSON.stringify(body) })
  }

  function disconnect() {
    void client?.deactivate()
    client = null
    connected.value = false
    hasConnected = false
    joins.clear()
  }
  return { connected, sessionId, connect, subscribe, publish, disconnect }
}
