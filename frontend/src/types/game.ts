export type GameTypeCode = 'VIETNAM_CAMPAIGN' | 'SNAKE' | 'TANK' | 'CHESS' | 'CHINESE_CHESS' | 'CONNECT_FOUR' | 'CARO' | 'OTHELLO' | 'BATTLESHIP' | 'MATCH_THREE' | 'BOMBERMAN' | 'WARLORDS_CAMPAIGN' | 'WARLORDS_DUEL'

export const GameType = {
  VIETNAM_CAMPAIGN: 'VIETNAM_CAMPAIGN',
  SNAKE: 'SNAKE',
  TANK: 'TANK',
  CHESS: 'CHESS',
  CHINESE_CHESS: 'CHINESE_CHESS',
  CONNECT_FOUR: 'CONNECT_FOUR',
  CARO: 'CARO', OTHELLO: 'OTHELLO', BATTLESHIP: 'BATTLESHIP', MATCH_THREE: 'MATCH_THREE', BOMBERMAN: 'BOMBERMAN', WARLORDS_CAMPAIGN: 'WARLORDS_CAMPAIGN', WARLORDS_DUEL: 'WARLORDS_DUEL'
} as const

export const GAME_TYPE_INFO: Record<GameTypeCode, { name: string; icon: string; realTime: boolean; solo?: boolean }> = {
  VIETNAM_CAMPAIGN: { name: 'Dấu chân Việt · Chiến dịch', icon: '★', realTime: true, solo: true },
  SNAKE: { name: 'Rắn Săn Mồi', icon: '🐍', realTime: true },
  TANK: { name: 'Tank Battle', icon: '🚜', realTime: true },
  CHESS: { name: 'Cờ Vua', icon: '♟️', realTime: false },
  CHINESE_CHESS: { name: 'Cờ Tướng', icon: '🀄', realTime: false },
  CONNECT_FOUR: { name: 'Connect Four', icon: '🔴', realTime: false },
  CARO: { name: 'Caro', icon: '✕', realTime: false },
  OTHELLO: { name: 'Othello', icon: '◐', realTime: false },
  BATTLESHIP: { name: 'Battleship', icon: '🚢', realTime: false },
  MATCH_THREE: { name: 'Match-3', icon: '💎', realTime: false, solo: true },
  BOMBERMAN: { name: 'Bomberman', icon: '💣', realTime: true },
  WARLORDS_CAMPAIGN: { name: 'Sơn Hà · Chiến dịch', icon: '⚔️', realTime: false, solo: true },
  WARLORDS_DUEL: { name: 'Sơn Hà · Đấu tướng', icon: '🏯', realTime: false }
}

export type RoomStatus = 'WAITING' | 'STARTING' | 'IN_PROGRESS' | 'PAUSED' | 'FINISHED' | 'CANCELLED'

export const MessageType = {
  JOIN_ROOM: 'JOIN_ROOM',
  LEAVE_ROOM: 'LEAVE_ROOM',
  ROOM_STATE: 'ROOM_STATE',
  PLAYER_JOINED: 'PLAYER_JOINED',
  PLAYER_LEFT: 'PLAYER_LEFT',
  PLAYER_READY: 'PLAYER_READY',
  START_GAME: 'START_GAME',
  GAME_STARTED: 'GAME_STARTED',
  MOVE: 'MOVE',
  SHOOT: 'SHOOT',
  TURN: 'TURN',
  ACTION: 'ACTION',
  STATE_UPDATE: 'STATE_UPDATE',
  GAME_OVER: 'GAME_OVER',
  SCORE_UPDATE: 'SCORE_UPDATE',
  TURN_START: 'TURN_START',
  TURN_END: 'TURN_END',
  MAKE_MOVE: 'MAKE_MOVE',
  MOVE_RESULT: 'MOVE_RESULT',
  ERROR: 'ERROR'
} as const

export type MessageType = typeof MessageType[keyof typeof MessageType]

export type PlayerState = 'WAITING' | 'PLAYING' | 'SPECTATING' | 'DISCONNECTED' | 'FINISHED'

export interface Player {
  id: string
  username: string
  sessionId: string
  score: number
  ready: boolean
  state: PlayerState
  color?: string
}

export interface GameRoom {
  id: string
  name: string
  gameType: GameTypeCode
  status: RoomStatus
  players: Player[]
  maxPlayers: number
  hostId: string
  settings: GameSettings
  createdAt: string
  startedAt: string | null
  endedAt: string | null
}

export interface GameSettings {
  customSettings: Record<string, unknown> | null
  maxPlayers: number
  timeLimitSeconds: number
  allowSpectators: boolean
}

export interface GameMessage<T = unknown> {
  type: MessageType
  roomId: string | null
  playerId: string | null
  payload: T
  timestamp: number
}

export interface CreateRoomRequest {
  name: string
  gameType: GameTypeCode
  maxPlayers: number
  hostId?: string
  hostUsername?: string
  difficulty?: 'EASY' | 'MEDIUM' | 'HARD'
  mapId?: import('@/game/tank/maps').TankMapId
  vietnamChapter?: number
  tankMode?: 'CLASSIC' | 'ASSAULT'
}

export interface JoinRoomRequest {
  username: string
  sessionId: string
  playerId?: string
}

export interface LeaderboardEntry {
  playerId: string
  username: string
  gameType: GameTypeCode
  score: number
}
