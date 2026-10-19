/**
 * Snake game state (mirrors backend SnakeGame.SnakeGameState)
 */
export interface SnakePosition {
  x: number
  y: number
}

export interface SnakeFood extends SnakePosition {
  radius: number
  value: number
}

export interface SnakeSnake {
  playerId: string
  username?: string
  color?: string
  body: SnakePosition[]
  angle: number
  targetAngle: number
  boosting: boolean
  alive: boolean
  score: number
  maxLength: number
  diedAt: number
}

export interface SnakeGameState {
  serverTime?: number
  snakes: Record<string, SnakeSnake>
  food: SnakeFood[]
  state: 'WAITING' | 'IN_PROGRESS' | 'FINISHED'
  gridWidth: number
  gridHeight: number
}

/**
 * Tank game state (mirrors backend TankGame.TankGameState)
 */
export interface TankTank {
  turretAngle: number
  maxHealth: number
  credits: number
  engineLevel: number
  cannonLevel: number
  armorLevel: number
  playerId: string
  color?: string
  x: number
  y: number
  angle: number
  health: number
  score: number
  alive: boolean
  radius: number
  shootCooldown: number
  shieldUntil?: number
  speedUntil?: number
  rapidFireUntil?: number
  diedAt: number
}

export interface TankBullet {
  shell: boolean
  targetX: number
  targetY: number
  x: number
  y: number
  angle: number
  ownerId: string
  life: number
  damage: number
}

export interface TankHealthPack {
  type?: 'HEALTH' | 'SHIELD' | 'SPEED' | 'RAPID_FIRE'
  x: number
  y: number
  radius: number
  healAmount: number
}

export interface TankWall {
  x: number
  y: number
  width: number
  height: number
}

export interface TankInput {
  forward: boolean
  backward: boolean
  turnLeft: boolean
  turnRight: boolean
  shoot: boolean
}

export interface TankGameState {
  enemyMarkers: { x: number; y: number }[]
  mode: 'CLASSIC' | 'ASSAULT'
  mapId: string
  wave: number
  enemiesRemaining: number
  nextWaveTicks: number
  infantry: { id: string; x: number; y: number; health: number; cooldown: number }[]
  explosions: { id: number; x: number; y: number; radius: number; expiresAt: number }[]
  serverTime?: number
  tanks: Record<string, TankTank>
  bullets: TankBullet[]
  healthPacks: TankHealthPack[]
  walls: TankWall[]
  state: 'WAITING' | 'IN_PROGRESS' | 'FINISHED'
  mapWidth: number
  mapHeight: number
}

/**
 * Chess state (mirrors backend ChessGame.ChessGameState)
 */
export type PieceColor = 'WHITE' | 'BLACK'
export type PieceType = 'KING' | 'QUEEN' | 'ROOK' | 'BISHOP' | 'KNIGHT' | 'PAWN'

export interface ChessPiece {
  type: PieceType
  color: PieceColor
  x: number
  y: number
  hasMoved: boolean
}

export interface ChessGameState {
  pieces: Record<string, ChessPiece>
  currentPlayer: string | null
  status: 'IN_PROGRESS' | 'FINISHED' | 'DRAW'
  moveHistory: string[]
  whitePlayer?: string | null
  blackPlayer?: string | null
  winner?: string | null
  check?: boolean
  checkmate?: boolean
  legalMoves: ChessMove[]
  lastMove?: ChessMove | null
}

export interface ChessMove {
  fromX: number
  fromY: number
  toX: number
  toY: number
  promotion?: PieceType
}

/**
 * Chinese Chess state
 */
export type XiangqiPieceColor = 'RED' | 'BLACK'
export type XiangqiPieceType = 'GENERAL' | 'ADVISOR' | 'ELEPHANT' | 'HORSE' | 'CHARIOT' | 'CANNON' | 'SOLDIER'

export interface XiangqiPiece {
  type: XiangqiPieceType
  color: XiangqiPieceColor
  x: number
  y: number
}

export interface XiangqiGameState {
  pieces: Record<string, XiangqiPiece>
  currentPlayer: string | null
  status: 'IN_PROGRESS' | 'FINISHED' | 'DRAW'
  moveHistory: string[]
  redPlayer?: string | null
  blackPlayer?: string | null
  winner?: string | null
  check?: boolean
  checkmate?: boolean
  legalMoves: XiangqiMove[]
  lastMove?: XiangqiMove | null
}

export interface XiangqiMove {
  fromX: number
  fromY: number
  toX: number
  toY: number
}
export interface ConnectFourState {
  roomId: string
  board: number[][]
  player1: string
  player2: string
  currentPlayer: string
  status: 'IN_PROGRESS' | 'FINISHED' | 'DRAW'
  winner: string | null
  moveCount: number
  lastMove: { row: number; column: number } | null
  winningCells: Array<{ row: number; column: number }>
  legalColumns: number[]
}
