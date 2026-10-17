import { useState } from 'react';
import StatusBadge from '../../components/StatusBadge';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { downloadFile, errorMessage, formatPrice } from '../../utils/format';

const Stat = ({ label, value }) => (
  <div className="card stat"><span className="muted">{label}</span><b>{value}</b></div>
);

export default function AdminDashboardPage() {
  const [range, setRange] = useState({ tu: '', den: '' });
  const [q, setQ] = useState('');
  const debounced = useDebounce(q.trim(), 400);
  const toast = useToast();
  const { data: s } = useFetch(() => adminService.stats(range.tu, range.den), [range.tu, range.den]);
  const { data: found } = useFetch(() => (debounced ? adminService.search(debounced) : Promise.resolve(null)), [debounced]);

  const exportCsv = async () => {
    try {
      downloadFile(await adminService.exportStats(range.tu, range.den), 'bao-cao-don-hang.csv');
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const max = Math.max(1, ...(s?.doanhThuTheoNgay || []).map((d) => Number(d.doanhThu)));

  return (
    <>
      <h2>Tìm kiếm tổng hợp</h2>
      <input className="wide" placeholder="Tìm sản phẩm, loại, tài khoản, đơn hàng..." value={q} onChange={(e) => setQ(e.target.value)} />
      {found && (
        <div className="card">
          <p><b>Sản phẩm ({found.sanPhams.length}):</b> {found.sanPhams.map((p) => p.tenMay).join(', ') || '—'}</p>
          <p><b>Loại ({found.loais.length}):</b> {found.loais.map((l) => l.tenLoai).join(', ') || '—'}</p>
          <p><b>Tài khoản ({found.taiKhoans.length}):</b> {found.taiKhoans.map((t) => `${t.hoTen} <${t.email}>`).join(', ') || '—'}</p>
          <p><b>Đơn hàng ({found.donHangs.length}):</b> {found.donHangs.map((d) => `${d.maDonHang} (${formatPrice(d.tongTien)})`).join(', ') || '—'}</p>
        </div>
      )}

      <div className="row between">
        <h2>Thống kê & báo cáo</h2>
        <div className="row">
          <input type="date" value={range.tu} onChange={(e) => setRange({ ...range, tu: e.target.value })} />
          <span>→</span>
          <input type="date" value={range.den} onChange={(e) => setRange({ ...range, den: e.target.value })} />
          <button className="btn" onClick={exportCsv}>Xuất CSV</button>
        </div>
      </div>
      {s && (
        <>
          <div className="stats">
            <Stat label="Doanh thu (đơn đã giao)" value={formatPrice(s.doanhThu)} />
            <Stat label="Số đơn hàng" value={s.soDon} />
            <Stat label="Đơn đã hủy" value={s.soDonHuy} />
            <Stat label="Yêu cầu đổi trả" value={s.soYeuCauDoiTra} />
            <Stat label="Sắp hết hàng" value={s.soSapHetHang} />
            <Stat label="Hết hàng" value={s.soHetHang} />
          </div>
          <div className="grid-2">
            <div className="card">
              <h3>Đơn theo trạng thái</h3>
              {Object.entries(s.donTheoTrangThai).map(([k, v]) => (
                <div key={k} className="row between"><StatusBadge status={k} /><b>{v}</b></div>
              ))}
              <h3>Bán chạy</h3>
              {s.banChay.length === 0 && <p className="muted">Chưa có dữ liệu</p>}
              {s.banChay.map((b) => (
                <div key={b.maSanPham} className="row between"><span>{b.tenMay}</span><b>{b.daBan}</b></div>
              ))}
            </div>
            <div className="card">
              <h3>Doanh thu theo ngày</h3>
              {s.doanhThuTheoNgay.length === 0 && <p className="muted">Chưa có dữ liệu</p>}
              {s.doanhThuTheoNgay.map((d) => (
                <div key={d.ngay} className="bar-row">
                  <span className="muted">{d.ngay}</span>
                  <div className="bar"><i style={{ width: `${(Number(d.doanhThu) / max) * 100}%` }} /></div>
                  <span>{formatPrice(d.doanhThu)}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </>
  );
}
