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
  const [editing, setEditing] = useState(null);
  const toast = useToast();
  const { data: categories, reload } = useFetch(productService.categories);

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
        <h2>Quản lý loại sản phẩm</h2>
        <button className="btn btn-primary" onClick={() => setEditing({ tenLoai: '', moTa: '', maLoaiCha: '' })}>+ Thêm loại</button>
      </div>
      <table className="table">
        <thead>
          <tr><th>Mã</th><th>Tên loại / hãng (dạng cây)</th><th>Mô tả</th><th></th></tr>
        </thead>
        <tbody>
          {flattenTree(categories || []).map((c) => (
            <tr key={c.maLoai}>
              <td>{c.maLoai}</td>
              <td>{categoryLabel(c)}</td>
              <td>{c.moTa}</td>
              <td className="row">
                <button className="btn" onClick={() => setEditing(c)}>Sửa</button>
                <button
                  className="btn btn-danger"
                  onClick={() => window.confirm(`Xóa "${c.tenLoai}"?`) && run(() => adminService.deleteCategory(c.maLoai), 'Đã xóa loại sản phẩm')}
                >
                  Xóa
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {editing && (
        <Modal title={editing.maLoai ? 'Sửa loại sản phẩm' : 'Thêm loại sản phẩm'} onClose={() => setEditing(null)}>
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault();
              run(() => adminService.saveCategory(editing.maLoai, editing), 'Đã lưu loại sản phẩm');
            }}
          >
            <FormField label="Tên loại / hãng" value={editing.tenLoai} onChange={(e) => setEditing({ ...editing, tenLoai: e.target.value })} required />
            <label className="field">
              <span>Danh mục cha</span>
              <select value={editing.maLoaiCha || ''} onChange={(e) => setEditing({ ...editing, maLoaiCha: e.target.value })}>
                <option value="">— Không có (hãng / danh mục gốc) —</option>
                {flattenTree(categories || []).filter((c) => c.maLoai !== editing.maLoai).map((c) => (
                  <option key={c.maLoai} value={c.maLoai}>{categoryLabel(c)}</option>
                ))}
              </select>
            </label>
            <FormField label="Mô tả" value={editing.moTa || ''} onChange={(e) => setEditing({ ...editing, moTa: e.target.value })} />
            <button className="btn btn-primary">Lưu</button>
          </form>
        </Modal>
      )}
    </>
  );
}
