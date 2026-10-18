export interface WarlordsProfile {
  gold: number; experience: number; weapon: number; armor: number; skill: number;
  unlocked: number; cleared: number[]; wins: number; losses: number
}
export interface WarlordsChapter {
  id: number; title: string; warlord: string; location: string; story: string; victory: string;
  encounter: string; enemyHp: number; enemyAttack: number; gold: number; experience: number; x: number; y: number
}
export interface WarlordsSkill { id: string; name: string; cost: number; description: string }
export interface WarlordsFighter {
  id: string; name: string; hp: number; maxHp: number; energy: number; shield: number; attack: number;
  skillRank: number; gold: number; experience: number; bot: boolean; missedTurns: number
}
export interface WarlordsEvent {
  type: string; actor: string; text: string; cells: Array<{ row: number; column: number }>; board: number[][];
  effects: { damage: number; absorbed: number; healing: number; energy: number; shield: number; gold: number; experience: number; combo: number } | null
}
export interface WarlordsState {
  mode: 'CAMPAIGN' | 'DUEL'; phase: 'MAP' | 'BATTLE' | 'RESULT'; status: string; winner: string | null;
  currentPlayer: string; encounterId: string; revision: number; turnDeadline: number; turnNumber: number;
  board: number[][]; fighters: WarlordsFighter[]; chapter: WarlordsChapter | null; chapters: WarlordsChapter[];
  skills: WarlordsSkill[]; profile: WarlordsProfile | null; events: WarlordsEvent[];
  rewards: Array<{ playerId: string; gold: number; experience: number }>; rewardsPending: boolean; practice: boolean; notice: string
}
export interface WarlordsHub { profile: WarlordsProfile; chapters: WarlordsChapter[]; skills: WarlordsSkill[] }
export const resourceNames = ['', 'Kiếm', 'Năng lượng', 'Hồi máu', 'Khiên', 'Vàng', 'Kinh nghiệm']
export const heroLevel = (profile: WarlordsProfile) => Math.min(20, 1 + Math.floor(profile.experience / 100))
export const upgrades = [
  { id: 'WEAPON', field: 'weapon', name: 'Rèn binh khí', icon: '⚔', description: '+1 sát thương cho mỗi ô kiếm', base: 60 },
  { id: 'ARMOR', field: 'armor', name: 'Củng cố giáp', icon: '⛨', description: '+15 HP tối đa', base: 50 },
  { id: 'SKILL', field: 'skill', name: 'Học binh pháp', icon: '✦', description: 'Tăng sức mạnh cả ba kỹ năng', base: 80 }
] as const
