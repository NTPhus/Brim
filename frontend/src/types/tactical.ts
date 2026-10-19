export interface TacticalVehicleView {
  rulesVersion: string
  weapon: { state: 'READY' | 'LOADING' | 'EMPTY' | 'DISABLED'; chamber: number; reserve: number; remainingMs: number }
  speed: number
  fuel: number
  fuelCapacity: number
  repairKits: number
  repairRemainingMs: number
  settled: boolean
  range: number
  lastCommandSeq: number
  commandStatus: string
}
export interface TacticalSupply { id: string; x: number; y: number; kind: 'SHELLS' | 'FUEL' | 'REPAIR'; quantity: number }
export interface TacticalContact { id: string; x: number; y: number; ageMs: number; current: boolean }
export const tacticalMessages: Record<string, string> = {
  SUPPLIED: 'Đã nhận vật tư; phần dư còn lại trong hòm.', INVENTORY_FULL: 'Đã mang đủ vật tư loại này.',
  NO_SUPPLY_NEARBY: 'Cần tới gần hòm tiếp tế và có đường tiếp cận.', REPAIR_STARTED: 'Đang sửa xe; giữ yên và ngừng bắn.',
  CANNOT_REPAIR: 'Cần dừng xe, có bộ sửa và HP dưới 70%.', ACK: 'Đã nhận lệnh.', INVALID_ACTION: 'Thao tác không hợp lệ.'
}
