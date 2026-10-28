import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import FormField from '../components/FormField';
import Modal from '../components/Modal';
import ReviewForm from '../components/ReviewForm';
import OrderSteps from '../components/OrderSteps';
import StatusBadge from '../components/StatusBadge';
import { CANCEL_REASONS, ORDER_STATUS, PAYMENT_METHODS } from '../config/constants';
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
  const [cancelForm, setCancelForm] = useState(null);
  const [reviewing, setReviewing] = useState(null);
  const { data: reviewStates, reload: reloadReviews } = useFetch(
    () => (o?.trangThai === 'DA_GIAO' ? customerService.orderReviews(ma) : Promise.resolve([])),
    [ma, o?.trangThai]
  );
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

  const submitCancel = async (e) => {
    e.preventDefault();
    const khac = cancelForm.lyDo === 'Lý do khác';
    if (khac && !cancelForm.khac.trim()) return toast('Vui lòng nhập lý do hủy', 'error');
    await run(() => orderService.cancel(o.maDonHang, cancelForm.lyDo, khac ? cancelForm.khac.trim() : null), 'Hủy đơn hàng thành công');
    setCancelForm(null);
  };

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;

  const huy = o.trangThai === 'DA_HUY';

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
              <p className="error">Đơn hàng đã bị hủy (cập nhật {formatDate(o.ngayCapNhat)}){o.lyDoHuy && ` · Lý do: ${o.lyDoHuy}`}</p>
            ) : (
              <>
                <OrderSteps trangThai={o.trangThai} />
                <p className="muted">Cập nhật lần cuối: {formatDate(o.ngayCapNhat || o.ngayMua)}</p>
              </>
            )}
          </div>

          <div className="section">
            <h3>Sản phẩm</h3>
            {o.chiTiet.map((d) => (
              <div key={d.maPhienBan || d.maSanPham} className="line-item">
                <img className="thumb" src={d.anhChinh} alt="" />
                <div className="grow">
                  <Link to={`/products/${d.maSanPham}`}>{d.tenMay}</Link>
                  <p className="muted">{formatPrice(d.donGia)} × {d.soLuong}</p>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <b>{formatPrice(d.donGia * d.soLuong)}</b>
                  {o.trangThai === 'DA_GIAO' && (() => {
                    const r = reviewStates?.find((x) => x.maSanPham === d.maSanPham);
                    if (!r) return null;
                    if (r.danhGia) return <div className="muted"><span className="stars">{'★'.repeat(r.danhGia.diemSao)}</span> Đã đánh giá</div>;
                    if (r.duocDanhGia) return <button className="btn" onClick={() => setReviewing(d)}>Đánh giá</button>;
                    return <small className="muted">{r.lyDo}</small>;
                  })()}
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
            <div className="row between"><span>Phí vận chuyển</span><span>{Number(o.phiVanChuyen) > 0 ? formatPrice(o.phiVanChuyen) : 'Miễn phí'}</span></div>
            {Number(o.giamPhiVanChuyen) > 0 && <div className="row between"><span>Giảm phí vận chuyển ({o.maKmVanChuyen})</span><span>- {formatPrice(o.giamPhiVanChuyen)}</span></div>}
            {Number(o.giamGia) > 0 && <div className="row between"><span>Giảm giá {o.maKm && `(${o.maKm})`}</span><span>- {formatPrice(o.giamGia)}</span></div>}
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
          {['CHO_XAC_NHAN', 'DANG_XU_LY'].includes(o.trangThai) && (
            <button className="btn btn-danger" onClick={() => setCancelForm({ lyDo: CANCEL_REASONS[0], khac: '' })}>Hủy đơn hàng</button>
          )}
        </aside>
        {reviewing && (
          <Modal title="Đánh giá sản phẩm" onClose={() => setReviewing(null)}>
            <ReviewForm maDonHang={o.maDonHang} sanPham={reviewing} onCancel={() => setReviewing(null)}
              onDone={() => { setReviewing(null); reloadReviews(); }} />
          </Modal>
        )}
        {cancelForm && (
          <Modal title="Hủy đơn hàng" onClose={() => setCancelForm(null)}>
            <form className="stack" onSubmit={submitCancel}>
              <p>Bạn chắc chắn muốn hủy đơn <b>{o.maDonHang}</b>? Số lượng sản phẩm sẽ được hoàn lại kho.</p>
              <label className="field">
                <span>Lý do hủy đơn</span>
                <select value={cancelForm.lyDo} onChange={(e) => setCancelForm({ ...cancelForm, lyDo: e.target.value })}>
                  {CANCEL_REASONS.map((r) => <option key={r}>{r}</option>)}
                </select>
              </label>
              {cancelForm.lyDo === 'Lý do khác' && (
                <textarea rows="3" placeholder="Nhập lý do của bạn" value={cancelForm.khac} onChange={(e) => setCancelForm({ ...cancelForm, khac: e.target.value })} />
              )}
              <div className="row end">
                <button type="button" className="btn" onClick={() => setCancelForm(null)}>Đóng</button>
                <button className="btn btn-danger">Xác nhận hủy</button>
              </div>
            </form>
          </Modal>
        )}
      </div>
    </>
  );
}
