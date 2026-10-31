
import { useState } from 'react';
import Modal from '../../components/Modal';
import StatusBadge from '../../components/StatusBadge';
import { ORDER_STATUS } from '../../config/constants';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import {
  errorMessage,
  formatDate,
  formatPrice
} from '../../utils/format';

// Chỉ hiển thị các bước tiếp theo hợp lệ.
// Backend cũng phải kiểm tra quy tắc này.
const NEXT = {
  CHO_XAC_NHAN: ['DA_XAC_NHAN', 'DA_HUY'],
  DA_XAC_NHAN: ['DANG_CHUAN_BI', 'DA_HUY'],
  DANG_CHUAN_BI: ['DANG_GIAO', 'DA_HUY'],
  DANG_GIAO: [],
  CHO_KHACH_XAC_NHAN: [],
  DA_GIAO: [],
  DA_HUY: [],
};

const empty = {
  q: '',
  trangThai: '',
  from: '',
  to: '',
  minTotal: '',
  maxTotal: '',
  sort: 'ngay_moi',
  size: 20
};

export default function AdminOrdersPage() {

  const [draft, setDraft] = useState({ ...empty });
  const [filter, setFilter] = useState({ ...empty });
  const [page, setPage] = useState(0);

  const [selected, setSelected] = useState(null);
  const [xacNhanGiao, setXacNhanGiao] = useState(null);
  const [dangGui, setDangGui] = useState(false);

  const toast = useToast();

  // Gọi API lọc và phân trang tại Backend
  const {
    data: result,
    loading,
    error,
    reload
  } = useFetch(
      () => adminService.filterOrders({
        q: filter.q || undefined,
        trangThai: filter.trangThai || undefined,
        from: filter.from || undefined,
        to: filter.to || undefined,
        minTotal: filter.minTotal === ''
            ? undefined : filter.minTotal,
        maxTotal: filter.maxTotal === ''
            ? undefined : filter.maxTotal,
        sort: filter.sort,
        size: filter.size,
        page,
      }),
      [filter, page]
  );

  const orders = result?.content || [];

  const update = (name, value) => {
    setDraft((prev) => ({
      ...prev,
      [name]: value
    }));
  };

  // Tìm kiếm
  const search = (e) => {
    e.preventDefault();

    if (draft.from && draft.to && draft.from > draft.to) {
      return toast('Khoảng ngày không hợp lệ', 'error');
    }

    if (
        draft.minTotal !== '' &&
        draft.maxTotal !== '' &&
        Number(draft.minTotal) > Number(draft.maxTotal)
    ) {
      return toast('Khoảng tổng tiền không hợp lệ', 'error');
    }

    setPage(0);
    setFilter({
      ...draft,
      q: draft.q.trim()
    });
  };

  // Xóa bộ lọc
  const reset = () => {
    setDraft({ ...empty });
    setFilter({ ...empty });
    setPage(0);
  };

  // Cập nhật đơn hàng
  const run = async (action, message) => {
    try {
      const updated = await action();
      toast(message);

      if (
          updated?.maDonHang &&
          selected?.maDonHang === updated.maDonHang
      ) {
        setSelected(updated);
      }

    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      reload();
    }
  };

  // Tính năng 18: Admin đánh dấu đã giao
  const danhDauDaGiao = async () => {
    if (dangGui || !xacNhanGiao) return;

    setDangGui(true);

    try {
      const updated = await adminService.markDelivered(
          xacNhanGiao.maDonHang
      );

      toast(
          'Đã chuyển đơn sang trạng thái chờ khách hàng xác nhận'
      );

      if (selected?.maDonHang === updated.maDonHang) {
        setSelected(updated);
      }

      setXacNhanGiao(null);

    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setDangGui(false);
      reload();
    }
  };

  const thoiGian = (o) => {
    if (o.trangThai === 'CHO_KHACH_XAC_NHAN') {
      return `Đã giao lúc ${formatDate(o.ngayGiao)} · chờ khách xác nhận`;
    }

    if (o.trangThai === 'DA_GIAO' && o.ngayKhachXacNhan) {
      return `${o.nguoiXacNhan || 'Khách hàng'} xác nhận lúc ${formatDate(o.ngayKhachXacNhan)}`;
    }

    return null;
  };

  // Trạng thái và hành động hợp lệ
  const trangThaiCell = (o) => {
    const next = NEXT[o.trangThai] || [];

    if (!next.length && o.trangThai !== 'DANG_GIAO') {
      return (
          <>
            <StatusBadge status={o.trangThai} />

            {thoiGian(o) && (
                <div className="muted">
                  <small>{thoiGian(o)}</small>
                </div>
            )}
          </>
      );
    }

    return (
        <div className="admin-order-status-actions">
          {next.length > 0 ? (
              <select
                  className="admin-order-status-select"
                  value={o.trangThai}
                  onChange={(e) =>
                      run(
                          () => adminService.updateOrderStatus(
                              o.maDonHang,
                              e.target.value
                          ),
                          'Đã cập nhật trạng thái'
                      )
                  }
              >
                <option value={o.trangThai}>
                  {ORDER_STATUS[o.trangThai] || o.trangThai}
                </option>

                {next.map((value) => (
                    <option key={value} value={value}>
                      {ORDER_STATUS[value] || value}
                    </option>
                ))}
              </select>
          ) : (
              <StatusBadge status={o.trangThai} />
          )}

          {o.trangThai === 'DANG_GIAO' && (
              <button
                  className="btn btn-primary"
                  onClick={() => setXacNhanGiao(o)}
              >
                Xác nhận đã giao hàng
              </button>
          )}
        </div>
    );
  };

  return (
      <>
        <h2>Quản lý đơn hàng</h2>

        {/* BỘ LỌC TÍNH NĂNG 17 */}
        <form
            className="admin-order-filter"
            onSubmit={search}
        >
          <input
              aria-label="Tìm đơn hàng"
              placeholder="Mã đơn / tên khách / người nhận / SĐT"
              value={draft.q}
              onChange={(e) => update('q', e.target.value)}
          />

          <select
              aria-label="Trạng thái đơn"
              value={draft.trangThai}
              onChange={(e) =>
                  update('trangThai', e.target.value)
              }
          >
            <option value="">Tất cả trạng thái</option>

            {Object.entries(ORDER_STATUS)
                .filter(([v]) => v !== 'DANG_XU_LY')
                .map(([v, label]) => (
                    <option key={v} value={v}>
                      {label}
                    </option>
                ))}
          </select>

          <label>
            Từ ngày
            <input
                type="date"
                value={draft.from}
                onChange={(e) => update('from', e.target.value)}
            />
          </label>

          <label>
            Đến ngày
            <input
                type="date"
                value={draft.to}
                onChange={(e) => update('to', e.target.value)}
            />
          </label>

          <label>
            Tổng tiền từ
            <input
                type="number"
                min="0"
                value={draft.minTotal}
                onChange={(e) =>
                    update('minTotal', e.target.value)
                }
                placeholder="VND"
            />
          </label>

          <label>
            Tổng tiền đến
            <input
                type="number"
                min="0"
                value={draft.maxTotal}
                onChange={(e) =>
                    update('maxTotal', e.target.value)
                }
                placeholder="VND"
            />
          </label>

          <select
              aria-label="Sắp xếp"
              value={draft.sort}
              onChange={(e) => update('sort', e.target.value)}
          >
            <option value="ngay_moi">Mới nhất trước</option>
            <option value="ngay_cu">Cũ nhất trước</option>
            <option value="tien_cao">
              Tổng tiền cao đến thấp
            </option>
            <option value="tien_thap">
              Tổng tiền thấp đến cao
            </option>
          </select>

          <select
              aria-label="Số đơn mỗi trang"
              value={draft.size}
              onChange={(e) =>
                  update('size', Number(e.target.value))
              }
          >
            {[10, 20, 50].map((n) => (
                <option key={n} value={n}>
                  {n} đơn / trang
                </option>
            ))}
          </select>

          <button className="btn btn-primary" type="submit">
            Tìm kiếm
          </button>

          <button
              className="btn"
              type="button"
              onClick={reset}
          >
            Xóa bộ lọc
          </button>
        </form>

        {/* DANH SÁCH ĐƠN HÀNG */}
        {loading && (
            <p className="muted">Đang tải đơn hàng...</p>
        )}

        {error && <p className="error">{error}</p>}

        {!error && (
            <div style={{ overflowX: 'auto' }}>
              <table className="table admin-order-table">
                <thead>
                <tr>
                  <th>Mã đơn</th>
                  <th>Khách hàng</th>
                  <th>Ngày mua</th>
                  <th>Sản phẩm</th>
                  <th>Tổng tiền</th>
                  <th>Trạng thái</th>
                  <th></th>
                </tr>
                </thead>

                <tbody>
                {orders.map((o) => (
                    <tr key={o.maDonHang}>
                      <td>{o.maDonHang}</td>
                      <td>{o.tenKhach || o.maKh}</td>
                      <td>{formatDate(o.ngayMua)}</td>
                      <td>
                        {(o.chiTiet || [])
                            .map((d) =>
                                `${d.tenMay} × ${d.soLuong}`
                            )
                            .join(', ')}
                      </td>
                      <td>{formatPrice(o.tongTien)}</td>
                      <td>{trangThaiCell(o)}</td>
                      <td>
                        <button
                            className="btn"
                            onClick={() => setSelected(o)}
                        >
                          Chi tiết
                        </button>
                      </td>
                    </tr>
                ))}

                {!loading && orders.length === 0 && (
                    <tr>
                      <td colSpan="7" className="muted">
                        Không có đơn hàng phù hợp.
                      </td>
                    </tr>
                )}
                </tbody>
              </table>
            </div>
        )}

        {/* PHÂN TRANG */}
        {!error && (
            <div className="admin-order-pagination">
              <button
                  className="btn"
                  disabled={loading || page === 0}
                  onClick={() => setPage((p) => p - 1)}
              >
                Trang trước
              </button>

              <span>
            Trang {Math.min(page + 1, result?.totalPages || 1)}
                /{result?.totalPages || 1}
                {' · '}
                {result?.totalElements ?? 0} đơn
          </span>

              <button
                  className="btn"
                  disabled={
                      loading ||
                      !result ||
                      page + 1 >= result.totalPages
                  }
                  onClick={() => setPage((p) => p + 1)}
              >
                Trang sau
              </button>
            </div>
        )}

        {/* MODAL CHI TIẾT ĐƠN */}
        {selected && (
            <Modal
                title={`Đơn ${selected.maDonHang}`}
                onClose={() => setSelected(null)}
            >
              <div className="admin-order-customer-info">
                <p>
                  <strong>Mã đơn hàng:</strong> {selected.maDonHang}
                </p>

                <p>
                  <strong>Khách hàng:</strong> {selected.tenKhach || selected.maKh}
                </p>

                <p>
                  <strong>Số điện thoại:</strong>{' '}
                  {laySoDienThoai(selected) || 'Chưa có thông tin'}
                </p>

                <p>
                  <strong>Địa chỉ giao hàng:</strong>{' '}
                  {selected.diaChiGiaoHang || '—'}
                </p>

                <p>
                  <strong>Ngày đặt hàng:</strong>{' '}
                  {formatDate(selected.ngayMua)}
                </p>
              </div>

              <p>
                Giao đến: {selected.diaChiGiaoHang || '—'}
              </p>

              {selected.ngayGiao && (
                  <p>
                    Admin đánh dấu đã giao:
                    {' '}
                    {formatDate(selected.ngayGiao)}
                  </p>
              )}

              {selected.ngayKhachXacNhan && (
                  <p>
                    Khách xác nhận:
                    {' '}
                    {formatDate(selected.ngayKhachXacNhan)}
                    {' '}
                    ({selected.nguoiXacNhan || 'Khách hàng'})
                  </p>
              )}

              {(selected.chiTiet || []).map((d) => (
                  <div
                      key={d.maPhienBan}
                      className="row between"
                  >
              <span>
                {d.tenMay} · {formatPrice(d.donGia)}
              </span>

                    {selected.trangThai === 'CHO_XAC_NHAN' ? (
                        <input
                            className="qty"
                            type="number"
                            min="0"
                            defaultValue={d.soLuong}
                            onBlur={(e) =>
                                Number(e.target.value) !== d.soLuong &&
                                run(
                                    () => adminService.updateOrderLine(
                                        selected.maDonHang,
                                        d.maPhienBan,
                                        Number(e.target.value)
                                    ),
                                    'Đã cập nhật số lượng'
                                )
                            }
                        />
                    ) : (
                        <span>× {d.soLuong}</span>
                    )}
                  </div>
              ))}

              <p className="muted">
                Chỉ chỉnh số lượng được khi đơn đang chờ xác nhận.
              </p>

              <div className="summary">
                <div className="row between">
                  <span>Tạm tính</span>
                  <span>{formatPrice(selected.tamTinh)}</span>
                </div>

                <div className="row between">
              <span>
                Giảm giá {selected.maKm && `(${selected.maKm})`}
              </span>
                  <span>- {formatPrice(selected.giamGia)}</span>
                </div>

                <div className="row between">
                  <b>Tổng</b>
                  <b className="price">
                    {formatPrice(selected.tongTien)}
                  </b>
                </div>
              </div>

              {trangThaiCell(selected)}
            </Modal>
        )}

        {/* MODAL XÁC NHẬN ĐÃ GIAO - TÍNH NĂNG 18 */}
        {xacNhanGiao && (
            <Modal
                title="Xác nhận đã giao hàng"
                onClose={() =>
                    !dangGui && setXacNhanGiao(null)
                }
            >
              <p>
                Bạn xác nhận đơn
                {' '}
                <b>{xacNhanGiao.maDonHang}</b>
                {' '}
                đã được giao đến khách?
                Đơn sẽ chuyển sang chờ khách xác nhận.
              </p>

              <div className="row end">
                <button
                    className="btn"
                    disabled={dangGui}
                    onClick={() => setXacNhanGiao(null)}
                >
                  Hủy
                </button>

                <button
                    className="btn btn-primary"
                    disabled={dangGui}
                    onClick={danhDauDaGiao}
                >
                  {dangGui
                      ? 'Đang xử lý...'
                      : 'Xác nhận đã giao'}
                </button>
              </div>
            </Modal>
        )}
      </>
  );
}
const laySoDienThoai = (o) => {
  if (o.soDienThoaiNhan) {
    return o.soDienThoaiNhan;
  }

  const diaChi = o.diaChiGiaoHang || '';

  const match = diaChi.match(/(?:^|[^\d])((?:\+84|0)\d{9})(?!\d)/);

  return match ? match[1] : null;
};
