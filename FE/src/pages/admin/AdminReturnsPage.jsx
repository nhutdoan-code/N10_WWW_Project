import { useEffect, useMemo, useState } from 'react';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';
import {
  RETURN_ACTIONS, GROUPS, STATUS_LABELS, TYPE_LABELS,
  countPending, filterRequests, formatAdminDate, normalizeRequests,
} from '../../utils/afterSalesAdmin';
import { afterSalesActions, afterSalesStatusLabel } from '../../utils/afterSalesWorkflow';

const initial = { query: '', status: '', type: '', from: '', to: '', sort: 'priority' };
const actionsFor = (request) => request?.source === 'after'
  ? afterSalesActions(request)
  : (RETURN_ACTIONS[request?.trangThai] || []);
const statusLabel = (request, status = request?.trangThai) => request?.source === 'after'
  ? afterSalesStatusLabel(status, request.loai)
  : (STATUS_LABELS[status] || status);

export default function AdminReturnsPage() {
  const { data, loading, error, reload } = useFetch(async () => {
    const [returns, afterSales] = await Promise.all([adminService.returns(), adminService.afterSales()]);
    return { returns: returns || [], afterSales: afterSales || [] };
  });
  const [filters, setFilters] = useState(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selected, setSelected] = useState(null);
  const [note, setNote] = useState('');
  const [resultNote, setResultNote] = useState('');
  const [internalNote, setInternalNote] = useState('');
  const [loadingInternal, setLoadingInternal] = useState(false);
  const [savingInternal, setSavingInternal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refundMethod, setRefundMethod] = useState('MOMO');
  const [refundReference, setRefundReference] = useState('');
  const toast = useToast();
  const rows = useMemo(() => normalizeRequests(data), [data]);
  const results = useMemo(() => filterRequests(rows, filters), [rows, filters]);
  const pageCount = Math.max(1, Math.ceil(results.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visible = results.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const detail = selected ? rows.find(r => r.source === selected.source && r.maYeuCau === selected.maYeuCau) : null;
  const detailActions = actionsFor(detail);
  useEffect(() => { setPage(1); }, [filters, pageSize]);
  useEffect(() => {
    setNote('');
    setResultNote('');
    setInternalNote('');
    setRefundMethod('MOMO');
    setRefundReference('');
    if (!selected) return;
    let alive = true;
    setLoadingInternal(true);
    adminService.getInternalAfterSalesNote(selected.source, selected.maYeuCau)
      .then(payload => { if (alive) setInternalNote(payload?.ghiChu || ''); })
      .catch(() => { if (alive) toast('Không tải được ghi chú nội bộ', 'error'); })
      .finally(() => { if (alive) setLoadingInternal(false); });
    return () => { alive = false; };
  }, [selected]);
  const saveInternal = async () => {
    if (!detail || savingInternal) return;
    setSavingInternal(true);
    try {
      await adminService.saveInternalAfterSalesNote(detail.source, detail.maYeuCau, internalNote);
      toast('Đã lưu ghi chú nội bộ');
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setSavingInternal(false); }
  };
  const setFilter = (key, value) => setFilters(s => ({ ...s, [key]: value }));
  const statusSort = () => setFilter('sort', filters.sort === 'statusAsc' ? 'statusDesc' : 'statusAsc');
  const dateSort = () => setFilter('sort', filters.sort === 'dateDesc' ? 'dateAsc' : 'dateDesc');
  const doneToday = rows.filter(r => GROUPS.completed.includes(r.trangThai) &&
    String(r.ngayCapNhat || '').slice(0,10) === new Date().getFullYear() + '-' + String(new Date().getMonth()+1).padStart(2,'0') + '-' + String(new Date().getDate()).padStart(2,'0')).length;
  const applyAction = async (r, next) => {
    if (busy) return;
    if (r.source === 'after' && !note.trim()) { toast('Nhập nội dung cập nhật trước khi chuyển bước.', 'error'); return; }
    if (r.source === 'return' && next === 'TU_CHOI' && !note.trim()) { toast('Nhập lý do từ chối trước khi xác nhận.', 'error'); return; }
    if (r.source === 'after' && next === 'HOAN_TAT' && !resultNote.trim()) { toast('Nhập kết quả xử lý trước khi hoàn tất.', 'error'); return; }
    if (next === 'HOAN_TAT' && !window.confirm('Xác nhận hoàn tất yêu cầu sau khi đã xử lý thực tế?')) return;
    setBusy(true);
    try {
      if (r.source === 'return') await adminService.updateReturn(r.maYeuCau, next, note.trim());
      else await adminService.updateAfterSale(r.maYeuCau, {
        trangThai: next,
        ghiChuXuLy: note.trim(),
        ketQuaXuLy: resultNote.trim() || null,
      });
      toast('Đã cập nhật trạng thái yêu cầu');
      setNote('');
      setResultNote('');
      reload();
      window.dispatchEvent(new Event('phonenest:after-sales-updated'));
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setBusy(false); }
  };

  const restock = async () => {
    if (!detail || busy) return;
    if (!window.confirm('Xác nhận đã nhận và kiểm tra sản phẩm trả về? Hệ thống sẽ cộng số lượng vào kho và ghi lịch sử biến động.')) return;
    setBusy(true);
    try {
      await adminService.restockReturn(detail.maYeuCau);
      toast('Đã xác nhận nhận hàng và nhập lại kho');
      reload();
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setBusy(false); }
  };

  const recordRefund = async () => {
    if (!detail || busy || !refundReference.trim()) return;
    if (!window.confirm(`Sau khi bạn đã chuyển ${Number(detail.soTienHoanDuKien || 0).toLocaleString('vi-VN')} ₫ ở ${refundMethod}, xác nhận ghi lại mã đối soát này?`)) return;
    setBusy(true);
    try {
      await adminService.recordReturnRefund(detail.maYeuCau, { hinhThuc: refundMethod, maGiaoDich: refundReference.trim() });
      toast('Đã ghi nhận thông tin hoàn tiền');
      setRefundReference('');
      reload();
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setBusy(false); }
  };

  return <div className="after-sales-admin">
    <h2>Quản lý hậu mãi</h2>
    <p className="muted">Quản lý yêu cầu đổi/trả, bảo hành và khiếu nại. Hoàn tất không đồng nghĩa đã chuyển tiền hoàn thực tế.</p>
    {loading && <p className="muted">Đang tải dữ liệu...</p>}
    {error && <p className="error">{error} <button className="btn" onClick={reload}>Tải lại</button></p>}
    {!loading && !error && data && <>
      <div className="as-stats">
        <div><span>Chờ xử lý</span><strong>{countPending(rows)}</strong></div>
        <div><span>Đang xử lý</span><strong>{rows.filter(r => GROUPS.processing.includes(r.trangThai)).length}</strong></div>
        <div><span>Hoàn tất hôm nay</span><strong>{doneToday}</strong></div>
        <div><span>Tổng yêu cầu</span><strong>{rows.length}</strong></div>
      </div>
      <div className="as-filters">
        <label className="as-search">Tìm mã yêu cầu, mã đơn, tên khách, SĐT
          <input aria-label="Tìm kiếm hậu mãi" placeholder="Ví dụ: YC001, HM002, DH003, Nguyễn Văn A..." value={filters.query} onChange={e => setFilter('query',e.target.value)} />
        </label>
        <label>Trạng thái<select value={filters.status} onChange={e=>setFilter('status',e.target.value)}>
          <option value="">Tất cả</option><option value="waiting">Chờ xử lý</option><option value="processing">Đang xử lý</option><option value="completed">Hoàn tất</option><option value="rejected">Từ chối</option>
        </select></label>
        <label>Loại yêu cầu<select value={filters.type} onChange={e=>setFilter('type',e.target.value)}>
          <option value="">Tất cả</option>{Object.entries(TYPE_LABELS).map(([key,label])=><option key={key} value={key}>{label}</option>)}
        </select></label>
        <label>Từ ngày<input aria-label="Từ ngày" type="date" value={filters.from} max={filters.to || undefined} onChange={e=>setFilter('from',e.target.value)} /></label>
        <label>Đến ngày<input aria-label="Đến ngày" type="date" value={filters.to} min={filters.from || undefined} onChange={e=>setFilter('to',e.target.value)} /></label>
        <label>Sắp xếp<select value={filters.sort} onChange={e=>setFilter('sort',e.target.value)}>
          <option value="priority">Ưu tiên chưa xử lý · mới nhất</option><option value="dateDesc">Ngày mới nhất</option><option value="dateAsc">Ngày cũ nhất</option><option value="statusAsc">Chờ xử lý trước</option><option value="statusDesc">Hoàn tất trước</option>
        </select></label>
        <button className="btn" onClick={() => setFilters(initial)}>Xóa bộ lọc</button>
      </div>
      <div className="as-list-top"><h3>Danh sách yêu cầu ({results.length})</h3><label>Hiển thị <select value={pageSize} onChange={e=>setPageSize(Number(e.target.value))}><option value={10}>10</option><option value={20}>20</option></select> dòng / trang</label></div>
      <div className="after-sales-table-wrap"><table className="table as-table">
        <thead><tr><th>Mã yêu cầu</th><th>Đơn hàng</th><th>Khách hàng</th><th>Loại / sản phẩm</th><th><button className="as-sort" onClick={dateSort}>Ngày ↕</button></th><th><button className="as-sort" onClick={statusSort}>Trạng thái ↕</button></th><th>Xử lý</th></tr></thead>
        <tbody>{visible.length===0 && <tr><td colSpan="7" className="as-empty">Không có yêu cầu phù hợp bộ lọc.</td></tr>}
          {visible.map(r => <tr key={`${r.source}-${r.maYeuCau}`} className="as-clickable" onClick={()=>setSelected({source:r.source,maYeuCau:r.maYeuCau})}>
            <td><strong>{r.maYeuCau}</strong></td><td>{r.maDonHang}</td><td>{r.tenKhach}{r.soDienThoai && <div className="muted">{r.soDienThoai}</div>}</td>
            <td>{TYPE_LABELS[r.loai] || r.loai}<div className="muted">{r.tenSanPham || 'Toàn đơn'}{r.soLuong ? ` · SL ${r.soLuong}` : ''}</div></td>
            <td>{formatAdminDate(r.ngayTao)}</td><td><span className={`as-status as-${r.trangThai}`}>{statusLabel(r)}</span></td>
            <td><button className="btn" onClick={e=>{e.stopPropagation();setSelected({source:r.source,maYeuCau:r.maYeuCau});}}>{actionsFor(r).length ? 'Xử lý' : 'Xem chi tiết'}</button></td>
          </tr>)}</tbody></table></div>
      <div className="as-pagination"><span>Trang {currentPage}/{pageCount} · {results.length} yêu cầu</span><button className="btn" disabled={currentPage===1} onClick={()=>setPage(1)}>Đầu</button><button className="btn" disabled={currentPage===1} onClick={()=>setPage(p=>p-1)}>Trước</button><button className="btn" disabled={currentPage===pageCount} onClick={()=>setPage(p=>p+1)}>Sau</button><button className="btn" disabled={currentPage===pageCount} onClick={()=>setPage(pageCount)}>Cuối</button></div>
    </>}
    {detail && <div className="as-overlay" onMouseDown={e=>{if(e.target===e.currentTarget)setSelected(null)}}>
      <section className="as-modal" role="dialog" aria-modal="true" aria-label={`Chi tiết yêu cầu ${detail.maYeuCau}`}>
        <div className="as-modal-header"><h3>Yêu cầu {detail.maYeuCau}</h3><button className="btn" onClick={()=>setSelected(null)} aria-label="Đóng chi tiết">✕</button></div>
        <div className="as-detail-grid"><div><b>Đơn hàng</b><p>{detail.maDonHang}</p></div><div><b>Khách hàng</b><p>{detail.tenKhach}</p></div>
          <div><b>Số điện thoại</b><p>{detail.soDienThoai || 'Chưa có'}</p></div><div><b>Ngày yêu cầu</b><p>{formatAdminDate(detail.ngayTao)}</p></div>
          <div><b>Loại yêu cầu</b><p>{TYPE_LABELS[detail.loai] || detail.loai}</p></div><div><b>Trạng thái</b><p><span className={`as-status as-${detail.trangThai}`}>{statusLabel(detail)}</span></p></div></div>
        <p><b>Sản phẩm:</b> {detail.tenSanPham || 'Toàn đơn'} {detail.soLuong ? `· Số lượng ${detail.soLuong}` : ''}</p>
        {detail.chiTiet?.length > 0 && <div className="as-request-items"><b>Các dòng hàng</b>{detail.chiTiet.map((item) => <p key={item.maPhienBan}>{item.tenSanPham} · SL {item.soLuong} · {item.daNhapKho ? `Đã nhập kho ${formatAdminDate(item.ngayNhapKho)}` : 'Chưa nhập kho'}</p>)}</div>}
        {detail.soTienHoanDuKien != null && <p><b>Số tiền hoàn dự kiến:</b> {Number(detail.soTienHoanDuKien).toLocaleString('vi-VN')} ₫</p>}
        <p><b>Lý do / mô tả:</b> {detail.lyDo}</p>
        {detail.hinhAnhMinhChung && <div><b>Ảnh minh chứng</b><p><a href={detail.hinhAnhMinhChung} target="_blank" rel="noreferrer"><img className="as-proof" src={detail.hinhAnhMinhChung} alt="Bằng chứng khách gửi" /></a></p></div>}
        <p><b>Phản hồi đã lưu:</b> {detail.ghiChuXuLy || 'Chưa có'}</p>
        {detail.source === 'after' && detail.ketQuaXuLy && <p><b>Kết quả xử lý:</b> {detail.ketQuaXuLy}</p>}
        <div className="as-review"><label>Ghi chú nội bộ (chỉ Admin thấy)
          <textarea rows="3" maxLength={1000} placeholder="Thông tin trao đổi nội bộ, không gửi cho khách hàng" value={internalNote} disabled={loadingInternal} onChange={e=>setInternalNote(e.target.value)} />
        </label><button className="btn" disabled={savingInternal || loadingInternal} onClick={saveInternal}>{savingInternal ? 'Đang lưu...' : 'Lưu ghi chú nội bộ'}</button></div>
        {detail.lichSu?.length > 0 && <div><b>Lịch sử xử lý</b><ol className="as-history">{detail.lichSu.map((h,i)=><li key={i}>{formatAdminDate(h.thoiGian)} · {statusLabel(detail, h.trangThai)} {h.ghiChu ? `· ${h.ghiChu}` : ''}</li>)}</ol></div>}
        {detail.source === 'return' && ['DA_DUYET', 'DANG_XU_LY'].includes(detail.trangThai) && !detail.daNhapKho && <div className="as-review"><b>Nhận hàng và nhập kho</b><p className="muted">Chỉ xác nhận sau khi cửa hàng đã nhận và kiểm tra đủ sản phẩm. Số lượng tồn và lịch sử kho được cập nhật cùng giao dịch.</p><button className="btn btn-primary" disabled={busy} onClick={restock}>{busy ? 'Đang lưu...' : 'Xác nhận đã nhận hàng và nhập kho'}</button></div>}
        {detail.source === 'return' && detail.loai === 'TRA_HANG_HOAN_TIEN' && detail.trangThai === 'DANG_XU_LY' && <div className="as-review"><b>Đối soát hoàn tiền</b>{detail.ngayHoanTien ? <p>Đã ghi nhận {Number(detail.soTienHoanDuKien || 0).toLocaleString('vi-VN')} ₫ qua {detail.hinhThucHoanTien} · Mã: <code>{detail.maGiaoDichHoanTien}</code> · {formatAdminDate(detail.ngayHoanTien)}</p> : <><p className="muted">Thực hiện chuyển tiền trong MoMo/ngân hàng trước, sau đó nhập mã giao dịch để lưu đối soát. Hệ thống không tự gọi cổng thanh toán.</p><label>Hình thức<select value={refundMethod} onChange={e=>setRefundMethod(e.target.value)}><option value="MOMO">MoMo</option><option value="CHUYEN_KHOAN">Chuyển khoản</option><option value="TIEN_MAT">Tiền mặt</option><option value="KHAC">Khác</option></select></label><label>Mã giao dịch / đối soát<input maxLength={150} value={refundReference} onChange={e=>setRefundReference(e.target.value)} placeholder="Mã giao dịch từ MoMo/ngân hàng" /></label><button className="btn btn-primary" disabled={busy || !detail.daNhapKho || !refundReference.trim()} onClick={recordRefund}>{busy ? 'Đang lưu...' : 'Ghi nhận đã hoàn tiền'}</button>{!detail.daNhapKho && <p className="muted">Cần xác nhận nhận hàng và nhập kho trước.</p>}</>}</div>}
        {detailActions.length > 0 && <div className="as-review">
          <label>{detail.source === 'after' ? 'Nội dung cập nhật bước xử lý (khách hàng nhìn thấy, bắt buộc)' : 'Ghi chú xử lý / lý do từ chối (khách hàng có thể nhìn thấy)'}<textarea rows="3" maxLength={500} placeholder="Nhập nội dung cập nhật..." value={note} onChange={e=>setNote(e.target.value)}/></label>
          {detail.source === 'after' && detailActions.some(([next]) => next === 'HOAN_TAT') && <label>Kết quả xử lý (bắt buộc khi hoàn tất)
            <textarea rows="3" maxLength={1000} placeholder="Ví dụ: Đã thay linh kiện và kiểm tra máy hoạt động bình thường." value={resultNote} onChange={e=>setResultNote(e.target.value)}/>
          </label>}
          <div className="after-sales-actions">{detailActions.map(([next,label]) =>
            <button key={next} className="btn" disabled={busy || (next === 'HOAN_TAT' && detail.source === 'return' && (!detail.daNhapKho || (detail.loai === 'TRA_HANG_HOAN_TIEN' && !detail.ngayHoanTien)))} onClick={()=>applyAction(detail,next)}>{busy ? 'Đang lưu...' : label}</button>)}</div>
        </div>}
        <button className="btn" onClick={()=>setSelected(null)}>Đóng</button>
      </section>
    </div>}
  </div>;
}
