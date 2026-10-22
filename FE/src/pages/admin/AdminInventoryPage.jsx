import { useState } from 'react';
import StatusBadge from '../../components/StatusBadge';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';
import { productStatus } from '../../utils/stock';

const FILTERS = [
  { value: '', label: 'Tất cả' },
  { value: 'sap-het', label: 'Sắp hết (≤ 10)' },
  { value: 'het', label: 'Hết hàng' },
];

export default function AdminInventoryPage() {
  const [loc, setLoc] = useState('');
  const [amount, setAmount] = useState({});
  const { data: products, reload } = useFetch(() => adminService.inventory(loc), [loc]);
  const toast = useToast();

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  return (
    <>
      <h2>Quản lý tồn kho</h2>
      <div className="chips">
        {FILTERS.map((f) => (
          <button key={f.value} className={loc === f.value ? 'chip active' : 'chip'} onClick={() => setLoc(f.value)}>{f.label}</button>
        ))}
      </div>
      <table className="table">
        <thead>
          <tr><th>Mã</th><th>Tên máy</th><th>Tồn kho</th><th>Trạng thái</th><th>Số lượng</th><th></th></tr>
        </thead>
        <tbody>
          {products?.map((p) => {
            const n = Number(amount[p.maSanPham]);
            return (
              <tr key={p.maSanPham}>
                <td>{p.maSanPham}</td>
                <td>{p.tenMay}</td>
                <td><b>{p.soLuongTon}</b></td>
                <td><StatusBadge status={productStatus(p)} /></td>
                <td><input className="qty" type="number" min="0" value={amount[p.maSanPham] ?? ''} onChange={(e) => setAmount({ ...amount, [p.maSanPham]: e.target.value })} /></td>
                <td><div className="row">
                  <button className="btn" disabled={!n} onClick={() => run(() => adminService.importStock(p.maSanPham, n), 'Đã nhập kho')}>Nhập thêm</button>
                  <button className="btn" disabled={amount[p.maSanPham] === undefined || amount[p.maSanPham] === ''} onClick={() => run(() => adminService.setStock(p.maSanPham, n), 'Đã điều chỉnh tồn kho')}>Đặt lại</button>
                </div></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </>
  );
}
