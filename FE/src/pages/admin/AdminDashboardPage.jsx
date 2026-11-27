import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BarList, Donut, LineChart, StackBar } from '../../components/Charts';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { COLORS, fillDays, groupMonths, isoDaysAgo } from '../../utils/chart';
import { downloadFile, errorMessage, formatNumber, formatPrice } from '../../utils/format';

const Stat = ({ label, value, sub, to, className = '' }) => {
  const body = (
    <>
      <span className="muted">{label}</span>
      <b>{value}</b>
      {sub && <span className="sub">{sub}</span>}
    </>
  );
  return to ? (
    <Link to={to} className={`card stat stat-link ${className}`} aria-label={`Mở danh sách liên quan: ${label}`}>
      {body}
    </Link>
  ) : (
    <div className={`card stat ${className}`}>{body}</div>
  );
};

const PRESETS = [
  { label: '7 ngày', days: 7 },
  { label: '30 ngày', days: 30 },
  { label: '90 ngày', days: 90 },
  { label: 'Tất cả', days: null },
];

const STATUS_COLORS = {
  CHO_XAC_NHAN: '#e2ac35',
  DA_XAC_NHAN: '#5fa884',
  DANG_CHUAN_BI: '#8f9a94',
  DANG_GIAO: '#b8862a',
  CHO_KHACH_XAC_NHAN: '#3e6b59',
  DA_GIAO: '#2f7a5a',
  DA_HUY: '#c0392b',
};

const STATUS_LABELS = {
  CHO_XAC_NHAN: 'Chờ xác nhận',
  DA_XAC_NHAN: 'Đã xác nhận',
  DANG_CHUAN_BI: 'Đang chuẩn bị',
  DANG_GIAO: 'Đang giao',
  CHO_KHACH_XAC_NHAN: 'Chờ khách xác nhận',
  DA_GIAO: 'Hoàn thành',
  DA_HUY: 'Đã hủy',
};

const fmtDay = (k) => `${k.slice(8, 10)}/${k.slice(5, 7)}`;
const fmtMonth = (k) => `${k.slice(5, 7)}/${k.slice(0, 4)}`;
const orderLink = ({ status, from, to, dateField = 'ngayMua' } = {}) => {
  const params = new URLSearchParams();
  if (status) params.set('trangThai', status);
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  if (dateField !== 'ngayMua') {
    params.set('ngayTheo', dateField);
    if (dateField === 'ngayGiao') params.set('sort', 'ngay_giao_moi');
  }
  const query = params.toString();
  return `/admin/orders${query ? `?${query}` : ''}`;
};

const returnQueueLink = (from, to) => {
  const params = new URLSearchParams({ status: 'open', source: 'return' });
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  return `/admin/returns?${params.toString()}`;
};

