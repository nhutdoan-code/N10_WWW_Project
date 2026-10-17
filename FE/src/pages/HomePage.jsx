import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import CategoryTree from '../components/CategoryTree';
import ProductCard from '../components/ProductCard';
import { PRICE_RANGES, SORT_OPTIONS } from '../config/constants';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';
import { formatPrice } from '../utils/format';

export default function HomePage() {
  const [params] = useSearchParams();
  const q = params.get('q') || '';
  const ai = params.get('ai') === '1' && q !== '';
  const [maLoai, setMaLoai] = useState('');
  const [price, setPrice] = useState(0);
  const [sort, setSort] = useState('');
  const { data: categories } = useFetch(productService.categories);
  const range = PRICE_RANGES[price];
  const macDinh = !q && !maLoai && !price && !sort;

  const { data: products, loading, error } = useFetch(
    () => (ai ? productService.aiSearch(q) : productService.list({ q, maLoai, min: range.min, max: range.max, sort })),
    [q, ai, maLoai, price, sort]
  );

  const noiBat = macDinh && products
    ? [...products].filter((p) => p.trangThai === 'CON_HANG').sort((a, b) => b.diemDanhGiaTb - a.diemDanhGiaTb)[0]
    : null;
  const hang = categories?.filter((c) => !c.maLoaiCha) || [];

  return (
    <>
      {macDinh && (
        <section className="hero">
          <div>
            <h1>Chọn điện thoại chính hãng, giá niêm yết rõ ràng</h1>
            <p>Lọc theo hãng và mức giá, so sánh cấu hình, đặt hàng và theo dõi đơn ngay trên PhoneNest.</p>
            <div className="hero-brands">
              {hang.map((c) => (
                <button key={c.maLoai} onClick={() => setMaLoai(c.maLoai)}>{c.tenLoai}</button>
              ))}
            </div>
          </div>
          {noiBat && (
            <Link to={`/products/${noiBat.maSanPham}`} className="hero-product">
              <img src={noiBat.anhChinh} alt="" />
              <div>
                <span className="hero-rate">★ {noiBat.diemDanhGiaTb} · Được đánh giá cao</span>
                <b>{noiBat.tenMay}</b>
                <span className="hero-price">{formatPrice(noiBat.giaBan)}</span>
                <span className="hero-cta">Xem chi tiết</span>
              </div>
            </Link>
          )}
        </section>
      )}
      <div className="shop">
        {!ai && categories && (
          <aside className="card">
            <h3>Danh mục</h3>
            <CategoryTree categories={categories} value={maLoai} onChange={setMaLoai} />
          </aside>
        )}
        <div>
          {q && <h2>{ai ? 'Gợi ý AI' : 'Kết quả tìm kiếm'} cho "{q}"</h2>}
          <div className="toolbar">
            <span className="count">{products ? <><b>{products.length}</b> sản phẩm</> : 'Đang tải sản phẩm...'}</span>
            {!ai && (
              <div className="row">
                <select aria-label="Mức giá" value={price} onChange={(e) => setPrice(Number(e.target.value))}>
                  {PRICE_RANGES.map((r, i) => (
                    <option key={r.label} value={i}>{r.label}</option>
                  ))}
                </select>
                <select aria-label="Sắp xếp" value={sort} onChange={(e) => setSort(e.target.value)}>
                  {SORT_OPTIONS.map((s) => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                  ))}
                </select>
              </div>
            )}
          </div>
          {error && <p className="error">{error}</p>}
          {products?.length === 0 && <p className="muted">Không có sản phẩm phù hợp. Thử đổi mức giá hoặc danh mục khác.</p>}
          <div className="grid">
            {!products && loading && Array.from({ length: 8 }, (_, i) => <div key={i} className="card skeleton" />)}
            {products?.map((sp) => (
              <ProductCard key={sp.maSanPham} sp={sp} />
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
