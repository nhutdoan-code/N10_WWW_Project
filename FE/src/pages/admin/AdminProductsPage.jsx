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
  const [editing, setEditing] = useState(null);
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
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  return (
    <>
      <div className="row between">
        <h2>Quản lý sản phẩm</h2>
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ Thêm sản phẩm</button>
      </div>
      <input className="wide" placeholder="Tìm theo tên, mã, hãng..." value={q} onChange={(e) => setQ(e.target.value)} />
      <table className="table">
        <thead>
          <tr><th>Mã</th><th>Ảnh</th><th>Tên máy</th><th>Hãng</th><th>Giá</th><th>Tồn</th><th>Trạng thái</th><th></th></tr>
        </thead>
        <tbody>
          {products?.map((p) => (
            <tr key={p.maSanPham}>
              <td>{p.maSanPham}</td>
              <td><ProductImage className="thumb" src={p.anhChinh} alt="" /></td>
              <td>{p.tenMay}</td>
              <td>{p.tenThuongHieu || p.tenLoai}</td>
              <td>{formatPrice(p.giaBan)}</td>
              <td>{p.soLuongTon}</td>
              <td><StatusBadge status={productStatus(p)} /></td>
              <td><div className="row">
                <button className="btn" onClick={() => setEditing(p)}>Sửa</button>
                <button
                  className="btn btn-danger"
                  onClick={() => window.confirm(`Xóa "${p.tenMay}"?`) && run(() => adminService.deleteProduct(p.maSanPham), 'Đã xóa sản phẩm')}
                >
                  Xóa
                </button>
              </div></td>
            </tr>
          ))}
        </tbody>
      </table>
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
    </>
  );
}
