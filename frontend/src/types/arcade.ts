export interface GridState {
  board: number[][]; player1: string; player2: string; currentPlayer: string;
  status: string; winner: string | null; score1: number; score2: number;
  legalMoves: Array<{ row: number; column: number }>;
  winningCells: Array<{ row: number; column: number }>;
  lastMove: { row: number; column: number } | null; passedPlayer: string | null;
}
export interface BattleshipState {
  phase: string; currentPlayer: string; winner: string | null;
  ownBoard: number[][]; targetBoard: number[][]; placedShips: number; opponentPlacedShips: number;
  fleet: number[]; nextShipLength: number | null; remainingOwn: number; remainingOpponent: number;
}
export interface MatchThreeState {
  board: number[][]; covers: number[][]; level: number; unlocked: number; movesLeft: number;
  score: number; target: number; collectType: number; collected: number; collectTarget: number;
  levelStatus: string; status: string; currentPlayer: string; winner: string | null;
  events: Array<{ type: string; cells: Array<{ row: number; column: number }>; board: number[][]; points: number }>;
}
export interface BombermanState {
  board: number[][]; players: Array<{ id: string; name: string; row: number; column: number; alive: boolean; range: number; capacity: number }>;
  bombs: Array<{ row: number; column: number; owner: string; range: number; explodesAt: number }>;
  flames: Array<{ row: number; column: number }>; items: Record<string, string>;
  status: string; winner: string | null; ticksLeft: number; revision: number;
}
