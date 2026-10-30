export const STORAGE_KEYS = { user: 'phonenest_user', guestCart: 'phonenest_guest_cart', compare: 'phonenest_compare' };

export const SORT_OPTIONS = [
  { value: '', label: 'Mới nhất' },
  { value: 'ban_chay', label: 'Bán chạy' },
  { value: 'gia_asc', label: 'Giá thấp đến cao' },
  { value: 'gia_desc', label: 'Giá cao đến thấp' },
  { value: 'danh_gia', label: 'Đánh giá cao' },
];

export const PRICE_RANGES = [
  { label: 'Tất cả mức giá' },
  { label: 'Dưới 5 triệu', max: 5000000 },
  { label: '5 - 10 triệu', min: 5000000, max: 10000000 },
  { label: '10 - 20 triệu', min: 10000000, max: 20000000 },
  { label: 'Trên 20 triệu', min: 20000000 },
];

export const PAYMENT_METHODS = [
  { value: 'COD', label: 'Thanh toán khi nhận hàng (COD)' },
  { value: 'CHUYEN_KHOAN', label: 'Chuyển khoản ngân hàng' },
  { value: 'VI_DIEN_TU', label: 'Ví điện tử' },
];

export const ORDER_STATUS = {
  CHO_XAC_NHAN: 'Chờ xác nhận',
  DA_XAC_NHAN: 'Đã xác nhận',
  DANG_CHUAN_BI: 'Đang chuẩn bị',
  DANG_GIAO: 'Đang giao',
  DA_GIAO: 'Đã giao',
  DA_HUY: 'Đã hủy',
  DANG_XU_LY: 'Đang xử lý',
};

export const CANCEL_REASONS = [
  'Muốn thay đổi địa chỉ giao hàng',
  'Muốn nhập / thay đổi mã khuyến mãi',
  'Muốn thay đổi sản phẩm trong đơn (màu sắc, dung lượng, số lượng...)',
  'Thủ tục thanh toán quá rắc rối',
  'Tìm thấy giá tốt hơn ở nơi khác',
  'Đổi ý, không muốn mua nữa',
  'Lý do khác',
];

export const PRODUCT_STATUS = {
  CON_HANG: 'Còn hàng',
  SAP_HET: 'Sắp hết hàng',
  HET_HANG: 'Hết hàng',
  NGUNG_KINH_DOANH: 'Ngừng kinh doanh',
};

export const RETURN_STATUS = {
  CHO_DUYET: 'Chờ duyệt',
  DA_DUYET: 'Đã duyệt',
  TU_CHOI: 'Từ chối',
  DA_HOAN_TIEN: 'Đã hoàn tiền',
};

export const ORDER_STEPS = ['CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DANG_CHUAN_BI', 'DANG_GIAO', 'DA_GIAO'];

