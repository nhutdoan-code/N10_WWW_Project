import { useEffect, useMemo, useState } from 'react';
import AdminPagination from '../../components/AdminPagination';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import MoneyInput from '../../components/MoneyInput';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatPrice } from '../../utils/format';
import { kiemTraVoucher, laTienMat, mucGiam, tinhGiamVoucher, trangThaiVoucher } from '../../utils/voucherAdmin';

const EMPTY = {
  maKm: '', tenKm: '', loai: 'DON_HANG', kieuGiam: 'SO_TIEN', phanTramGiam: '', soTienGiamToiDa: '',
  soTienGiamCoDinh: '', donToiThieu: '', soLuong: '', daDung: 0, chiKhachMoi: false, gioiHanMoiKhach: 1,
  ngayBatDau: '', ngayKetThuc: '', dieuKienApDung: '',
};

// Mẫu nhanh theo thông lệ bán lẻ điện thoại: tiền mặt theo bậc đơn hoặc % có trần.
const MAU_NHANH = [
  { ten: 'Tầm trung · 150K', v: { loai: 'DON_HANG', kieuGiam: 'SO_TIEN', soTienGiamCoDinh: '150000', donToiThieu: '3000000' } },
  { ten: 'Cận cao cấp · 400K', v: { loai: 'DON_HANG', kieuGiam: 'SO_TIEN', soTienGiamCoDinh: '400000', donToiThieu: '7000000' } },
  { ten: 'Flagship · 800K', v: { loai: 'DON_HANG', kieuGiam: 'SO_TIEN', soTienGiamCoDinh: '800000', donToiThieu: '15000000' } },
  { ten: '5% tối đa 300K', v: { loai: 'DON_HANG', kieuGiam: 'PHAN_TRAM', phanTramGiam: '5', soTienGiamToiDa: '300000', donToiThieu: '5000000' } },
  { ten: 'Khách mới · 150K', v: { loai: 'DON_HANG', kieuGiam: 'SO_TIEN', soTienGiamCoDinh: '150000', donToiThieu: '1000000', chiKhachMoi: true } },
  { ten: 'Freeship 50K', v: { loai: 'VAN_CHUYEN', kieuGiam: 'PHAN_TRAM', phanTramGiam: '100', soTienGiamToiDa: '50000', donToiThieu: '1000000' } },
];

// Ví dụ cho khung xem trước: giá trị đơn (voucher đơn hàng) hoặc phí giao hàng theo vùng (voucher vận chuyển).
const VI_DU_DON = [3_000_000, 7_000_000, 15_000_000, 25_000_000];
const VI_DU_SHIP = [{ ten: 'TP.HCM', phi: 15_000 }, { ten: 'Miền Nam', phi: 30_000 }, { ten: 'Miền Trung', phi: 40_000 }, { ten: 'Miền Bắc', phi: 50_000 }];

const toNumberOrNull = (v) => (v === '' || v == null ? null : Number(v));
const chuoi = (v) => (v == null ? '' : String(v));

function moTaTuDong(v) {
  const m = mucGiam(v);
  const parts = [m.chiTiet];
  if (Number(v.donToiThieu) > 0) parts.push(`cho đơn từ ${formatPrice(v.donToiThieu)}`);
  let s = parts.join(' ');
  if (v.loai === 'VAN_CHUYEN') s += ' chưa đạt ngưỡng freeship. Không gồm phí khai giá và phụ phí COD';
  if (v.chiKhachMoi) s += '. Chỉ dành cho đơn hàng đầu tiên';
  if (Number(v.gioiHanMoiKhach) === 1) s += '. Mỗi tài khoản dùng 1 lần';
  if (v.soLuong) s += `. Số lượng có hạn (${v.soLuong} lượt)`;
  return s;
}

