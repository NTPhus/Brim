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

const HISTORY = {
  LANG_VAY: {
    label: 'Làng Vây 1968', era: '6–7 / 02 / 1968', title: 'Làng Vây · hiệp đồng và tín hiệu',
    fact: 'Tại Làng Vây, xe tăng PT-76 hiệp đồng với bộ binh; súng tín hiệu được dùng để phối hợp các mũi tiến công.',
    source: 'Bảo tàng Lịch sử Quân sự Việt Nam / Câu chuyện lịch sử',
    url: 'https://cauchuyenlichsu.vn/ky-uc-song-mai/khau-sung-hieu-lenh-trong-tran-danh-lang-vay'
  },
  TRUONG_SON: {
    label: 'Trường Sơn 1959–1975', era: '1959–1975', title: 'Trường Sơn · mạch tiếp vận',
    fact: 'Từ năm 1959, tuyến Trường Sơn từng bước nối hậu phương với chiến trường. Đường vận tải, kho và trạm sửa chữa giúp duy trì tiếp tế.',
    source: 'Bảo tàng Lịch sử Quốc gia',
    url: 'https://baotanglichsu.vn/vi/Articles/3096/18043/djuong-truong-son-con-djuong-noi-hau-phuong-va-tien-tuyen.html'
  },
  APRIL_1975: {
    label: 'Tăng thiết giáp 1975', era: '30 / 04 / 1975', title: 'Dấu mốc của lực lượng tăng thiết giáp',
    fact: 'Ngày 30/4/1975, xe tăng 843 (T-54B) và 390 (T-59) tiến vào Dinh Độc Lập. Hai hiện vật được công nhận là bảo vật quốc gia.',
    source: 'Bảo tàng Lịch sử Quốc gia',
    url: 'https://baotanglichsu.vn/vi/Articles/1508/51535/bao-vat-quoc-gia-trong-bao-tang-luc-luong-tang-thiet-giap.html'
  }
} as const

// These tactical layouts and encounters are fiction, linked to documented themes.
const OPERATIONS = {
  ARENA: { title: 'Mở hành lang', history: 'LANG_VAY', sectors: ['Trạm tín hiệu', 'Điểm tập kết', 'Hành lang an toàn'] },
  WAREHOUSE: { title: 'Giữ tuyến hậu cần', history: 'APRIL_1975', sectors: ['Cổng kho phía bắc', 'Trạm sửa dã chiến', 'Cửa ngõ tiếp vận'] },
  CROSSROADS: { title: 'Giữ đầu mối liên lạc', history: 'LANG_VAY', sectors: ['Đài liên lạc', 'Giao điểm phía đông', 'Lối rút an toàn'] },
  CANYON: { title: 'Thông tuyến qua đèo', history: 'TRUONG_SON', sectors: ['Trạm dẫn đường', 'Điểm giao hàng', 'Cửa đèo'] },
  RUINS: { title: 'Mở đường qua khu đổ nát', history: 'APRIL_1975', sectors: ['Trạm quan sát', 'Ngã ba dã chiến', 'Hành lang đội xe'] },
  FORTRESS: { title: 'Kiểm soát vành đai', history: 'LANG_VAY', sectors: ['Trạm hiệu lệnh', 'Cửa vành đai', 'Điểm hội quân'] },
  DEPOT: { title: 'Bảo toàn mạch vận tải', history: 'TRUONG_SON', sectors: ['Kho trung chuyển', 'Trạm nhiên liệu', 'Bến giao nhận'] },
  BUNKERS: { title: 'Hiệp đồng qua công sự', history: 'LANG_VAY', sectors: ['Điểm quan sát', 'Lối xuyên công sự', 'Trạm hội quân'] }
} as const

export function tankOperation(id: string) {
  const operation = OPERATIONS[id as TankMapId] ?? OPERATIONS.ARENA
  return { ...operation, context: HISTORY[operation.history] }
}

/** Same swept, expanded-wall contact calculation as the authoritative simulation. */
export function shellImpact(fromX: number, fromY: number, toX: number, toY: number, walls: TankWall[]) {
  let hit = 1
  for (const wall of walls) {
    if (wall.blocksProjectile === false) continue
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
