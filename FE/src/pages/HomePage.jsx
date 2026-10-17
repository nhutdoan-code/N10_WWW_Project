import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import CategoryTree from '../components/CategoryTree';
import ProductCard from '../components/ProductCard';
import { PRICE_RANGES, SORT_OPTIONS } from '../config/constants';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';

export default function HomePage() {
  const [params] = useSearchParams();
  const q = params.get('q') || '';
  const ai = params.get('ai') === '1' && q !== '';
  const [maLoai, setMaLoai] = useState('');
  const [price, setPrice] = useState(0);
  const [sort, setSort] = useState('');
  const { data: categories } = useFetch(productService.categories);
  const range = PRICE_RANGES[price];

  const { data: products, loading, error } = useFetch(
    () => (ai ? productService.aiSearch(q) : productService.list({ q, maLoai, min: range.min, max: range.max, sort })),
    [q, ai, maLoai, price, sort]
  );

  return (
    <div className="shop">
      {!ai && categories && (
        <aside className="card">
          <h3>Danh mục</h3>
          <CategoryTree categories={categories} value={maLoai} onChange={setMaLoai} />
        </aside>
      )}
      <div>
      {q && <h2>{ai ? 'Gợi ý AI' : 'Kết quả tìm kiếm'} cho "{q}"</h2>}
      {!ai && (
        <div className="filters">
          <div className="row">
            <select value={price} onChange={(e) => setPrice(Number(e.target.value))}>
              {PRICE_RANGES.map((r, i) => (
                <option key={r.label} value={i}>{r.label}</option>
              ))}
            </select>
            <select value={sort} onChange={(e) => setSort(e.target.value)}>
              {SORT_OPTIONS.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
          </div>
        </div>
      )}
      {loading && <p className="muted">Đang tải...</p>}
      {error && <p className="error">{error}</p>}
      {products?.length === 0 && <p className="muted">Không có sản phẩm phù hợp.</p>}
      <div className="grid">
        {products?.map((sp) => (
          <ProductCard key={sp.maSanPham} sp={sp} />
        ))}
      </div>
      </div>
    </div>
  );
}
