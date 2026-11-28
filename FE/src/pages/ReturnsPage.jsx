import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import StatusBadge from '../components/StatusBadge';
import useFetch from '../hooks/useFetch';
import useScrollToHash from '../hooks/useScrollToHash';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { orderService } from '../service/orderService';
import { errorMessage, formatDate, formatDay, formatPrice } from '../utils/format';
import { afterSalesStatusLabel } from '../utils/afterSalesWorkflow';
import { eligible, remainingLabel, warrantyEnd, returnEnd, deadlineTone } from '../utils/warrantyPolicy';
import { TYPES, REASONS, allowedConditions, refundOptions, FAULTS, descriptionHint, APPEARANCES, TOPICS, MAX_IMAGES, imageError, proofRequired, validateForm, MISMATCHES, BANKS, accountDigits, normalizeHolder, preferredCondition, refundEstimate, maskAccount, evidenceHint, clearFieldErrors, validationAfterChange, visibleValidationErrors, selectedFaults, toggleFault, warrantyAppearanceWarning, sameExchange, upgradeExchange, exchangeQuote } from '../utils/afterSalesForm';

import { downloadReceipt, printReceipt } from '../utils/afterSalesReceipt';

const EMPTY = { maDonHang: '', maPhienBan: '', soLuong: 1, lyDo: '', reason: '', condition: '', fault: '', sealConfirmed: '', mismatch: '', refund: '', bank: '', account: '', holder: '', targetId: '', appearance: '', topic: '' };
const STATUS = { CHO_DUYET: 'Chờ tiếp nhận', CHO_TIEP_NHAN: 'Chờ tiếp nhận', DANG_KIEM_TRA: 'Đang kiểm tra', DANG_XAC_MINH: 'Đang xác minh', DANG_XU_LY: 'Đang xử lý', DA_DUYET: 'Đã duyệt', TU_CHOI: 'Từ chối', HOAN_TAT: 'Hoàn tất', DA_HOAN_TIEN: 'Đã hoàn tiền' };
const HINTS = { TRA_HANG_HOAN_TIEN: 'Ví dụ: máy không lên nguồn khi mở hộp, đã thử sạc...', DOI_HANG: 'Nêu lý do đổi và phiên bản bạn mong muốn...', KHIEU_NAI: 'Nêu sự việc, thời điểm và cách bạn mong muốn được hỗ trợ...' };
const policyLines = (type, p) => {
  const days = p?.returnDays ? `trong ${p.returnDays} ngày` : 'trong thời hạn quy định';
  const months = p?.warrantyMonths ? `${p.warrantyMonths} tháng` : 'theo thời hạn quy định';
  const returns = [`Đổi trả ${days} kể từ ngày bạn xác nhận đã nhận hàng.`, 'Cửa hàng kiểm tra máy, phụ kiện và minh chứng trước khi duyệt.'];
  const warranty = [`Bảo hành ${months} kể từ ngày nhận hàng.`, 'Cửa hàng thẩm định để sửa chữa, thay linh kiện hoặc đổi máy tương đương nếu không thể sửa.'];
  if (type === 'KHIEU_NAI') return ['Bạn có thể phản ánh chung hoặc liên quan đến một đơn hàng.', 'Cửa hàng sẽ tiếp nhận và phản hồi sau khi xác minh.'];
  if (type === 'BAO_HANH') return warranty;
  if (type === 'TRA_HANG_HOAN_TIEN') return [...returns, 'Dự kiến hoàn tiền trong 3–7 ngày làm việc sau khi nhận và kiểm tra máy.'];
  if (type === 'DOI_HANG') return [...returns, 'Giá và tồn kho phiên bản đổi được kiểm tra lại khi xử lý.'];
  return [returns[0], warranty[0], returns[1]];
};

function Field({ name, label, required = true, error, children }) {
  return <div className="field as-field"><label htmlFor={`as-${name}`}>{label}{required && <span className="as-required"> *</span>}</label>{children}{error && <small className="as-error" id={`as-${name}-error`} role="alert">{error}</small>}</div>;
}
function Preview({ file, remove }) {
  const [url, setUrl] = useState('');
  useEffect(() => { const objectUrl = URL.createObjectURL(file); setUrl(objectUrl); return () => URL.revokeObjectURL(objectUrl); }, [file]);
  return <div className="as-thumb">{url && <img src={url} alt={`Ảnh ${file.name}`} />}<small>{file.name}</small><button className="btn" type="button" aria-label={`Xóa ${file.name}`} onClick={remove}>Xóa ảnh</button></div>;
}
const requestType = r => r.loai || r.loaiYeuCau || 'TRA_HANG_HOAN_TIEN';
const requestImages = r => r.anhMinhChung || (r.hinhAnhMinhChung ? [r.hinhAnhMinhChung] : []);
const RequestStatus = ({ request, status = request.trangThai }) => ['BAO_HANH', 'KHIEU_NAI'].includes(requestType(request))
  ? <span className={`badge badge-${status}`}>{afterSalesStatusLabel(status, requestType(request))}</span>
  : <StatusBadge status={status} />;
