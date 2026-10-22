export const NGUONG_SAP_HET = 10;

export const productStatus = (p) => {
  if (p.trangThai !== 'CON_HANG' && p.trangThai !== 'HET_HANG') return p.trangThai;
  if (p.soLuongTon <= 0) return 'HET_HANG';
  return p.soLuongTon <= NGUONG_SAP_HET ? 'SAP_HET' : 'CON_HANG';
};