function LuotDung({ k }) {
  const tong = k.soLuong;
  const pct = tong ? Math.min(100, Math.round((Number(k.daDung) / Number(tong)) * 100)) : 0;
  return (
    <div className="km-usage">
      <span>{k.daDung}{tong != null ? ` / ${tong}` : ''}</span>
      {tong != null ? <div className="co-progress"><span style={{ width: `${pct}%` }} /></div> : <small className="muted">Không giới hạn</small>}
    </div>
  );
}

export default function AdminPromotionsPage() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const debounced = useDebounce(q);
  const { data: result, loading, reload } = useFetch(
    () => adminService.promotionsPage({ q: debounced || undefined, page, size }),
    [debounced, page, size]
  );
  const { data: all, reload: reloadAll } = useFetch(adminService.promotions);
  const list = result?.content || [];
  const [editing, setEditing] = useState(null);
  const toast = useToast();
  const today = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    if (result && page > 0 && (result.totalElements === 0 || page >= result.totalPages)) {
      setPage(Math.max(0, result.totalPages - 1));
    }
  }, [result, page]);

  const thongKe = useMemo(() => {
    const dem = { 'Đang chạy': 0, 'Sắp diễn ra': 0, 'Hết hạn': 0, 'Hết lượt': 0, 'Đã tắt': 0 };
    (all || []).forEach((k) => { dem[trangThaiVoucher(k, today).label] += 1; });
    return dem;
  }, [all, today]);

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      setEditing(null);
      reload();
      reloadAll();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const patch = (changes) => setEditing((cur) => ({ ...cur, ...changes }));
  const set = (key) => (e) => patch({ [key]: e.target.value });
  const isNew = Boolean(editing?.isNew);
  const kiemTra = editing ? kiemTraVoucher(editing) : { loi: [], canhBao: [] };

  const submit = (e) => {
    e.preventDefault();
    if (kiemTra.loi.length) {
      toast(kiemTra.loi[0], 'error');
      return;
    }
    const { isNew: taoMoi, ...data } = editing;
    const tienMat = data.kieuGiam === 'SO_TIEN';
    run(
      () => adminService.savePromotion(taoMoi ? null : data.maKm, {
        ...data,
        phanTramGiam: tienMat ? null : Number(data.phanTramGiam),
        soTienGiamToiDa: tienMat ? null : toNumberOrNull(data.soTienGiamToiDa),
        soTienGiamCoDinh: tienMat ? toNumberOrNull(data.soTienGiamCoDinh) : null,
        donToiThieu: Number(data.donToiThieu || 0),
        soLuong: toNumberOrNull(data.soLuong),
        gioiHanMoiKhach: Number(data.gioiHanMoiKhach || 1),
        dieuKienApDung: data.dieuKienApDung?.trim() || moTaTuDong(data),
      }),
      'Đã lưu khuyến mãi'
    );
  };

  // Xóa chỉ cho mã chưa từng dùng; mã đã dùng thì đề nghị tắt để giữ lịch sử đơn hàng.
  const xoa = async (k) => {
    try {
      const impact = await adminService.promotionImpact(k.maKm);
      if (impact?.coDonHangLienQuan) {
        if (k.trangThaiHoatDong !== false
          && window.confirm(`Mã ${k.maKm} đã được dùng trong ${impact.soDonHangDaDung} đơn hàng nên không thể xóa. Tắt mã này thay vì xóa?`)) {
          await run(() => adminService.archivePromotion(k.maKm), `Đã tắt mã ${k.maKm}`);
        }
        return;
      }
    } catch (e) {
      toast(errorMessage(e), 'error');
      return;
    }
    if (window.confirm(`Xóa vĩnh viễn mã ${k.maKm}?`)) {
      run(() => adminService.deletePromotion(k.maKm), 'Đã xóa khuyến mãi');
    }
  };

  const moSua = (k) => setEditing({
    ...k,
    kieuGiam: k.kieuGiam || 'PHAN_TRAM',
    phanTramGiam: chuoi(k.phanTramGiam),
    soTienGiamToiDa: chuoi(k.soTienGiamToiDa),
    soTienGiamCoDinh: chuoi(k.soTienGiamCoDinh),
    donToiThieu: chuoi(k.donToiThieu),
    soLuong: chuoi(k.soLuong),
  });

  const ship = editing?.loai === 'VAN_CHUYEN';
  const tienMat = editing && laTienMat(editing);

  return (
    <>
      <div className="row between">
        <div>
          <h2 style={{ marginBottom: 4 }}>Quản lý khuyến mãi</h2>
          <p className="muted" style={{ margin: 0 }}>
            Giảm tiền mặt theo bậc giá trị đơn, hoặc giảm % bắt buộc có mức trần để không âm lợi nhuận.
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY, isNew: true, ngayBatDau: today })}>+ Tạo mã</button>
      </div>

      <div className="km-stats">
        {Object.entries(thongKe).map(([label, n]) => (
          <div key={label} className="stat-card"><span>{label}</span><b>{n}</b></div>
        ))}
      </div>

      <div className="row" style={{ gap: '12px', margin: '16px 0' }}>
        <input
          type="search"
          style={{ flex: 1 }}
          placeholder="Tìm theo mã hoặc tên chương trình..."
          value={q}
          onChange={(event) => { setQ(event.target.value); setPage(0); }}
        />
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table className="table km-table">
          <thead>
            <tr><th>Mã / chương trình</th><th>Mức giảm</th><th>Điều kiện</th><th>Lượt dùng</th><th>Thời gian</th><th>Trạng thái</th><th></th></tr>
          </thead>
          <tbody>
            {list.map((k) => {
              const tt = trangThaiVoucher(k, today);
              const dangBat = k.trangThaiHoatDong !== false;
              const m = mucGiam(k);
              return (
                <tr key={k.maKm} className={dangBat ? '' : 'row-muted'}>
                  <td>
                    <div className="km-code">
                      <b>{k.maKm}</b>
                      <span className={`km-type ${k.loai === 'VAN_CHUYEN' ? 'ship' : ''}`}>{k.loai === 'VAN_CHUYEN' ? 'Vận chuyển' : 'Đơn hàng'}</span>
                    </div>
                    <div className="muted">{k.tenKm}</div>
                  </td>
                  <td>
                    <span className="voucher-tag">{m.ngan}</span>
                    <div className="muted km-sub">{laTienMat(k) ? 'Tiền mặt cố định' : k.soTienGiamToiDa ? `Tối đa ${formatPrice(k.soTienGiamToiDa)}` : 'Chưa có trần'}</div>
                  </td>
                  <td>
                    <div>{Number(k.donToiThieu) > 0 ? `Đơn từ ${formatPrice(k.donToiThieu)}` : 'Mọi đơn'}</div>
                    <div className="muted km-sub">
                      {k.gioiHanMoiKhach}/khách{k.chiKhachMoi && <> · <span className="badge badge-KHACH_MOI">Khách mới</span></>}
                    </div>
                  </td>
                  <td><LuotDung k={k} /></td>
                  <td className="km-sub">{k.ngayBatDau}<br />→ {k.ngayKetThuc}</td>
                  <td><span className={`badge ${tt.className}`}>{tt.label}</span></td>
                  <td>
                    <div className="row" style={{ flexWrap: 'nowrap' }}>
                      <button className="btn" onClick={() => moSua(k)}>Sửa</button>
                      {dangBat ? (
                        <button className="btn" onClick={() => window.confirm(`Tắt mã ${k.maKm}? Khách sẽ không dùng được mã này nữa.`) && run(() => adminService.archivePromotion(k.maKm), `Đã tắt mã ${k.maKm}`)}>Tắt</button>
                      ) : (
                        <button className="btn" onClick={() => run(() => adminService.restorePromotion(k.maKm), `Đã bật lại mã ${k.maKm}`)}>Bật</button>
                      )}
                      <button className="btn btn-danger" onClick={() => xoa(k)}>Xóa</button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {!loading && list.length === 0 && (
              <tr><td colSpan={7} style={{ textAlign: 'center', padding: '24px' }}>Không tìm thấy khuyến mãi phù hợp.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <AdminPagination
        page={page}
        totalPages={result?.totalPages}
        totalElements={result?.totalElements}
        size={size}
        loading={loading}
        onPageChange={setPage}
        onSizeChange={(nextSize) => { setSize(nextSize); setPage(0); }}
      />

      {editing && (
        <Modal title={isNew ? 'Tạo khuyến mãi' : `Sửa khuyến mãi ${editing.maKm}`} onClose={() => setEditing(null)} wide>
          <form className="form km-form" onSubmit={submit}>
            <div className="km-form-main">
              {isNew && (
                <div className="km-presets">
                  <span className="muted">Mẫu nhanh:</span>
                  {MAU_NHANH.map((m) => (
                    <button key={m.ten} type="button" className="btn btn-sm" onClick={() => patch({ ...EMPTY, ...m.v, maKm: editing.maKm, tenKm: editing.tenKm, ngayBatDau: editing.ngayBatDau, ngayKetThuc: editing.ngayKetThuc })}>
                      {m.ten}
                    </button>
                  ))}
                </div>
              )}

              <fieldset className="km-section">
                <legend>Thông tin</legend>
                <div className="grid-2">
                  <FormField label="Mã khuyến mãi" value={editing.maKm} onChange={(e) => patch({ maKm: e.target.value.toUpperCase().replace(/\s/g, '') })} disabled={!isNew} required placeholder="VD: GIAM150K" />
                  <FormField label="Tên chương trình" value={editing.tenKm} onChange={set('tenKm')} required />
                </div>
                <div className="km-segment-row">
                  <span>Áp dụng cho</span>
                  <div className="chips" role="radiogroup">
                    {[['DON_HANG', 'Giá trị đơn hàng'], ['VAN_CHUYEN', 'Phí vận chuyển']].map(([val, label]) => (
                      <button key={val} type="button" role="radio" aria-checked={editing.loai === val} disabled={!isNew}
                        className={`chip ${editing.loai === val ? 'active' : ''}`} onClick={() => patch({ loai: val })}>{label}</button>
                    ))}
                  </div>
                </div>
              </fieldset>

              <fieldset className="km-section">
                <legend>Mức giảm</legend>
                <div className="km-segment-row">
                  <span>Kiểu giảm</span>
                  <div className="chips" role="radiogroup">
                    {[['SO_TIEN', 'Tiền mặt cố định'], ['PHAN_TRAM', 'Phần trăm có trần']].map(([val, label]) => (
                      <button key={val} type="button" role="radio" aria-checked={editing.kieuGiam === val} disabled={!isNew}
                        className={`chip ${editing.kieuGiam === val ? 'active' : ''}`} onClick={() => patch({ kieuGiam: val })}>{label}</button>
                    ))}
                  </div>
                </div>
                {tienMat ? (
                  <label className="field">
                    <span>Số tiền giảm (₫)</span>
                    <MoneyInput value={editing.soTienGiamCoDinh} onChange={(raw) => patch({ soTienGiamCoDinh: raw })} disabled={!isNew} placeholder="150.000" required />
                  </label>
                ) : (
                  <div className="grid-2">
                    <FormField label="Phần trăm giảm (%)" type="number" min="0.01" max="100" step="0.01" value={editing.phanTramGiam} onChange={set('phanTramGiam')} disabled={!isNew} required placeholder={ship ? '100' : '5'} />
                    <label className="field">
                      <span>Giảm tối đa (₫) · bắt buộc</span>
                      <MoneyInput value={editing.soTienGiamToiDa} onChange={(raw) => patch({ soTienGiamToiDa: raw })} placeholder={ship ? '50.000' : '300.000'} required />
                    </label>
                  </div>
                )}
                {!isNew && <small className="muted">Loại, kiểu giảm và mức giảm không đổi được sau khi tạo (giữ đúng với các đơn đã dùng). Cần mức khác thì tạo mã mới.</small>}
              </fieldset>

              <fieldset className="km-section">
                <legend>Điều kiện</legend>
                <div className="grid-2">
                  <label className="field">
                    <span>Đơn tối thiểu (₫)</span>
                    <MoneyInput value={editing.donToiThieu} onChange={(raw) => patch({ donToiThieu: raw })} placeholder="0" />
                  </label>
                  <FormField label="Tổng số lượt (trống = không giới hạn)" type="number" min={Math.max(1, Number(editing.daDung) || 0)} value={editing.soLuong ?? ''} onChange={set('soLuong')} />
                </div>
                <div className="grid-2">
                  <FormField label="Số lần mỗi khách được dùng" type="number" min="1" value={editing.gioiHanMoiKhach ?? 1} onChange={set('gioiHanMoiKhach')} required />
                  <label className="option km-check">
                    <input type="checkbox" checked={!!editing.chiKhachMoi} onChange={(e) => patch({ chiKhachMoi: e.target.checked })} /> Chỉ dành cho đơn đầu tiên (khách mới)
                  </label>
                </div>
                <div className="grid-2">
                  <FormField label="Từ ngày" type="date" value={editing.ngayBatDau} onChange={set('ngayBatDau')} required />
                  <FormField label="Đến ngày" type="date" min={editing.ngayBatDau || undefined} value={editing.ngayKetThuc} onChange={set('ngayKetThuc')} required />
                </div>
              </fieldset>

              <fieldset className="km-section">
                <legend>Mô tả cho khách</legend>
                <label className="field">
                  <span className="row between">
                    <span>Điều kiện áp dụng (để trống sẽ tự tạo)</span>
                    <button type="button" className="link-btn" onClick={() => patch({ dieuKienApDung: moTaTuDong(editing) })}>Tạo mô tả tự động</button>
                  </span>
                  <textarea rows={2} value={editing.dieuKienApDung || ''} onChange={set('dieuKienApDung')} placeholder={moTaTuDong(editing)} />
                </label>
              </fieldset>
            </div>

            <aside className="km-preview">
              <div className={`km-ticket ${ship ? 'ship' : ''}`}>
                <div className="km-ticket-stub">
                  <b>{mucGiam(editing).ngan || '—'}</b>
                  <small>{ship ? 'phí ship' : 'đơn hàng'}</small>
                </div>
                <div className="km-ticket-body">
                  <b>{editing.tenKm || 'Tên chương trình'}</b>
                  <small>{editing.dieuKienApDung || moTaTuDong(editing)}</small>
                  <small className="muted">Mã {editing.maKm || '—'}</small>
                </div>
              </div>

              <h4>Khách được giảm bao nhiêu?</h4>
              <table className="km-example">
                <tbody>
                  {ship
                    ? VI_DU_SHIP.map((x) => {
                      const g = tinhGiamVoucher(editing, Math.max(Number(editing.donToiThieu || 0), 1_000_000), x.phi);
                      return <tr key={x.ten}><td>Phí ship {x.ten} {formatPrice(x.phi)}</td><td>{g ? `-${formatPrice(g)}` : '—'}</td></tr>;
                    })
                    : VI_DU_DON.map((don) => {
                      const g = tinhGiamVoucher(editing, don);
                      return (
                        <tr key={don}>
                          <td>Đơn {formatPrice(don)}</td>
                          <td>{g ? <>-{formatPrice(g)} <small className="muted">({((g / don) * 100).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%)</small></> : <small className="muted">Chưa đạt</small>}</td>
                        </tr>
                      );
                    })}
                </tbody>
              </table>

              {kiemTra.loi.map((m) => <p key={m} className="km-msg error">{m}</p>)}
              {kiemTra.canhBao.map((m) => <p key={m} className="km-msg warn">{m}</p>)}

              <button className="btn btn-primary btn-lg" disabled={kiemTra.loi.length > 0}>Lưu khuyến mãi</button>
            </aside>
          </form>
        </Modal>
      )}
    </>
  );
}
