import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { ref } from 'vue'

const WS_URL = '/api/ws/game'

export type MessageHandler<T = unknown> = (message: T) => void

let sharedClient: Client | null = null
let connectPromise: Promise<void> | null = null
const connected = ref(false)
const sessionId = ref(crypto.randomUUID())

// Queue subscriptions that were requested before connection was ready
type PendingSubscription = {
  destination: string
  handler: MessageHandler<unknown>
  resolve: (unsub: () => void) => void
}
const pendingSubscriptions: PendingSubscription[] = []

export function useStompClient() {
  async function connect(): Promise<void> {
    if (sharedClient?.connected) return
    if (connectPromise) return connectPromise

    connectPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('WebSocket connection timeout'))
      }, 10000)

      if (sharedClient) {
        void sharedClient.deactivate()
        sharedClient = null
      }

      const client = new Client({
        webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
        debug: (str) => console.debug('[STOMP]', str),
        reconnectDelay: 3000,
        onConnect: () => {
          clearTimeout(timeout)
          connected.value = true
          console.debug('[STOMP] Connected')

          // Flush pending subscriptions
          while (pendingSubscriptions.length > 0) {
            const pending = pendingSubscriptions.shift()!
            const sub = client.subscribe(pending.destination, (msg: IMessage) => {
              try {
                pending.handler(JSON.parse(msg.body))
              } catch (e) {
                console.error(`[STOMP] Failed to parse message from ${pending.destination}:`, e)
              }
            })
            pending.resolve(() => sub.unsubscribe())
          }

          resolve()
        },
        onStompError: (frame) => {
          console.error('[STOMP] Error:', frame.headers['message'])
          connected.value = false
        },
        onWebSocketClose: () => {
          connected.value = false
          console.debug('[STOMP] Disconnected')
        }
      })

      sharedClient = client
      client.activate()
    }).finally(() => {
      connectPromise = null
    })

    return connectPromise
  }

  /**
   * Subscribe to a STOMP destination.
   * If not yet connected, the subscription is queued and resolved once connected.
   */
  function subscribe<T>(destination: string, handler: MessageHandler<T>): () => void {
    if (sharedClient?.connected) {
      const sub = sharedClient.subscribe(destination, (msg: IMessage) => {
        try {
          handler(JSON.parse(msg.body) as T)
        } catch (e) {
          console.error(`[STOMP] Failed to parse message from ${destination}:`, e)
        }
      })
      return () => sub.unsubscribe()
    }

    // Not connected yet — queue the subscription
    console.warn(`[STOMP] Not yet connected, queuing subscription to ${destination}`)
    let unsubFn: (() => void) | null = null
    let unsubRequested = false

    const subscribePromise = new Promise<() => void>((resolve) => {
      pendingSubscriptions.push({
        destination,
        handler: handler as MessageHandler<unknown>,
        resolve
      })
    }).then((unsub) => {
      unsubFn = unsub
      if (unsubRequested) {
        unsub()
      }
    })

    void subscribePromise

    return () => {
      if (unsubFn) {
        unsubFn()
      } else {
        unsubRequested = true
      }
    }
  }

  function publish(destination: string, body: unknown): void {
    if (!sharedClient?.connected) {
      console.warn(`[STOMP] Not connected, cannot publish to ${destination}`)
      return
    }
    sharedClient.publish({
      destination,
      body: JSON.stringify(body)
    })
  }

  function disconnect(): void {
    if (sharedClient) {
      void sharedClient.deactivate()
      sharedClient = null
      connected.value = false
    }
  }

  return {
    connected,
    sessionId,
    connect,
    subscribe,
    publish,
    disconnect
  }
}
