import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import FormField from '../components/FormField';
import StatusBadge from '../components/StatusBadge';
import { ORDER_STATUS, ORDER_STEPS, PAYMENT_METHODS } from '../config/constants';
import useCart from '../hooks/useCart';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { orderService } from '../service/orderService';
import { errorMessage, formatDate, formatPrice } from '../utils/format';

export default function OrderDetailPage() {
  const { ma } = useParams();
  const { data: o, loading, error, reload } = useFetch(() => orderService.get(ma), [ma]);
  const [returnForm, setReturnForm] = useState(null);
  const { replace } = useCart();
  const toast = useToast();
  const navigate = useNavigate();

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;

  const huy = o.trangThai === 'DA_HUY';
  const buoc = ORDER_STEPS.indexOf(o.trangThai);

  return (
    <>
      <div className="row between">
        <div>
          <h2 style={{ marginBottom: 0 }}>Đơn hàng {o.maDonHang}</h2>
          <p className="muted">Đặt lúc {formatDate(o.ngayMua)} · {PAYMENT_METHODS.find((m) => m.value === o.phuongThucTt)?.label}</p>
        </div>
        <StatusBadge status={o.trangThai} />
      </div>

      <div className="order-layout">
        <div>
          <div className="section">
            <h3>Theo dõi vận chuyển</h3>
            {huy ? (
              <p className="error">Đơn hàng đã bị hủy (cập nhật {formatDate(o.ngayCapNhat)})</p>
            ) : (
              <>
                <ol className="steps">
                  {ORDER_STEPS.map((s, i) => (
                    <li key={s} className={i < buoc ? 'done' : i === buoc ? 'done current' : ''}>{ORDER_STATUS[s]}</li>
                  ))}
                </ol>
                <p className="muted">Cập nhật lần cuối: {formatDate(o.ngayCapNhat || o.ngayMua)}</p>
              </>
            )}
          </div>

          <div className="section">
            <h3>Sản phẩm</h3>
            {o.chiTiet.map((d) => (
              <div key={d.maSanPham} className="line-item">
                <img className="thumb" src={d.anhChinh} alt="" />
                <div className="grow">
                  <Link to={`/products/${d.maSanPham}`}>{d.tenMay}</Link>
                  <p className="muted">{formatPrice(d.donGia)} × {d.soLuong}</p>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <b>{formatPrice(d.donGia * d.soLuong)}</b>
                  {o.trangThai === 'DA_GIAO' && <div><Link to={`/products/${d.maSanPham}#danh-gia`}>Đánh giá</Link></div>}
                </div>
              </div>
            ))}
          </div>

          {returnForm && (
            <form
              className="form section"
              onSubmit={(e) => {
                e.preventDefault();
                run(async () => { await customerService.requestReturn({ maDonHang: o.maDonHang, ...returnForm }); setReturnForm(null); }, 'Đã gửi yêu cầu đổi trả');
              }}
            >
              <h3>Yêu cầu đổi trả / hoàn tiền</h3>
              <label className="field">
                <span>Lý do đổi trả</span>
                <textarea rows="3" required value={returnForm.lyDo} onChange={(e) => setReturnForm({ ...returnForm, lyDo: e.target.value })} />
              </label>
              <FormField label="Link ảnh minh chứng" value={returnForm.hinhAnhMinhChung} onChange={(e) => setReturnForm({ ...returnForm, hinhAnhMinhChung: e.target.value })} />
              <div className="row end">
                <button type="button" className="btn" onClick={() => setReturnForm(null)}>Hủy</button>
                <button className="btn btn-primary">Gửi yêu cầu</button>
              </div>
            </form>
          )}
        </div>

        <aside className="side">
          <div className="section" style={{ margin: 0 }}>
            <h3>Địa chỉ giao hàng</h3>
            <p>{o.diaChiGiaoHang || 'Không có thông tin'}</p>
          </div>
          <div className="summary">
            <div className="row between"><span>Tạm tính</span><span>{formatPrice(o.tamTinh)}</span></div>
            <div className="row between"><span>Giảm giá {o.maKm && `(${o.maKm})`}</span><span>- {formatPrice(o.giamGia)}</span></div>
            <div className="row between total"><b>Tổng thanh toán</b><b className="price">{formatPrice(o.tongTien)}</b></div>
          </div>
          {o.trangThaiDoiTra && <p>Đổi trả: <StatusBadge status={o.trangThaiDoiTra} /> <Link to="/returns">Xem chi tiết</Link></p>}
          <button
            className="btn"
            onClick={() => run(async () => { replace(await orderService.reorder(o.maDonHang)); navigate('/cart'); }, 'Đã thêm các sản phẩm vào giỏ hàng')}
          >
            Mua lại
          </button>
          <Link className="btn" to={`/orders/${o.maDonHang}/invoice`}>Xuất hóa đơn (PDF)</Link>
          {o.trangThai === 'DA_GIAO' && !o.trangThaiDoiTra && (
            <button className="btn" onClick={() => setReturnForm({ lyDo: '', hinhAnhMinhChung: '' })}>Yêu cầu đổi trả / hoàn tiền</button>
          )}
          {o.trangThai === 'CHO_XAC_NHAN' && (
            <button className="btn btn-danger" onClick={() => window.confirm('Hủy đơn hàng này?') && run(() => orderService.cancel(o.maDonHang), 'Đã hủy đơn hàng')}>
              Hủy đơn
            </button>
          )}
        </aside>
      </div>
    </>
  );
}
