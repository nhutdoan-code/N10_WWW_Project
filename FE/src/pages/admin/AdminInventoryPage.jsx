import { useState } from 'react';
import * as XLSX from 'xlsx';
import Modal from '../../components/Modal';
import StatusBadge from '../../components/StatusBadge';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';
import { NGUONG_SAP_HET, productStatus } from '../../utils/stock';

const FILTERS = [
  { value: '', label: 'Tất cả' },
  { value: 'con-hang', label: 'Còn hàng' },
  { value: 'sap-het', label: 'Sắp hết (≤ 10)' },
  { value: 'het', label: 'Hết hàng' },
];

export default function AdminInventoryPage() {
  const [query, setQuery] = useState('');
  const [loc, setLoc] = useState('');
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const [selected, setSelected] = useState(null);
  const [batchImport, setBatchImport] = useState(null);
  const [batchRows, setBatchRows] = useState([]);
  const [batchNote, setBatchNote] = useState('');
  const [historyVariant, setHistoryVariant] = useState(null);
  const [form, setForm] = useState({ amount: '', note: '' });
  const toast = useToast();
  const { data: result, loading, error, reload } = useFetch(() => adminService.inventory(query, loc, page, pageSize), [query, loc, page]);
  const { data: stats, reload: reloadStats } = useFetch(() => adminService.inventoryStats(), []);
  const { data: history } = useFetch(
    () => historyVariant
      ? adminService.inventoryHistory(historyVariant.maPhienBan || null)
      : Promise.resolve([]),
    [historyVariant]
  );
  const historyRows = Array.isArray(history) ? history : (history?.content || []);

  const legacyRows = Array.isArray(result)
    ? result.flatMap((product) => (product.phienBans || []).map((variant) => ({
      ...variant,
      maSanPham: product.maSanPham,
      tenMay: product.tenMay,
    })))
    : null;
  const matchesFilter = (variant) => {
    const stock = Number(variant.soLuongTon);
    if (loc === 'het') return stock === 0;
    if (loc === 'sap-het') return stock > 0 && stock <= NGUONG_SAP_HET;
    if (loc === 'con-hang') return stock > NGUONG_SAP_HET;
    return true;
  };
  const allRows = legacyRows
    ? legacyRows.filter(matchesFilter)
    : result?.content || [];
  const rows = legacyRows
    ? allRows.slice(page * pageSize, (page + 1) * pageSize)
    : allRows;
  const totalElements = legacyRows ? allRows.length : (result?.totalElements ?? allRows.length);
  const totalPages = result?.totalPages ?? (totalElements ? Math.ceil(totalElements / pageSize) : 0);
  const visibleStats = stats || allRows.reduce((result, variant) => {
    const stock = Number(variant.soLuongTon);
    result.tongPhienBan += 1;
    if (stock === 0) result.hetHang += 1;
    else if (stock <= NGUONG_SAP_HET) result.sapHet += 1;
    else result.conHang += 1;
    return result;
  }, { tongPhienBan: 0, conHang: 0, sapHet: 0, hetHang: 0 });

  const openAction = (mode, variant, product) => {
    setSelected({ mode, variant, product });
    setForm({ amount: '', note: '' });
  };

  const toVariant = (row) => ({
    maPhienBan: row.maPhienBan,
    sku: row.sku,
    ram: row.ram,
    boNho: row.boNho,
    mauSac: row.mauSac,
    giaBan: row.giaBan,
    soLuongTon: row.soLuongTon,
    trangThai: row.trangThai,
  });

  const submit = async (event) => {
    event.preventDefault();
    const amount = Number(form.amount);
    try {
      if (selected.mode === 'import') {
        await adminService.importStock(selected.variant.maPhienBan, amount, form.note);
        toast('Đã nhập kho');
      } else {
        await adminService.adjustStock(selected.variant.maPhienBan, amount, form.note);
        toast('Đã điều chỉnh tồn kho');
      }
      setSelected(null);
      reload();
      reloadStats();
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  };

  const readImportFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' });
      const sheet = workbook.Sheets[workbook.SheetNames[0]];
      const rawRows = XLSX.utils.sheet_to_json(sheet, { defval: '' });
      const parsed = rawRows.map((row, index) => {
        const sku = String(row.SKU ?? row.sku ?? row['Mã SKU'] ?? '').trim();
        const value = row.soLuong ?? row['Số lượng'] ?? row['Số lượng nhập'] ?? '';
        const soLuong = Number(value);
        if (!sku || !Number.isInteger(soLuong) || soLuong <= 0) {
          throw new Error(`Dòng ${index + 2}: SKU hoặc số lượng không hợp lệ`);
        }
        return { sku, soLuong };
      });
      if (!parsed.length) throw new Error('File không có dữ liệu');
      setBatchRows(parsed);
    } catch (error) {
      setBatchRows([]);
      toast(error.message || 'Không thể đọc file Excel', 'error');
    }
    event.target.value = '';
  };

  const submitBatchImport = async (event) => {
    event.preventDefault();
    try {
      await adminService.importStockBatch(batchRows, batchNote);
      toast(`Đã nhập kho ${batchRows.length} SKU`);
      setBatchImport(null);
      setBatchRows([]);
      setBatchNote('');
      reload();
      reloadStats();
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  };

  return (
    <>
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h2>Quản lý tồn kho</h2>
          <p className="muted">Theo dõi số lượng tồn, nhập kho, điều chỉnh và lịch sử biến động từng SKU.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setBatchImport(true)}>Nhập kho</button>
      </div>
      <div className="stats-grid">
        <div className="stat-card"><span>Tổng phiên bản</span><b>{visibleStats.tongPhienBan}</b></div>
        <div className="stat-card"><span>Còn hàng</span><b>{visibleStats.conHang}</b></div>
        <div className="stat-card"><span>Sắp hết hàng</span><b>{visibleStats.sapHet}</b></div>
        <div className="stat-card"><span>Hết hàng</span><b>{visibleStats.hetHang}</b></div>
      </div>
      <div className="row" style={{ margin: '18px 0', alignItems: 'center' }}>
        <input className="input" placeholder="Tìm SKU, mã sản phẩm hoặc tên máy" value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} />
        {FILTERS.map((filter) => (
          <button key={filter.value} className={loc === filter.value ? 'btn active' : 'btn'} onClick={() => { setLoc(filter.value); setPage(0); }}>{filter.label}</button>
        ))}
        <button className="btn" onClick={() => setHistoryVariant({ maPhienBan: '' })}>Lịch sử kho</button>
      </div>
      <table className="table">
        <thead><tr><th>SKU</th><th>Sản phẩm</th><th>Phiên bản</th><th>Giá bán</th><th>Tồn khả dụng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.maPhienBan}>
              <td><b>{row.sku}</b></td>
              <td>{row.tenMay}</td>
              <td>{row.ram}GB / {row.boNho}GB · {row.mauSac}</td>
              <td>{Number(row.giaBan).toLocaleString('vi-VN')} đ</td>
              <td><b>{row.soLuongTon}</b></td>
              <td><StatusBadge status={productStatus(row)} /></td>
              <td className="row">
                <button className="btn" onClick={() => openAction('import', toVariant(row), row)}>Nhập kho</button>
                <button className="btn" onClick={() => openAction('adjust', toVariant(row), row)}>Điều chỉnh</button>
                <button className="btn" onClick={() => setHistoryVariant(toVariant(row))}>Lịch sử</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {error && <p className="error">{error}</p>}
      {!loading && !error && rows.length === 0 && <p className="muted">Không có SKU phù hợp với bộ lọc hiện tại.</p>}
      <div className="inventory-pagination">
        <span>{totalElements} SKU · Trang {totalPages ? page + 1 : 0}/{totalPages}</span>
        <div className="row">
          <button className="btn" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Trước</button>
          <button className="btn" disabled={!result || page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>Sau</button>
        </div>
      </div>

      {selected && (
        <Modal title={selected.mode === 'import' ? 'Nhập kho' : 'Điều chỉnh tồn kho'} onClose={() => setSelected(null)}>
          <p><b>{selected.product.tenMay}</b> · {selected.variant.sku}</p>
          <p>Tồn hiện tại: <b>{selected.variant.soLuongTon}</b></p>
          <form className="inventory-form" onSubmit={submit}>
            <label className="inventory-field">
              <span>{selected.mode === 'import' ? 'Số lượng nhập' : 'Số lượng kiểm kê thực tế'}</span>
              <input type="number" min="0" required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </label>
            <label className="inventory-field">
              <span>{selected.mode === 'import' ? 'Ghi chú' : 'Lý do điều chỉnh'}{selected.mode === 'adjust' && ' *'}</span>
              <textarea rows="3" required={selected.mode === 'adjust'} value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
            </label>
            {selected.mode === 'import' && form.amount !== '' && <p className="inventory-preview">Tồn sau nhập: <b>{selected.variant.soLuongTon + Number(form.amount)}</b> ({selected.variant.soLuongTon} + {Number(form.amount)})</p>}
            {selected.mode === 'adjust' && form.amount !== '' && <p className="inventory-preview">Chênh lệch: <b>{Number(form.amount) - selected.variant.soLuongTon}</b></p>}
            <div className="inventory-form-actions"><button type="button" className="btn" onClick={() => setSelected(null)}>Hủy</button><button className="btn btn-primary">Xác nhận</button></div>
          </form>
        </Modal>
      )}

      {batchImport && (
        <Modal title="Nhập kho hàng loạt bằng Excel" onClose={() => setBatchImport(null)}>
          <p className="muted">File cần có cột <b>SKU</b> và <b>Số lượng</b>. Mỗi SKU chỉ được xuất hiện một lần.</p>
          <form className="inventory-form" onSubmit={submitBatchImport}>
            <label className="inventory-field">
              <span>Chọn file Excel</span>
              <input type="file" accept=".xlsx,.xls" required onChange={readImportFile} />
            </label>
            {batchRows.length > 0 && (
              <div className="inventory-import-preview">
                <b>{batchRows.length} SKU hợp lệ</b>
                <div className="inventory-import-list">
                  {batchRows.slice(0, 8).map((row) => <span key={row.sku}>{row.sku}: +{row.soLuong}</span>)}
                  {batchRows.length > 8 && <span>... và {batchRows.length - 8} SKU khác</span>}
                </div>
              </div>
            )}
            <label className="inventory-field">
              <span>Ghi chú</span>
              <textarea rows="3" value={batchNote} onChange={(event) => setBatchNote(event.target.value)} />
            </label>
            <div className="inventory-form-actions">
              <button type="button" className="btn" onClick={() => setBatchImport(null)}>Hủy</button>
              <button className="btn btn-primary" disabled={!batchRows.length}>Xác nhận nhập kho</button>
            </div>
          </form>
        </Modal>
      )}

      {historyVariant && (
        <Modal title="Lịch sử biến động kho" wide onClose={() => setHistoryVariant(null)}>
          <table className="table"><thead><tr><th>Thời gian</th><th>SKU</th><th>Loại</th><th>Số lượng</th><th>Tồn trước</th><th>Tồn sau</th><th>Đơn hàng</th><th>Ghi chú</th></tr></thead>
            <tbody>{historyRows.map((item) => <tr key={item.maBienDong}><td>{item.thoiGian}</td><td>{item.sku}</td><td>{item.loaiBienDong}</td><td>{['BAN_HANG'].includes(item.loaiBienDong) ? -item.soLuong : `+${item.soLuong}`}</td><td>{item.tonTruoc}</td><td>{item.tonSau}</td><td>{item.maDonHang || '-'}</td><td>{item.ghiChu || '-'}</td></tr>)}</tbody>
          </table>
        </Modal>
      )}
    </>
  );
}
