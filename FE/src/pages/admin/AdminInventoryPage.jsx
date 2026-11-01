import { useState } from 'react';
import Modal from '../../components/Modal';
import StatusBadge from '../../components/StatusBadge';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';
import { NGUONG_SAP_HET, productStatus } from '../../utils/stock';

export const WARN_STOCK_THRESHOLD = 10000;

const FILTERS = [
  { value: '', label: 'Tất cả' },
  { value: 'sap-het', label: 'Sắp hết (≤ 10)' },
  { value: 'het', label: 'Hết hàng' },
];

export default function AdminInventoryPage() {
  const [loc, setLoc] = useState('');
  const [amount, setAmount] = useState({});
  const [confirmDialog, setConfirmDialog] = useState(null);

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

  const handleAction = (variant, isImport) => {
    const n = Number(amount[variant.maPhienBan]);
    const actionName = isImport ? 'nhập thêm' : 'đặt lại tồn kho thành';
    const perform = () => run(
      () => (isImport ? adminService.importStock(variant.maPhienBan, n) : adminService.setStock(variant.maPhienBan, n)),
      isImport ? 'Đã nhập kho' : 'Đã điều chỉnh tồn kho'
    );

    if (n > WARN_STOCK_THRESHOLD) {
      setConfirmDialog({
        title: 'Cảnh báo số lượng lớn',
        message: `Bạn đang yêu cầu ${actionName} ${n.toLocaleString('vi-VN')} sản phẩm cho phiên bản ${variant.sku || variant.maPhienBan}. Số lượng này vượt ngưỡng an toàn thông thường (${WARN_STOCK_THRESHOLD.toLocaleString('vi-VN')}). Bạn có chắc chắn muốn tiếp tục?`,
        onConfirm: () => {
          setConfirmDialog(null);
          perform();
        },
      });
    } else {
      perform();
    }
  };

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
          <button key={f.value} className={loc === f.value ? 'chip active' : 'chip'} onClick={() => setLoc(f.value)}>
            {f.label}
          </button>
        ))}
      </div>
      <table className="table">
        <thead>
          <tr>
            <th>Mã</th>
            <th>Tên máy</th>
            <th>Phiên bản</th>
            <th>Tồn kho</th>
            <th>Trạng thái</th>
            <th>Số lượng</th>
            <th></th>
          </tr>
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
                <td>
                  <input
                    className="qty"
                    type="number"
                    min="0"
                    value={amount[variant.maPhienBan] ?? ''}
                    onChange={(e) => setAmount({ ...amount, [variant.maPhienBan]: e.target.value })}
                  />
                </td>
                <td>
                  <div className="row">
                    <button
                      className="btn"
                      disabled={!n || n <= 0}
                      onClick={() => handleAction(variant, true)}
                    >
                      Nhập thêm
                    </button>
                    <button
                      className="btn"
                      disabled={amount[variant.maPhienBan] === undefined || amount[variant.maPhienBan] === ''}
                      onClick={() => handleAction(variant, false)}
                    >
                      Đặt lại
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {confirmDialog && (
        <Modal title={confirmDialog.title} onClose={() => setConfirmDialog(null)}>
          <p style={{ margin: '12px 0 20px', lineHeight: '1.5' }}>{confirmDialog.message}</p>
          <div className="row" style={{ justifyContent: 'flex-end', gap: '10px' }}>
            <button className="btn btn-ghost" type="button" onClick={() => setConfirmDialog(null)}>
              Hủy bỏ
            </button>
            <button className="btn btn-primary" type="button" onClick={confirmDialog.onConfirm}>
              Xác nhận thực hiện
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