const localToday = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const [range, setRange] = useState({ tu: '', den: '' });
  const [q, setQ] = useState('');
  const debounced = useDebounce(q.trim(), 400);
  const toast = useToast();

  const isInvalidDateRange = Boolean(range.tu && range.den && range.tu > range.den);

  const { data: s, loading, error, reload } = useFetch(
    () => (isInvalidDateRange ? Promise.resolve(null) : adminService.stats(range.tu, range.den)),
    [range.tu, range.den, isInvalidDateRange]
  );

  const { data: found, loading: searching } = useFetch(
    () => (debounced ? adminService.search(debounced) : Promise.resolve(null)),
    [debounced]
  );

  const exportCsv = async () => {
    if (isInvalidDateRange) {
      toast('Ngày bắt đầu không được sau ngày kết thúc', 'error');
      return;
    }
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
    const raw = s?.doanhThuTheoNgay || [];
    if (raw.length === 0) return [];
    const days = fillDays(raw);
    const rows = group === 'thang' ? groupMonths(days) : days;
    return rows.map((r) => ({
      label: group === 'thang' ? `Tháng ${fmtMonth(r.ngay)}` : r.ngay.split('-').reverse().join('/'),
      short: group === 'thang' ? fmtMonth(r.ngay) : fmtDay(r.ngay),
      date: r.ngay,
      value: Number(r[metric] || 0),
    }));
  }, [s, metric, group]);

  const openChartOrders = (point) => {
    let from = point.date;
    let to = point.date;
    if (group === 'thang') {
      const [year, month] = point.date.split('-').map(Number);
      const lastDay = new Date(year, month, 0).getDate();
      const monthFrom = `${point.date}-01`;
      const monthTo = `${point.date}-${String(lastDay).padStart(2, '0')}`;
      from = range.tu && range.tu > monthFrom ? range.tu : monthFrom;
      const maxTo = range.den || localToday();
      to = maxTo < monthTo ? maxTo : monthTo;
    }
    navigate(orderLink({ status: 'DA_GIAO', from, to, dateField: 'ngayGiao' }));
  };

  const hasSearchResults = found && (
    found.sanPhams?.length > 0 ||
    found.loais?.length > 0 ||
    found.taiKhoans?.length > 0 ||
    found.donHangs?.length > 0
  );

  return (
    <>
      <h2>Tìm kiếm tổng hợp</h2>
      <input
        className="wide"
        placeholder="Tìm sản phẩm, loại sản phẩm, tài khoản, đơn hàng..."
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      {debounced && (
        <div className="card search-results-panel">
          {searching ? (
            <p className="muted">Đang tìm kiếm...</p>
          ) : hasSearchResults ? (
            <div>
              {found.sanPhams?.length > 0 && (
                <div className="search-domain-row">
                  <span className="search-domain-label">Sản phẩm ({found.sanPhams.length}):</span>
                  <span>{found.sanPhams.map((p) => p.tenMay).join(', ')}</span>
                </div>
              )}
              {found.loais?.length > 0 && (
                <div className="search-domain-row">
                  <span className="search-domain-label">Loại sản phẩm ({found.loais.length}):</span>
                  <span>{found.loais.map((l) => l.tenLoai).join(', ')}</span>
                </div>
              )}
              {found.taiKhoans?.length > 0 && (
                <div className="search-domain-row">
                  <span className="search-domain-label">Tài khoản ({found.taiKhoans.length}):</span>
                  <span>{found.taiKhoans.map((t) => `${t.hoTen} <${t.email}>`).join(', ')}</span>
                </div>
              )}
              {found.donHangs?.length > 0 && (
                <div className="search-domain-row">
                  <span className="search-domain-label">Đơn hàng ({found.donHangs.length}):</span>
                  <span>{found.donHangs.map((d) => `${d.maDonHang} (${formatPrice(d.tongThanhToan || d.tongTien)})`).join(', ')}</span>
                </div>
              )}
            </div>
          ) : (
            <p className="muted">Không tìm thấy kết quả nào phù hợp với &quot;{debounced}&quot;.</p>
          )}
        </div>
      )}

      <div className="row between" style={{ marginTop: 24 }}>
        <div>
          <h2>Thống kê & Tổng quan vận hành</h2>
          <p className="dashboard-subtitle">
            Doanh thu và đơn đã giao được thống kê theo ngày giao; yêu cầu đổi trả còn mở theo ngày tạo; các trạng thái đơn khác và tồn kho theo thời điểm hiện tại / ngày đặt.
          </p>
        </div>
        <div className="row" style={{ flexWrap: 'wrap', gap: 8 }}>
          <div className="chips">
            {PRESETS.map((p) => (
              <button
                key={p.label}
                className={preset === p ? 'chip active' : 'chip'}
                onClick={() => setRange({ tu: p.days ? isoDaysAgo(p.days) : '', den: '' })}
              >
                {p.label}
              </button>
            ))}
          </div>
          <input
            type="date"
            value={range.tu}
            onChange={(e) => setRange({ ...range, tu: e.target.value })}
            title="Từ ngày"
          />
          <span>–</span>
          <input
            type="date"
            value={range.den}
            onChange={(e) => setRange({ ...range, den: e.target.value })}
            title="Đến ngày"
          />
          <button
            className="btn btn-secondary"
            onClick={() => setRange({ tu: '', den: '' })}
            title="Đặt lại về toàn thời gian"
          >
            Đặt lại
          </button>
          <button className="btn" onClick={exportCsv} disabled={isInvalidDateRange}>
            Xuất CSV
          </button>
        </div>
      </div>

      {isInvalidDateRange && (
        <div className="dashboard-error-banner">
          <span>Ngày bắt đầu không được sau ngày kết thúc. Vui lòng điều chỉnh khoảng thời gian.</span>
        </div>
      )}

      {error && !isInvalidDateRange && (
        <div className="dashboard-error-banner">
          <span>Không thể tải dữ liệu thống kê: {error}</span>
          <button className="btn" onClick={reload}>Thử lại</button>
        </div>
      )}

      {loading && !s && (
        <div className="card dashboard-skeleton">
          Đang tải dữ liệu tổng quan...
        </div>
      )}

      {s && (
        <>
          {/* 1. Primary KPIs: Recognized Revenue & Pipeline GMV */}
          <div className="stats-primary-grid">
            <Stat
              className="stat-primary"
              label="Doanh thu đã ghi nhận"
              value={formatPrice(s.doanhThu)}
              to={orderLink({ status: 'DA_GIAO', from: range.tu, to: range.den, dateField: 'ngayGiao' })}
              sub="Chỉ tính đơn đã giao thành công"
            />
            <Stat
              className="stat-pipeline"
              label="Giá trị đang xử lý (Pipeline GMV)"
              value={formatPrice(s.giaTriDangXuLy)}
              to={orderLink({ status: 'DANG_XU_LY', from: range.tu, to: range.den })}
              sub="Đơn đang xác nhận, chuẩn bị hoặc giao"
            />
          </div>

          {/* 2. Order & Operational KPIs */}
          <div className="stats-orders-grid">
            <Stat
              label="Tổng đơn hàng"
              value={formatNumber(s.soDon)}
              to={orderLink({ from: range.tu, to: range.den })}
              sub="Đơn đặt trong kỳ"
            />
            <Stat
              label="Đã giao"
              value={formatNumber(s.soDonThanhCong)}
              to={orderLink({ status: 'DA_GIAO', from: range.tu, to: range.den, dateField: 'ngayGiao' })}
              sub="Giao thành công"
            />
            <Stat
              label="Đang xử lý"
              value={formatNumber(s.soDonDangXuLy)}
              to={orderLink({ status: 'DANG_XU_LY', from: range.tu, to: range.den })}
              sub="Chờ & luân chuyển"
            />
            <Stat
              label="Đã hủy"
              value={formatNumber(s.soDonHuy)}
              to={orderLink({ status: 'DA_HUY', from: range.tu, to: range.den })}
              sub="Hủy trong kỳ"
            />
            <Stat
              label="Yêu cầu đổi trả"
              value={formatNumber(s.soYeuCauDoiTra)}
              to={returnQueueLink(range.tu, range.den)}
              sub="Đang chờ xử lý trong kỳ"
            />
          </div>

          {/* 3. Revenue Trend Chart */}
          <div className="card" style={{ marginBottom: 20 }}>
            <div className="row between">
              <div className="dashboard-card-header">
                <h3>{metric === 'doanhThu' ? 'Doanh thu giao thành công' : 'Số đơn đã giao'} theo {group === 'ngay' ? 'ngày giao' : 'tháng giao'}</h3>
                <p>Bấm một điểm để xem các đơn đã giao trong ngày hoặc tháng tương ứng.</p>
              </div>
              <div className="row">
                <div className="chips">
                  <button className={metric === 'doanhThu' ? 'chip active' : 'chip'} onClick={() => setMetric('doanhThu')}>Doanh thu (₫)</button>
                  <button className={metric === 'soDon' ? 'chip active' : 'chip'} onClick={() => setMetric('soDon')}>Số đơn giao</button>
                </div>
                <div className="chips">
                  <button className={group === 'ngay' ? 'chip active' : 'chip'} onClick={() => setGroup('ngay')}>Ngày</button>
                  <button className={group === 'thang' ? 'chip active' : 'chip'} onClick={() => setGroup('thang')}>Tháng</button>
                </div>
              </div>
            </div>
            {series.length === 0 ? (
              <p className="muted" style={{ padding: '36px 0', textAlign: 'center' }}>
                Chưa có đơn hàng nào được giao trong khoảng thời gian này.
              </p>
            ) : (
              <LineChart
                data={series}
                format={metric === 'doanhThu' ? 'money' : 'count'}
                color={metric === 'doanhThu' ? COLORS.blue : COLORS.aqua}
                label="Biểu đồ doanh thu giao thành công"
                onPointClick={openChartOrders}
              />
            )}
          </div>

          {/* 4. Order Status Distribution & Brand Sales */}
          <div className="grid-2" style={{ marginBottom: 20 }}>
            <div className="card">
              <div className="dashboard-card-header">
                <h3>Phân bố trạng thái đơn hàng</h3>
                <p>Bấm một trạng thái để xem các đơn tương ứng trong kỳ.</p>
              </div>
              <Donut
                centerLabel="Đơn hàng"
                items={Object.keys(STATUS_LABELS).filter((k) => k !== 'DANG_XU_LY').map((k) => ({
                  key: k,
                  name: STATUS_LABELS[k],
                  value: s.donTheoTrangThai?.[k] || 0,
                  color: STATUS_COLORS[k] || COLORS.blue,
                }))}
                onSelect={(item) => navigate(orderLink({ status: item.key, from: range.tu, to: range.den }))}
              />
            </div>

            <div className="card">
              <div className="dashboard-card-header">
                <h3>Doanh số sản phẩm theo danh mục</h3>
                <p>Bấm một nhóm để tìm sản phẩm thuộc danh mục đó.</p>
              </div>
              {s.theoHang?.length === 0 ? (
                <p className="muted" style={{ padding: '24px 0', textAlign: 'center' }}>
                  Chưa có dữ liệu doanh số theo danh mục trong kỳ.
                </p>
              ) : (
                <BarList
                  items={s.theoHang.map((h) => ({ name: h.ten, value: Number(h.doanhThu) }))}
                  color={COLORS.blue}
                  format={formatPrice}
                  onSelect={(item) => navigate(`/admin/products?q=${encodeURIComponent(item.name)}`)}
                />
              )}
            </div>
          </div>

          {/* 5. Top-Selling Products & Inventory Health */}
          <div className="grid-2">
            <div className="card">
              <div className="dashboard-card-header">
                <h3>Sản phẩm bán chạy</h3>
                <p>Top sản phẩm có số lượng bán nhiều nhất từ các đơn đã giao thành công</p>
              </div>
              {s.banChay?.length === 0 ? (
                <p className="muted" style={{ padding: '24px 0', textAlign: 'center' }}>
                  Chưa có sản phẩm bán ra trong khoảng thời gian này.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {s.banChay.map((b, idx) => (
                    <Link key={b.maSanPham} to={`/admin/products?q=${encodeURIComponent(b.maSanPham)}`} className="top-product-item top-product-link">
                      <div style={{ display: 'flex', alignItems: 'center' }}>
                        <span className={`top-rank top-rank-${idx + 1}`}>{idx + 1}</span>
                        <div>
                          <div style={{ fontWeight: 600, color: 'var(--ink)' }}>{b.tenMay}</div>
                          <span className="muted" style={{ fontSize: '0.78rem' }}>Mã: {b.maSanPham}</span>
                        </div>
                      </div>
                      <b style={{ color: 'var(--brand)', whiteSpace: 'nowrap' }}>{formatNumber(b.daBan)} máy</b>
                    </Link>
                  ))}
                </div>
              )}
            </div>

            <div className="card">
              <div className="dashboard-card-header">
                <div className="row between">
                  <h3>Tình trạng tồn kho theo phiên bản (SKU)</h3>
                  <Link to="/admin/inventory" className="muted" style={{ fontSize: '0.85rem' }}>Quản lý kho →</Link>
                </div>
                <p>Bấm một phần biểu đồ hoặc chú giải để mở tồn kho theo nhóm (ngưỡng sắp hết: 1–10).</p>
              </div>

              <StackBar
                items={[
                  { key: 'con-hang', name: 'Còn hàng (>10)', value: s.soConHang, color: COLORS.aqua },
                  { key: 'sap-het', name: 'Sắp hết (1–10)', value: s.soSapHetHang, color: COLORS.yellow },
                  { key: 'het', name: 'Hết hàng (0)', value: s.soHetHang, color: COLORS.red },
                ]}
                onSelect={(item) => navigate(`/admin/inventory?loc=${item.key}`)}
              />

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginTop: 18 }}>
                <Link to="/admin/inventory?loc=het" className="card stat stat-link" style={{ padding: 10, textAlign: 'center' }}>
                  <span className="muted" style={{ fontSize: '0.75rem' }}>Hết hàng</span>
                  <b style={{ color: COLORS.red }}>{formatNumber(s.soHetHang)}</b>
                  <span className="sub">SKU</span>
                </Link>
                <Link to="/admin/inventory?loc=sap-het" className="card stat stat-link" style={{ padding: 10, textAlign: 'center' }}>
                  <span className="muted" style={{ fontSize: '0.75rem' }}>Sắp hết</span>
                  <b style={{ color: COLORS.yellow }}>{formatNumber(s.soSapHetHang)}</b>
                  <span className="sub">SKU</span>
                </Link>
                <Link to="/admin/inventory?loc=con-hang" className="card stat stat-link" style={{ padding: 10, textAlign: 'center' }}>
                  <span className="muted" style={{ fontSize: '0.75rem' }}>Tồn kho an toàn</span>
                  <b style={{ color: COLORS.aqua }}>{formatNumber(s.soConHang)}</b>
                  <span className="sub">SKU</span>
                </Link>
              </div>
            </div>
          </div>
        </>
      )}
    </>
  );
}
