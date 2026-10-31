import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import CategoryTree from '../components/CategoryTree';
import HeroSlider from '../components/HeroSlider';
import Icon from '../components/Icon';
import ProductCard from '../components/ProductCard';
import ProductImage from '../components/ProductImage';
import ProductFilters from '../components/ProductFilters';
import { PRICE_RANGES, SORT_OPTIONS } from '../config/constants';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';
import { productStatus } from '../utils/stock';
import { formatPrice } from '../utils/format';

const SERVICES = [
  { icon: 'shield', title: 'Hàng chính hãng', text: 'Nguyên seal, bảo hành đầy đủ' },
  { icon: 'truck', title: 'Giao hàng nhanh', text: 'Phí ship rõ ràng theo khu vực' },
  { icon: 'refresh', title: 'Đổi trả dễ dàng', text: 'Hỗ trợ đổi trả khi máy lỗi' },
  { icon: 'gift', title: 'Voucher mỗi ngày', text: 'Tự chọn mã tốt nhất khi thanh toán' },
];

const POPULAR_BRANDS = ['iPhone', 'Samsung Galaxy', 'Xiaomi / Redmi / POCO', 'OPPO', 'Điện thoại Google', 'Điện thoại vivo'];
const CATEGORY_TREE_NAMES = ['iPhone', 'Samsung Galaxy', 'Xiaomi / Redmi / POCO', 'OPPO'];
const PRODUCTS_PER_PAGE = 12;

const Section = ({ title, to, children }) => (
  <section className="block">
    <div className="block-head">
      <h2>{title}</h2>
      {to && <Link to={to}>Xem tất cả</Link>}
    </div>
    {children}
  </section>
);

