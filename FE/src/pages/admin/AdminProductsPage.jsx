import { useState } from 'react';
import Modal from '../../components/Modal';
import ProductForm from '../../components/ProductForm';
import ProductImage from '../../components/ProductImage';
import StatusBadge from '../../components/StatusBadge';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { productService } from '../../service/productService';
import { errorMessage, formatPrice } from '../../utils/format';
import { productStatus } from '../../utils/stock';

export default function AdminProductsPage() {
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [editing, setEditing] = useState(null);
  const [syncing, setSyncing] = useState(false);
  const [showSyncModal, setShowSyncModal] = useState(false);

  // Impact modal state
  const [impactProduct, setImpactProduct] = useState(null);
  const [impactData, setImpactData] = useState(null);
  const [impactLoading, setImpactLoading] = useState(false);

  const debounced = useDebounce(q);
  const toast = useToast();
  const { data: products, reload } = useFetch(() => adminService.products(debounced), [debounced]);
  const { data: categories } = useFetch(productService.categories);
  const { data: brands } = useFetch(productService.brands);

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      setEditing(null);
      setImpactProduct(null);
      setImpactData(null);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const handleSyncConfirm = async () => {
    try {
      setSyncing(true);
      const res = await adminService.reindexProducts();
      const count = res?.soLuong ?? 0;
      toast(`Đồng bộ dữ liệu AI thành công. Đã cập nhật ${count} sản phẩm.`);
      setShowSyncModal(false);
    } catch (e) {
      toast('Không thể đồng bộ dữ liệu AI. Dịch vụ tư vấn thông minh hiện chưa sẵn sàng. Vui lòng thử lại sau.', 'error');
    } finally {
      setSyncing(false);
    }
  };

  const openImpact = async (product) => {
    setImpactProduct(product);
    setImpactData(null);
    setImpactLoading(true);
    try {
      const data = await adminService.productImpact(product.maSanPham);
      setImpactData(data);
    } catch (e) {
      toast(errorMessage(e), 'error');
      setImpactProduct(null);
    } finally {
      setImpactLoading(false);
    }
  };

  const filteredProducts = (products || []).filter((p) => {
    if (statusFilter === 'ACTIVE') {
      return p.trangThai !== 'NGUNG_KINH_DOANH';
    }
    if (statusFilter === 'INACTIVE') {
      return p.trangThai === 'NGUNG_KINH_DOANH';
    }
    return true;
  });

  return (
    <>
      <div className="row between" style={{ alignItems: 'center', marginBottom: '16px' }}>
        <h2>Quản lý sản phẩm</h2>
        <div className="row" style={{ gap: '10px', alignItems: 'center' }}>
          <div style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <button
              className="btn btn-secondary"
              onClick={() => setShowSyncModal(true)}
              disabled={syncing}
              title="Cập nhật lại dữ liệu sản phẩm dùng cho tính năng tìm kiếm và tư vấn thông minh. Thông thường hệ thống sẽ tự động đồng bộ khi thông tin sản phẩm thay đổi."
            >
              {syncing ? 'Đang đồng bộ dữ liệu AI...' : 'Đồng bộ dữ liệu AI'}
            </button>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '20px',
                height: '20px',
                borderRadius: '50%',
                background: '#e2e8f0',
                color: '#64748b',
                fontSize: '0.8rem',
                fontWeight: 700,
                cursor: 'help',
              }}
              title="Cập nhật lại dữ liệu sản phẩm dùng cho tính năng tìm kiếm và tư vấn thông minh. Thông thường hệ thống sẽ tự động đồng bộ khi thông tin sản phẩm thay đổi."
            >
              ?
            </span>
          </div>
          <button className="btn btn-primary" onClick={() => setEditing({})}>
            + Thêm sản phẩm
          </button>
        </div>
      </div>

      <div className="row" style={{ gap: '12px', marginBottom: '16px', alignItems: 'center' }}>
        <input
          style={{ flex: 1 }}
          placeholder="Tìm theo tên máy, mã sản phẩm, hãng..."
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{ minWidth: '180px' }}
        >
          <option value="ALL">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang kinh doanh</option>
          <option value="INACTIVE">Ngừng kinh doanh</option>
        </select>
      </div>

      <table className="table">
        <thead>
          <tr>
            <th>Mã</th>
            <th>Ảnh</th>
            <th>Tên máy</th>
            <th>Hãng</th>
            <th>Giá</th>
            <th>Tồn</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {filteredProducts.map((p) => {
            const isArchived = p.trangThai === 'NGUNG_KINH_DOANH';
            return (
              <tr key={p.maSanPham}>
                <td><code>{p.maSanPham}</code></td>
                <td><ProductImage className="thumb" src={p.anhChinh} alt="" /></td>
                <td><b>{p.tenMay}</b></td>
                <td>{p.tenThuongHieu || p.tenLoai}</td>
                <td>{formatPrice(p.giaBan)}</td>
                <td>{p.soLuongTon}</td>
                <td><StatusBadge status={productStatus(p)} /></td>
                <td>
                  <div className="row" style={{ gap: '6px' }}>
                    <button className="btn" onClick={() => setEditing(p)}>Sửa</button>
                    {isArchived ? (
                      <button
                        className="btn btn-primary"
                        onClick={() => run(() => adminService.restoreProduct(p.maSanPham), 'Đã khôi phục sản phẩm kinh doanh')}
                      >
                        Khôi phục
                      </button>
                    ) : (
                      <button
                        className="btn btn-danger"
                        onClick={() => openImpact(p)}
                      >
                        Ngừng kinh doanh
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
          {filteredProducts.length === 0 && (
            <tr>
              <td colSpan={8} style={{ textAlign: 'center', padding: '24px', color: '#666' }}>
                Không tìm thấy sản phẩm nào phù hợp.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {/* Product Edit / Create Modal */}
      {editing && categories && (
        <Modal title={editing.maSanPham ? 'Sửa sản phẩm' : 'Thêm sản phẩm'} onClose={() => setEditing(null)} wide>
          <ProductForm
            key={editing.maSanPham || 'new'}
            product={editing}
            categories={categories}
            brands={brands || []}
            onCancel={() => setEditing(null)}
            onSubmit={(data) => run(() => adminService.saveProduct(editing.maSanPham, data), 'Đã lưu sản phẩm')}
          />
        </Modal>
      )}

      {/* Soft Delete / Archive Impact Modal */}
      {impactProduct && (
        <Modal
          title="Xác nhận ngừng kinh doanh sản phẩm"
          onClose={() => {
            setImpactProduct(null);
            setImpactData(null);
          }}
        >
          {impactLoading ? (
            <p className="center muted">Đang phân tích dữ liệu liên kết...</p>
          ) : (
            <div>
              <p style={{ marginTop: 0 }}>
                Bạn có chắc chắn muốn ngừng kinh doanh sản phẩm <b>{impactProduct.tenMay}</b> (mã <code>{impactProduct.maSanPham}</code>)?
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
                  <span>Số biến thể (màu sắc/bộ nhớ):</span>
                  <b>{impactData?.bienTheCount ?? 0}</b>
                </div>
                <div className="row between" style={{ marginBottom: '6px' }}>
                  <span>Số lượng tồn kho:</span>
                  <b>{impactData?.soLuongTon ?? impactProduct.soLuongTon ?? 0}</b>
                </div>
                <div className="row between" style={{ marginBottom: '6px' }}>
                  <span>Đơn hàng đang xử lý:</span>
                  <b style={{ color: (impactData?.activeOrdersCount || 0) > 0 ? '#b91c1c' : '#15803d' }}>
                    {impactData?.activeOrdersCount ?? 0}
                  </b>
                </div>
                <div className="row between">
                  <span>Tổng đơn hàng trong lịch sử:</span>
                  <b>{impactData?.totalOrdersCount ?? 0}</b>
                </div>
              </div>

              {(impactData?.activeOrdersCount || 0) > 0 ? (
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
                  <strong>Lưu ý an toàn dữ liệu:</strong> Sản phẩm này đang có{' '}
                  <b>{impactData.activeOrdersCount}</b> đơn hàng chưa hoàn tất. Hệ thống chặn xóa cứng (hard delete) nhằm đảm bảo toàn vẹn dữ liệu. Thao tác này sẽ chuyển sản phẩm sang trạng thái <b>Ngừng kinh doanh</b> (soft archive), ẩn khỏi trang bán lẻ nhưng giữ nguyên lịch sử đơn hàng.
                </div>
              ) : (
                <p className="muted" style={{ fontSize: '0.85rem', marginBottom: '16px' }}>
                  Sản phẩm sẽ được ẩn khỏi trang danh mục và tìm kiếm của khách hàng, nhưng vẫn bảo lưu toàn bộ lịch sử kế toán và có thể khôi phục bất cứ lúc nào.
                </p>
              )}

              <div className="row" style={{ justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  className="btn"
                  onClick={() => {
                    setImpactProduct(null);
                    setImpactData(null);
                  }}
                >
                  Hủy bỏ
                </button>
                <button
                  className="btn btn-danger"
                  onClick={() =>
                    run(
                      () => adminService.archiveProduct(impactProduct.maSanPham),
                      'Đã chuyển sản phẩm sang trạng thái ngừng kinh doanh'
                    )
                  }
                >
                  Xác nhận ngừng kinh doanh
                </button>
              </div>
            </div>
          )}
        </Modal>
      )}

      {/* Custom AI Sync Confirmation Modal */}
      {showSyncModal && (
        <Modal title="Đồng bộ lại dữ liệu AI?" onClose={() => !syncing && setShowSyncModal(false)}>
          <div style={{ lineHeight: '1.6', fontSize: '0.92rem', color: 'var(--ink)' }}>
            <p style={{ marginTop: 0, marginBottom: '12px' }}>
              Hệ thống sẽ cập nhật lại thông tin các sản phẩm đang kinh doanh để phục vụ tính năng tìm kiếm và tư vấn AI.
            </p>
            <p style={{ marginBottom: '12px', color: 'var(--muted)' }}>
              Thông thường bạn không cần thực hiện thao tác này vì hệ thống sẽ tự động cập nhật khi sản phẩm thay đổi.
            </p>
            <div
              style={{
                background: '#f8fafc',
                border: '1px solid var(--line)',
                borderRadius: '8px',
                padding: '10px 14px',
                marginBottom: '20px',
                fontSize: '0.86rem',
                color: '#475569',
              }}
            >
              💡 <b>Lưu ý:</b> Chỉ nên đồng bộ lại khi kết quả tư vấn chưa phản ánh đúng dữ liệu sản phẩm hiện tại.
            </div>
          </div>
          <div className="row" style={{ justifyContent: 'flex-end', gap: '10px' }}>
            <button className="btn" onClick={() => setShowSyncModal(false)} disabled={syncing}>
              Hủy
            </button>
            <button className="btn btn-primary" onClick={handleSyncConfirm} disabled={syncing}>
              {syncing ? 'Đang đồng bộ dữ liệu AI...' : 'Bắt đầu đồng bộ'}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
