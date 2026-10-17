import { Link } from 'react-router-dom';
import { SPEC_LABELS } from '../config/constants';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';
import { formatPrice } from '../utils/format';

const specs = (sp) => (sp.thongSoKyThuat || '').split('|').map((t) => t.trim());
const strip = (text, label) => (text.toLowerCase().startsWith(label.toLowerCase()) ? text.slice(label.length).trim() : text);

export default function ComparePage() {
  const { ids, toggle, clear } = useCompare();
  const { data: products, loading } = useFetch(() => Promise.all(ids.map(productService.get)), [ids.join(',')]);

  if (ids.length === 0) return <p className="muted">Chưa chọn sản phẩm nào để so sánh. <Link to="/">Chọn sản phẩm</Link> (bấm nút ⇄).</p>;
  if (loading || !products) return <p className="muted">Đang tải...</p>;

  const rows = Math.max(...products.map((p) => specs(p).length));

  return (
    <>
      <div className="row between">
        <h2>So sánh sản phẩm</h2>
        <button className="btn" onClick={clear}>Xóa tất cả</button>
      </div>
      {products.length < 2 && <p className="muted">Hãy chọn thêm ít nhất 1 sản phẩm nữa để so sánh.</p>}
      <table className="table compare">
        <tbody>
          <tr>
            <th></th>
            {products.map((p) => (
              <td key={p.maSanPham}>
                <Link to={`/products/${p.maSanPham}`}><img className="thumb-lg" src={p.anhChinh} alt="" /><br /><b>{p.tenMay}</b></Link>
                <br /><button className="btn btn-ghost" onClick={() => toggle(p.maSanPham)}>Bỏ</button>
              </td>
            ))}
          </tr>
          <tr><th>Giá bán</th>{products.map((p) => <td key={p.maSanPham} className="price">{formatPrice(p.giaBan)}</td>)}</tr>
          <tr><th>Hãng</th>{products.map((p) => <td key={p.maSanPham}>{p.tenLoai}</td>)}</tr>
          <tr><th>Đánh giá</th>{products.map((p) => <td key={p.maSanPham}>★ {p.diemDanhGiaTb}</td>)}</tr>
          {Array.from({ length: rows }, (_, i) => (
            <tr key={i}>
              <th>{SPEC_LABELS[i] || `Thông số ${i + 1}`}</th>
              {products.map((p) => <td key={p.maSanPham}>{strip(specs(p)[i] || '-', SPEC_LABELS[i] || '')}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