export default function HomePage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const maLoai = params.get('loai') || '';
  const price = Number(params.get('gia')) || 0;
  const maTh = params.get('hang') || '';
  const ram = params.get('ram') || '';
  const boNho = params.get('bonho') || '';
  const chiConHang = params.get('conhang') === '1';
  const rating = params.get('sao') || '';
  const sort = params.get('sort') || '';
  const page = Math.max(1, Number(params.get('page')) || 1);
  const { data: categories } = useFetch(productService.categories);
  const { data: brands } = useFetch(productService.brands);
  const range = PRICE_RANGES[price];
  const macDinh = !q && !maLoai && !price && !sort && !maTh && !ram && !boNho && !chiConHang && !rating;

  const setFilter = (key, value) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  };
  const xoaLoc = () => {
    const next = new URLSearchParams(params);
    ['hang', 'ram', 'bonho', 'conhang', 'sao', 'gia'].forEach((k) => next.delete(k));
    setParams(next);
  };
  const { data: products, loading, error } = useFetch(
      () => productService.list({
        q: q || undefined,
        maLoai: maLoai || undefined,
        maThuongHieu: maTh || undefined,
        min: range.min,
        max: range.max,
        ram: ram || undefined,
        boNho: boNho || undefined,
        conHang: chiConHang || undefined,
        minRating: rating || undefined,
        sort: sort || undefined,
      }),
      [q, maLoai, maTh, price, ram, boNho, chiConHang, rating, sort]
  );
  const { data: banChay } = useFetch(() => (macDinh ? productService.list({ sort: 'ban_chay' }) : Promise.resolve(null)), [macDinh]);

  const conHang = (banChay || []).filter((p) => ['CON_HANG', 'SAP_HET'].includes(productStatus(p)));
  const danhGia = [...conHang].sort((a, b) => b.diemDanhGiaTb - a.diemDanhGiaTb);
  const giaTot = [...conHang].filter((p) => p.giaBan < 10000000).sort((a, b) => b.diemDanhGiaTb - a.diemDanhGiaTb);
  const hang = categories?.filter((c) => !c.maLoaiCha) || [];
  const thuongHieuNoiBat = POPULAR_BRANDS
    .map((name) => hang.find((category) => category.tenLoai === name))
    .filter(Boolean);
  const popularCategoryIds = new Set(
    CATEGORY_TREE_NAMES
      .map((name) => hang.find((category) => category.tenLoai === name)?.maLoai)
      .filter(Boolean)
  );
  const categoryParentById = new Map((categories || []).map((category) => [category.maLoai, category.maLoaiCha]));
  const filteredCategories = (categories || []).filter((category) => {
    let parentId = category.maLoaiCha;
    while (parentId) {
      if (popularCategoryIds.has(parentId)) return true;
      parentId = categoryParentById.get(parentId);
    }
    return popularCategoryIds.has(category.maLoai);
  });
  const totalProducts = products?.length || 0;
  const totalPages = Math.max(1, Math.ceil(totalProducts / PRODUCTS_PER_PAGE));
  const currentPage = Math.min(page, totalPages);
  const firstProduct = totalProducts ? (currentPage - 1) * PRODUCTS_PER_PAGE + 1 : 0;
  const lastProduct = Math.min(currentPage * PRODUCTS_PER_PAGE, totalProducts);
  const visibleProducts = products?.slice(firstProduct - 1, lastProduct);
  const setPage = (nextPage) => {
    const next = new URLSearchParams(params);
    if (nextPage <= 1) next.delete('page');
    else next.set('page', String(nextPage));
    setParams(next);
    document.getElementById('tat-ca')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };
  const pageItems = totalPages <= 7
    ? Array.from({ length: totalPages }, (_, index) => index + 1)
    : currentPage <= 4
      ? [1, 2, 3, 4, 5, 'ellipsis', totalPages]
      : currentPage >= totalPages - 3
        ? [1, 'ellipsis', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages]
        : [1, 'ellipsis', currentPage - 1, currentPage, currentPage + 1, 'ellipsis', totalPages];

  const slide = (id, tone, tag, title, text, cta, to, p) => ({
    id, tone, tag, title, text, cta, to, image: p?.anhChinh, price: p && formatPrice(p.giaBan),
  });
  const slides = [
    slide('flagship', 'blue', 'Flagship nổi bật', danhGia[0]?.tenMay || 'Điện thoại chính hãng', 'Hiệu năng đỉnh cao, camera chuyên nghiệp. Xem chi tiết và so sánh cấu hình ngay.', 'Xem chi tiết', danhGia[0] ? `/products/${danhGia[0].maSanPham}` : '/', danhGia[0]),
    slide('hot', 'violet', 'Bán chạy nhất tuần', conHang[0]?.tenMay || 'Mẫu máy được chọn nhiều', 'Được khách hàng PhoneNest chọn mua nhiều nhất, sẵn hàng và giao nhanh.', 'Mua ngay', conHang[0] ? `/products/${conHang[0].maSanPham}` : '/', conHang[0]),
    slide('cheap', 'orange', 'Giá tốt dưới 10 triệu', giaTot[0]?.tenMay || 'Giá tốt mỗi ngày', 'Cấu hình đủ dùng, pin bền, giá mềm cho học sinh sinh viên và gia đình.', 'Xem ngay', '/?gia=2', giaTot[0]),
    slide('promo', 'teal', 'Ưu đãi khách hàng mới', 'Voucher tự động áp dụng khi thanh toán', 'Đăng ký tài khoản để nhận ưu đãi: hệ thống tự chọn mã giảm giá và mã vận chuyển tốt nhất cho bạn.', 'Đăng ký ngay', '/register', null),
  ];

  return (
    <>
      {macDinh && (
        <>
          <section className="hero-grid">
            <HeroSlider slides={slides} />
            <aside className="hero-side">
              <h3>Bán chạy nhất</h3>
              {conHang.slice(0, 4).map((p, i) => (
                <Link key={p.maSanPham} to={`/products/${p.maSanPham}`} className="rank-item">
                  <b className="rank-no">{i + 1}</b>
                  <ProductImage src={p.anhChinh} alt="" />
                  <span><em>{p.tenMay}</em><strong>{formatPrice(p.giaBan)}</strong></span>
                </Link>
              ))}
              <button className="rank-more" onClick={() => document.getElementById('tat-ca')?.scrollIntoView({ behavior: 'smooth' })}>Xem tất cả sản phẩm</button>
            </aside>
          </section>

          <section className="services">
            {SERVICES.map((s) => (
              <div key={s.title}>
                <span className="svc-icon"><Icon name={s.icon} size={22} /></span>
                <p><b>{s.title}</b><span>{s.text}</span></p>
              </div>
            ))}
          </section>

          <section className="brand-tiles">
            {thuongHieuNoiBat.map((c) => (
              <Link key={c.maLoai} to={`/?loai=${c.maLoai}`}>{c.tenLoai.replace(/^Điện thoại\s+/i, '')}</Link>
            ))}
          </section>

          {conHang.length > 0 && (
            <Section title="Được đánh giá cao">
              <div className="grid grid-5">{danhGia.slice(0, 5).map((sp) => <ProductCard key={sp.maSanPham} sp={sp} />)}</div>
            </Section>
          )}
          {giaTot.length > 0 && (
            <Section title="Giá tốt dưới 10 triệu" to="/?gia=2">
              <div className="grid grid-5">{giaTot.slice(0, 5).map((sp) => <ProductCard key={sp.maSanPham} sp={sp} />)}</div>
            </Section>
          )}
        </>
      )}

      <section className="block" id="tat-ca">
        <div className="block-head">
          <h2>{q ? `Kết quả cho "${q}"` : maLoai ? categories?.find((c) => c.maLoai === maLoai)?.tenLoai || 'Sản phẩm' : 'Tất cả điện thoại'}</h2>
          <span className="count">{products ? <><b>{products.length}</b> sản phẩm</> : 'Đang tải...'}</span>
        </div>
        <div className="shop">
          {categories && (
              <aside className="card">
                {categories && (
                    <>
                      <h3>Danh mục</h3>
                      <CategoryTree categories={filteredCategories} value={maLoai} onChange={(v) => setFilter('loai', v)}/>
                    </>
                )}
                <ProductFilters
                    brands={brands || []}
                    values={{hang: maTh, ram, bonho: boNho, conhang: chiConHang, sao: rating}}
                    onChange={setFilter}
                    onReset={xoaLoc}
                />
              </aside>
          )}
          <div>
            <div className="toolbar">
              <div className="chips">
                {PRICE_RANGES.map((r, i) => (
                    <button key={r.label} className={price === i ? 'chip active' : 'chip'}
                            onClick={() => setFilter('gia', i || '')}>{r.label}</button>
                ))}
              </div>
              <select aria-label="Sắp xếp" value={sort} onChange={(e) => setFilter('sort', e.target.value)}>
                {SORT_OPTIONS.map((s) => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>
            {error && <p className="error">{error}</p>}
            {products?.length === 0 && <p className="muted">Không có sản phẩm phù hợp. Thử đổi mức giá hoặc danh mục khác.</p>}
            <div className="grid">
              {!products && loading && Array.from({ length: 8 }, (_, i) => <div key={i} className="card skeleton" />)}
              {visibleProducts?.map((sp) => (
                <ProductCard key={sp.maSanPham} sp={sp} />
              ))}
            </div>
            {products && totalProducts > 0 && (
              <div className="home-pagination">
                <div className="home-pagination-pages" aria-label="Phân trang sản phẩm">
                  <button type="button" className="page-arrow" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)} aria-label="Trang trước">‹</button>
                  {pageItems.map((item, index) => item === 'ellipsis'
                    ? <span className="page-ellipsis" key={`ellipsis-${index}`}>...</span>
                    : (
                      <button
                        type="button"
                        key={item}
                        className={item === currentPage ? 'page-number active' : 'page-number'}
                        aria-current={item === currentPage ? 'page' : undefined}
                        onClick={() => setPage(item)}
                      >
                        {item}
                      </button>
                    ))}
                  <button type="button" className="page-arrow" disabled={currentPage === totalPages} onClick={() => setPage(currentPage + 1)} aria-label="Trang sau">›</button>
                </div>
              </div>
            )}
          </div>
        </div>
      </section>
    </>
  );
}
