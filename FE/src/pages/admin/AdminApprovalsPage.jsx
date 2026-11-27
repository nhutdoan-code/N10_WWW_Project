import { useEffect, useMemo, useState } from 'react';
import Modal from '../../components/Modal';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { productService } from '../../service/productService';
import { errorMessage, formatDate, formatPrice } from '../../utils/format';
import { APPROVAL_LABEL, LY_DO_KHOA, approvalView } from '../../utils/approvalUi';

/*
 * Man hinh phe duyet ban hang bien the (Giai doan 2B.7C).
 * - Moi thao tac chi ap dung cho MOT bien the, co hop thoai xac nhan rieng; khong co duyet hang loat.
 * - Du lieu trong hop thoai luon tai moi tu Backend; gui kem maKiemTra de Backend tu choi (409)
 *   neu bien the da doi. Khi 409: hien canh bao, yeu cau tai lai, KHONG tu gui lai.
 */

const HANH_DONG = {
  APPROVE_VARIANT: 'Phê duyệt',
  REVOKE_VARIANT_APPROVAL: 'Thu hồi phê duyệt',
  BLOCK_VARIANT: 'Khóa bán',
  UNBLOCK_VARIANT: 'Mở khóa',
  INVALIDATE_VARIANT_APPROVAL: 'Tự động hủy phê duyệt (dữ liệu đổi)',
};

const MIN_TEXT = 10;

const money = (v) => (v === null || v === undefined ? '—' : formatPrice(v));
const kinhDoanh = (v) => v.trangThai === 'DANG_KINH_DOANH' && v.trangThaiSanPham === 'DANG_KINH_DOANH';

function ApprovalBadge({ v }) {
  const view = approvalView(v);
  return <span className={`badge ${view.cls}`}>{view.label}</span>;
}

