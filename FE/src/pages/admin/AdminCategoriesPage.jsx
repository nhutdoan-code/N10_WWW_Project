import { useState } from 'react';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { productService } from '../../service/productService';
import { errorMessage } from '../../utils/format';
import { categoryLabel, flattenTree } from '../../utils/tree';

export default function AdminCategoriesPage() {
  const [tab, setTab] = useState('categories'); // 'categories' | 'brands'
  const [editingCategory, setEditingCategory] = useState(null);

  // Brand impact modal state
  const [impactBrand, setImpactBrand] = useState(null);
  const [impactBrandData, setImpactBrandData] = useState(null);
  const [impactBrandLoading, setImpactBrandLoading] = useState(false);

  const toast = useToast();
  const { data: categories, reload: reloadCategories } = useFetch(productService.categories);
  const { data: brands, reload: reloadBrands } = useFetch(adminService.brands);

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      setEditingCategory(null);
      setImpactBrand(null);
      setImpactBrandData(null);
      reloadCategories();
      reloadBrands();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const openBrandImpact = async (b) => {
    setImpactBrand(b);
    setImpactBrandData(null);
    setImpactBrandLoading(true);
    try {
      const data = await adminService.brandImpact(b.maThuongHieu);
      setImpactBrandData(data);
    } catch (e) {
      toast(errorMessage(e), 'error');
      setImpactBrand(null);
    } finally {
      setImpactBrandLoading(false);
    }
  };

  return (
    <>
      <div className="row between" style={{ alignItems: 'center', marginBottom: '16px' }}>
        <h2>Quản lý phân loại & thương hiệu</h2>
        {tab === 'categories' && (
          <button
            className="btn btn-primary"
            onClick={() => setEditingCategory({ tenLoai: '', moTa: '', maLoaiCha: '' })}
          >
            + Thêm loại danh mục
          </button>
        )}
      </div>

      {/* Tabs */}
      <div className="row" style={{ gap: '10px', marginBottom: '16px' }}>
        <button
          className={`btn ${tab === 'categories' ? 'btn-primary' : ''}`}
          onClick={() => setTab('categories')}
        >
          Cây danh mục sản phẩm
        </button>
        <button
          className={`btn ${tab === 'brands' ? 'btn-primary' : ''}`}
          onClick={() => setTab('brands')}
        >
          Thương hiệu sản phẩm
        </button>
      </div>

      {tab === 'categories' && (
        <table className="table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Tên loại (dạng cây)</th>
              <th>Mô tả</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {flattenTree(categories || []).map((c) => (
              <tr key={c.maLoai}>
                <td><code>{c.maLoai}</code></td>
                <td>{categoryLabel(c)}</td>
                <td>{c.moTa || '—'}</td>
                <td>
                  <div className="row" style={{ gap: '6px' }}>
                    <button className="btn" onClick={() => setEditingCategory(c)}>
                      Sửa
                    </button>
                    <button
                      className="btn btn-danger"
                      onClick={() =>
                        window.confirm(`Xóa "${c.tenLoai}"?`) &&
                        run(() => adminService.deleteCategory(c.maLoai), 'Đã xóa loại sản phẩm')
                      }
                    >
                      Xóa
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {tab === 'brands' && (
        <table className="table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Tên thương hiệu</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {(brands || []).map((b) => {
              const isActive = b.trangThaiHoatDong !== false;
              return (
                <tr key={b.maThuongHieu} style={{ opacity: isActive ? 1 : 0.65 }}>
                  <td><code>{b.maThuongHieu}</code></td>
                  <td><b>{b.tenThuongHieu}</b></td>
                  <td>
                    <span className={`badge ${isActive ? 'badge-audit-create' : 'badge-audit-delete'}`}>
                      {isActive ? 'Đang hoạt động' : 'Đã dừng'}
                    </span>
                  </td>
                  <td>
                    <div className="row" style={{ gap: '6px' }}>
                      {isActive ? (
                        <button
                          className="btn btn-danger"
                          onClick={() => openBrandImpact(b)}
                          title="Dừng hoạt động thương hiệu (soft archive, không cascade delete sản phẩm)"
                        >
                          Dừng hoạt động
                        </button>
                      ) : (
                        <button
                          className="btn btn-primary"
                          onClick={() =>
                            run(
                              () => adminService.restoreBrand(b.maThuongHieu),
                              'Đã khôi phục hoạt động thương hiệu'
                            )
                          }
                        >
                          Khôi phục
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
            {(!brands || brands.length === 0) && (
              <tr>
                <td colSpan={4} style={{ textAlign: 'center', padding: '24px', color: '#666' }}>
                  Không có thương hiệu nào.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      {/* Category Edit / Create Modal */}
      {editingCategory && (
        <Modal
          title={editingCategory.maLoai ? 'Sửa loại sản phẩm' : 'Thêm loại sản phẩm'}
          onClose={() => setEditingCategory(null)}
        >
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault();
              run(
                () => adminService.saveCategory(editingCategory.maLoai, editingCategory),
                'Đã lưu loại sản phẩm'
              );
            }}
          >
            <FormField
              label="Tên loại danh mục"
              value={editingCategory.tenLoai}
              onChange={(e) =>
                setEditingCategory({ ...editingCategory, tenLoai: e.target.value })
              }
              required
            />
            <label className="field">
              <span>Danh mục cha</span>
              <select
                value={editingCategory.maLoaiCha || ''}
                onChange={(e) =>
                  setEditingCategory({ ...editingCategory, maLoaiCha: e.target.value })
                }
              >
                <option value="">— Không có (danh mục gốc) —</option>
                {flattenTree(categories || [])
                  .filter((c) => c.maLoai !== editingCategory.maLoai)
                  .map((c) => (
                    <option key={c.maLoai} value={c.maLoai}>
                      {categoryLabel(c)}
                    </option>
                  ))}
              </select>
            </label>
            <FormField
              label="Mô tả"
              value={editingCategory.moTa || ''}
              onChange={(e) =>
                setEditingCategory({ ...editingCategory, moTa: e.target.value })
              }
            />
            <button className="btn btn-primary">Lưu</button>
          </form>
        </Modal>
      )}

      {/* Brand Soft Delete Impact Modal */}
      {impactBrand && (
        <Modal
          title="Xác nhận dừng hoạt động thương hiệu"
          onClose={() => {
            setImpactBrand(null);
            setImpactBrandData(null);
          }}
        >
          {impactBrandLoading ? (
            <p className="center muted">Đang kiểm tra dữ liệu sản phẩm liên kết...</p>
          ) : (
            <div>
              <p style={{ marginTop: 0 }}>
                Bạn có chắc chắn muốn dừng hoạt động thương hiệu <b>{impactBrand.tenThuongHieu}</b> (mã <code>{impactBrand.maThuongHieu}</code>)?
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
                <div className="row between">
                  <span>Số lượng sản phẩm thuộc thương hiệu này:</span>
                  <b style={{ color: (impactBrandData?.productCount || 0) > 0 ? '#b91c1c' : '#15803d' }}>
                    {impactBrandData?.productCount ?? 0} sản phẩm
                  </b>
                </div>
              </div>

              {(impactBrandData?.productCount || 0) > 0 ? (
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
                  <strong>Nguyên tắc bảo vệ dữ liệu (No Cascade Delete):</strong> Thương hiệu này đang có{' '}
                  <b>{impactBrandData.productCount}</b> sản phẩm. Hệ thống KHÔNG thực hiện xóa theo tầng (cascade delete) làm mất dữ liệu sản phẩm. Thay vào đó, thương hiệu sẽ chuyển sang trạng thái <b>Ngừng hoạt động</b> (soft archive). Toàn bộ sản phẩm và hóa đơn liên quan vẫn được bảo toàn nguyên vẹn.
                </div>
              ) : (
                <p className="muted" style={{ fontSize: '0.85rem', marginBottom: '16px' }}>
                  Thương hiệu sẽ được chuyển sang trạng thái ngừng hoạt động và có thể kích hoạt lại bất kỳ lúc nào.
                </p>
              )}

              <div className="row" style={{ justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  className="btn"
                  onClick={() => {
                    setImpactBrand(null);
                    setImpactBrandData(null);
                  }}
                >
                  Hủy bỏ
                </button>
                <button
                  className="btn btn-danger"
                  onClick={() =>
                    run(
                      () => adminService.archiveBrand(impactBrand.maThuongHieu),
                      'Đã dừng hoạt động thương hiệu'
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
