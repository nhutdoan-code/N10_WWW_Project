import { useParams } from 'react-router-dom';
import { PAYMENT_METHODS } from '../config/constants';
import useFetch from '../hooks/useFetch';
import { orderService } from '../service/orderService';
import { formatDate, formatPrice } from '../utils/format';

export default function InvoicePage() {
  const { ma } = useParams();
  const { data: o, loading, error } = useFetch(() => orderService.get(ma), [ma]);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <div className="invoice">
      <div className="row between no-print">
        <h2>Hóa đơn</h2>
        <button className="btn btn-primary" onClick={() => window.print()}>In / Lưu PDF</button>
      </div>
      <h1>PhoneNest - HÓA ĐƠN BÁN HÀNG</h1>
      <p>Mã đơn: <b>{o.maDonHang}</b> · Ngày: {formatDate(o.ngayMua)}</p>
      <p>Giao đến: {o.diaChiGiaoHang}</p>
      <p>Thanh toán: {PAYMENT_METHODS.find((m) => m.value === o.phuongThucTt)?.label}</p>
      <table className="table">
        <thead>
          <tr><th>Sản phẩm</th><th>Đơn giá</th><th>SL</th><th>Thành tiền</th></tr>
        </thead>
        <tbody>
          {o.chiTiet.map((d) => (
            <tr key={d.maSanPham}>
              <td>{d.tenMay}</td>
              <td>{formatPrice(d.donGia)}</td>
              <td>{d.soLuong}</td>
              <td>{formatPrice(d.donGia * d.soLuong)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>Tạm tính: {formatPrice(o.tamTinh)}</p>
      <p>Giảm giá: {formatPrice(o.giamGia)}</p>
      <p>Phí vận chuyển: {formatPrice(o.phiVanChuyen)}</p>
      <p>Giảm phí vận chuyển: {formatPrice(o.giamPhiVanChuyen)}</p>
      <h3>Tổng thanh toán: {formatPrice(o.tongTien)}</h3>
    </div>
  );
}
