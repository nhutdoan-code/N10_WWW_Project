import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { BarList, Donut, LineChart, StackBar } from '../../components/Charts';
import { ORDER_STATUS } from '../../config/constants';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { COLORS, fillDays, groupMonths, isoDaysAgo } from '../../utils/chart';
import { downloadFile, errorMessage, formatPrice } from '../../utils/format';

const Stat = ({ label, value, to }) => {
  const body = <><span className="muted">{label}</span><b>{value}</b></>;
  return to ? <Link to={to} className="card stat stat-link">{body}</Link> : <div className="card stat">{body}</div>;
};

const PRESETS = [
  { label: '7 ngày', days: 7 },
  { label: '30 ngày', days: 30 },
  { label: '90 ngày', days: 90 },
  { label: 'Tất cả', days: null },
];
const STATUS_COLORS = { CHO_XAC_NHAN: COLORS.yellow, DANG_XU_LY: COLORS.blue, DANG_GIAO: COLORS.violet, DA_GIAO: COLORS.aqua, DA_HUY: COLORS.red };
const fmtDay = (k) => `${k.slice(8, 10)}/${k.slice(5, 7)}`;
const fmtMonth = (k) => `${k.slice(5, 7)}/${k.slice(0, 4)}`;

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

  const [metric, setMetric] = useState('doanhThu');
  const [group, setGroup] = useState('ngay');
  const preset = PRESETS.find((p) => (p.days ? range.tu === isoDaysAgo(p.days) && !range.den : !range.tu && !range.den));

  const series = useMemo(() => {
    const days = fillDays(s?.doanhThuTheoNgay || []);
    const rows = group === 'thang' ? groupMonths(days) : days;
    return rows.map((r) => ({
      label: group === 'thang' ? `Tháng ${fmtMonth(r.ngay)}` : r.ngay.split('-').reverse().join('/'),
      short: group === 'thang' ? fmtMonth(r.ngay) : fmtDay(r.ngay),
      value: Number(r[metric]),
    }));
  }, [s, metric, group]);

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
          <div className="chips">
            {PRESETS.map((p) => (
              <button key={p.label} className={preset === p ? 'chip active' : 'chip'} onClick={() => setRange({ tu: p.days ? isoDaysAgo(p.days) : '', den: '' })}>{p.label}</button>
            ))}
          </div>
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
            <Stat label="Yêu cầu đổi trả" value={s.soYeuCauDoiTra} to="/admin/returns" />
            <Stat label="Sắp hết hàng" value={s.soSapHetHang} to="/admin/inventory" />
            <Stat label="Hết hàng" value={s.soHetHang} to="/admin/inventory" />
          </div>

          <div className="card">
            <div className="row between">
              <h3>{metric === 'doanhThu' ? 'Doanh thu' : 'Số đơn hàng'} theo {group === 'ngay' ? 'ngày' : 'tháng'}</h3>
              <div className="row">
                <div className="chips">
                  <button className={metric === 'doanhThu' ? 'chip active' : 'chip'} onClick={() => setMetric('doanhThu')}>Doanh thu</button>
                  <button className={metric === 'soDon' ? 'chip active' : 'chip'} onClick={() => setMetric('soDon')}>Số đơn</button>
                </div>
                <div className="chips">
                  <button className={group === 'ngay' ? 'chip active' : 'chip'} onClick={() => setGroup('ngay')}>Ngày</button>
                  <button className={group === 'thang' ? 'chip active' : 'chip'} onClick={() => setGroup('thang')}>Tháng</button>
                </div>
              </div>
            </div>
            <LineChart data={series} format={metric === 'doanhThu' ? 'money' : 'count'} color={metric === 'doanhThu' ? COLORS.blue : COLORS.orange} label="Biểu đồ xu hướng" />
          </div>

          <div className="grid-2">
            <div className="card">
              <h3>Đơn theo trạng thái</h3>
              <Donut
                centerLabel="đơn hàng"
                items={Object.entries(s.donTheoTrangThai).map(([k, v]) => ({ name: ORDER_STATUS[k] || k, value: v, color: STATUS_COLORS[k] || COLORS.blue }))}
              />
            </div>
            <div className="card">
              <h3>Doanh thu theo hãng</h3>
              <BarList items={s.theoHang.map((h) => ({ name: h.ten, value: Number(h.doanhThu) }))} color={COLORS.blue} format={formatPrice} />
            </div>
          </div>

          <div className="grid-2">
            <div className="card">
              <h3>Sản phẩm bán chạy</h3>
              <BarList items={s.banChay.map((b) => ({ name: b.tenMay, value: b.daBan }))} color={COLORS.aqua} format={(v) => `${v} máy`} />
            </div>
            <div className="card">
              <div className="row between"><h3>Tình trạng kho</h3><Link to="/admin/inventory" className="muted">Quản lý tồn kho</Link></div>
              <StackBar
                items={[
                  { name: 'Còn hàng', value: s.soConHang, color: COLORS.aqua },
                  { name: 'Sắp hết hàng', value: s.soSapHetHang, color: COLORS.yellow },
                  { name: 'Hết hàng', value: s.soHetHang, color: COLORS.red },
                ]}
              />
            </div>
          </div>
        </>
      )}
    </>
  );
}
