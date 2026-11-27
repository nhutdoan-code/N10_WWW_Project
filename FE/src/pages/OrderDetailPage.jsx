import { useEffect, useRef, useState } from 'react';
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
import { errorMessage, formatDate, formatDay, formatPrice } from '../utils/format';
import { getOrderActions } from '../utils/orderActions';
import { returnActive, warrantyActive, warrantyEnd } from '../utils/warrantyPolicy';
import { formatKhoangNgayGiao } from '../utils/shipping';

export default function OrderDetailPage() {
  const { ma } = useParams();
  const [searchParams] = useSearchParams();
  const { data: o, loading, error, reload } = useFetch(() => orderService.get(ma), [ma]);
  const {
    data: paymentStatus,
    error: paymentStatusError,
    reload: reloadPaymentStatus,
  } = useFetch(
    () => (o?.phuongThucTt === 'VI_DIEN_TU' ? orderService.paymentStatus(ma) : Promise.resolve(null)),
    [ma, o?.phuongThucTt]
  );
  const [cancelForm, setCancelForm] = useState(null);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [reviewing, setReviewing] = useState(null);
  const [confirmReceive, setConfirmReceive] = useState(false);
  useEffect(() => {
    if (o?.trangThai === 'CHO_KHACH_XAC_NHAN' && searchParams.get('confirmReceive') === '1') setConfirmReceive(true);
  }, [o?.trangThai, ma, searchParams]);
  const [submitting, setSubmitting] = useState(false);
  const [paying, setPaying] = useState(false);
  const [reorderPicker, setReorderPicker] = useState(false);
  const reloadPaymentStatusRef = useRef(reloadPaymentStatus);
  reloadPaymentStatusRef.current = reloadPaymentStatus;
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

  useEffect(() => {
    const finalPaymentStatuses = ['DA_THANH_TOAN', 'THAT_BAI', 'CAN_HOAN_TIEN', 'DA_HOAN_TIEN', 'DA_HUY'];
    if (o?.phuongThucTt !== 'VI_DIEN_TU'
      || !searchParams.has('resultCode')
      || finalPaymentStatuses.includes(paymentStatus?.status)) {
      return undefined;
    }

    let attempts = 0;
    const timer = window.setInterval(() => {
      attempts += 1;
      reloadPaymentStatusRef.current();
      if (attempts >= 30) window.clearInterval(timer);
    }, 2000);

    return () => window.clearInterval(timer);
  }, [ma, o?.phuongThucTt, paymentStatus?.status, searchParams]);

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
    if (cancelBusy) return;
    setCancelBusy(true);
    try {
      await orderService.cancel(o.maDonHang, cancelForm.lyDo, khac ? cancelForm.khac.trim() : null);
      toast('Hủy đơn hàng thành công');
      setCancelForm(null);
      reload();
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setCancelBusy(false); }
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
  const openWarranty = (existingRequests || []).some(r => r.maDonHang === o.maDonHang && r.loai === 'BAO_HANH' && ['CHO_TIEP_NHAN','DANG_KIEM_TRA','DANG_XAC_MINH','DANG_XU_LY','DA_DUYET'].includes(r.trangThai));
  const huy = o.trangThai === 'DA_HUY';
  const ngayGiaoDuKien = formatKhoangNgayGiao(o.ngayGiaoDuKienTu, o.ngayGiaoDuKienDen);
  const dangChoGiao = ['CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DANG_CHUAN_BI', 'DANG_GIAO'].includes(o.trangThai);
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
        <div className="as-order-status"><StatusBadge status={o.trangThai} />{openWarranty && <Link className="as-deadline-badge" to="/returns">Bảo hành: Đang xử lý</Link>}</div>
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
                {dangChoGiao && ngayGiaoDuKien && (
                  <p className="ship-eta">Dự kiến giao hàng: <b>{ngayGiaoDuKien}</b></p>
                )}
                <p className="muted">Cập nhật lần cuối: {formatDate(o.ngayCapNhat || o.ngayMua)}</p>
                {o.trangThai === 'CHO_KHACH_XAC_NHAN' && (
                    <p>Đơn hàng đã được giao{o.ngayGiao && ` lúc ${formatDate(o.ngayGiao)}`}. Vui lòng xác nhận nếu bạn đã nhận được hàng.</p>
                )}
                {o.ngayKhachXacNhan && <p className="muted">Hoàn thành lúc {formatDate(o.ngayKhachXacNhan)}</p>}
              </>
            )}
          </div>

          {o.phuongThucTt === 'VI_DIEN_TU' && o.trangThai !== 'DA_HUY' && (
            <div className="section">
              <h3>Thanh toán MoMo</h3>
              {paymentStatus?.status === 'DA_THANH_TOAN' ? (
                <p style={{ color: 'var(--ok)', fontWeight: 500, margin: 0 }}>✓ Đã thanh toán thành công qua ví MoMo</p>
              ) : paymentStatus?.status === 'CAN_HOAN_TIEN' ? (
                <p className="muted">MoMo đã nhận thanh toán; cửa hàng đang đối soát hoàn tiền.</p>
              ) : paymentStatus?.status === 'DA_HOAN_TIEN' ? (
                <p className="muted">Giao dịch MoMo đã được hoàn tiền.</p>
              ) : paymentStatus?.status === 'THAT_BAI' ? (
                <p className="muted">Thanh toán chưa thành công.</p>
              ) : (
                <p className="muted">Đang chờ MoMo xác nhận thanh toán.</p>
              )}
              {paymentStatusError && (
                <p className="error" role="alert">
                  Không tải được trạng thái thanh toán: {paymentStatusError}
                </p>
              )}
              {paymentStatus?.status !== 'DA_THANH_TOAN'
                && paymentStatus?.status !== 'CAN_HOAN_TIEN'
                && paymentStatus?.status !== 'DA_HOAN_TIEN'
                && (!paymentStatus?.transactionId || paymentStatus?.status === 'THAT_BAI') && (
                  <button type="button" className="btn btn-primary" onClick={payWithMomo} disabled={paying}>
                    {paying ? 'Đang chuyển đến MoMo...' : 'Thanh toán với MoMo'}
                  </button>
              )}
              {paymentStatus?.status === 'CHO_THANH_TOAN' && paymentStatus?.transactionId && (
                <button type="button" className="btn" onClick={reloadPaymentStatus}>
                  Cập nhật trạng thái
                </button>
              )}
            </div>
          )}

          {o.trangThai === 'DA_HUY' && paymentStatus?.status === 'CAN_HOAN_TIEN' && (
            <div className="section" style={{ borderLeft: '4px solid #f59e0b', background: 'var(--warn-soft)', padding: '1rem', borderRadius: 8 }}>
              <h3 style={{ color: 'var(--warn)', margin: '0 0 0.5rem 0' }}>Lưu ý về thanh toán MoMo</h3>
              <p style={{ margin: 0, color: 'var(--warn)' }}>
                Đơn hàng đã hủy nhưng MoMo ghi nhận thanh toán thành công. Cửa hàng đang tiếp nhận để xử lý hoàn tiền hoặc đối soát cho bạn.
              </p>
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
                      <button type="button" className={openWarranty ? "btn" : "btn btn-primary"} onClick={() => setReviewing(d)}>Đánh giá</button>
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
            {(Number(o.phiKhaiGia) > 0 || Number(o.phuPhiCod) > 0) && (
              <div className="ship-breakdown">
                <div className="row between"><span>· Phí giao hàng</span><span>{formatPrice(Number(o.phiVanChuyen) - Number(o.phiKhaiGia || 0) - Number(o.phuPhiCod || 0))}</span></div>
                {Number(o.phiKhaiGia) > 0 && <div className="row between"><span>· Phí khai giá (bảo hiểm)</span><span>{formatPrice(o.phiKhaiGia)}</span></div>}
                {Number(o.phuPhiCod) > 0 && <div className="row between"><span>· Phụ phí COD</span><span>{formatPrice(o.phuPhiCod)}</span></div>}
              </div>
            )}
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
          {(actions.afterDelivery || actions.complain || hasAfterSalesRequest) && <details className="as-support-menu" open={openWarranty || undefined}>
            <summary className="btn">Hỗ trợ sau mua {openWarranty && <span className="as-deadline-badge">Đang xử lý</span>}</summary>
            <div className="as-support-menu-items">
              {actions.afterDelivery && <>
                {returnActive(o) ? <Link className="btn" to={`/returns?orderId=${encodeURIComponent(o.maDonHang)}&loai=TRA_HANG_HOAN_TIEN`}>Yêu cầu đổi trả</Link> : <span className="btn as-disabled-action" role="link" aria-disabled="true" tabIndex={0} title="Đã quá hạn hoặc chưa đủ điều kiện đổi trả">Yêu cầu đổi trả</span>}
                {!returnActive(o) && <small className="muted">Đã quá hạn hoặc chưa đủ điều kiện đổi trả.</small>}
                {warrantyActive(o) ? <Link className="btn" to={`/returns?orderId=${encodeURIComponent(o.maDonHang)}&loai=BAO_HANH`}>Yêu cầu bảo hành</Link> : <span className="btn as-disabled-action" role="link" aria-disabled="true" tabIndex={0} title="Không còn đủ điều kiện bảo hành">Yêu cầu bảo hành</span>}
                {warrantyEnd(o) && <small className="muted">Hạn bảo hành: {formatDay(warrantyEnd(o))}</small>}
              </>}
              {actions.complain && <Link className="btn" to={`/returns?maDonHang=${encodeURIComponent(o.maDonHang)}&loai=KHIEU_NAI`}>Gửi khiếu nại</Link>}
              {hasAfterSalesRequest && <Link className="btn" to="/returns">Theo dõi yêu cầu</Link>}
            </div>
          </details>}
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
              <p>Bạn xác nhận đã nhận đầy đủ sản phẩm của đơn hàng này? Chỉ xác nhận khi bạn đã thực sự nhận đủ hàng. Sau khi xác nhận, đơn hàng sẽ hoàn tất và bắt đầu tính thời hạn đổi trả, bảo hành.</p>
              <div className="row end">
                <button className="btn" disabled={submitting} onClick={() => setConfirmReceive(false)}>Hủy</button>
                <button className="btn btn-primary" disabled={submitting} onClick={submitReceived}>{submitting ? 'Đang xử lý...' : 'Xác nhận đã nhận'}</button>
              </div>
            </Modal>
        )}
        {cancelForm && (
          <Modal title="Hủy đơn hàng" onClose={() => { if (!cancelBusy) setCancelForm(null); }}>
            <form className="cancel-order-form" onSubmit={submitCancel}>
              <div className="cancel-order-summary"><small>Đơn hàng cần hủy</small><strong>{o.maDonHang}</strong><p>Bạn có chắc chắn muốn hủy đơn hàng này? Thao tác không thể hoàn tác.</p></div>
              <label className="field">
                <span>Lý do hủy đơn</span>
                <select value={cancelForm.lyDo} onChange={(e) => setCancelForm({ ...cancelForm, lyDo: e.target.value })}>
                  {CANCEL_REASONS.map((r) => <option key={r}>{r}</option>)}
                </select>
              </label>
              {cancelForm.lyDo === 'Lý do khác' && (
                <textarea required maxLength={500} rows="3" placeholder="Nhập lý do của bạn" value={cancelForm.khac} onChange={(e) => setCancelForm({ ...cancelForm, khac: e.target.value })} />
              )}
              <div className="cancel-order-actions">
                <button type="button" className="btn" disabled={cancelBusy} onClick={() => setCancelForm(null)}>Giữ đơn hàng</button>
                <button type="submit" className="btn btn-danger" disabled={cancelBusy}>{cancelBusy ? "Đang hủy…" : "Xác nhận hủy đơn"}</button>
              </div>
            </form>
          </Modal>
        )}
      </div>
    </>
  );
}
