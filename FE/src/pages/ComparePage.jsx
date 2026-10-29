import { Link } from 'react-router-dom';
import ProductImage from '../components/ProductImage';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';
import { formatPrice } from '../utils/format';
import { SPEC_FIELDS, specValue } from '../utils/specs';

export default function ComparePage() {
  const { ids, toggle, clear } = useCompare();
  const { data: products, loading } = useFetch(() => Promise.all(ids.map(productService.get)), [ids.join(',')]);

  if (ids.length === 0) return <p className="muted">Chưa chọn sản phẩm nào để so sánh. <Link to="/">Chọn sản phẩm</Link> (bấm nút ⇄).</p>;
  if (loading || !products) return <p className="muted">Đang tải...</p>;

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
                <Link to={`/products/${p.maSanPham}`}><ProductImage className="thumb-lg" src={p.anhChinh} alt="" /><br /><b>{p.tenMay}</b></Link>
                <br /><button className="btn btn-ghost" onClick={() => toggle(p.maSanPham)}>Bỏ</button>
              </td>
            ))}
          </tr>
          <tr><th>Giá bán</th>{products.map((p) => <td key={p.maSanPham} className="price">{formatPrice(p.giaBan)}</td>)}</tr>
          <tr><th>Hãng</th>{products.map((p) => <td key={p.maSanPham}>{p.tenLoai}</td>)}</tr>
          <tr><th>Đánh giá</th>{products.map((p) => <td key={p.maSanPham}>★ {p.diemDanhGiaTb}</td>)}</tr>
          {SPEC_FIELDS.map((f) => (
            <tr key={f.key}>
              <th>{f.label}</th>
              {products.map((p) => <td key={p.maSanPham}>{specValue(f, p.thongSo)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
