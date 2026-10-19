import type { TankWall } from '@/types/gameState'

export const TANK_MAPS = {
  ARENA: { name: 'Đấu trường mở', detail: 'Khoảng trống rộng, giao tranh trực diện', terrain: 0x737e78, wall: 0x40516a },
  WAREHOUSE: { name: 'Nhà kho', detail: 'Các dãy kho và hành lang giao nhau', terrain: 0x777c85, wall: 0x465168 },
  CROSSROADS: { name: 'Giao lộ', detail: 'Bốn khu nhà quanh ngã tư trung tâm', terrain: 0x72746c, wall: 0x596271 },
  CANYON: { name: 'Hẻm núi', detail: 'Vách đá xen kẽ, đường vòng quanh sườn', terrain: 0xbe9365, wall: 0x946044 },
  RUINS: { name: 'Phế tích', detail: 'Tường đổ chữ L, nhiều góc phục kích', terrain: 0x89936b, wall: 0x747867 },
  FORTRESS: { name: 'Pháo đài', detail: 'Sân trong và bốn cửa đột nhập', terrain: 0x7b8585, wall: 0x555e70 },
  DEPOT: { name: 'Bãi tiếp vận', detail: 'Container so le và nhiều lối xuyên ngang', terrain: 0x95816c, wall: 0x81634f },
  BUNKERS: { name: 'Cụm lô cốt', detail: 'Lô cốt rải rác quanh chiến trường trung tâm', terrain: 0x65836d, wall: 0x465b45 }
} as const

export type TankMapId = keyof typeof TANK_MAPS
export function tankMap(id: string) { return TANK_MAPS[id as TankMapId] ?? TANK_MAPS.ARENA }

/** Same swept, expanded-wall contact calculation as the authoritative simulation. */
export function shellImpact(fromX: number, fromY: number, toX: number, toY: number, walls: TankWall[]) {
  let hit = 1
  for (const wall of walls) {
    let enter = 0, exit = 1
    const origin = [fromX, fromY], delta = [toX - fromX, toY - fromY]
    const min = [wall.x - 5, wall.y - 5], max = [wall.x + wall.width + 5, wall.y + wall.height + 5]
    for (let axis = 0; axis < 2; axis++) {
      if (Math.abs(delta[axis]) < 1e-9) {
        if (origin[axis] < min[axis] || origin[axis] > max[axis]) { enter = Infinity; break }
      } else {
        const near = (min[axis] - origin[axis]) / delta[axis], far = (max[axis] - origin[axis]) / delta[axis]
        enter = Math.max(enter, Math.min(near, far)); exit = Math.min(exit, Math.max(near, far))
        if (enter > exit) { enter = Infinity; break }
      }
    }
    hit = Math.min(hit, enter)
  }
  return { x: fromX + (toX - fromX) * hit, y: fromY + (toY - fromY) * hit, blocked: hit < 1 }
}
