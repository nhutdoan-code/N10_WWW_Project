import { useEffect, useState } from 'react';
import AdminPagination from '../../components/AdminPagination';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatPrice } from '../../utils/format';
import { trangThaiVoucher } from '../../utils/voucherAdmin';

const EMPTY = { maKm: '', tenKm: '', loai: 'DON_HANG', phanTramGiam: '', soTienGiamToiDa: '', donToiThieu: 0, soLuong: '', daDung: 0, chiKhachMoi: false, gioiHanMoiKhach: 1, ngayBatDau: '', ngayKetThuc: '', dieuKienApDung: '' };

const toNumberOrNull = (v) => (v === '' || v == null ? null : Number(v));

export default function AdminPromotionsPage() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const debounced = useDebounce(q);
  const { data: result, loading, reload } = useFetch(
    () => adminService.promotionsPage({ q: debounced || undefined, page, size }),
    [debounced, page, size]
  );
  const list = result?.content || [];
  const [editing, setEditing] = useState(null);
  const toast = useToast();
  const today = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    if (result && page > 0 && (result.totalElements === 0 || page >= result.totalPages)) {
      setPage(Math.max(0, result.totalPages - 1));
    }
  }, [result, page]);

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      setEditing(null);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const set = (key) => (e) => setEditing({ ...editing, [key]: e.target.value });
  const submit = (e) => {
    e.preventDefault();
    const { isNew, ...data } = editing;
    run(
      () => adminService.savePromotion(isNew ? null : data.maKm, {
        ...data,
        phanTramGiam: Number(data.phanTramGiam),
        soTienGiamToiDa: toNumberOrNull(data.soTienGiamToiDa),
        donToiThieu: Number(data.donToiThieu || 0),
        soLuong: toNumberOrNull(data.soLuong),
        gioiHanMoiKhach: Number(data.gioiHanMoiKhach || 1),
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

  const isNew = Boolean(editing?.isNew);

  return (
    <>
      <div className="row between">
        <h2>Quản lý khuyến mãi</h2>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY, isNew: true })}>+ Tạo mã</button>
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
      <table className="table">
        <thead>
          <tr><th>Mã</th><th>Tên chương trình</th><th>Loại</th><th>Giảm</th><th>Tối đa</th><th>Đơn tối thiểu</th><th>Đã dùng</th><th>Thời gian</th><th>Trạng thái</th><th></th></tr>
        </thead>
        <tbody>
          {list?.map((k) => {
            const tt = trangThaiVoucher(k, today);
            const dangBat = k.trangThaiHoatDong !== false;
            return (
              <tr key={k.maKm} className={dangBat ? '' : 'row-muted'}>
                <td><b>{k.maKm}</b></td>
                <td>
                  {k.tenKm}
                  {k.chiKhachMoi && <> <span className="badge badge-KHACH_MOI">Khách hàng mới</span></>}
                </td>
                <td>{k.loai === 'VAN_CHUYEN' ? 'Vận chuyển' : 'Đơn hàng'}</td>
                <td>{k.phanTramGiam}%</td>
                <td>{k.soTienGiamToiDa ? formatPrice(k.soTienGiamToiDa) : '—'}</td>
                <td>{Number(k.donToiThieu) > 0 ? formatPrice(k.donToiThieu) : '—'}</td>
                <td>{k.daDung}{k.soLuong != null && ` / ${k.soLuong}`}</td>
                <td>{k.ngayBatDau} → {k.ngayKetThuc}</td>
                <td><span className={`badge ${tt.className}`}>{tt.label}</span></td>
                <td className="row">
                  <button className="btn" onClick={() => setEditing(k)}>Sửa</button>
                  {dangBat ? (
                    <button className="btn" onClick={() => window.confirm(`Tắt mã ${k.maKm}? Khách sẽ không dùng được mã này nữa.`) && run(() => adminService.archivePromotion(k.maKm), `Đã tắt mã ${k.maKm}`)}>Tắt</button>
                  ) : (
                    <button className="btn" onClick={() => run(() => adminService.restorePromotion(k.maKm), `Đã bật lại mã ${k.maKm}`)}>Bật</button>
                  )}
                  <button className="btn btn-danger" onClick={() => xoa(k)}>Xóa</button>
                </td>
              </tr>
            );
          })}
          {!loading && list.length === 0 && (
            <tr><td colSpan={10} style={{ textAlign: 'center', padding: '24px' }}>Không tìm thấy khuyến mãi phù hợp.</td></tr>
          )}
        </tbody>
      </table>
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
        <Modal title={isNew ? 'Tạo khuyến mãi' : 'Sửa khuyến mãi'} onClose={() => setEditing(null)}>
          <form className="form" onSubmit={submit}>
            <FormField label="Mã khuyến mãi" value={editing.maKm} onChange={(e) => setEditing({ ...editing, maKm: e.target.value.toUpperCase().replace(/\s/g, '') })} disabled={!isNew} required />
            <FormField label="Tên chương trình" value={editing.tenKm} onChange={set('tenKm')} required />
            <label className="field">
              <span>Loại voucher</span>
              <select value={editing.loai || 'DON_HANG'} onChange={set('loai')} disabled={!isNew}>
                <option value="DON_HANG">Giảm giá đơn hàng</option>
                <option value="VAN_CHUYEN">Giảm phí vận chuyển</option>
              </select>
            </label>
            <div className="grid-2">
              <FormField label="Phần trăm giảm (%)" type="number" min="0.01" max="100" step="0.01" value={editing.phanTramGiam} onChange={set('phanTramGiam')} disabled={!isNew} required />
              <FormField label={`Giảm tối đa (₫)${editing.loai === 'VAN_CHUYEN' ? ' (bắt buộc)' : ''}`} type="number" min="0" value={editing.soTienGiamToiDa ?? ''} onChange={set('soTienGiamToiDa')} required={editing.loai === 'VAN_CHUYEN'} />
            </div>
            {!isNew && (
              <small className="muted">Loại voucher và phần trăm giảm không đổi được sau khi tạo (đảm bảo đúng với các đơn đã dùng). Cần mức giảm khác thì tạo mã mới.</small>
            )}
            <div className="grid-2">
              <FormField label="Đơn tối thiểu (₫)" type="number" min="0" value={editing.donToiThieu ?? 0} onChange={set('donToiThieu')} />
              <FormField label="Số lượng mã (để trống = không giới hạn)" type="number" min={Math.max(1, Number(editing.daDung) || 0)} value={editing.soLuong ?? ''} onChange={set('soLuong')} />
            </div>
            <FormField label="Giới hạn mỗi khách" type="number" min="1" value={editing.gioiHanMoiKhach ?? 1} onChange={set('gioiHanMoiKhach')} required />
            <label className="option">
              <input type="checkbox" checked={!!editing.chiKhachMoi} onChange={(e) => setEditing({ ...editing, chiKhachMoi: e.target.checked })} /> Chỉ dành cho khách mua lần đầu
            </label>
            <div className="grid-2">
              <FormField label="Từ ngày" type="date" value={editing.ngayBatDau} onChange={set('ngayBatDau')} required />
              <FormField label="Đến ngày" type="date" min={editing.ngayBatDau || undefined} value={editing.ngayKetThuc} onChange={set('ngayKetThuc')} required />
            </div>
            <FormField label="Điều kiện áp dụng (mô tả cho khách)" value={editing.dieuKienApDung || ''} onChange={set('dieuKienApDung')} />
            <button className="btn btn-primary">Lưu</button>
          </form>
        </Modal>
      )}
    </>
  );
}
