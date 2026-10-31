import { formatMoneyInput, moneyToNumber } from './money.js';

export const RAM_OPTIONS = [4, 6, 8, 12, 16, 24];
export const STORAGE_OPTIONS = [64, 128, 256, 512, 1024];
export const TRANG_THAI_PHIEN_BAN = [
  'CON_HANG',
  'HET_HANG',
  'NGUNG_KINH_DOANH'
];

let keySeq = 0;
const nextKey = (prefix) => `${prefix}-${Date.now().toString(36)}-${(keySeq += 1)}`;

export const newColorRow = () => ({
  key: nextKey('color'),
  maPhienBan: null,
  sku: '',
  mauSac: '',
  soLuongTon: 0,
  trangThai: 'CON_HANG',
});

export const newConfiguration = () => ({
  key: nextKey('config'),
  ram: '',
  boNho: '',
  giaBan: '',
  giaNiemYet: '',
  priceConflict: false,
  originalPrices: [],
  colors: [newColorRow()],
});

export function configurationLabel(config) {
  const ram = config?.ram === '' || config?.ram == null ? '?' : config.ram;
  const boNho = config?.boNho === '' || config?.boNho == null ? '?' : config.boNho;
  return `${ram}GB / ${boNho}GB`;
}

const moneyText = (value) => (value === null || value === undefined || value === '' ? '' : String(Number(value)));

/**
 * Backend trả phienBans phẳng (mỗi màu một record).
 * UI admin gom lại theo RAM + bộ nhớ; mỗi màu vẫn giữ maPhienBan/sku/trạng thái.
 */
export function groupVariantsForForm(phienBans = []) {
  const map = new Map();
  const warnings = [];
  const list = Array.isArray(phienBans) ? phienBans : [];

  for (const pb of list) {
    const ram = pb.ram === null || pb.ram === undefined ? '' : String(pb.ram);
    const boNho = pb.boNho === null || pb.boNho === undefined ? '' : String(pb.boNho);
    const groupKey = `${ram}|${boNho}`;

    let config = map.get(groupKey);
    if (!config) {
      config = {
        key: nextKey('config'),
        ram,
        boNho,
        giaBan: moneyText(pb.giaBan),
        giaNiemYet: moneyText(pb.giaNiemYet),
        priceConflict: false,
        originalPrices: [],
        colors: [],
      };
      map.set(groupKey, config);
    }

    const giaBan = moneyText(pb.giaBan);
    if (giaBan !== '' && !config.originalPrices.includes(giaBan)) {
      config.originalPrices.push(giaBan);
    }

    config.colors.push({
      key: pb.maPhienBan ? `color-${pb.maPhienBan}` : nextKey('color'),
      maPhienBan: pb.maPhienBan || null,
      sku: pb.sku || '',
      mauSac: pb.mauSac || '',
      soLuongTon: pb.soLuongTon ?? 0,
      trangThai: pb.trangThai || (Number(pb.soLuongTon) > 0 ? 'CON_HANG' : 'HET_HANG'),
    });
  }

  for (const config of map.values()) {
    if (config.originalPrices.length > 1) {
      config.priceConflict = true;
      warnings.push(
        `Phiên bản ${configurationLabel(config)} có nhiều mức giá khác nhau (${config.originalPrices
          .map((p) => formatMoneyInput(p))
          .join(', ')}). Khi lưu, giá của phiên bản sẽ được áp dụng cho tất cả màu.`,
      );
    }
  }

  return { configurations: [...map.values()], warnings };
}

/**
 * Chuyển state UI (cấu hình + nhiều màu) về đúng DTO phẳng của backend.
 * Mỗi màu trở thành một PhienBanSanPham riêng và nhận giá của cấu hình.
 */
export function flattenConfigurations(configurations = []) {
  const rows = [];

  for (const config of configurations) {
    const ram = Number(config.ram);
    const boNho = Number(config.boNho);
    const giaBan = moneyToNumber(config.giaBan);
    const giaNiemYet = moneyToNumber(config.giaNiemYet);

    for (const color of config.colors || []) {
      rows.push({
        maPhienBan: color.maPhienBan || null,
        sku: (color.sku || '').trim(),
        ram,
        boNho,
        mauSac: (color.mauSac || '').trim(),
        giaBan,
        giaNiemYet,
        soLuongTon: Number(color.soLuongTon || 0),
        trangThai: color.trangThai || (Number(color.soLuongTon) > 0 ? 'CON_HANG' : 'HET_HANG'),
      });
    }
  }

  return rows;
}

/**
 * Validate toàn bộ cấu hình trước khi flatten. Trả về mảng thông báo lỗi.
 */
export function validateConfigurations(configurations = []) {
  const errors = [];

  if (!Array.isArray(configurations) || configurations.length === 0) {
    return ['Sản phẩm phải có ít nhất một phiên bản'];
  }

  const combos = new Set();
  const skus = new Set();

  configurations.forEach((config, index) => {
    const label = configurationLabel(config);
    const ram = Number(config.ram);
    const boNho = Number(config.boNho);
    const validRam = Number.isInteger(ram) && ram > 0;
    const validBoNho = Number.isInteger(boNho) && boNho > 0;

    if (!validRam) errors.push(`Phiên bản ${index + 1}: RAM phải là số nguyên lớn hơn 0`);
    if (!validBoNho) errors.push(`Phiên bản ${index + 1}: bộ nhớ phải là số nguyên lớn hơn 0`);

    if (validRam && validBoNho) {
      const combo = `${ram}|${boNho}`;
      if (combos.has(combo)) errors.push(`Phiên bản ${label} đã tồn tại`);
      else combos.add(combo);
    }

    const giaBan = moneyToNumber(config.giaBan);
    const giaNiemYet = moneyToNumber(config.giaNiemYet);

    if (giaBan === null || !Number.isFinite(giaBan) || giaBan < 0) {
      errors.push(`Phiên bản ${label}: giá bán không hợp lệ`);
    }
    if (giaNiemYet !== null && giaBan !== null && giaNiemYet < giaBan) {
      errors.push(`Phiên bản ${label}: giá niêm yết không được nhỏ hơn giá bán`);
    }

    if (!config.colors || config.colors.length === 0) {
      errors.push(`Phiên bản ${label} phải có ít nhất một màu`);
      return;
    }

    const colorKeys = new Set();
    config.colors.forEach((color) => {
      const mauSac = (color.mauSac || '').trim();
      const colorKey = mauSac.toLowerCase();
      const colorLabel = mauSac || '(chưa nhập)';

      if (!mauSac) {
        errors.push(`Phiên bản ${label}: màu sắc không được để trống`);
      } else if (colorKeys.has(colorKey)) {
        errors.push(`Màu sắc "${mauSac}" đã tồn tại trong phiên bản ${label}`);
      } else {
        colorKeys.add(colorKey);
      }

      const soLuongTon = Number(color.soLuongTon);
      if (!Number.isInteger(soLuongTon) || soLuongTon < 0) {
        errors.push(`Phiên bản ${label}, màu ${colorLabel}: số lượng tồn phải là số nguyên không âm`);
      }

      if (!TRANG_THAI_PHIEN_BAN.includes(color.trangThai)) {
        errors.push(`Phiên bản ${label}, màu ${colorLabel}: trạng thái không hợp lệ`);
      }

      const sku = (color.sku || '').trim().toLowerCase();
      if (sku) {
        if (skus.has(sku)) errors.push(`SKU bị trùng: ${color.sku}`);
        else skus.add(sku);
      }
    });
  });

  return errors;
}