export default function AdminApprovalsPage() {
  const toast = useToast();
  const [q, setQ] = useState('');
  const [trangThaiPheDuyet, setTrangThaiPheDuyet] = useState('');
  const [trangThai, setTrangThai] = useState('');
  const [maThuongHieu, setMaThuongHieu] = useState('');
  const [chiKhongDuocBan, setChiKhongDuocBan] = useState(false);
  const [statFilter, setStatFilter] = useState('');
  const [selected, setSelected] = useState(null);
  const debounced = useDebounce(q);

  const params = {
    q: debounced || undefined,
    trangThaiPheDuyet: trangThaiPheDuyet || undefined,
    trangThai: trangThai || undefined,
    maThuongHieu: maThuongHieu || undefined,
    chiKhongDuocBan: chiKhongDuocBan || undefined,
  };
  const { data, loading, error, reload } = useFetch(
    () => adminService.variants(params),
    [debounced, trangThaiPheDuyet, trangThai, maThuongHieu, chiKhongDuocBan],
  );
  const { data: brands } = useFetch(productService.brands);
  const rows = useMemo(() => data || [], [data]);
  const visibleRows = useMemo(() => {
    if (statFilter === 'allowed') return rows.filter((v) => v.duocPhepBan);
    if (statFilter === 'mismatch') return rows.filter((v) => v.canhBaoPheDuyetLech);
    return rows;
  }, [rows, statFilter]);

  const filterFromStat = (key) => {
    if (key === 'pending' || key === 'blocked') {
      const status = key === 'pending' ? 'PENDING_REVIEW' : 'BLOCKED';
      setStatFilter('');
      setTrangThaiPheDuyet((current) => current === status ? '' : status);
      setChiKhongDuocBan(false);
      return;
    }
    const nextFilter = statFilter === key ? '' : key;
    setStatFilter(nextFilter);
    setTrangThaiPheDuyet(nextFilter ? 'APPROVED' : '');
    setChiKhongDuocBan(false);
  };

  const stats = useMemo(() => rows.reduce((s, v) => {
    if (v.duocPhepBan) s.duocBan += 1;
    if (v.trangThaiPheDuyet === 'PENDING_REVIEW') s.cho += 1;
    if (v.trangThaiPheDuyet === 'BLOCKED') s.khoa += 1;
    if (v.canhBaoPheDuyetLech) s.lech += 1;
    return s;
  }, { duocBan: 0, cho: 0, khoa: 0, lech: 0 }), [rows]);
  const hasFilters = Boolean(q.trim() || trangThaiPheDuyet || trangThai || maThuongHieu || chiKhongDuocBan || statFilter);
  const clearFilters = () => {
    setQ('');
    setTrangThaiPheDuyet('');
    setTrangThai('');
    setMaThuongHieu('');
    setChiKhongDuocBan(false);
    setStatFilter('');
  };

  return (
    <>
      <div>
        <h2>Phê duyệt bán hàng</h2>
        <p className="muted">
          Mỗi biến thể chỉ được bán khi đang kinh doanh, đã phê duyệt và dữ liệu (giá, giá niêm yết, RAM, ROM, màu)
          còn khớp với dữ liệu đã phê duyệt. Mọi thao tác được ghi nhật ký.
        </p>
      </div>
      <div className="stats-grid" data-testid="approval-stats">
        {[
          ['allowed', 'Được phép bán', stats.duocBan, statFilter === 'allowed'],
          ['pending', 'Chờ phê duyệt', stats.cho, trangThaiPheDuyet === 'PENDING_REVIEW'],
          ['blocked', 'Đang khóa bán', stats.khoa, trangThaiPheDuyet === 'BLOCKED'],
          ['mismatch', 'Đã duyệt nhưng dữ liệu đổi', stats.lech, statFilter === 'mismatch'],
        ].map(([key, label, count, active]) => (
          <button
            key={key}
            type="button"
            className={`stat-card stat-card-button${active ? ' active' : ''}`}
            aria-pressed={active}
            onClick={() => filterFromStat(key)}
          >
            <span>{label}</span><b>{count}</b>
          </button>
        ))}
      </div>

      <div className="approval-filters">
        <input className="input" aria-label="Tìm kiếm" placeholder="Tên máy, mã sản phẩm, mã biến thể hoặc SKU"
          value={q} onChange={(e) => setQ(e.target.value)} />
        <select className="input" aria-label="Trạng thái phê duyệt" value={trangThaiPheDuyet}
          onChange={(e) => { setTrangThaiPheDuyet(e.target.value); setStatFilter(''); }}>
          <option value="">Mọi trạng thái phê duyệt</option>
          <option value="APPROVED">Đã phê duyệt</option>
          <option value="PENDING_REVIEW">Chờ phê duyệt</option>
          <option value="BLOCKED">Đang khóa bán</option>
        </select>
        <select className="input" aria-label="Trạng thái kinh doanh" value={trangThai} onChange={(e) => setTrangThai(e.target.value)}>
          <option value="">Mọi trạng thái kinh doanh</option>
          <option value="DANG_KINH_DOANH">Đang kinh doanh</option>
          <option value="NGUNG_KINH_DOANH">Ngừng kinh doanh</option>
        </select>
        <select className="input" aria-label="Thương hiệu" value={maThuongHieu} onChange={(e) => setMaThuongHieu(e.target.value)}>
          <option value="">Mọi thương hiệu</option>
          {(brands || []).map((b) => <option key={b.maThuongHieu} value={b.maThuongHieu}>{b.tenThuongHieu}</option>)}
        </select>
        <label className="approval-check">
          <input type="checkbox" checked={chiKhongDuocBan} onChange={(e) => { setChiKhongDuocBan(e.target.checked); setStatFilter(''); }} />
          Chỉ biến thể không đủ điều kiện bán
        </label>
        {hasFilters && <button type="button" className="btn approval-clear-filters" onClick={clearFilters}>Xóa bộ lọc</button>}
      </div>

      <div className="table-scroll">
        <table className="table" data-testid="approval-table">
          <thead>
            <tr>
              <th>Sản phẩm</th><th>Mã / SKU</th><th>Cấu hình</th><th>Giá bán</th><th>Giá niêm yết</th>
              <th>Tồn</th><th>Kinh doanh</th><th>Phê duyệt</th><th>Bán được</th><th></th>
            </tr>
          </thead>
          <tbody>
            {visibleRows.map((v) => (
              <tr key={v.maPhienBan} data-ma={v.maPhienBan}>
                <td><b>{v.tenMay}</b><div className="muted">{v.tenThuongHieu}</div></td>
                <td>{v.maPhienBan}<div className="muted">{v.sku}</div></td>
                <td>{v.ram}GB / {v.boNho}GB · {v.mauSac}</td>
                <td>{money(v.giaBan)}</td>
                <td>{money(v.giaNiemYet)}</td>
                <td>{v.soLuongTon}</td>
                <td>{kinhDoanh(v) ? 'Đang kinh doanh' : 'Ngừng kinh doanh'}</td>
                <td>
                  <ApprovalBadge v={v} />
                  {v.lyDoKhoa && <div className="muted">{LY_DO_KHOA[v.lyDoKhoa] || v.lyDoKhoa}</div>}
                </td>
                <td>{v.duocPhepBan ? <span className="badge badge-APPROVED">Có</span> : <span className="badge badge-BLOCKED">Không</span>}</td>
                <td><button className="btn" onClick={() => setSelected(v.maPhienBan)}>Chi tiết</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {loading && <p className="muted">Đang tải…</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && visibleRows.length === 0 && <p className="muted">Không có biến thể phù hợp với bộ lọc.</p>}

      {selected && (
        <VariantApprovalModal
          maPhienBan={selected}
          toast={toast}
          onClose={() => setSelected(null)}
          onChanged={reload}
        />
      )}
    </>
  );
}

function VariantApprovalModal({ maPhienBan, toast, onClose, onChanged }) {
  const [detail, setDetail] = useState(null);
  const [history, setHistory] = useState([]);
  const [loadError, setLoadError] = useState('');
  const [mode, setMode] = useState(null); // approve | revoke | block | unblock
  const [conflict, setConflict] = useState('');

  const load = async () => {
    setLoadError('');
    try {
      const [d, h] = await Promise.all([
        adminService.variantApproval(maPhienBan),
        adminService.variantApprovalHistory(maPhienBan),
      ]);
      setDetail(d);
      setHistory(h || []);
    } catch (e) {
      setLoadError(errorMessage(e));
    }
  };

  useEffect(() => { load(); }, [maPhienBan]); // eslint-disable-line react-hooks/exhaustive-deps

  const reloadAfterConflict = async () => {
    setConflict('');
    await load();
  };

  const done = async (message) => {
    toast(message);
    setMode(null);
    await load();
    onChanged();
  };

  const d = detail;
  return (
    <Modal title={`Phê duyệt biến thể ${maPhienBan}`} onClose={onClose} wide>
      {loadError && <p className="error">{loadError}</p>}
      {!d && !loadError && <p className="muted">Đang tải dữ liệu mới nhất…</p>}
      {d && (
        <>
          <p><b>{d.tenMay}</b> · {d.tenThuongHieu} · SKU {d.sku}</p>
          <div className="approval-grid" data-testid="approval-current">
            <div><span>RAM</span><b>{d.ram} GB</b></div>
            <div><span>ROM</span><b>{d.boNho} GB</b></div>
            <div><span>Màu</span><b>{d.mauSac}</b></div>
            <div><span>Giá bán</span><b>{money(d.giaBan)}</b></div>
            <div><span>Giá niêm yết</span><b>{money(d.giaNiemYet)}</b></div>
            <div><span>Tồn kho</span><b>{d.soLuongTon}</b></div>
            <div><span>Kinh doanh</span><b>{kinhDoanh(d) ? 'Đang kinh doanh' : 'Ngừng kinh doanh'}</b></div>
            <div><span>Phê duyệt</span><b><ApprovalBadge v={d} /></b></div>
            <div><span>Được phép bán</span><b>{d.duocPhepBan ? 'Có' : 'Không'}</b></div>
          </div>
          {d.canhBaoPheDuyetLech && (
            <p className="approval-alert" role="alert">
              Trạng thái là APPROVED nhưng dữ liệu bán đã thay đổi sau khi duyệt (dấu vân tay không khớp).
              Biến thể này KHÔNG được bán. Thu hồi phê duyệt rồi phê duyệt lại với dữ liệu hiện tại.
            </p>
          )}
          <div className="approval-meta">
            {d.lyDoKhoa && <p>Lý do khóa: <b>{LY_DO_KHOA[d.lyDoKhoa] || d.lyDoKhoa}</b>{d.giaBanKhiKhoa != null && <> · giá khi khóa {money(d.giaBanKhiKhoa)}</>}</p>}
            {d.lyDoPheDuyet && <p>Lý do gần nhất: {d.lyDoPheDuyet}</p>}
            {d.nguonXacMinh && <p>Nguồn xác minh: {d.nguonXacMinh}</p>}
            {d.nguoiPheDuyet && <p className="muted">Bởi {d.nguoiPheDuyet} · {formatDate(d.ngayPheDuyet)}</p>}
          </div>

          {conflict && (
            <div className="approval-alert" role="alert" data-testid="approval-conflict">
              <p><b>Dữ liệu đã thay đổi.</b> {conflict}</p>
              <p>Yêu cầu chưa được thực hiện và sẽ không được gửi lại tự động.</p>
              <button className="btn btn-primary" onClick={reloadAfterConflict}>Tải lại dữ liệu mới nhất</button>
            </div>
          )}

          {!mode && !conflict && (
            <div className="row approval-actions">
              {d.trangThaiPheDuyet === 'PENDING_REVIEW' && kinhDoanh(d) && (
                <button className="btn btn-primary" onClick={() => setMode('approve')}>Phê duyệt…</button>
              )}
              {d.trangThaiPheDuyet === 'APPROVED' && (
                <button className="btn" onClick={() => setMode('revoke')}>Thu hồi phê duyệt…</button>
              )}
              {d.trangThaiPheDuyet !== 'BLOCKED' && (
                <button className="btn btn-danger" onClick={() => setMode('block')}>Khóa bán…</button>
              )}
              {d.trangThaiPheDuyet === 'BLOCKED' && (
                <button className="btn" onClick={() => setMode('unblock')}>Mở khóa…</button>
              )}
            </div>
          )}

          {mode && !conflict && (
            <ActionForm
              mode={mode}
              detail={d}
              onCancel={() => setMode(null)}
              onDone={done}
              onConflict={setConflict}
              toast={toast}
            />
          )}

          <h3 className="approval-history-title">Lịch sử phê duyệt</h3>
          <HistoryTable history={history} />
        </>
      )}
    </Modal>
  );
}

const TIEU_DE = {
  approve: 'Phê duyệt bán hàng',
  revoke: 'Thu hồi phê duyệt',
  block: 'Khóa bán',
  unblock: 'Mở khóa (chỉ chuyển về Chờ phê duyệt)',
};

function ActionForm({ mode, detail, onCancel, onDone, onConflict, toast }) {
  const [lyDo, setLyDo] = useState('');
  const [nguon, setNguon] = useState('');
  const [lyDoKhoa, setLyDoKhoa] = useState('');
  const [xacNhan, setXacNhan] = useState(false);
  const [sending, setSending] = useState(false);
  const [err, setErr] = useState('');

  const canNguon = mode === 'approve' || mode === 'unblock';
  const valid = lyDo.trim().length >= MIN_TEXT
    && (!canNguon || nguon.trim().length >= MIN_TEXT)
    && (mode !== 'block' || lyDoKhoa)
    && xacNhan;

  const submit = async (e) => {
    e.preventDefault();
    if (!valid || sending) return;
    setSending(true);
    setErr('');
    // Du lieu xac nhan = DUNG du lieu Admin dang nhin thay (khong doc lai ngam).
    const d = detail;
    try {
      if (mode === 'approve') {
        await adminService.updateVariantApproval(d.maPhienBan, {
          trangThaiPheDuyet: 'APPROVED', lyDo: lyDo.trim(), nguonXacMinh: nguon.trim(),
          giaPheDuyet: d.giaBan, giaNiemYetPheDuyet: d.giaNiemYet, ramPheDuyet: d.ram, boNhoPheDuyet: d.boNho,
          mauSacPheDuyet: d.mauSac, maKiemTra: d.maKiemTra,
        });
        await onDone('Đã phê duyệt biến thể');
      } else if (mode === 'revoke') {
        await adminService.updateVariantApproval(d.maPhienBan, {
          trangThaiPheDuyet: 'PENDING_REVIEW', lyDo: lyDo.trim(), maKiemTra: d.maKiemTra,
        });
        await onDone('Đã thu hồi phê duyệt');
      } else if (mode === 'block') {
        await adminService.updateVariantApproval(d.maPhienBan, {
          trangThaiPheDuyet: 'BLOCKED', lyDoKhoa, lyDo: lyDo.trim(), maKiemTra: d.maKiemTra,
        });
        await onDone('Đã khóa bán biến thể');
      } else {
        await adminService.unblockVariant(d.maPhienBan, {
          lyDo: lyDo.trim(), nguonXacMinh: nguon.trim(), giaBanHienTai: d.giaBan, giaNiemYetHienTai: d.giaNiemYet,
          ramHienTai: d.ram, boNhoHienTai: d.boNho, mauSacHienTai: d.mauSac, maKiemTra: d.maKiemTra,
        });
        await onDone('Đã mở khóa. Biến thể chuyển về Chờ phê duyệt.');
      }
    } catch (ex) {
      if (ex.response?.status === 409) {
        onConflict(errorMessage(ex));
      } else {
        setErr(errorMessage(ex));
        toast(errorMessage(ex), 'error');
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <form className="approval-form card" onSubmit={submit} data-testid={`approval-form-${mode}`}>
      <h3>{TIEU_DE[mode]}</h3>
      {mode === 'unblock' && (
        <p className="muted">
          Biến thể đang khóa vì <b>{LY_DO_KHOA[detail.lyDoKhoa] || 'lý do khác'}</b>.
          {detail.lyDoKhoa === 'GIA_SEED_CHUA_XAC_MINH' && ' Giá bán phải được cập nhật bằng giá đã xác minh (khác giá lúc khóa) trước khi mở khóa.'}
          {' '}Mở khóa KHÔNG mở bán: biến thể về Chờ phê duyệt và cần một lần phê duyệt riêng.
        </p>
      )}
      {mode === 'block' && (
        <label className="field">
          <span>Lý do khóa</span>
          <select className="input" value={lyDoKhoa} onChange={(e) => setLyDoKhoa(e.target.value)} required>
            <option value="">— Chọn lý do —</option>
            {Object.entries(LY_DO_KHOA).filter(([k]) => k !== 'NGUNG_KINH_DOANH').map(([k, label]) => (
              <option key={k} value={k}>{label}</option>
            ))}
          </select>
        </label>
      )}
      {canNguon && (
        <label className="field">
          <span>Nguồn / căn cứ xác minh (tối thiểu {MIN_TEXT} ký tự, mô tả nội dung đã đối chiếu)</span>
          <textarea className="input" rows={3} maxLength={500} value={nguon} onChange={(e) => setNguon(e.target.value)}
            placeholder="Ví dụ: Bảng giá nhà phân phối ngày 14/11/2026, ảnh chụp lưu tại …" />
        </label>
      )}
      <label className="field">
        <span>Lý do (tối thiểu {MIN_TEXT} ký tự)</span>
        <textarea className="input" rows={2} maxLength={255} value={lyDo} onChange={(e) => setLyDo(e.target.value)} />
      </label>
      <label className="approval-check">
        <input type="checkbox" checked={xacNhan} onChange={(e) => setXacNhan(e.target.checked)} />
        {mode === 'approve' && `Tôi xác nhận đã đối chiếu dữ liệu hiện tại: ${detail.ram}GB / ${detail.boNho}GB · ${detail.mauSac} · giá bán ${money(detail.giaBan)} · giá niêm yết ${money(detail.giaNiemYet)} với nguồn trên.`}
        {mode === 'unblock' && `Tôi xác nhận dữ liệu hiện tại (${detail.ram}GB / ${detail.boNho}GB · ${detail.mauSac} · ${money(detail.giaBan)}) đã được kiểm tra lại.`}
        {mode === 'revoke' && 'Tôi xác nhận thu hồi: biến thể sẽ tạm ngừng nhận đơn ngay.'}
        {mode === 'block' && 'Tôi xác nhận khóa bán: biến thể sẽ không thể đặt mua cho đến khi được mở khóa và duyệt lại.'}
      </label>
      {err && <p className="error">{err}</p>}
      <div className="row">
        <button type="submit" className={mode === 'block' ? 'btn btn-danger' : 'btn btn-primary'} disabled={!valid || sending}>
          {sending ? 'Đang gửi…' : 'Xác nhận'}
        </button>
        <button type="button" className="btn" onClick={onCancel} disabled={sending}>Hủy</button>
      </div>
    </form>
  );
}

const tt = (x) => APPROVAL_LABEL[x] || x || '—';

/** Du lieu ban tai thoi diem thao tac (khoi boiCanh do Backend ghi kem moi ban ghi). */
function boiCanh(h) {
  const b = h.sau?.boiCanh;
  if (!b) return '—';
  const gia = b.giaBan != null ? formatPrice(b.giaBan) : '—';
  const ny = b.giaNiemYet != null ? ` · NY ${formatPrice(b.giaNiemYet)}` : '';
  return `${b.ram}/${b.boNho}GB ${b.mauSac || ''} · ${gia}${ny} · tồn ${b.soLuongTon ?? '?'}`;
}

/** Thay doi du lieu ban (vd. tu dong huy do doi gia): hien gia/cau hinh cu -> moi. */
function thayDoi(h) {
  const t = h.truoc || {};
  const s = h.sau || {};
  const out = [];
  for (const [k, nhan] of [['giaBan', 'Giá'], ['giaNiemYet', 'Niêm yết'], ['ram', 'RAM'], ['boNho', 'ROM'], ['mauSac', 'Màu']]) {
    if (k in t && k in s && t[k] !== s[k]) {
      const f = (v) => (k.startsWith('gia') && v != null ? formatPrice(v) : v ?? '—');
      out.push(`${nhan}: ${f(t[k])} → ${f(s[k])}`);
    }
  }
  return out;
}

function HistoryTable({ history }) {
  if (!history.length) return <p className="muted">Chưa có thao tác phê duyệt nào.</p>;
  return (
    <div className="table-scroll">
      <table className="table" data-testid="approval-history">
        <thead>
          <tr>
            <th>Thời điểm</th><th>Người thực hiện</th><th>Hành động</th><th>Trạng thái</th>
            <th>Dữ liệu tại thời điểm thao tác</th><th>Lý do / nguồn</th>
          </tr>
        </thead>
        <tbody>
          {history.map((h) => (
            <tr key={h.maNhatKy}>
              <td>{formatDate(h.thoiGian)}</td>
              <td>{h.tenNguoiThucHien || h.maTk || '—'}</td>
              <td>{HANH_DONG[h.hanhDong] || h.hanhDong}</td>
              <td>{tt(h.truoc?.trangThaiPheDuyet)} → {tt(h.sau?.trangThaiPheDuyet ?? h.sau?.boiCanh?.trangThaiPheDuyet)}</td>
              <td>
                {boiCanh(h)}
                {thayDoi(h).map((x) => <div key={x} className="muted">{x}</div>)}
              </td>
              <td>
                {h.sau?.lyDoPheDuyet || '—'}
                {h.sau?.nguonXacMinh && <div className="muted">Nguồn: {h.sau.nguonXacMinh}</div>}
                {(h.sau?.lyDoKhoa || h.sau?.boiCanh?.lyDoKhoa) && (
                  <div className="muted">Khóa: {LY_DO_KHOA[h.sau?.lyDoKhoa || h.sau.boiCanh.lyDoKhoa] || h.sau?.lyDoKhoa}</div>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
