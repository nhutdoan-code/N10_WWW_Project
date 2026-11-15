/**
 * 2B.7C: giỏ khách vãng lai lưu trong localStorage là ảnh chụp cũ (có thể từ phiên bản
 * Frontend cũ, thiếu duocPhepBan hoặc giá đã đổi). Luôn làm mới từ Backend; biến thể
 * không xác minh được thì đánh dấu KHÔNG được bán (fail-closed) nhưng vẫn cho xóa.
 *
 * @param guest danh sách dòng giỏ { sanPham, phienBan, soLuong } đọc từ localStorage
 * @param fetchProduct (maSanPham) => Promise<SanPhamDto> (API công khai /products/{ma})
 */
export async function refreshGuestItems(guest, fetchProduct) {
  const list = Array.isArray(guest) ? guest.filter((i) => i?.phienBan?.maPhienBan) : [];
  const maSps = [...new Set(list.map((i) => i.sanPham?.maSanPham).filter(Boolean))];
  const fresh = new Map();
  await Promise.all(maSps.map(async (ma) => {
    try {
      fresh.set(ma, await fetchProduct(ma));
    } catch {
      fresh.set(ma, null);
    }
  }));
  return list.map((i) => {
    const sp = fresh.get(i.sanPham?.maSanPham);
    const pb = sp?.phienBans?.find((x) => x.maPhienBan === i.phienBan.maPhienBan);
    if (!sp || !pb) {
      return { ...i, phienBan: { ...i.phienBan, duocPhepBan: false }, khongXacMinh: true };
    }
    return { ...i, sanPham: sp, phienBan: pb };
  });
}
