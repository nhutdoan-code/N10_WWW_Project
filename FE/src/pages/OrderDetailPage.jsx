import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Modal from '../components/Modal';
import ReviewForm from '../components/ReviewForm';
import OrderSteps from '../components/OrderSteps';
import ProductImage from '../components/ProductImage';
import StatusBadge from '../components/StatusBadge';
import { CANCEL_REASONS, ORDER_STATUS, PAYMENT_METHODS } from '../config/constants';
import useCart from '../hooks/useCart';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { orderService } from '../service/orderService';
import { errorMessage, formatDate, formatPrice } from '../utils/format';
import { getOrderActions } from '../utils/orderActions';

export default function OrderDetailPage() {
  const { ma } = useParams();
  const [searchParams] = useSearchParams();
  const { data: o, loading, error, reload } = useFetch(() => orderService.get(ma), [ma]);
  const { data: paymentStatus } = useFetch(
    () => (o?.phuongThucTt === 'VI_DIEN_TU' ? orderService.paymentStatus(ma) : Promise.resolve(null)),
    [ma, o?.phuongThucTt]
  );
  const [cancelForm, setCancelForm] = useState(null);
  const [reviewing, setReviewing] = useState(null);
  const [confirmReceive, setConfirmReceive] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [paying, setPaying] = useState(false);
  const [reorderPicker, setReorderPicker] = useState(false);
  const { data: reviewStates, loading: reviewsLoading, error: reviewsError, reload: reloadReviews } = useFetch(
    () => (o?.trangThai === 'DA_GIAO' ? customerService.orderReviews(ma) : Promise.resolve([])),
    [ma, o?.trangThai]
  );
  // Yêu cầu hậu mãi có thể là đổi trả, bảo hành hoặc khiếu nại.
  // Chỉ hiện nút theo dõi trên đơn này khi thực sự đã có yêu cầu.
  const { data: existingRequests } = useFetch(
    () => (o?.maDonHang
      ? Promise.all([customerService.returns(), customerService.afterSales()])
          .then(([returns, afterSales]) => [...(returns || []), ...(afterSales || [])])
      : Promise.resolve([])),
    [ma, o?.maDonHang]
  );
  const { replace } = useCart();
  const toast = useToast();
  const navigate = useNavigate();

  const payWithMomo = async () => {
    setPaying(true);
    try {
      const payment = await orderService.createMomoPayment(ma);
      window.location.assign(payment.payUrl);
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setPaying(false);
    }
  };

  useEffect(() => {
    const resultCode = searchParams.get('resultCode');
    if (resultCode === null) return;
    if (resultCode === '0') {
      toast('MoMo đã tiếp nhận thanh toán. Trạng thái sẽ được cập nhật sau ít giây.');
      reload();
    } else {
      toast(`Thanh toán MoMo chưa thành công: ${searchParams.get('message') || 'Giao dịch bị hủy'}`, 'error');
    }
  }, [searchParams]);

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
  const submitReceived = async () => {
    setSubmitting(true);
    try {
      await orderService.confirmReceived(o.maDonHang);
      toast('Xác nhận nhận hàng thành công! Đơn hàng của bạn đã được hoàn tất.');
      setConfirmReceive(false);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setSubmitting(false);
    }
  };
  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;

  const actions = getOrderActions(o.trangThai);
  const hasAfterSalesRequest = Boolean(o.trangThaiDoiTra) ||
    (existingRequests || []).some((req) => req.maDonHang === o.maDonHang);
  const huy = o.trangThai === 'DA_HUY';
  const muaLaiMotSanPham = (dong) => {
    if (!dong?.maPhienBan) {
      toast('Không xác định được phiên bản sản phẩm để mua lại.', 'error');
      return;
    }
    setReorderPicker(false);
    navigate(`/checkout?mua=${encodeURIComponent(dong.maPhienBan)}&sl=1`);
  };

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
                {o.trangThai === 'CHO_KHACH_XAC_NHAN' && (
                    <p>Đơn hàng đã được giao{o.ngayGiao && ` lúc ${formatDate(o.ngayGiao)}`}. Vui lòng xác nhận nếu bạn đã nhận được hàng.</p>
                )}
                {o.ngayKhachXacNhan && <p className="muted">Hoàn thành lúc {formatDate(o.ngayKhachXacNhan)}</p>}
              </>
            )}
          </div>

          {o.phuongThucTt === 'VI_DIEN_TU' && o.trangThai !== 'DA_HUY' && paymentStatus?.status !== 'DA_THANH_TOAN' && paymentStatus?.status !== 'CAN_HOAN_TIEN' && (
            <div className="section">
              <h3>Thanh toán MoMo</h3>
              <p className="muted">Trạng thái: {paymentStatus?.status === 'THAT_BAI' ? 'Chưa thành công' : 'Chờ thanh toán'}</p>
              <p className="muted">Thanh toán trực tuyến an toàn qua ví MoMo.</p>
              <button type="button" className="btn btn-primary" onClick={payWithMomo} disabled={paying}>
                {paying ? 'Đang chuyển đến MoMo...' : 'Thanh toán lại với MoMo'}
              </button>
            </div>
          )}

          {paymentStatus?.status === 'CAN_HOAN_TIEN' && (
            <div className="section" style={{ borderLeft: '4px solid #f59e0b', background: '#fffbeb', padding: '1rem', borderRadius: 8 }}>
              <h3 style={{ color: '#b45309', margin: '0 0 0.5rem 0' }}>Lưu ý về thanh toán MoMo</h3>
              <p style={{ margin: 0, color: '#92400e' }}>
                Đơn hàng đã hủy nhưng hệ thống ghi nhận bạn đã thanh toán MoMo thành công. Cửa hàng đang tiếp nhận để xử lý hoàn tiền hoặc đối soát cho bạn.
              </p>
            </div>
          )}

          {o.phuongThucTt === 'VI_DIEN_TU' && paymentStatus?.status === 'DA_THANH_TOAN' && (
            <div className="section">
              <h3>Thanh toán MoMo</h3>
              <p style={{ color: '#16a34a', fontWeight: 500, margin: 0 }}>✓ Đã thanh toán thành công qua ví MoMo</p>
            </div>
          )}

          <div className="section">
            <h3>Sản phẩm</h3>
            {o.chiTiet.map((d) => (
              <div key={d.maPhienBan || d.maSanPham} className="line-item">
                <ProductImage className="thumb" src={d.anhChinh} alt="" />
                <div className="grow">
                  <Link to={`/products/${d.maSanPham}`}>{d.tenMay}</Link>
                  <p className="muted">{formatPrice(d.donGia)} × {d.soLuong}</p>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <b>{formatPrice(d.donGia * d.soLuong)}</b>
                </div>
              </div>
            ))}
          </div>

          {o.trangThai === 'DA_GIAO' && (
            <section className="section order-reviews" id="danh-gia-don-hang">
              <h3>Đánh giá sản phẩm đã mua</h3>
              <p className="muted">Chia sẻ trải nghiệm của bạn với từng sản phẩm trong đơn hàng.</p>
              {reviewsLoading && <p className="muted" role="status">Đang kiểm tra trạng thái đánh giá...</p>}
              {reviewsError && (
                <div className="error" role="alert">
                  <p>Không tải được trạng thái đánh giá: {reviewsError}</p>
                  <button type="button" className="btn" onClick={reloadReviews}>Thử tải lại</button>
                </div>
              )}
              {!reviewsLoading && !reviewsError && o.chiTiet.map((d) => {
                const r = reviewStates?.find((x) => x.maSanPham === d.maSanPham);
                return (
                  <div key={d.maSanPham} className="line-item">
                    <ProductImage className="thumb" src={d.anhChinh} alt="" />
                    <div className="grow">
                      <Link to={`/products/${d.maSanPham}`}>{d.tenMay}</Link>
                      {r?.danhGia ? (
                        <p className="muted"><span className="stars">{'★'.repeat(r.danhGia.diemSao)}</span> Đã đánh giá</p>
                      ) : r?.duocDanhGia ? (
                        <p className="muted">Bạn có thể đánh giá sản phẩm này.</p>
                      ) : (
                        <p className="muted">{r?.lyDo || 'Không tìm thấy trạng thái đánh giá cho sản phẩm này.'}</p>
                      )}
                    </div>
                    {r?.duocDanhGia && !r.danhGia && (
                      <button type="button" className="btn btn-primary" onClick={() => setReviewing(d)}>Đánh giá</button>
                    )}
                  </div>
                );
              })}
            </section>
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
          {o.trangThai === 'CHO_KHACH_XAC_NHAN' && (
              <button className="btn btn-primary" onClick={() => setConfirmReceive(true)}>Đã nhận được hàng</button>
          )}
          {actions.reorder && (
            <>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => o.chiTiet.length === 1
                  ? muaLaiMotSanPham(o.chiTiet[0])
                  : setReorderPicker(true)}
              >
                Mua lại sản phẩm
              </button>
              <button
                type="button"
                className="btn"
                onClick={() => {
                  if (!window.confirm('Thêm tất cả sản phẩm của đơn cũ vào giỏ hiện tại? Các sản phẩm đang có trong giỏ vẫn được giữ lại.')) return;
                  run(async () => { replace(await orderService.reorder(o.maDonHang)); navigate('/cart'); }, 'Đã thêm sản phẩm của đơn cũ vào giỏ');
                }}
              >
                Thêm cả đơn vào giỏ
              </button>
            </>
          )}
          <Link className="btn" to={`/orders/${o.maDonHang}/invoice`}>
            {actions.afterDelivery ? 'Xuất hóa đơn (PDF)' : 'Tải thông tin đơn hàng (PDF)'}
          </Link>
          {actions.afterDelivery && (
            <>
              <Link className="btn" to={`/returns?maDonHang=${encodeURIComponent(o.maDonHang)}&loai=TRA_HANG_HOAN_TIEN`}>Đổi / trả hàng</Link>
              <Link className="btn" to={`/returns?maDonHang=${encodeURIComponent(o.maDonHang)}&loai=BAO_HANH`}>Yêu cầu bảo hành</Link>
            </>
          )}
          {actions.complain && (
            <Link className="btn" to={`/returns?maDonHang=${encodeURIComponent(o.maDonHang)}&loai=KHIEU_NAI`}>Gửi khiếu nại</Link>
          )}
          {hasAfterSalesRequest && (
            <Link className="btn" to="/returns">Theo dõi yêu cầu hậu mãi</Link>
          )}
          {actions.cancel && (
            <button className="btn btn-danger" onClick={() => setCancelForm({ lyDo: CANCEL_REASONS[0], khac: '' })}>Hủy đơn hàng</button>
          )}
        </aside>
        {actions.reorder && reorderPicker && (
          <Modal title="Chọn sản phẩm muốn mua lại" onClose={() => setReorderPicker(false)}>
            <p className="muted">Mỗi lần chỉ mua một sản phẩm, số lượng mặc định là 1. Giỏ hàng hiện tại không bị thay đổi.</p>
            {o.chiTiet.map((dong) => (
              <div key={dong.maPhienBan} className="line-item">
                <ProductImage className="thumb" src={dong.anhChinh} alt="" />
                <div className="grow">
                  <b>{dong.tenMay}</b>
                  <p className="muted">Giá mua cũ: {formatPrice(dong.donGia)}</p>
                </div>
                <button type="button" className="btn btn-primary" onClick={() => muaLaiMotSanPham(dong)}>
                  Mua 1 sản phẩm
                </button>
              </div>
            ))}
          </Modal>
        )}
        {reviewing && (
          <Modal title="Đánh giá sản phẩm" onClose={() => setReviewing(null)}>
            <ReviewForm maDonHang={o.maDonHang} sanPham={reviewing} onCancel={() => setReviewing(null)}
              onDone={() => { setReviewing(null); reloadReviews(); }} />
          </Modal>
        )}
        {confirmReceive && (
            <Modal title="Xác nhận đã nhận hàng" onClose={() => !submitting && setConfirmReceive(false)}>
              <p>Bạn xác nhận đã nhận đầy đủ sản phẩm của đơn hàng này? Sau khi xác nhận, đơn hàng sẽ được hoàn tất.</p>
              <div className="row end">
                <button className="btn" disabled={submitting} onClick={() => setConfirmReceive(false)}>Hủy</button>
                <button className="btn btn-primary" disabled={submitting} onClick={submitReceived}>{submitting ? 'Đang xử lý...' : 'Xác nhận đã nhận'}</button>
              </div>
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
