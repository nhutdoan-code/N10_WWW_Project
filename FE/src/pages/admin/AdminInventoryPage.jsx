import { useState } from 'react';
import StatusBadge from '../../components/StatusBadge';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';
import { NGUONG_SAP_HET, productStatus } from '../../utils/stock';

const FILTERS = [
  { value: '', label: 'Tất cả' },
  { value: 'sap-het', label: 'Sắp hết (≤ 10)' },
  { value: 'het', label: 'Hết hàng' },
];

export default function AdminInventoryPage() {
  const [loc, setLoc] = useState('');
  const [amount, setAmount] = useState({});
  // Backend lọc theo từng phiên bản; client vẫn lọc lại để chỉ hiển thị đúng dòng khớp.
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

  // Tồn kho thuộc từng phiên bản: mỗi dòng là một SKU, không dùng tồn chung của sản phẩm
  const rows = (products || [])
    .flatMap((p) => (p.phienBans || []).map((pb) => ({ product: p, variant: pb })))
    .filter(({ variant }) => {
      const ton = Number(variant.soLuongTon);
      if (loc === 'het') return ton === 0;
      if (loc === 'sap-het') return ton > 0 && ton <= NGUONG_SAP_HET;
      return true;
    });

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
          <tr><th>Mã</th><th>Tên máy</th><th>Phiên bản</th><th>Tồn kho</th><th>Trạng thái</th><th>Số lượng</th><th></th></tr>
        </thead>
        <tbody>
          {rows.map(({ product, variant }) => {
            const n = Number(amount[variant.maPhienBan]);
            return (
              <tr key={variant.maPhienBan}>
                <td>{product.maSanPham}</td>
                <td>{product.tenMay}</td>
                <td>{variant.ram}GB / {variant.boNho}GB · {variant.mauSac}</td>
                <td><b>{variant.soLuongTon}</b></td>
                <td><StatusBadge status={productStatus(variant)} /></td>
                <td><input className="qty" type="number" min="0" value={amount[variant.maPhienBan] ?? ''} onChange={(e) => setAmount({ ...amount, [variant.maPhienBan]: e.target.value })} /></td>
                <td><div className="row">
                  <button className="btn" disabled={!n} onClick={() => run(() => adminService.importStock(variant.maPhienBan, n), 'Đã nhập kho')}>Nhập thêm</button>
                  <button className="btn" disabled={amount[variant.maPhienBan] === undefined || amount[variant.maPhienBan] === ''} onClick={() => run(() => adminService.setStock(variant.maPhienBan, n), 'Đã điều chỉnh tồn kho')}>Đặt lại</button>
                </div></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </>
  );
}
