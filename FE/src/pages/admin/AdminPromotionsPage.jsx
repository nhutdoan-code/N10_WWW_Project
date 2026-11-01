import { useState } from 'react';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatPrice } from '../../utils/format';

const EMPTY = {
  maKm: '',
  tenKm: '',
  loai: 'DON_HANG',
  phanTramGiam: '',
  soTienGiamToiDa: '',
  donToiThieu: 0,
  soLuong: '',
  chiKhachMoi: false,
  ngayBatDau: '',
  ngayKetThuc: '',
  dieuKienApDung: '',
  trangThaiHoatDong: true,
};

export default function AdminPromotionsPage() {
  const { data: list, reload } = useFetch(adminService.promotions);
  const [editing, setEditing] = useState(null);
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Impact modal state
  const [impactKm, setImpactKm] = useState(null);
  const [impactData, setImpactData] = useState(null);
  const [impactLoading, setImpactLoading] = useState(false);

  const toast = useToast();
  const today = new Date().toISOString().slice(0, 10);

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      setEditing(null);
      setImpactKm(null);
      setImpactData(null);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const openImpact = async (km) => {
    setImpactKm(km);
    setImpactData(null);
    setImpactLoading(true);
    try {
      const data = await adminService.promotionImpact(km.maKm);
      setImpactData(data);
    } catch (e) {
      toast(errorMessage(e), 'error');
      setImpactKm(null);
    } finally {
      setImpactLoading(false);
    }
  };

  const set = (key) => (e) => setEditing({ ...editing, [key]: e.target.value });

  const submit = (e) => {
    e.preventDefault();
    const { isNew, ...data } = editing;

    const pct = Number(data.phanTramGiam);
    if (isNaN(pct) || pct <= 0 || pct > 100) {
      toast('Phần trăm giảm giá phải nằm trong khoảng từ 1% đến 100%', 'error');
      return;
    }

    if (data.ngayBatDau && data.ngayKetThuc && data.ngayBatDau > data.ngayKetThuc) {
      toast('Ngày bắt đầu không được sau ngày kết thúc', 'error');
      return;
    }

    run(
      () =>
        adminService.savePromotion(isNew ? null : data.maKm, {
          ...data,
          phanTramGiam: pct,
          soTienGiamToiDa:
            data.soTienGiamToiDa === '' || data.soTienGiamToiDa == null
              ? null
              : Number(data.soTienGiamToiDa),
          donToiThieu: Number(data.donToiThieu || 0),
          soLuong:
            data.soLuong === '' || data.soLuong == null
              ? null
              : Number(data.soLuong),
          trangThaiHoatDong: data.trangThaiHoatDong ?? true,
        }),
      'Đã lưu khuyến mãi'
    );
  };

  const filteredList = (list || []).filter((k) => {
    const isActive = k.trangThaiHoatDong !== false;
    if (statusFilter === 'ACTIVE') return isActive;
    if (statusFilter === 'INACTIVE') return !isActive;
    return true;
  });

  return (
    <>
      <div className="row between" style={{ alignItems: 'center', marginBottom: '16px' }}>
        <h2>Quản lý khuyến mãi</h2>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY, isNew: true })}>
          + Tạo mã khuyến mãi
        </button>
      </div>

      <div className="row" style={{ gap: '12px', marginBottom: '16px', alignItems: 'center' }}>
        <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem' }}>
          <span>Lọc theo trạng thái hoạt động:</span>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            style={{ minWidth: '180px' }}
          >
            <option value="ALL">Tất cả khuyến mãi</option>
            <option value="ACTIVE">Đang kích hoạt</option>
            <option value="INACTIVE">Ngừng hoạt động</option>
          </select>
        </label>
      </div>

      <table className="table">
        <thead>
          <tr>
            <th>Mã</th>
            <th>Tên chương trình</th>
            <th>Loại</th>
            <th>Giảm</th>
            <th>Tối đa</th>
            <th>Đơn tối thiểu</th>
            <th>Đã dùng</th>
            <th>Thời gian</th>
            <th>Hiệu lực</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {filteredList.map((k) => {
            const isActive = k.trangThaiHoatDong !== false;
            const hieuLuc = k.ngayBatDau <= today && today <= k.ngayKetThuc;
            return (
              <tr key={k.maKm} style={{ opacity: isActive ? 1 : 0.65 }}>
                <td><b><code>{k.maKm}</code></b></td>
                <td>{k.tenKm}</td>
                <td>{k.loai === 'VAN_CHUYEN' ? 'Vận chuyển' : 'Đơn hàng'}</td>
                <td><b>{k.phanTramGiam}%</b></td>
                <td>{k.soTienGiamToiDa ? formatPrice(k.soTienGiamToiDa) : '—'}</td>
                <td>{Number(k.donToiThieu) > 0 ? formatPrice(k.donToiThieu) : '—'}</td>
                <td>{k.daDung}{k.soLuong != null && ` / ${k.soLuong}`}</td>
                <td><small>{k.ngayBatDau} → {k.ngayKetThuc}</small></td>
                <td>
                  <span className={`badge ${hieuLuc ? 'badge-audit-create' : 'badge-audit-delete'}`}>
                    {hieuLuc ? 'Trong hạn' : today < k.ngayBatDau ? 'Sắp diễn ra' : 'Hết hạn'}
                  </span>
                </td>
                <td>
                  <span className={`badge ${isActive ? 'badge-audit-create' : 'badge-audit-delete'}`}>
                    {isActive ? 'Đang hoạt động' : 'Đã dừng'}
                  </span>
                </td>
                <td>
                  <div className="row" style={{ gap: '6px' }}>
                    <button className="btn" onClick={() => setEditing(k)}>Sửa</button>
                    {isActive ? (
                      <button
                        className="btn btn-danger"
                        onClick={() => openImpact(k)}
                        title="Dừng hoạt động mã để tránh lỗi các đơn hàng liên kết"
                      >
                        Dừng
                      </button>
                    ) : (
                      <button
                        className="btn btn-primary"
                        onClick={() => run(() => adminService.restorePromotion(k.maKm), 'Đã kích hoạt lại mã khuyến mãi')}
                      >
                        Kích hoạt
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
          {filteredList.length === 0 && (
            <tr>
              <td colSpan={11} style={{ textAlign: 'center', padding: '24px', color: '#666' }}>
                Không có mã khuyến mãi nào phù hợp.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {/* Edit / Create Promotion Modal */}
      {editing && (
        <Modal title={editing.isNew ? 'Tạo khuyến mãi mới' : 'Sửa khuyến mãi'} onClose={() => setEditing(null)}>
          <form className="form" onSubmit={submit}>
            <FormField label="Mã khuyến mãi" value={editing.maKm} onChange={set('maKm')} disabled={!editing.isNew} required />
            <FormField label="Tên chương trình" value={editing.tenKm} onChange={set('tenKm')} required />
            <label className="field">
              <span>Loại voucher</span>
              <select value={editing.loai || 'DON_HANG'} onChange={set('loai')}>
                <option value="DON_HANG">Giảm giá đơn hàng (tối đa 1 voucher / đơn)</option>
                <option value="VAN_CHUYEN">Giảm phí vận chuyển (tối đa 1 voucher / đơn)</option>
              </select>
            </label>
            <div className="grid-2">
              <FormField label="Phần trăm giảm (1 - 100%)" type="number" min="1" max="100" value={editing.phanTramGiam} onChange={set('phanTramGiam')} required />
              <FormField label="Giảm tối đa (₫)" type="number" min="0" value={editing.soTienGiamToiDa ?? ''} onChange={set('soTienGiamToiDa')} />
            </div>
            <div className="grid-2">
              <FormField label="Đơn tối thiểu (₫)" type="number" min="0" value={editing.donToiThieu ?? 0} onChange={set('donToiThieu')} />
              <FormField label="Số lượng mã (để trống = không giới hạn)" type="number" min="1" value={editing.soLuong ?? ''} onChange={set('soLuong')} />
            </div>
            <label className="option">
              <input type="checkbox" checked={!!editing.chiKhachMoi} onChange={(e) => setEditing({ ...editing, chiKhachMoi: e.target.checked })} /> Chỉ dành cho khách mua lần đầu
            </label>
            <div className="grid-2">
              <FormField label="Từ ngày" type="date" value={editing.ngayBatDau} onChange={set('ngayBatDau')} required />
              <FormField label="Đến ngày" type="date" value={editing.ngayKetThuc} onChange={set('ngayKetThuc')} required />
            </div>
            <FormField label="Điều kiện áp dụng" value={editing.dieuKienApDung || ''} onChange={set('dieuKienApDung')} />
            <button className="btn btn-primary">Lưu thông tin</button>
          </form>
        </Modal>
      )}

      {/* Promotion Soft Delete / Archive Impact Modal */}
      {impactKm && (
        <Modal
          title="Xác nhận dừng hoạt động khuyến mãi"
          onClose={() => {
            setImpactKm(null);
            setImpactData(null);
          }}
        >
          {impactLoading ? (
            <p className="center muted">Đang kiểm tra lịch sử đơn hàng của mã khuyến mãi...</p>
          ) : (
            <div>
              <p style={{ marginTop: 0 }}>
                Bạn có chắc chắn muốn dừng hoạt động mã khuyến mãi <b>{impactKm.maKm}</b> ({impactKm.tenKm})?
              </p>

              <div
                style={{
                  background: '#f8f9fa',
                  padding: '12px 16px',
                  borderRadius: '8px',
                  marginBottom: '16px',
                  fontSize: '0.9rem',
                }}
              >
                <div className="row between" style={{ marginBottom: '6px' }}>
                  <span>Số lượt đã sử dụng:</span>
                  <b>{impactData?.daDung ?? impactKm.daDung ?? 0}</b>
                </div>
                <div className="row between">
                  <span>Số đơn hàng liên kết trong hệ thống:</span>
                  <b style={{ color: (impactData?.donHangCount || 0) > 0 ? '#b91c1c' : '#15803d' }}>
                    {impactData?.donHangCount ?? 0} đơn
                  </b>
                </div>
              </div>

              {(impactData?.donHangCount || 0) > 0 ? (
                <div
                  style={{
                    background: '#fef2f2',
                    border: '1px solid #f87171',
                    borderRadius: '8px',
                    padding: '10px 14px',
                    color: '#991b1b',
                    fontSize: '0.85rem',
                    marginBottom: '16px',
                  }}
                >
                  <strong>Bảo vệ lịch sử kế toán:</strong> Mã khuyến mãi này đã được áp dụng trong{' '}
                  <b>{impactData.donHangCount}</b> đơn hàng. Việc xóa vật lý hoàn toàn khỏi cơ sở dữ liệu bị chặn để tránh phá vỡ tính toàn vẹn của hóa đơn. Thao tác này sẽ chuyển mã sang trạng thái <b>Ngừng hoạt động</b> (soft archive), ngăn khách hàng mới sử dụng nhưng bảo lưu lịch sử đơn hàng cũ.
                </div>
              ) : (
                <p className="muted" style={{ fontSize: '0.85rem', marginBottom: '16px' }}>
                  Mã khuyến mãi sẽ không thể áp dụng cho các đơn hàng mới. Bạn có thể kích hoạt lại bất kỳ lúc nào.
                </p>
              )}

              <div className="row" style={{ justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  className="btn"
                  onClick={() => {
                    setImpactKm(null);
                    setImpactData(null);
                  }}
                >
                  Hủy bỏ
                </button>
                <button
                  className="btn btn-danger"
                  onClick={() =>
                    run(
                      () => adminService.archivePromotion(impactKm.maKm),
                      'Đã dừng hoạt động mã khuyến mãi'
                    )
                  }
                >
                  Xác nhận dừng hoạt động
                </button>
              </div>
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
