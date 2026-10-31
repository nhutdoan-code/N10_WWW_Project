export const NGUONG_SAP_HET = 10;

export const productStatus = (p) => {
  if (p.trangThai === 'NGUNG_KINH_DOANH') return 'NGUNG_KINH_DOANH';
  if (p.soLuongTon <= 0) return 'HET_HANG';
  return p.soLuongTon <= NGUONG_SAP_HET ? 'SAP_HET' : 'CON_HANG';
};
