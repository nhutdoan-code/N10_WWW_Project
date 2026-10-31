import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import ProductImage from '../components/ProductImage';
import useToast from '../hooks/useToast';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import { MAX_COMPARE_PRODUCTS } from '../context/CompareContext';
import { productService } from '../service/productService';
import { formatPrice } from '../utils/format';
import { SPEC_FIELDS, specValue } from '../utils/specs';

export default function ComparePage() {
  const { ids, toggle, clear } = useCompare();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [query, setQuery] = useState('');
  const toast = useToast();
  const { data: products, loading, error } = useFetch(() => Promise.all(ids.map(productService.get)), [ids.join(',')]);
  const { data: catalog, loading: catalogLoading, error: catalogError } = useFetch(productService.list);
  const canAdd = ids.length < MAX_COMPARE_PRODUCTS;
  const candidates = useMemo(() => {
    const search = query.trim().toLocaleLowerCase('vi');
    return (catalog || []).filter((p) => !ids.includes(p.maSanPham)
      && (!search || p.tenMay.toLocaleLowerCase('vi').includes(search)));
  }, [catalog, ids, query]);

  const addProduct = (maSanPham) => {
    try {
      toggle(maSanPham);
      setQuery('');
      if (ids.length + 1 >= MAX_COMPARE_PRODUCTS) setPickerOpen(false);
    } catch (e) {
      toast(e.message, 'error');
    }
  };

  if (ids.length === 0) return <p className="muted">Chưa chọn sản phẩm nào để so sánh. <Link to="/">Chọn sản phẩm</Link> (bấm nút ⇄).</p>;
  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error" role="alert">Không tải được sản phẩm so sánh: {error}</p>;
  if (!products) return <p className="muted">Đang tải...</p>;

  return (
    <>
      <div className="row between">
        <h2>So sánh sản phẩm</h2>
        <button className="btn" onClick={clear}>Xóa tất cả</button>
      </div>
      {products.length < 2 && <p className="muted">Hãy chọn thêm ít nhất 1 sản phẩm nữa để so sánh.</p>}
      <div className="compare-table-wrap">
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
            {canAdd && (
              <td className="compare-add-cell">
                <button type="button" className="compare-add-button" onClick={() => setPickerOpen((open) => !open)}
                  aria-expanded={pickerOpen} aria-controls="compare-product-picker">
                  <span className="compare-add-icon" aria-hidden="true">+</span>
                  <b>Thêm sản phẩm</b>
                  <small className="muted">So sánh tối đa {MAX_COMPARE_PRODUCTS} sản phẩm</small>
                </button>
              </td>
            )}
          </tr>
          <tr><th>Giá bán</th>{products.map((p) => <td key={p.maSanPham} className="price">{formatPrice(p.giaBan)}</td>)}{canAdd && <td />}</tr>
          <tr><th>Hãng</th>{products.map((p) => <td key={p.maSanPham}>{p.tenLoai}</td>)}{canAdd && <td />}</tr>
          <tr><th>Đánh giá</th>{products.map((p) => <td key={p.maSanPham}>★ {p.diemDanhGiaTb}</td>)}{canAdd && <td />}</tr>
          {SPEC_FIELDS.map((f) => (
            <tr key={f.key}>
              <th>{f.label}</th>
              {products.map((p) => <td key={p.maSanPham}>{specValue(f, p.thongSo)}</td>)}
              {canAdd && <td />}
            </tr>
          ))}
          </tbody>
        </table>
      </div>
      {pickerOpen && createPortal(
        <div className="compare-picker-overlay" onClick={() => setPickerOpen(false)}>
          <div className="compare-picker" id="compare-product-picker" role="dialog" aria-modal="true"
            aria-labelledby="compare-picker-title" onClick={(event) => event.stopPropagation()}>
            <div className="row between">
              <b id="compare-picker-title">Chọn sản phẩm</b>
              <button type="button" className="btn btn-ghost" onClick={() => setPickerOpen(false)}>Đóng</button>
            </div>
            <input type="search" aria-label="Tìm sản phẩm để so sánh" placeholder="Tìm tên sản phẩm..."
              value={query} onChange={(event) => setQuery(event.target.value)} />
            {catalogLoading && <p className="muted" role="status">Đang tải danh sách sản phẩm...</p>}
            {catalogError && <p className="error" role="alert">Không tải được danh sách sản phẩm: {catalogError}</p>}
            {!catalogLoading && !catalogError && candidates.length === 0 && (
              <p className="muted">Không còn sản phẩm phù hợp để thêm.</p>
            )}
            <div className="compare-picker-list">
              {!catalogLoading && !catalogError && candidates.map((p) => (
                <button type="button" key={p.maSanPham} className="compare-picker-product" onClick={() => addProduct(p.maSanPham)}>
                  <ProductImage className="thumb" src={p.anhChinh} alt="" />
                  <span className="compare-picker-info">
                    <b>{p.tenMay}</b>
                    <small className="price">{formatPrice(p.giaBan)}</small>
                  </span>
                  <span className="compare-picker-plus" aria-hidden="true">+</span>
                </button>
              ))}
            </div>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
