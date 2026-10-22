const yn = (v) => (v ? 'Có' : 'Không');

export const SPEC_FIELDS = [
  { key: 'kichThuocManHinh', label: 'Kích thước màn hình', show: (t) => t.kichThuocManHinh && `${t.kichThuocManHinh} inch` },
  { key: 'loaiManHinh', label: 'Công nghệ màn hình', show: (t) => t.loaiManHinh },
  { key: 'tanSoQuet', label: 'Tần số quét', show: (t) => t.tanSoQuet && `${t.tanSoQuet} Hz` },
  { key: 'chip', label: 'Chip xử lý', show: (t) => t.chip },
  { key: 'ram', label: 'RAM', show: (t) => t.ram && `${t.ram} GB` },
  { key: 'boNho', label: 'Bộ nhớ trong', show: (t) => t.boNho && `${t.boNho} GB` },
  { key: 'cameraSau', label: 'Camera sau', show: (t) => t.cameraSau },
  { key: 'cameraTruoc', label: 'Camera trước', show: (t) => t.cameraTruoc && `${t.cameraTruoc} MP` },
  { key: 'pin', label: 'Pin', show: (t) => t.pin && `${t.pin} mAh` },
  { key: 'congSuatSac', label: 'Công suất sạc', show: (t) => t.congSuatSac && `${t.congSuatSac} W` },
  { key: 'heDieuHanh', label: 'Hệ điều hành', show: (t) => t.heDieuHanh },
  { key: 'nfc', label: 'NFC', show: (t) => yn(t.nfc) },
  { key: 'ho5G', label: 'Hỗ trợ 5G', show: (t) => yn(t.ho5G) },
  { key: 'trongLuong', label: 'Trọng lượng', show: (t) => t.trongLuong && `${t.trongLuong} g` },
];

export const specValue = (field, thongSo) => field.show(thongSo || {}) || '—';
