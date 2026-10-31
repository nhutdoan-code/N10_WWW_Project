
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import ProductImage from '../components/ProductImage';
import StatusBadge from '../components/StatusBadge';
import Modal from '../components/Modal';

import { ORDER_STATUS, PAYMENT_METHODS } from '../config/constants';

import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';

import { orderService } from '../service/orderService';
import { errorMessage, formatDate, formatPrice } from '../utils/format';

const SHOW_ITEMS = 2;

export default function OrdersPage() {
  const {
    data: orders,
    loading,
    error,
    reload
  } = useFetch(orderService.list);

  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState('');
  const [oldest, setOldest] = useState(false);

  // Tính năng 18: xác nhận nhận hàng
  const [xacNhan, setXacNhan] = useState(null);
  const [dangGui, setDangGui] = useState(false);

  const toast = useToast();

  const tt = params.get('tt') || '';

  // Đếm số lượng đơn theo trạng thái
  const counts = useMemo(() => {
    const c = { '': orders?.length || 0 };

    (orders || []).forEach((o) => {
      c[o.trangThai] = (c[o.trangThai] || 0) + 1;
    });

    return c;
  }, [orders]);

  // Tìm kiếm, lọc, sắp xếp đơn hàng
  const shown = useMemo(() => {
    const key = q.trim().toLowerCase();

    const list = (orders || []).filter((o) =>
        (!tt || o.trangThai === tt) &&
        (
            !key ||
            o.maDonHang.toLowerCase().includes(key) ||
            (o.chiTiet || []).some((d) =>
                (d.tenMay || '').toLowerCase().includes(key)
            )
        )
    );

    return oldest ? [...list].reverse() : list;
  }, [orders, tt, q, oldest]);

  // Khách xác nhận đã nhận được hàng
  const guiXacNhan = async () => {
    if (
        dangGui ||
        !xacNhan ||
        xacNhan.trangThai !== 'CHO_KHACH_XAC_NHAN'
    ) {
      return;
    }

    setDangGui(true);

    try {
      await orderService.confirmReceived(xacNhan.maDonHang);

      toast(
          'Xác nhận nhận hàng thành công! Đơn hàng của bạn đã được hoàn tất.'
      );

      setXacNhan(null);
      await reload();

    } catch (e) {
      toast(errorMessage(e), 'error');

    } finally {
      setDangGui(false);
    }
  };

  // Hooks phải được gọi trước các return có điều kiện
  if (loading) {
    return <p className="muted">Đang tải...</p>;
  }

  if (error) {
    return <p className="error">{error}</p>;
  }

  if (!orders || orders.length === 0) {
    return (
        <p className="muted">
          Bạn chưa có đơn hàng nào.{' '}
          <Link to="/">Mua sắm ngay</Link>
        </p>
    );
  }

  const tabs = [
    ['', 'Tất cả'],
    ...Object.entries(ORDER_STATUS)
  ];

  const chon = (v) => {
    setParams(v ? { tt: v } : {});
  };

  return (
      <>
        <h2>Lịch sử đơn hàng</h2>

        {/* Tabs trạng thái */}
        <div className="status-tabs">
          {tabs.map(([v, label]) => (
              <button
                  key={v}
                  className={tt === v ? 'tab active' : 'tab'}
                  onClick={() => chon(v)}
              >
                {label}
                {' '}
                <span className="count">
              {counts[v] || 0}
            </span>
              </button>
          ))}
        </div>

        {/* Tìm kiếm và sắp xếp */}
        <div className="order-tools">
          <input
              placeholder="Tìm theo mã đơn hoặc tên sản phẩm..."
              value={q}
              onChange={(e) => setQ(e.target.value)}
          />

          <select
              value={oldest ? 'old' : 'new'}
              onChange={(e) =>
                  setOldest(e.target.value === 'old')
              }
          >
            <option value="new">Mới nhất trước</option>
            <option value="old">Cũ nhất trước</option>
          </select>
        </div>

        {shown.length === 0 && (
            <p className="muted">
              Không có đơn hàng nào phù hợp.
            </p>
        )}

        {/* Danh sách đơn hàng */}
        {shown.map((o) => (
            <div
                key={o.maDonHang}
                className="card order"
            >
              <div className="row between">
                <Link to={`/orders/${o.maDonHang}`}>
                  <b>{o.maDonHang}</b>
                </Link>

                <StatusBadge status={o.trangThai} />
              </div>

              <p className="muted">
                {formatDate(o.ngayMua)}
                {' · '}
                {PAYMENT_METHODS.find(
                    (m) => m.value === o.phuongThucTt
                )?.label}

                {o.maKm && ` · Mã ${o.maKm}`}
              </p>

              {/* Sản phẩm trong đơn */}
              {(o.chiTiet || [])
                  .slice(0, SHOW_ITEMS)
                  .map((d) => (
                      <div
                          key={d.maPhienBan || d.maSanPham}
                          className="row between"
                      >
                <span className="row">
                  <ProductImage
                      className="thumb"
                      src={d.anhChinh}
                      alt=""
                  />

                  {d.tenMay} × {d.soLuong}
                </span>

                        <span>
                  {formatPrice(
                      d.donGia * d.soLuong
                  )}
                </span>
                      </div>
                  ))}

              {(o.chiTiet || []).length > SHOW_ITEMS && (
                  <p className="muted">
                    và {o.chiTiet.length - SHOW_ITEMS} sản phẩm khác
                  </p>
              )}

              {/* Tổng tiền và thao tác */}
              <div className="row between">
                <b className="price">
                  Tổng: {formatPrice(o.tongTien)}
                </b>

                <span className="row">

              {/* Tính năng 18 */}
                  {o.trangThai === 'CHO_KHACH_XAC_NHAN' && (
                      <button
                          className="btn btn-primary"
                          onClick={() => setXacNhan(o)}
                      >
                        Đã nhận được hàng
                      </button>
                  )}

                  <Link
                      className="btn"
                      to={`/orders/${o.maDonHang}`}
                  >
                Xem chi tiết
              </Link>

            </span>
              </div>
            </div>
        ))}

        {/* Modal xác nhận đã nhận hàng */}
        {xacNhan && (
            <Modal
                title="Xác nhận đã nhận hàng"
                onClose={() => {
                  if (!dangGui) {
                    setXacNhan(null);
                  }
                }}
            >
              <p>
                Bạn xác nhận đã nhận đầy đủ sản phẩm của đơn hàng{' '}
                <b>{xacNhan.maDonHang}</b>?
              </p>

              <p className="muted">
                Sau khi xác nhận, đơn hàng sẽ được hoàn tất.
              </p>

              <div className="row end">
                <button
                    className="btn"
                    disabled={dangGui}
                    onClick={() => setXacNhan(null)}
                >
                  Hủy
                </button>

                <button
                    className="btn btn-primary"
                    disabled={dangGui}
                    onClick={guiXacNhan}
                >
                  {dangGui
                      ? 'Đang xử lý...'
                      : 'Xác nhận đã nhận'}
                </button>
              </div>
            </Modal>
        )}
      </>
  );
}