export default function ReturnsPage() {
  const [params] = useSearchParams();
  const requestedOrder = params.get('orderId') || params.get('maDonHang') || '';
  const [loai, setLoai] = useState(TYPES[params.get('loai')] ? params.get('loai') : '');
  const [form, setForm] = useState({...EMPTY, maDonHang: requestedOrder});
  const [interaction, setInteraction] = useState({});
  const [submitError, setSubmitError] = useState('');
  const validationEpoch = useRef(0);
  const renderEpoch = validationEpoch.current;
  const [bankQuery, setBankQuery] = useState('');
  const [bankOpen, setBankOpen] = useState(false);
  const [catalog, setCatalog] = useState([]);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogError, setCatalogError] = useState('');
  const [targetQuery, setTargetQuery] = useState('');
  const [targetOpen, setTargetOpen] = useState(false);
  const [catalogRetry, setCatalogRetry] = useState(0);
  const typeChanging = useRef(false);
  const [images, setImages] = useState([]);
  const [imageErrors, setImageErrors] = useState([]);
  const [locLoai, setLocLoai] = useState('');
  const [locStatus, setLocStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState('');
  const [receipt, setReceipt] = useState(null);
  const [orderPicker, setOrderPicker] = useState(false);
  const [recent, setRecent] = useState([]);
  const lock = useRef(false);
  const formRef = useRef(null);
  const toast = useToast();
  const {data: orders, loading: orderLoading, error: orderError, reload: reloadOrders} = useFetch(orderService.list);
  const lastOrderRefresh = useRef(0);
  useEffect(() => {
    if (!loai || loai === 'KHIEU_NAI') return;
    function refreshOnReturn() {
      if (document.visibilityState !== 'visible' || orderLoading || busy) return;
      const now = Date.now();
      if (now - lastOrderRefresh.current < 1000) return;
      lastOrderRefresh.current = now;
      reloadOrders();
    }
    window.addEventListener('focus', refreshOnReturn);
    document.addEventListener('visibilitychange', refreshOnReturn);
    return () => {
      window.removeEventListener('focus', refreshOnReturn);
      document.removeEventListener('visibilitychange', refreshOnReturn);
    };
  }, [loai, orderLoading, busy, reloadOrders]);
  const {data: policy} = useFetch(customerService.afterSalesPolicy);
  const {data: requests, loading, error, reload} = useFetch(async () => {
    const [returns, after] = await Promise.all([customerService.returns(), customerService.afterSales()]);
    return [...(returns || []), ...(after || [])];
  });
  useScrollToHash(!loading && requests != null);
  const allOrders = orders || [];
  const validOrders = allOrders.filter(o => eligible(o, loai));
  const order = validOrders.find(o => o.maDonHang === form.maDonHang);
  const product = order?.chiTiet?.find(d => d.maPhienBan === form.maPhienBan);
  const same = sameExchange(form.reason);
  const quoteFor = v => exchangeQuote(order, product, form.soLuong, form.reason, v);
  const target = catalog.find(v => v.id === (same ? form.maPhienBan : form.targetId) && quoteFor(v));
  const quote = target ? quoteFor(target) : null;
  const targetMatches = catalog.filter(v => quoteFor(v) && targetQuery.toLocaleLowerCase('vi').trim().split(/\s+/).every(word => v.label.toLocaleLowerCase('vi').includes(word))).slice(0,20);
  useEffect(() => {
    if (loai !== 'DOI_HANG') return;
    let active = true; setCatalogLoading(true); setCatalogError(''); setCatalog([]);
    customerService.exchangeOptions().then(data => { if (active) setCatalog(data || []); }).catch(err => { if (active) setCatalogError(errorMessage(err)); }).finally(() => { if (active) setCatalogLoading(false); });
    return () => { active = false; };
  }, [loai, catalogRetry]);
  useEffect(() => {
    if (loai && loai !== 'KHIEU_NAI' && !form.maDonHang && validOrders.length === 1) setForm(f => ({...f, maDonHang:validOrders[0].maDonHang}));
  }, [loai, form.maDonHang, validOrders.length, validOrders[0]?.maDonHang]);
  useEffect(() => {
    if (loai && loai !== 'KHIEU_NAI' && order?.chiTiet?.length === 1 && !form.maPhienBan) setForm(f => ({...f, maPhienBan:order.chiTiet[0].maPhienBan}));
  }, [loai, order?.maDonHang, form.maPhienBan, order?.chiTiet?.length]);
  useEffect(() => {
    if (loai !== 'TRA_HANG_HOAN_TIEN') return;
    const options = refundOptions(order);
    if (options.length === 1) setForm(f => f.refund === options[0] ? f : {...f, refund:options[0], ...(options[0] !== 'Chuyển khoản' ? {bank:'',account:'',holder:''} : {})});
  }, [loai, order?.maDonHang, order?.phuongThucTt]);
  useEffect(() => {
    if (orders && loai && form.maDonHang && !orders.some(o => o.maDonHang === form.maDonHang && eligible(o, loai))) {
      setForm(f => ({...f, maDonHang:'', maPhienBan:'', soLuong:1, targetId:'', refund:'', bank:'', account:'', holder:'', sealConfirmed:'', ...(loai === 'BAO_HANH' ? {fault:'', appearance:''} : {})}));
      setInteraction(e => clearFieldErrors(e,'maDonHang')); setSubmitError(''); setBankQuery(''); setBankOpen(false); setTargetQuery(''); setTargetOpen(false);
    }
  }, [orders, loai, form.maDonHang]);
  const pending = allOrders.filter(o => o.trangThai === 'CHO_KHACH_XAC_NHAN');
  const isReturn = loai === 'TRA_HANG_HOAN_TIEN' || loai === 'DOI_HANG';
  const requiredProof = proofRequired(loai, form.reason, form.condition);
  const estimate = refundEstimate(order, product, form.soLuong);
  const bankMatches = BANKS.filter(bank => bank.toLowerCase().includes(bankQuery.toLowerCase().trim()));
  const allRequests = [...new Map([...(requests || []), ...recent.filter(r => !(requests || []).some(x => x.maYeuCau === r.maYeuCau))].map(r => [r.maYeuCau, r])).values()].sort((a,b) => new Date(b.ngayTao) - new Date(a.ngayTao));
  const filtered = allRequests.filter(r => (!locLoai || requestType(r) === locLoai) && (!locStatus || r.trangThai === locStatus || (locStatus === 'HOAN_TAT' && r.trangThai === 'DA_HOAN_TIEN')));
  const invalidFields = validateForm(loai, form, order, product, images);
  if (loai === 'DOI_HANG' && !target) invalidFields.targetId = (same ? 'Phiên bản đã mua không đủ hàng để đổi 1–1; vui lòng chọn hoàn tiền.' : 'Chọn máy còn hàng có giá không thấp hơn giá trị thực mua.');
  const errors = {...visibleValidationErrors(invalidFields, interaction), submit:submitError};
  const allowed = allowedConditions(form.reason);
  function change(name, value) {
    if (name === 'reason' && value !== form.reason) { resetValidation(); setImageErrors([]); }
    setForm(f => {
      const next = {...f, [name]:value};
      if (name === 'maDonHang' || name === 'maPhienBan') { Object.assign(next,{soLuong:1,targetId:'',sealConfirmed:''}); if (loai === 'BAO_HANH') Object.assign(next,{fault:'',appearance:''}); if (name === 'maDonHang') Object.assign(next,{maPhienBan:'',refund:'',bank:'',account:'',holder:''}); }
      if (name === 'reason') {
        next.condition = preferredCondition(value) || (allowedConditions(value).includes(f.condition) ? f.condition : '');
        next.fault = ''; next.mismatch = ''; next.sealConfirmed = ''; next.targetId = '';
      }
      if (name === 'soLuong' || next.reason !== 'Đổi ý') next.sealConfirmed = '';
      if (loai !== 'BAO_HANH' && next.reason !== 'Máy lỗi') next.fault = '';
      if (next.reason !== 'Không đúng mô tả') next.mismatch = '';
      if (name === 'refund' && value !== 'Chuyển khoản') Object.assign(next,{bank:'',account:'',holder:''});
      if (name === 'soLuong' && target && target.stock < Number(value)) next.targetId = '';
      return next;
    });
    if (['maDonHang','maPhienBan','reason'].includes(name) || (name === 'soLuong' && target && target.stock < Number(value))) { setTargetQuery(''); setTargetOpen(false); }
    if (name === 'maDonHang' || (name === 'refund' && value !== 'Chuyển khoản')) { setBankQuery(''); setBankOpen(false); }
    // New dependent fields start clean even after a previous submit.
    setInteraction(e => validationAfterChange(e,name)); setSubmitError('');
  }
  function resetValidation() { validationEpoch.current += 1; setInteraction({}); setSubmitError(''); }
  function changeType(value) {
    typeChanging.current = true; resetValidation(); setBankQuery(''); setBankOpen(false); setLoai(value); setOrderPicker(false); setReceipt(null); setTargetQuery(''); setTargetOpen(false);
    setForm(f => ({...EMPTY, maDonHang: eligible(allOrders.find(o => o.maDonHang === f.maDonHang), value) ? f.maDonHang : (!orders || eligible(allOrders.find(o => o.maDonHang === requestedOrder), value)) ? requestedOrder : ''}));
    setImages([]); setImageErrors([]); setSuccess(''); requestAnimationFrame(() => {typeChanging.current = false;});
  }
  async function copyOrder(code) { try { await navigator.clipboard.writeText(code); toast('Đã sao chép mã đơn hàng'); } catch { toast('Không thể sao chép. Mã đơn: ' + code, 'error'); } }
  function addFiles(files) {
    const added = [...images]; const issues = [];
    for (const file of files) {
      const issue = imageError(file);
      if (issue) { issues.push(`${file.name}: ${issue}`); continue; }
      if (added.some(f => f.name === file.name && f.size === file.size && f.lastModified === file.lastModified)) { issues.push(`${file.name}: Ảnh đã được chọn.`); continue; }
      if (added.length >= MAX_IMAGES) { issues.push(`${file.name}: Chỉ được chọn tối đa ${MAX_IMAGES} ảnh.`); continue; }
      added.push(file);
    }
    setImages(added); setImageErrors(issues); setSubmitError('');
  }
  const attrs = name => ({id:`as-${name}`, 'aria-invalid': !!errors[name], 'aria-describedby': name === 'lyDo' ? 'as-lyDo-hint' : errors[name] ? `as-${name}-error` : undefined,
    onBlur: e => {
      // Validate only a control that actually received focus; grouped controls validate on submit.
      if (renderEpoch !== validationEpoch.current || typeChanging.current || e.relatedTarget?.id === 'as-loai' || ['loai','reason','condition','refund','mismatch','bank','images','targetId','soLuong','maDonHang','appearance','fault','sealConfirmed'].includes(name)) return;
      setInteraction(old => ({...old, [name]:'touched'}));
    }});
  const select = (name, label, options) => <Field name={name} label={label} error={errors[name]}><select {...attrs(name)} required value={form[name]} onChange={e => change(name,e.target.value)}><option value="" disabled>Chọn {label.toLowerCase()}</option>{options.map(v => <option key={v}>{v}</option>)}</select></Field>;
  const cards = (name,label,options) => <Field name={name} label={label} error={errors[name]}><div className="as-radio-cards" role="radiogroup" aria-label={label} aria-required="true" aria-describedby={errors[name] ? `as-${name}-error` : undefined}>{options.map((v,i) => <label key={v} className={form[name] === v ? 'selected' : ''}><input type="radio" name={name} {...(i === 0 ? attrs(name) : {onBlur:attrs(name).onBlur})} checked={form[name] === v} value={v} onChange={() => change(name,v)} />{v}</label>)}</div></Field>;
  const faultPicker = () => <Field name="fault" label="Loại lỗi" error={errors.fault}><div className="as-chips as-fault-chips" role="group" aria-label="Loại lỗi (có thể chọn nhiều)" aria-describedby={errors.fault ? 'as-fault-error' : undefined}>{FAULTS.map((item,i) => <button key={item} type="button" className="btn" {...(i === 0 ? attrs('fault') : {})} aria-pressed={selectedFaults(form.fault).includes(item)} onClick={() => change('fault',toggleFault(form.fault,item))}>{selectedFaults(form.fault).includes(item) && <span aria-hidden="true">✓ </span>}{item}</button>)}</div><small className="muted">Có thể chọn nhiều loại lỗi.</small></Field>;
  async function submit(e) {
    e.preventDefault(); if (lock.current) return;
    const invalid = validateForm(loai, form, order, product, images);
    if (loai === 'DOI_HANG' && !target) invalid.targetId = (same ? 'Phiên bản đã mua không đủ hàng để đổi 1–1; vui lòng chọn hoàn tiền.' : 'Chọn máy còn hàng có giá không thấp hơn giá trị thực mua.');
    setInteraction(old => ({...old, ...Object.fromEntries(Object.keys(invalid).map(key => [key,'submitted']))})); setSubmitError('');
    if (Object.keys(invalid).length) { requestAnimationFrame(() => formRef.current?.querySelector('[aria-invalid="true"]')?.focus()); return; }
    lock.current = true; setBusy(true); setSuccess('');
    try {
      const urls = [];
      for (const file of images) { try { urls.push((await customerService.uploadAfterSaleImage(file)).url); } catch (err) { throw new Error(`${file.name}: ${errorMessage(err)}`); } }
      const {maDonHang, maPhienBan, soLuong, lyDo, ...chiTiet} = form;
      if (loai === 'DOI_HANG' && same) chiTiet.targetId = maPhienBan;
      if (isReturn && chiTiet.reason === 'Máy lỗi') delete chiTiet.condition;
      if (loai === 'TRA_HANG_HOAN_TIEN' && chiTiet.refund !== 'Chuyển khoản') { delete chiTiet.bank; delete chiTiet.account; delete chiTiet.holder; }
      const payload = {maDonHang: maDonHang || null, maPhienBan: loai === 'KHIEU_NAI' ? null : maPhienBan, lyDo: lyDo.trim(), chiTiet, anhMinhChung: urls};
      const saved = isReturn ? await customerService.requestReturn({...payload, loaiYeuCau: loai, soLuong:Number(soLuong)}) : await customerService.createAfterSale({...payload, loai});
      setReceipt(saved); setRecent(old => [saved, ...old]); setLocLoai(''); setLocStatus(''); setSuccess(`Đã gửi yêu cầu ${saved.maYeuCau}. Dự kiến tiếp nhận trong 1–2 ngày làm việc.`); toast(`Đã gửi yêu cầu ${saved.maYeuCau}`);
      resetValidation(); setBankQuery(''); setBankOpen(false); setLoai(''); setForm({...EMPTY}); setTargetQuery(''); setTargetOpen(false); setImages([]); setImageErrors([]); reload();
    } catch (err) { toast(err.response ? errorMessage(err) : err.message || errorMessage(err), 'error'); setSubmitError(err.response ? errorMessage(err) : err.message || errorMessage(err)); }
    finally { lock.current = false; setBusy(false); }
  }
  return <div className="checkout after-sales-page">
    <h2>Đổi trả &amp; bảo hành</h2><p className="muted">Gửi và theo dõi yêu cầu hỗ trợ sau mua hàng.</p>
    <section className="card after-sales-section">
      <div className="row between after-sales-heading"><h3>Tạo yêu cầu mới</h3></div>
      <div className="as-support"><a href="tel:18006868">Hotline 1800 6868</a></div>
      {success && <div className="as-success" role="status">{success}{receipt && <div className="as-receipt-actions"><button type="button" className="btn" onClick={() => downloadReceipt(receipt)}>Tải phiếu tiếp nhận</button><button type="button" className="btn" onClick={() => {if (!printReceipt(receipt)) toast('Vui lòng cho phép cửa sổ in.', 'error');}}>In / lưu PDF</button></div>}</div>}
      <details key={loai || "initial"} className="as-policy-light"><summary>Xem chính sách hỗ trợ sau mua</summary><ul>{policyLines(loai,policy).map(line => <li key={line}>{line}</li>)}</ul></details>
      {orderError && <p className="as-error" role="alert">Không tải được đơn hàng: {orderError}</p>}
      <form ref={formRef} className="form" onSubmit={submit} noValidate aria-busy={busy}>
        <fieldset disabled={busy} className="as-fields">
          <Field name="loai" label="Loại yêu cầu" error={errors.loai}><select {...attrs('loai')} required value={loai} onChange={e => changeType(e.target.value)}><option value="" disabled>Chọn loại yêu cầu</option>{Object.entries(TYPES).map(([key,v]) => <option key={key} value={key}>{v}</option>)}</select></Field>
          {loai && <>
            <Field name="maDonHang" label="Đơn hàng" required={loai !== 'KHIEU_NAI'} error={errors.maDonHang}>
              {(loai === 'KHIEU_NAI' || validOrders.length !== 1) && <button type="button" {...attrs('maDonHang')} className="as-order-toggle btn" aria-expanded={orderPicker} aria-controls="as-order-options" onClick={() => setOrderPicker(v => !v)}>{order ? `${order.chiTiet?.[0]?.tenMay || 'Đơn hàng'} · Đơn #${order.maDonHang.slice(-8)}` : 'Chọn đơn hàng'} <span aria-hidden="true">▾</span></button>}
              {orderPicker && <div id="as-order-options" className="as-order-options" role="group" aria-label="Danh sách đơn hàng">
                {loai === 'KHIEU_NAI' && <button type="button" className="as-order-choice" onClick={() => {change('maDonHang','');setOrderPicker(false);}}>Không liên quan đơn hàng cụ thể</button>}
                {validOrders.map(o => <button type="button" key={o.maDonHang} className={`as-order-choice ${o.maDonHang === form.maDonHang ? 'selected' : ''}`} aria-pressed={o.maDonHang === form.maDonHang} onClick={() => {change('maDonHang',o.maDonHang);setOrderPicker(false);requestAnimationFrame(() => document.getElementById('as-maDonHang')?.focus());}}><strong>{o.chiTiet?.[0]?.tenMay || 'Sản phẩm'}{o.chiTiet?.length > 1 ? ` +${o.chiTiet.length-1}` : ''}</strong><span className="muted">Đơn #{o.maDonHang.slice(-8)} · {formatDay(o.ngayMua)} · {formatPrice(o.tongTien)}</span>{loai !== 'KHIEU_NAI' && <span className={`as-deadline-badge ${deadlineTone(o,loai)}`}>{remainingLabel(o,loai)}</span>}</button>)}
              </div>}
            </Field>
            {orderLoading && <p className="muted">Đang tải đơn hàng…</p>}
            {!orderLoading && !orderError && !validOrders.length && loai !== 'KHIEU_NAI' && <p className="as-notice" role="status">{loai === 'BAO_HANH' ? `Bạn chưa có đơn nào đủ điều kiện bảo hành${policy?.warrantyMonths ? ` trong ${policy.warrantyMonths} tháng` : ''}.` : `Bạn chưa có đơn nào đủ điều kiện đổi trả${policy?.returnDays ? ` trong ${policy.returnDays} ngày` : ''}.`}</p>}
            {order && <div className="after-sales-order-preview as-selected-order">
              <b>{order.chiTiet?.[0]?.tenMay || 'Đơn hàng'}{order.chiTiet?.length > 1 ? ` +${order.chiTiet.length-1} sản phẩm` : ''}</b>
              <div className="as-order-meta"><span className="as-order-code">Đơn #{order.maDonHang.slice(-8)}<button type="button" className="as-copy-icon" title="Sao chép mã đơn đầy đủ" aria-label="Sao chép mã đơn đầy đủ" onClick={() => copyOrder(order.maDonHang)}><svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg></button></span><span>Mua {formatDay(order.ngayMua)} · {formatPrice(order.tongTien)}</span></div>
              {loai !== 'KHIEU_NAI' && <><span className={`as-deadline-badge ${deadlineTone(order,loai)}`}>{remainingLabel(order,loai)}</span><small className="as-order-dates">{order.ngayKhachXacNhan ? 'Nhận' : 'Giao'} {formatDay(order.ngayKhachXacNhan || order.ngayGiao)} · {loai === 'BAO_HANH' ? 'Bảo hành đến' : 'Đổi trả đến'} {formatDay(loai === 'BAO_HANH' ? warrantyEnd(order) : returnEnd(order))}</small></>}
              <Link target="_blank" rel="noopener noreferrer" to={`/orders/${encodeURIComponent(order.maDonHang)}`}>Xem đơn hàng ↗</Link>
            </div>}
            {loai !== 'KHIEU_NAI' && pending.length > 0 && <div className="as-pending"><div className="as-pending-head"><span>⚠ Có {pending.length} đơn chưa xác nhận nhận hàng. Cần xác nhận trước khi đổi trả/bảo hành.</span><Link target="_blank" rel="noopener noreferrer" to={pending.length === 1 ? `/orders/${encodeURIComponent(pending[0].maDonHang)}?confirmReceive=1` : '/orders?tt=CHO_KHACH_XAC_NHAN'}>Xem và xác nhận ↗</Link></div></div>}
            {loai !== 'KHIEU_NAI' && <Field name="maPhienBan" label="Sản phẩm cần xử lý" error={errors.maPhienBan}>{order?.chiTiet?.length === 1 ? <div id="as-maPhienBan" className="as-product-fixed" tabIndex={0} aria-invalid={!!errors.maPhienBan}><strong>{order.chiTiet[0].tenMay}</strong><small>{formatPrice(order.chiTiet[0].donGia)} / máy · Số lượng đã mua: {order.chiTiet[0].soLuong}</small></div> : <select {...attrs('maPhienBan')} required value={form.maPhienBan} onChange={e => change('maPhienBan',e.target.value)}><option value="" disabled>Chọn sản phẩm</option>{(order?.chiTiet || []).map(d => <option key={d.maPhienBan} value={d.maPhienBan}>{d.tenMay} · Mã phiên bản: {d.maPhienBan} · SL {d.soLuong}</option>)}</select>}</Field>}
            {isReturn && product && product.soLuong > 1 && <Field name="soLuong" label="Số lượng" error={errors.soLuong}><div className="as-stepper"><button type="button" className="btn" disabled={Number(form.soLuong)<=1} aria-label="Giảm số lượng" onClick={() => change('soLuong',Math.max(1,Number(form.soLuong)-1))}>−</button><output {...attrs('soLuong')} tabIndex={0} aria-live="polite">{form.soLuong} / {product.soLuong}</output><button type="button" className="btn" disabled={Number(form.soLuong)>=product.soLuong} aria-label="Tăng số lượng" onClick={() => change('soLuong',Math.min(product.soLuong,Number(form.soLuong)+1))}>+</button></div></Field>}
            {(isReturn || loai === 'BAO_HANH') && <h4 className="as-group-title">Thông tin sự cố</h4>}
            {isReturn && cards('reason','Lý do',loai === 'DOI_HANG' ? REASONS.filter(r => sameExchange(r) || upgradeExchange(r)) : REASONS)}
            {isReturn && form.reason === 'Đổi ý' && <p className="as-warning">Đổi ý: máy phải còn nguyên seal, đầy đủ phụ kiện; có thể phát sinh phí sau thẩm định.</p>}
            {isReturn && form.reason && form.reason !== 'Máy lỗi' && (allowed.length === 1 ? <Field name="condition" label="Tình trạng máy" error={errors.condition}><div id="as-condition" tabIndex={0} aria-invalid={!!errors.condition} aria-describedby={errors.condition ? 'as-condition-error' : undefined} className="as-fixed-condition"><span aria-hidden="true">✓</span><strong>{allowed[0]}</strong><small>Theo chính sách đổi ý.</small></div></Field> : cards('condition','Tình trạng máy',allowed))}
            {isReturn && form.reason === 'Đổi ý' && <div className="as-field"><label className="as-seal-confirm"><input {...attrs('sealConfirmed')} type="checkbox" required checked={form.sealConfirmed === 'true'} onChange={e => change('sealConfirmed',e.target.checked ? 'true' : '')}/><span>Tôi xác nhận tất cả máy yêu cầu xử lý chưa bóc seal, đầy đủ phụ kiện. <span className="as-required">*</span></span></label>{errors.sealConfirmed && <small className="as-error" id="as-sealConfirmed-error" role="alert">{errors.sealConfirmed}</small>}</div>}
            {isReturn && form.reason === 'Giao sai hàng' && <small className="muted">Ưu tiên còn nguyên seal; nếu đã mở máy để kiểm tra, chọn Đã kích hoạt. Nếu máy bị hỏng, chọn lý do Máy lỗi.</small>}
            {isReturn && form.reason === 'Không đúng mô tả' && <>{cards('mismatch','Điểm khác với mô tả',MISMATCHES)}<small className="muted">Chọn điểm khác chính; nêu thêm các điểm khác trong mô tả chi tiết.</small></>}
            {isReturn && form.reason === 'Máy lỗi' && faultPicker()}
            {loai === 'DOI_HANG' && same && <Field name="targetId" label="Đổi 1–1 cùng phiên bản" error={errors.targetId}><div id="as-targetId" className="as-product-fixed" tabIndex={0}><strong>{product?.tenMay || 'Chọn sản phẩm đã mua'}</strong><small>{target ? `${target.label} · Còn ${target.stock} máy · Không chênh lệch` : catalogLoading ? 'Đang tải tồn kho…' : 'Chưa đủ hàng để đổi đúng phiên bản. Có thể chọn Trả hàng / hoàn tiền.'}</small></div>{catalogError && <button type="button" className="btn" onClick={() => setCatalogRetry(n=>n+1)}>Thử tải lại tồn kho</button>}</Field>}
            {loai === 'DOI_HANG' && upgradeExchange(form.reason) && <Field name="targetId" label="Sản phẩm/phiên bản muốn đổi" error={errors.targetId}>
              <div onBlur={e => {if (!e.currentTarget.contains(e.relatedTarget)) setTargetOpen(false);}}>
              <input {...attrs('targetId')} value={targetQuery} placeholder="Tìm tên máy, dung lượng hoặc màu sắc" onFocus={() => setTargetOpen(true)} onChange={e => {setTargetQuery(e.target.value);change('targetId','');setTargetOpen(true);}} />
              {catalogLoading && <small>Đang tải danh mục…</small>}{catalogError && <p role="alert" className="as-error">Không tải được danh mục. <button type="button" className="btn" onClick={() => setCatalogRetry(n => n+1)}>Thử lại</button></p>}
              {targetOpen && !catalogLoading && !catalogError && <div className="as-order-options" role="group" aria-label="Phiên bản còn hàng">{targetMatches.length ? targetMatches.map(v => <button type="button" key={v.id} className={`as-order-choice ${target?.id === v.id ? 'selected' : ''}`} onClick={() => {change('targetId',v.id);setTargetQuery(v.label);setTargetOpen(false);}}><strong>{v.label}</strong><span>{formatPrice(v.price)} · Còn {v.stock} máy</span></button>) : <small>Không có phiên bản phù hợp hoặc đủ số lượng.</small>}</div>}
              {target && quote && <div className="as-selected-order after-sales-order-preview"><strong>{target.label}</strong><span>{formatPrice(target.price)} / máy</span><small>Giá trị thực mua: {formatPrice(quote.net)} · Cần bù: {formatPrice(quote.difference)}. Chỉ thu sau khi cửa hàng kiểm tra và duyệt; chưa giữ hàng khi gửi yêu cầu.</small></div>}
              {product && estimate && <small className="muted">Chỉ hiện máy còn hàng có tổng giá từ {formatPrice(estimate.amount)} trở lên (cho số lượng đã chọn).</small>}
              </div>
            </Field>}
            {loai === 'BAO_HANH' && <>
              <Field name="appearance" label="Tình trạng ngoại hình" error={errors.appearance}><div className="as-chips" role="group" aria-label="Tình trạng ngoại hình">{APPEARANCES.map((item,i) => <button key={item} type="button" {...(i === 0 ? attrs('appearance') : {})} className="btn" aria-pressed={form.appearance === item} onClick={() => change('appearance',item)}>{item}</button>)}</div></Field>
              {warrantyAppearanceWarning(form.appearance) && <p className="as-warning" role="status">{warrantyAppearanceWarning(form.appearance)}</p>}
              {faultPicker()}
              <p className="as-warranty-note">Cửa hàng sẽ kiểm tra lỗi và quyết định sửa chữa, thay linh kiện hoặc đổi máy tương đương nếu không thể sửa. Hoàn tiền được xử lý qua yêu cầu trả hàng.</p>
            </>}
            {loai === 'KHIEU_NAI' && select('topic','Chủ đề',TOPICS)}
            {(isReturn || loai === 'BAO_HANH') && <h4 className="as-group-title">Bằng chứng</h4>}
            <Field name="lyDo" label="Mô tả chi tiết"><textarea {...attrs('lyDo')} rows="5" required maxLength={1000} placeholder={isReturn || loai === 'BAO_HANH' ? descriptionHint(loai,form.reason) : HINTS[loai]} value={form.lyDo} onChange={e => change('lyDo',e.target.value)} /><div className={`as-count ${!errors.lyDo && form.lyDo.trim().length > 0 ? 'as-count-valid' : 'muted'}`}><span id="as-lyDo-hint" className={errors.lyDo ? 'as-error' : undefined} role={errors.lyDo ? 'alert' : undefined}>{errors.lyDo || (form.reason === 'Khác' ? 'Nêu rõ lý do khác.' : 'Mô tả ngắn gọn vấn đề.')}</span><span>{form.lyDo.length}/1000</span></div></Field>
            <Field name="images" label="Ảnh minh chứng" required={requiredProof} error={errors.images}>
              <small className={`as-proof-hint ${requiredProof ? 'as-proof-required' : 'muted'}`}>{evidenceHint(loai,form.reason,form.condition)}</small>
              <div className="as-drop" onDragOver={e => e.preventDefault()} onDrop={e => {e.preventDefault(); if (!busy) addFiles(e.dataTransfer.files);}}>
                <input {...attrs('images')} type="file" aria-required={requiredProof} multiple accept="image/jpeg,image/png,image/webp" className="as-file" onChange={e => {addFiles(e.target.files);e.target.value='';}} />
                <label className="btn as-pick" htmlFor="as-images">Chọn ảnh</label><p>Kéo thả ảnh vào đây.</p><small className="muted">JPG, PNG, WEBP · tối đa 5 MB/ảnh · {images.length}/{MAX_IMAGES} ảnh</small>
              </div>
              {imageErrors.map((msg,i) => <small role="alert" className="as-error" key={`${i}-${msg}`}>{msg}</small>)}
              <div className="as-thumbs">{images.map((file,i) => <Preview key={`${file.name}-${file.lastModified}-${file.size}`} file={file} remove={() => setImages(old => old.filter((_,j) => i !== j))} />)}</div>
            </Field>
            {loai === 'TRA_HANG_HOAN_TIEN' && <>
              <h4 className="as-group-title">Hoàn tiền</h4>
              {order?.phuongThucTt === 'COD' ? <div className="as-refund-fixed" id="as-refund" tabIndex={0} aria-invalid={!!errors.refund}><strong>Hoàn tiền qua chuyển khoản</strong><small>Đơn của bạn thanh toán COD nên sẽ được hoàn tiền qua chuyển khoản.</small>{errors.refund && <small className="as-error" role="alert">{errors.refund}</small>}</div> : order?.phuongThucTt === 'CHUYEN_KHOAN' ? <div className="as-refund-fixed" id="as-refund" tabIndex={0} aria-invalid={!!errors.refund}><strong>Hoàn về giao dịch VNPay gốc</strong><small>Không cần nhập tài khoản ngân hàng. Cửa hàng sẽ gửi yêu cầu hoàn về giao dịch đã thanh toán.</small>{errors.refund && <small className="as-error" role="alert">{errors.refund}</small>}</div> : order?.phuongThucTt === 'VI_DIEN_TU' ? cards('refund','Hình thức hoàn tiền',refundOptions(order)) : <p className="muted">Chọn đơn có thông tin thanh toán để xác định hình thức hoàn tiền.</p>}
              {refundOptions(order).includes('Chuyển khoản') && form.refund === 'Chuyển khoản' && <div className="as-fields as-subfields">
                <p className="as-bank-notice">🔒 Chỉ nhập thông tin nhận tiền, không nhập mật khẩu hoặc mã OTP.</p>
                <Field name="holder" label="Chủ tài khoản" error={errors.holder}><input {...attrs('holder')} required value={form.holder} maxLength={100} autoComplete="off" placeholder="VD: NGUYEN VAN AN" onChange={e => change('holder',normalizeHolder(e.target.value))}/><small className="muted">Nhập đúng họ tên trên tài khoản ngân hàng (in hoa, không dấu). Nên dùng tài khoản của người đặt hàng.</small>{order?.tenKhach && form.holder.trim() && normalizeHolder(order.tenKhach).trim().replace(/\s+/g,' ') !== form.holder.trim().replace(/\s+/g,' ') && <small className="muted">Tên chủ tài khoản khác tên người đặt hàng. Vui lòng kiểm tra lại; cửa hàng có thể yêu cầu đối soát.</small>}</Field>
                <Field name="account" label="Số tài khoản" error={errors.account}><input {...attrs('account')} type="text" required inputMode="numeric" pattern="[0-9]{6,20}" autoComplete="off" maxLength={20} value={form.account} placeholder="Nhập 6–20 chữ số" onChange={e => change('account',accountDigits(e.target.value))}/></Field>
                <Field name="bank" label="Ngân hàng" error={errors.bank}>
                  <div onBlur={e => {if (!e.currentTarget.contains(e.relatedTarget)) setBankOpen(false);}}>
                    <input {...attrs('bank')} autoComplete="off" placeholder="Tìm và chọn ngân hàng" value={bankOpen ? bankQuery : form.bank} aria-expanded={bankOpen} aria-controls="as-bank-options" onFocus={() => {setBankQuery(form.bank);setBankOpen(true);}} onChange={e => {setBankQuery(e.target.value);change('bank','');setBankOpen(true);}} onKeyDown={e => {if(e.key==='Escape') {setBankOpen(false);}}}/>
                    {bankOpen && <div id="as-bank-options" className="as-order-options as-bank-options" role="group" aria-label="Danh sách ngân hàng">{bankMatches.length ? bankMatches.map(bank => <button key={bank} type="button" className={`as-order-choice ${form.bank===bank?'selected':''}`} onClick={() => {change('bank',bank);setBankQuery(bank);setBankOpen(false);}}>{bank}</button>) : <small>Không tìm thấy ngân hàng. Hãy liên hệ hỗ trợ nếu ngân hàng của bạn chưa có trong danh sách.</small>}</div>}
                  </div>
                </Field>
              </div>}
              <p className="muted">Dự kiến hoàn tiền trong 3–7 ngày làm việc sau khi cửa hàng nhận và kiểm tra máy.</p>
              {product && <section className="as-refund-summary" aria-label="Tóm tắt yêu cầu hoàn tiền"><h4>Kiểm tra trước khi gửi</h4><dl><dt>Sản phẩm</dt><dd>{product.tenMay} · SL {form.soLuong}</dd><dt>Lý do</dt><dd>{form.reason || 'Chưa chọn'}</dd><dt>Hình thức hoàn tiền</dt><dd>{form.refund || 'Chưa chọn'}</dd><dt>Tiền hoàn dự kiến</dt><dd className="as-refund-total">{estimate ? formatPrice(estimate.amount) : 'Cửa hàng xác nhận sau'}</dd>{estimate && <><dt>Ưu đãi đã phân bổ</dt><dd>{formatPrice(estimate.discount)}</dd></>}<dt>Thời gian dự kiến</dt><dd>3–7 ngày làm việc sau thẩm định</dd></dl><small className="muted">Ước tính theo giá đã mua sau khi phân bổ ưu đãi hàng hóa; không gồm phí vận chuyển. Số tiền cuối cùng phụ thuộc kết quả kiểm tra và tỷ lệ hoàn được duyệt.</small></section>}
            </>}
          </>}
          {loai === 'BAO_HANH' && product && <section className="as-refund-summary as-warranty-summary" aria-label="Tóm tắt yêu cầu bảo hành"><h4>Kiểm tra trước khi gửi</h4><dl><dt>Sản phẩm</dt><dd>{product.tenMay}</dd><dt>Hạn bảo hành</dt><dd>{formatDay(warrantyEnd(order))}</dd><dt>Ngoại hình</dt><dd>{form.appearance || 'Chưa chọn'}</dd><dt>Loại lỗi</dt><dd>{selectedFaults(form.fault).join(', ') || 'Chưa chọn'}</dd><dt>Ảnh minh chứng</dt><dd>{images.length} / {MAX_IMAGES}</dd><dt>Dự kiến tiếp nhận</dt><dd>1–2 ngày làm việc</dd></dl><small className="muted">Phương án xử lý và chi phí (nếu có) sẽ được cửa hàng thông báo sau khi kiểm tra máy. Gửi yêu cầu không đồng nghĩa đã được duyệt bảo hành miễn phí.</small></section>}
          {errors.submit && <p className="as-error" role="alert">{errors.submit}</p>}
          <button className="btn btn-primary" type="submit" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu'}</button>
        </fieldset>
      </form>
    </section>
    <section className="after-sales-section">
      <div className="row between after-sales-heading"><h3>Yêu cầu của tôi</h3><div className="as-filters"><select aria-label="Lọc loại yêu cầu" value={locLoai} onChange={e => setLocLoai(e.target.value)}><option value="">Tất cả loại</option>{Object.entries(TYPES).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select><select aria-label="Lọc trạng thái" value={locStatus} onChange={e => setLocStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(STATUS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></div></div>
      {loading && <p className="muted">Đang tải yêu cầu…</p>}{error && <div className="as-error" role="alert">Không tải được yêu cầu: {error} <button className="btn" onClick={reload}>Thử lại</button></div>}
      {!loading && !error && filtered.length === 0 && <p className="muted">{allRequests.length ? 'Không có kết quả theo bộ lọc.' : 'Bạn chưa có yêu cầu nào'}</p>}
      {filtered.map(r => <article key={r.maYeuCau} id={r.maYeuCau} className="card after-sales-request"><div className="row between after-sales-heading"><div><b>{TYPES[requestType(r)]}</b> · <span className="muted">{r.maYeuCau}</span></div><RequestStatus request={r}/></div>
        <p>Đơn hàng: {r.maDonHang ? <Link to={`/orders/${encodeURIComponent(r.maDonHang)}`}>{r.maDonHang}</Link> : 'Không gắn đơn hàng'}</p><p>Sản phẩm: {r.tenSanPham || 'Không chỉ định'}{r.soLuong ? ` · SL: ${r.soLuong}` : ''}</p><p style={{whiteSpace:'pre-wrap'}}>{maskAccount(r.lyDo)}</p>
        {r.exchange && <div className="as-description"><b>Giá trị đổi máy:</b> {formatPrice(r.exchange.oldNet)}<p>{Number(r.exchange.difference) === 0 ? 'Không chênh lệch' : `Cần bù ${formatPrice(r.exchange.difference)} · ${r.exchange.paidAt ? 'Đã ghi nhận thu tiền' : ['DA_DUYET','DANG_XU_LY'].includes(r.trangThai) ? 'Đã duyệt, liên hệ cửa hàng để thanh toán chênh lệch' : 'Chưa thu tiền khi gửi yêu cầu'}`}</p></div>}
        {r.soTienHoanDuKien != null && <p><b>{r.ngayHoanTien ? 'Đã ghi nhận hoàn tiền' : 'Tiền hoàn dự kiến'}:</b> {formatPrice(r.soTienHoanDuKien)}</p>}
        <div className="as-thumbs">{requestImages(r).map((url,i) => <a key={url} href={url} target="_blank" rel="noreferrer"><img className="as-evidence" src={url} alt={`Minh chứng ${i+1}`} /></a>)}</div>
        <p className="muted">Gửi lúc: {formatDate(r.ngayTao)}{r.ngayCapNhat && ` · Cập nhật: ${formatDate(r.ngayCapNhat)}`}</p>{r.ghiChuXuLy && <p><b>Phản hồi:</b> {maskAccount(r.ghiChuXuLy)}</p>}{r.ketQuaXuLy && <p className="after-sales-outcome"><b>Kết quả xử lý:</b> {maskAccount(r.ketQuaXuLy)}</p>}<div className="as-receipt-actions"><button type="button" className="btn" onClick={() => downloadReceipt(r)}>Tải phiếu tiếp nhận</button><button type="button" className="btn" onClick={() => {if (!printReceipt(r)) toast('Vui lòng cho phép cửa sổ in.', 'error');}}>In / lưu PDF</button></div>{r.lichSu?.length > 0 && <details open><summary>Tiến trình xử lý ({r.lichSu.length})</summary><ol className="after-sales-timeline">{r.lichSu.map((item,i) => <li key={i}><RequestStatus request={r} status={item.trangThai}/> <span className="muted">{formatDate(item.thoiGian)}</span>{item.ghiChu && <div>{maskAccount(item.ghiChu)}</div>}</li>)}</ol></details>}
      </article>)}
    </section>
  </div>;
}
