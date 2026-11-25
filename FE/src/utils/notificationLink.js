// Trang cần mở khi khách bấm vào một thông báo.
// Ưu tiên đường dẫn backend gửi kèm (ThongBao.duongDan); thông báo cũ chưa có thì suy ra từ nội dung.
const MA_DON = /\bDH\d+\b/;

export function notificationLink(n) {
  if (!n) return null;
  const link = typeof n.duongDan === 'string' ? n.duongDan.trim() : '';
  // Chỉ nhận đường dẫn nội bộ của SPA, tránh mở URL ngoài từ dữ liệu thông báo.
  if (link.startsWith('/') && !link.startsWith('//')) return link;

  const noiDung = n.noiDung || '';
  if (/^yêu cầu/i.test(noiDung.trim())) return '/returns';
  const ma = noiDung.match(MA_DON);
  if (ma) return `/orders/${ma[0]}`;
  return null;
}
