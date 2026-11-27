import { useContext, useEffect, useMemo, useState } from 'react';
import { AuthContext } from '../../context/AuthContext';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';
import { WARRANTY_OPTIONS } from '../../utils/warrantyPolicy';
import {
  AFTER_ACTIONS, RETURN_ACTIONS, GROUPS, STATUS_LABELS, TYPE_LABELS,
  countPending, filterRequests, formatAdminDate, normalizeRequests, isOverdue, completedToday, parseRequestText, canComplete, REJECT_REASONS, money, SLA_HOURS,
} from '../../utils/afterSalesAdmin';

const initial = { query: '', status: '', type: '', from: '', to: '', sort: 'priority' };
const ACTIONS = { return: RETURN_ACTIONS, after: AFTER_ACTIONS };

export default function AdminReturnsPage() {
  const {user} = useContext(AuthContext) || {};
  const { data, loading, error, reload } = useFetch(async () => {
    const [returns, afterSales] = await Promise.all([adminService.returns(), adminService.afterSales()]);
    return { returns: returns || [], afterSales: afterSales || [] };
  });
  const [filters, setFilters] = useState(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selected, setSelected] = useState(null);
  const [note, setNote] = useState('');
  const [internalNote, setInternalNote] = useState('');
  const [loadingInternal, setLoadingInternal] = useState(false);
  const [savingInternal, setSavingInternal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refundMethod, setRefundMethod] = useState('MOMO');
  const [refundReference, setRefundReference] = useState('');
  const [refundAmount, setRefundAmount] = useState('');
  const [adjustment, setAdjustment] = useState('');
  const [refundDate, setRefundDate] = useState('');
  const [workflow, setWorkflow] = useState(null);
  const [workflowError, setWorkflowError] = useState('');
  const [revision, setRevision] = useState(0);
  const [decision, setDecision] = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const [actionNote, setActionNote] = useState('');
  const [department, setDepartment] = useState('');
  const [photo, setPhoto] = useState(null);
  const [warrantyOutcome, setWarrantyOutcome] = useState('');
  const toast = useToast();
  const rows = useMemo(() => normalizeRequests(data), [data]);
  const results = useMemo(() => filterRequests(rows, filters), [rows, filters]);
  const pageCount = Math.max(1, Math.ceil(results.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visible = results.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const detail = selected ? rows.find(r => r.source === selected.source && r.maYeuCau === selected.maYeuCau) : null;
  useEffect(() => { setPage(1); }, [filters, pageSize]);
  useEffect(() => {
    setNote('');
    setInternalNote('');
    setRefundMethod('MOMO');
    setRefundReference('');
    setRefundAmount(''); setAdjustment(''); setRefundDate(''); setDecision(null); setPhoto(null); setActionNote(''); setRejectReason(''); setDepartment('');
    setWarrantyOutcome('');
    if (!selected) return;
    setNote(draftRead(selected));
    let alive = true;
    setLoadingInternal(true);
    adminService.getInternalAfterSalesNote(selected.source, selected.maYeuCau)
      .then(payload => { if (alive) setInternalNote(payload?.ghiChu || ''); })
      .catch(() => { if (alive) toast('Không tải được ghi chú nội bộ', 'error'); })
      .finally(() => { if (alive) setLoadingInternal(false); });
    return () => { alive = false; };
  }, [selected]);
  useEffect(() => {
    if (!selected) { setWorkflow(null); return; }
    let active = true;
    setWorkflow(null); setWorkflowError('');
    adminService.afterSalesWorkflow(selected.source, selected.maYeuCau)
      .then(v => { if(active) setWorkflow(v); })
      .catch(e => { if(active) setWorkflowError(errorMessage(e)); });
    return () => { active = false; };
  }, [selected, revision]);
  const refresh = () => { reload(); setRevision(v => v+1); };
  const draftKey = r => `phonenest:after-sales-draft:${user?.maTk}:${r.source}:${r.maYeuCau}`;
  const draftRead = r => { try { return sessionStorage.getItem(draftKey(r)) || ''; } catch { return ''; } };
  const saveDraft = () => { try { sessionStorage.setItem(draftKey(detail),note); toast('Đã lưu nháp trong tab này, chưa gửi khách'); } catch { toast('Không lưu được nháp','error'); } };
  const clearDraft = r => { try { sessionStorage.removeItem(draftKey(r)); } catch {} };
  const saveInternal = async () => {
    if (!detail || savingInternal) return;
    setSavingInternal(true);
    try {
      await adminService.saveInternalAfterSalesNote(detail.source, detail.maYeuCau, internalNote);
      toast('Đã lưu ghi chú nội bộ'); setRevision(v=>v+1);
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setSavingInternal(false); }
  };
  const setFilter = (key, value) => setFilters(s => ({ ...s, [key]: value }));
  const statusSort = () => setFilter('sort', filters.sort === 'statusAsc' ? 'statusDesc' : 'statusAsc');
  const dateSort = () => setFilter('sort', filters.sort === 'dateDesc' ? 'dateAsc' : 'dateDesc');
  const doneToday = rows.filter(r => completedToday(r)).length;
  const steps = workflow?.steps || [];
  const parsed = parseRequestText(detail?.lyDo);
  const {fields,description} = parsed;
  const isOpen = detail && !['HOAN_TAT','TU_CHOI','DA_HOAN_TIEN'].includes(detail.trangThai);
  const images = (detail?.anhMinhChung?.length ? detail.anhMinhChung : [detail?.hinhAnhMinhChung]).filter(Boolean);
  const beginAction = (action, status) => {
    setDecision({action,status}); setRejectReason(''); setDepartment('');
    setActionNote(status === 'TU_CHOI' ? '' : action === 'STATUS' || action === 'REPLY' ? note : '');
  };
  const executeAction = async () => {
    if (busy || !detail || !decision) return;
    let text = actionNote.trim();
    if (decision.status === 'TU_CHOI' && (!rejectReason || !text)) { toast('Chọn lý do từ chối và nhập phản hồi riêng','error'); return; }
    if (decision.status === 'HOAN_TAT' && detail.loai === 'BAO_HANH') {
      if (!warrantyOutcome || !text) { toast('Chọn kết quả và ghi rõ xử lý bảo hành','error'); return; }
      text = `Kết quả bảo hành: ${warrantyOutcome}. ${text}`;
    }
    if (decision.action !== 'STATUS' && !text) { toast('Vui lòng nhập nội dung','error'); return; }
    if (decision.action === 'TRANSFER' && !department) { toast('Vui lòng chọn bộ phận','error'); return; }
    if (text.length + (decision.status === 'TU_CHOI' ? rejectReason.length+12 : 0) > 500) { toast('Phản hồi quá dài','error'); return; }
    setBusy(true);
    try {
      await adminService.afterSalesAction(detail.source,detail.maYeuCau,{...decision,note:text,reason:rejectReason,department});
      if (decision.action === 'REPLY' || decision.action === 'STATUS') { setNote(''); clearDraft(detail); }
      setDecision(null); toast('Đã ghi nhận thao tác'); refresh();
      window.dispatchEvent(new Event('phonenest:after-sales-updated'));
    } catch(e) { toast(errorMessage(e),'error'); }
    finally { setBusy(false); }
  };
  const copy = async (e,value) => { e.stopPropagation(); try { await navigator.clipboard.writeText(value); toast('Đã sao chép'); } catch { toast('Không sao chép được','error'); } };
  const restock = async () => {
    if (!detail || busy || !steps.includes('RECEIVE_CHECK')) return;
    if (!window.confirm('Xác nhận đã nhận và kiểm tra sản phẩm trả về? Hệ thống sẽ cộng số lượng vào kho và ghi lịch sử biến động.')) return;
    setBusy(true);
    try {
      await adminService.restockReturn(detail.maYeuCau);
      toast('Đã xác nhận nhận hàng và nhập lại kho');
      refresh();
    } catch (e) { toast(errorMessage(e), 'error'); }
    finally { setBusy(false); }
  };

  const recordRefund = async () => {
    if (!detail || busy || !refundReference.trim()) return;
    const ceiling = Number(detail.soTienHoanDuKien || 0);
    const amount = Number(refundAmount || ceiling);
    if (!Number.isSafeInteger(amount) || amount <= 0 || amount > ceiling || (amount < ceiling && !adjustment.trim())) { toast('Kiểm tra số tiền và lý do điều chỉnh','error'); return; }
    const method = workflow?.payment === 'COD' || fields['Hoàn tiền đề nghị'] === 'Chuyển khoản' ? 'CHUYEN_KHOAN' : refundMethod;
    if (!window.confirm(`Xác nhận đã chuyển thực tế ${money(amount)} qua ${method}?`)) return;
    setBusy(true);
    try {
      await adminService.recordReturnRefund(detail.maYeuCau, { hinhThuc:method, maGiaoDich:refundReference.trim(), soTien:amount, lyDoDieuChinh:adjustment.trim(), thoiDiemHoan:refundDate || null });
      toast('Đã ghi nhận thông tin hoàn tiền'); setRefundReference(''); refresh();
    } catch(e) { toast(errorMessage(e),'error'); }
    finally { setBusy(false); }
  };
  const refundBase = Number(detail?.soTienHoanDuKien || 0);
  
  const actionNames = {TRIAGE:'Tiếp nhận để thẩm định',REPLY:'Gửi phản hồi',REQUEST_INFO:'Yêu cầu bổ sung thông tin/ảnh',TRANSFER:'Chuyển bộ phận',RECEIVE_CHECK:'Xác nhận nhận máy và kiểm tra',EXCHANGE_HANDOVER:'Xác nhận đã giao máy đổi',WARRANTY_RECEIVE:'Nhận máy bảo hành',WARRANTY_REPAIR:'Ghi nhận đã xử lý kỹ thuật',WARRANTY_HANDOVER:'Xác nhận trả máy cho khách',RESTOCK:'Nhập kho',REFUND:'Ghi nhận hoàn tiền',INTERNAL_NOTE:'Lưu ghi chú nội bộ',STATUS:'Cập nhật trạng thái'};
  const statCards = [['waiting','Chờ tiếp nhận',countPending(rows)],['processing','Đang xử lý',rows.filter(r=>GROUPS.processing.includes(r.trangThai)).length],['overdue','Quá hạn phản hồi',rows.filter(r=>isOverdue(r)).length],['today','Hoàn tất hôm nay',doneToday],['','Tổng yêu cầu',rows.length]];
  return <div className="after-sales-admin">
    <h2>Quản lý hậu mãi</h2>
    <p className="muted">Quản lý yêu cầu đổi/trả, bảo hành và khiếu nại. Hoàn tất không đồng nghĩa đã chuyển tiền hoàn thực tế.</p>
    {loading && <p className="muted">Đang tải dữ liệu...</p>}
    {error && <p className="error">{error} <button className="btn" onClick={reload}>Tải lại</button></p>}
    {!loading && !error && data && <>
      <div className="as-stats">
        {statCards.map(([status,label,count])=><button type="button" key={label} className={`as-stat-card ${filters.status===status?'active':''}`} onClick={()=>setFilters({...initial,status})}><span>{label}</span><strong>{count}</strong></button>)}
      </div>
      <p className="muted">HM: bảo hành/khiếu nại · YC: đổi trả. Hai trạng thái chờ được hiển thị chung là Chờ tiếp nhận. SLA phản hồi dự kiến: {SLA_HOURS} giờ lịch.</p>
      <div className="as-filters">
        <label className="as-search">Tìm mã yêu cầu, mã đơn, tên khách, SĐT
          <input aria-label="Tìm kiếm hậu mãi" placeholder="Ví dụ: YC001, HM002, DH003, Nguyễn Văn A..." value={filters.query} onChange={e => setFilter('query',e.target.value)} />
        </label>
        <label>Trạng thái<select value={filters.status} onChange={e=>setFilter('status',e.target.value)}>
          <option value="">Tất cả</option><option value="waiting">Chờ tiếp nhận</option><option value="approved">Đã duyệt</option><option value="overdue">Quá hạn phản hồi</option><option value="today">Hoàn tất hôm nay</option><option value="processing">Đang xử lý</option><option value="completed">Hoàn tất</option><option value="rejected">Từ chối</option>
        </select></label>
        <label>Loại yêu cầu<select value={filters.type} onChange={e=>setFilter('type',e.target.value)}>
          <option value="">Tất cả</option>{Object.entries(TYPE_LABELS).map(([key,label])=><option key={key} value={key}>{label}</option>)}
        </select></label>
        <label>Từ ngày<input aria-label="Từ ngày" type="date" value={filters.from} max={filters.to || undefined} onChange={e=>setFilter('from',e.target.value)} /></label>
        <label>Đến ngày<input aria-label="Đến ngày" type="date" value={filters.to} min={filters.from || undefined} onChange={e=>setFilter('to',e.target.value)} /></label>
        <label>Sắp xếp<select value={filters.sort} onChange={e=>setFilter('sort',e.target.value)}>
          <option value="priority">Ưu tiên xử lý</option><option value="dateDesc">Ngày mới nhất</option><option value="dateAsc">Ngày cũ nhất</option><option value="statusAsc">Chờ xử lý trước</option><option value="statusDesc">Hoàn tất trước</option>
        </select></label>
        <button className="btn" onClick={() => setFilters(initial)}>Xóa bộ lọc</button>
      </div>
      <div className="as-list-top"><h3>Danh sách yêu cầu ({results.length})</h3><label>Hiển thị <select value={pageSize} onChange={e=>setPageSize(Number(e.target.value))}><option value={10}>10</option><option value={20}>20</option></select> dòng / trang</label></div>
      <div className="after-sales-table-wrap"><table className="table as-table">
        <thead><tr><th>Mã yêu cầu</th><th>Đơn hàng</th><th>Khách hàng</th><th>Loại / sản phẩm</th><th><button className="as-sort" onClick={dateSort}>Ngày ↕</button></th><th><button className="as-sort" onClick={statusSort}>Trạng thái ↕</button></th><th>Xử lý</th></tr></thead>
        <tbody>{visible.length===0 && <tr><td colSpan="7" className="as-empty">Không có yêu cầu phù hợp bộ lọc.</td></tr>}
          {visible.map(r => <tr key={`${r.source}-${r.maYeuCau}`} className="as-clickable" onClick={()=>setSelected({source:r.source,maYeuCau:r.maYeuCau})}>
            <td><strong>{r.maYeuCau}</strong><button type="button" className="as-copy-small" aria-label="Sao chép mã yêu cầu" onClick={e=>copy(e,r.maYeuCau)}>⧉</button></td><td>{r.maDonHang || "Không gắn đơn"}{r.maDonHang && <button type="button" className="as-copy-small" aria-label="Sao chép mã đơn" onClick={e=>copy(e,r.maDonHang)}>⧉</button>}</td><td>{r.tenKhach}<div className="muted">{r.soDienThoai || 'SĐT: —'}</div></td>
            <td>{TYPE_LABELS[r.loai] || r.loai}<div className="muted">{r.tenSanPham || (r.loai==='KHIEU_NAI' ? parseRequestText(r.lyDo).fields['Chủ đề'] || 'Khiếu nại chung' : 'Toàn đơn')}{r.soLuong ? ` · ×${r.soLuong}` : ''}</div></td>
            <td>{formatAdminDate(r.ngayTao)}</td><td><span className={`as-status as-${r.trangThai}`}>{STATUS_LABELS[r.trangThai] || r.trangThai}</span>{isOverdue(r)&&<div className="as-late">Quá SLA</div>}</td>
            <td><button className="btn" onClick={e=>{e.stopPropagation();setSelected({source:r.source,maYeuCau:r.maYeuCau});}}>{(ACTIONS[r.source][r.trangThai] || []).length ? 'Xử lý' : 'Xem chi tiết'}</button></td>
          </tr>)}</tbody></table></div>
      <div className="as-pagination"><span>Trang {currentPage}/{pageCount} · {results.length} yêu cầu</span><button className="btn" disabled={currentPage===1} onClick={()=>setPage(1)}>Đầu</button><button className="btn" disabled={currentPage===1} onClick={()=>setPage(p=>p-1)}>Trước</button><button className="btn" disabled={currentPage===pageCount} onClick={()=>setPage(p=>p+1)}>Sau</button><button className="btn" disabled={currentPage===pageCount} onClick={()=>setPage(pageCount)}>Cuối</button></div>
    </>}
    {detail && <div className="as-overlay" onMouseDown={e=>{if(!busy&&e.target===e.currentTarget)setSelected(null)}}>
      <section className="as-modal" role="dialog" aria-modal="true" aria-label={`Chi tiết yêu cầu ${detail.maYeuCau}`}>
        <div className="as-modal-header"><h3>Yêu cầu {detail.maYeuCau}</h3><button className="btn" disabled={busy} onClick={()=>setSelected(null)} aria-label="Đóng chi tiết">✕</button></div>
        <div className="as-detail-grid"><div><b>Đơn hàng</b><p>{detail.maDonHang || "Không gắn đơn hàng"}</p></div><div><b>Khách hàng</b><p>{detail.tenKhach}</p></div>
          <div><b>Số điện thoại</b><p>{detail.soDienThoai || workflow?.phone || 'Chưa có'}</p></div><div><b>Ngày yêu cầu</b><p>{formatAdminDate(detail.ngayTao)}</p></div>
          <div><b>Loại yêu cầu</b><p>{TYPE_LABELS[detail.loai] || detail.loai}</p></div><div><b>Trạng thái</b><p><span className={`as-status as-${detail.trangThai}`}>{STATUS_LABELS[detail.trangThai] || detail.trangThai}</span></p></div></div>
        <p><b>Sản phẩm:</b> {detail.tenSanPham || (detail.maDonHang ? 'Toàn đơn' : 'Không chỉ định')} {detail.soLuong ? `· Số lượng ${detail.soLuong}` : ''}</p>
        {detail.chiTiet?.length > 0 && <div className="as-request-items"><b>Các dòng hàng</b>{detail.chiTiet.map((item) => <p key={item.maPhienBan}>{item.tenSanPham} · SL {item.soLuong} · {item.daNhapKho ? `Đã nhập kho ${formatAdminDate(item.ngayNhapKho)}` : 'Chưa nhập kho'}</p>)}</div>}
        <section className="as-work-block"><h4>Kiểm tra điều kiện</h4>
          {!workflow && !workflowError && <p>Đang tải điều kiện...</p>}
          {workflowError && <p className="error">{workflowError} <button className="btn" onClick={()=>setRevision(v=>v+1)}>Thử lại</button></p>}
          {workflow && <><p>Trạng thái đơn: {workflow.orderStatus === 'DA_GIAO' ? 'Đã nhận hàng' : workflow.orderStatus || 'Không gắn đơn'}</p><p>Giao: {formatAdminDate(workflow.delivered)} · Xác nhận: {formatAdminDate(workflow.confirmed)}</p>
            {workflow.deadline && <p>Hạn: {formatAdminDate(workflow.deadline)} · <strong className={workflow.eligibleAtSubmission?'as-good':'as-late'}>{workflow.eligibleAtSubmission?'Đủ hạn tại lúc gửi':'Cần kiểm tra điều kiện tại lúc gửi'}</strong></p>}
            {workflow.deadline && <p className="muted">Hiện tại: {new Date(workflow.deadline).getTime() < Date.now() ? 'Đã hết hạn' : `Còn ${Math.ceil((new Date(workflow.deadline).getTime()-Date.now())/86400000)} ngày`}. Yêu cầu gửi đúng hạn vẫn được xử lý.</p>}
            {workflow.orderStatus && <p>Voucher: {workflow.voucher || 'Không dùng'} · Ưu đãi đơn: {money(workflow.discount)}</p>}</>}
          {fields['Lý do']==='Đổi ý' && <p className={fields['Tình trạng máy']==='Còn nguyên seal'?'muted':'as-late'}>Đổi ý: kiểm tra seal và phụ kiện thực tế trước khi duyệt.</p>}
          {['Rơi vỡ','Vào nước'].includes(fields['Ngoại hình']) && <p className="as-late">Có thể không thuộc bảo hành miễn phí, cần thẩm định.</p>}
        </section>
        <section className="as-work-block"><h4>Thông tin sự cố</h4><dl className="as-kv">{Object.entries(fields).filter(([k])=>!['Hoàn tiền đề nghị','Ngân hàng','Số tài khoản','Chủ tài khoản'].includes(k)).map(([k,v])=><div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl><h5>Mô tả của khách hàng</h5><p className="as-description">{description || 'Chưa có'}</p></section>
        {detail.loai==='TRA_HANG_HOAN_TIEN' && <section className="as-work-block"><h4>Thông tin hoàn tiền</h4><dl className="as-kv">{['Hoàn tiền đề nghị','Ngân hàng','Số tài khoản','Chủ tài khoản'].filter(k=>fields[k]).map(k=><div key={k}><dt>{k}</dt><dd>{fields[k]}</dd></div>)}</dl><p><b>{detail.ngayHoanTien?'Đã ghi nhận hoàn':'Giá trị đủ điều kiện dự kiến'}:</b> {money(refundBase)}</p>{workflow?.gross!=null&&<p>Giá hàng {money(workflow.gross)} − ưu đãi phân bổ {money(workflow.allocatedDiscount)} = {money(Math.max(0,Number(workflow.gross)-Number(workflow.allocatedDiscount)))}</p>}<p className="muted">Đã phân bổ ưu đãi hàng hóa, chưa trừ phí điều chỉnh; không gồm phí vận chuyển.</p></section>}
        <section className="as-work-block"><h4>Bằng chứng ({images.length})</h4>{images.length ? <div className="as-thumbs">{images.map((url,i)=><div key={url} className="as-photo-card"><button type="button" onClick={()=>setPhoto(i)} aria-label={`Phóng to ảnh ${i+1}`}><img className="as-evidence" src={url} alt={`Minh chứng ${i+1}`}/></button><a href={url} download target="_blank" rel="noreferrer">Tải / mở ảnh</a></div>)}</div>:<p className="muted">Chưa có ảnh minh chứng.</p>}{isOpen&&<button className="btn" disabled={busy} onClick={()=>beginAction('REQUEST_INFO')}>Yêu cầu bổ sung ảnh đúng</button>}</section>
        <section className="as-work-block"><h4>Phản hồi gửi khách: {detail.ghiChuXuLy ? 'Đã gửi' : 'Chưa có'}</h4>{detail.ghiChuXuLy&&<p className="as-description">{detail.ghiChuXuLy}</p>}</section>
        <div className="as-review"><label>Ghi chú nội bộ (chỉ Admin thấy)
          <textarea rows="3" maxLength={1000} placeholder="Thông tin trao đổi nội bộ, không gửi cho khách hàng" value={internalNote} disabled={loadingInternal} onChange={e=>setInternalNote(e.target.value)} />
        </label><button className="btn" disabled={savingInternal || loadingInternal} onClick={saveInternal}>{savingInternal ? 'Đang lưu...' : 'Lưu ghi chú nội bộ'}</button></div>
        <section className="as-work-block"><h4>Nhật ký xử lý & phản hồi</h4>{workflow?.timeline?.length ? <ol className="as-history">{workflow.timeline.map(h=>{const d=h.duLieuSau||{};return <li key={h.maNhatKy}><strong>{actionNames[d.suKien] || (d.trangThai ? STATUS_LABELS[d.trangThai] : h.moTa)}</strong><div className="muted">{formatAdminDate(h.thoiGian)} · {h.tenNguoiThucHien}</div>{d.trangThai&&<div>{STATUS_LABELS[d.trangThai]||d.trangThai}</div>}{d.phanHoi&&<p>Phản hồi gửi khách: {d.phanHoi}</p>}{d.ghiNhan&&<p>{d.ghiNhan}</p>}{d.boPhan&&<p>Bộ phận: {d.boPhan}</p>}{d.soTienHoan!=null&&<p>Hoàn: {money(d.soTienHoan)} · {d.lyDoDieuChinh || 'Không điều chỉnh'}</p>}</li>})}</ol>:<p className="muted">Chưa có nhật ký quản trị.</p>}{detail.lichSu?.length>0&&<details><summary>Lịch sử trạng thái khách hàng</summary><ol className="as-history">{detail.lichSu.map((h,i)=><li key={i}>{formatAdminDate(h.thoiGian)} · {STATUS_LABELS[h.trangThai]||h.trangThai} {h.ghiChu||''}</li>)}</ol></details>}</section>
        {detail.source==='return'&&<section className="as-work-block"><h4>Quy trình đổi trả</h4><ol className="as-steps">{[['Duyệt yêu cầu',!['CHO_DUYET','TU_CHOI'].includes(detail.trangThai)],['Nhận máy và kiểm tra',steps.includes('RECEIVE_CHECK')||detail.daNhapKho],['Nhập kho',detail.daNhapKho],[detail.loai==='DOI_HANG'?'Giao máy đổi':'Ghi nhận hoàn tiền',detail.loai==='DOI_HANG'?steps.includes('EXCHANGE_HANDOVER'):!!detail.ngayHoanTien],['Hoàn tất',detail.trangThai==='HOAN_TAT']].map(([label,done])=><li key={label} className={done?'done':''}>{done?'✓':'○'} {label}</li>)}</ol>
          {isOpen&&['DA_DUYET','DANG_XU_LY'].includes(detail.trangThai)&&!detail.daNhapKho&&<>{!steps.includes('RECEIVE_CHECK')?<button className="btn" disabled={!workflow||busy} onClick={()=>beginAction('RECEIVE_CHECK')}>Nhận máy và kiểm tra</button>:<button className="btn btn-primary" disabled={!workflow||busy} onClick={restock}>Xác nhận nhập kho</button>}<p className="muted">Chỉ nhập kho bán được khi hàng thực tế đủ điều kiện nhập; hàng hỏng cần xử lý riêng, không cộng tồn bán.</p></>}
          {isOpen&&detail.loai==='DOI_HANG'&&detail.trangThai==='DANG_XU_LY'&&detail.daNhapKho&&!steps.includes('EXCHANGE_HANDOVER')&&<button className="btn" disabled={!workflow||busy} onClick={()=>beginAction('EXCHANGE_HANDOVER')}>Ghi nhận đã giao máy đổi</button>}
        </section>}
        {detail.loai==='BAO_HANH'&&<section className="as-work-block"><h4>Quy trình bảo hành</h4><ol className="as-steps">{[['Tiếp nhận yêu cầu',detail.trangThai!=='CHO_TIEP_NHAN'&&detail.trangThai!=='TU_CHOI'],['Nhận máy',steps.includes('WARRANTY_RECEIVE')],['Kiểm tra / sửa chữa',steps.includes('WARRANTY_REPAIR')],['Trả máy',steps.includes('WARRANTY_HANDOVER')],['Hoàn tất',detail.trangThai==='HOAN_TAT']].map(([label,done])=><li key={label} className={done?'done':''}>{done?'✓':'○'} {label}</li>)}</ol>{isOpen&&detail.trangThai==='DANG_XU_LY'&&!steps.includes('WARRANTY_HANDOVER')&&<button className="btn" disabled={!workflow||busy} onClick={()=>beginAction(!steps.includes('WARRANTY_RECEIVE')?'WARRANTY_RECEIVE':!steps.includes('WARRANTY_REPAIR')?'WARRANTY_REPAIR':'WARRANTY_HANDOVER')}>{actionNames[!steps.includes('WARRANTY_RECEIVE')?'WARRANTY_RECEIVE':!steps.includes('WARRANTY_REPAIR')?'WARRANTY_REPAIR':'WARRANTY_HANDOVER']}</button>}</section>}
        {detail.source==='return'&&detail.loai==='TRA_HANG_HOAN_TIEN'&&(detail.trangThai==='DANG_XU_LY'||detail.ngayHoanTien)&&<section className="as-work-block"><h4>Đối soát hoàn tiền</h4>{detail.ngayHoanTien?<p>Đã ghi nhận {money(refundBase)} qua {detail.hinhThucHoanTien} · Mã: {detail.maGiaoDichHoanTien} · {formatAdminDate(detail.ngayHoanTien)}</p>:<><p className="muted">Chuyển tiền bên ngoài hệ thống trước khi ghi nhận. Không tự động chuyển tiền.</p><label>Giá trị đủ điều kiện: {money(refundBase)}</label><label>Số tiền thực hoàn (VNĐ)<input type="number" min="1" max={refundBase} step="1" value={refundAmount} placeholder={String(refundBase)} onChange={e=>setRefundAmount(e.target.value)}/></label><p>Giá trị sau ưu đãi {money(refundBase)} − phí/điều chỉnh {money(Math.max(0,refundBase-Number(refundAmount||refundBase)))} = {money(refundAmount||refundBase)}</p>{Number(refundAmount||refundBase)<refundBase&&<label>Lý do điều chỉnh *<textarea maxLength={500} value={adjustment} onChange={e=>setAdjustment(e.target.value)}/></label>}{workflow?.payment==='COD'||fields['Hoàn tiền đề nghị']==='Chuyển khoản'?<p><b>Hình thức:</b> Chuyển khoản</p>:<label>Hình thức<select value={refundMethod} onChange={e=>setRefundMethod(e.target.value)}><option value="MOMO">MoMo</option><option value="CHUYEN_KHOAN">Chuyển khoản</option><option value="TIEN_MAT">Tiền mặt</option><option value="KHAC">Khác</option></select></label>}<label>Mã giao dịch *<input maxLength={150} value={refundReference} onChange={e=>setRefundReference(e.target.value)}/></label><label>Thời điểm hoàn thực tế (để trống = hiện tại)<input type="datetime-local" value={refundDate} onChange={e=>setRefundDate(e.target.value)}/></label><button className="btn btn-primary" disabled={busy||!workflow||!detail.daNhapKho||!refundReference.trim()} onClick={recordRefund}>Ghi nhận đã hoàn tiền</button>{!detail.daNhapKho&&<p className="muted">Cần nhập kho trước khi hoàn tiền.</p>}</>}</section>}
        {isOpen&&<section className="as-work-block"><h4>Phản hồi gửi khách hàng</h4>{detail.loai==='BAO_HANH'&&detail.trangThai==='DANG_XU_LY'&&<label>Kết quả bảo hành<select value={warrantyOutcome} onChange={e=>setWarrantyOutcome(e.target.value)}><option value="">Chọn kết quả thực tế</option>{WARRANTY_OPTIONS.map(x=><option key={x} value={x}>{x}</option>)}</select></label>}
          <textarea rows="3" maxLength={500} value={note} onChange={e=>setNote(e.target.value)} placeholder="Nội dung khách hàng sẽ nhìn thấy"/>
          <div className="after-sales-actions"><button className="btn" disabled={busy} onClick={saveDraft}>Lưu nháp</button><button className="btn" disabled={busy||!note.trim()} onClick={()=>beginAction('REPLY')}>Gửi phản hồi</button><button className="btn" disabled={busy} onClick={()=>beginAction('REQUEST_INFO')}>Yêu cầu bổ sung</button><button className="btn" disabled={busy} onClick={()=>beginAction('TRANSFER')}>Chuyển bộ phận</button></div>
          <div className="after-sales-actions">{detail.source==='return'&&detail.trangThai==='CHO_DUYET'&&!steps.includes('TRIAGE')&&<button className="btn" disabled={!workflow||busy} onClick={()=>beginAction('TRIAGE')}>Tiếp nhận để thẩm định</button>}{(ACTIONS[detail.source][detail.trangThai]||[]).map(([next,label])=><button key={next} className={`btn ${next==='DA_DUYET'?'btn-primary':''}`} disabled={busy||!workflow||(next==='HOAN_TAT'&&!canComplete(detail,steps))} onClick={()=>beginAction('STATUS',next)}>{next==='HOAN_TAT'&&detail.loai==='KHIEU_NAI'?'Giải quyết và đóng':label}</button>)}</div></section>}
        {decision&&<div className="as-decision-overlay" onKeyDown={e=>{if(e.key==='Escape'&&!busy)setDecision(null)}}><section className="as-work-block as-decision" role="dialog" aria-modal="true" aria-label="Xác nhận thao tác"><h4>{decision.action==='STATUS' ? `Xác nhận ${STATUS_LABELS[decision.status]||decision.status}` : actionNames[decision.action]}</h4>{decision.status==='TU_CHOI'&&<label>Lý do từ chối *<select autoFocus value={rejectReason} onChange={e=>setRejectReason(e.target.value)}><option value="">Chọn lý do</option>{REJECT_REASONS.map(x=><option key={x}>{x}</option>)}</select></label>}{decision.action==='TRANSFER'&&<label>Bộ phận *<select value={department} onChange={e=>setDepartment(e.target.value)}><option value="">Chọn bộ phận</option>{['Kỹ thuật','Kho','Kế toán','Chăm sóc khách hàng'].map(x=><option key={x}>{x}</option>)}</select></label>}<label>{['STATUS','REPLY','REQUEST_INFO','TRIAGE'].includes(decision.action)?'Phản hồi khách sẽ nhìn thấy':'Ghi nhận nội bộ'}<textarea autoFocus={decision.status!=='TU_CHOI'} rows="4" value={actionNote} maxLength={500} onChange={e=>setActionNote(e.target.value)}/></label>{decision.status==='DA_DUYET'&&<p>Duyệt xong: tiếp nhận máy, kiểm tra và nhập kho trước bước hoàn tiền/đổi máy.</p>}{decision.status==='TU_CHOI'&&<p className="muted">Phản hồi từ chối được nhập riêng, không dùng lại nội dung duyệt.</p>}{decision.status==='TU_CHOI'&&rejectReason&&actionNote.trim()&&<p className="as-description">Khách sẽ thấy: Từ chối — {rejectReason}: {actionNote.trim()}</p>}<div className="after-sales-actions"><button className="btn" disabled={busy} onClick={()=>setDecision(null)}>Hủy</button><button className="btn btn-primary" disabled={busy} onClick={executeAction}>Xác nhận</button></div></section></div>}
        {photo!==null&&images[photo]&&<div className="as-lightbox" role="dialog" aria-label="Xem ảnh minh chứng" onKeyDown={e=>{if(e.key==='Escape')setPhoto(null)}}><button className="btn" autoFocus onClick={()=>setPhoto(null)}>Đóng ảnh ✕</button><img src={images[photo]} alt={`Minh chứng ${photo+1}`}/><div className="after-sales-actions"><button className="btn" disabled={photo===0} onClick={()=>setPhoto(p=>p-1)}>Trước</button><span>{photo+1}/{images.length}</span><button className="btn" disabled={photo===images.length-1} onClick={()=>setPhoto(p=>p+1)}>Sau</button><a href={images[photo]} download target="_blank" rel="noreferrer">Tải ảnh</a></div></div>}
        <button className="btn" disabled={busy} onClick={()=>setSelected(null)}>Đóng</button>
      </section>
    </div>}
  </div>;
}
