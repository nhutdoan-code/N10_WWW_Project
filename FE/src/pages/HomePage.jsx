import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import CategoryTree from '../components/CategoryTree';
import HeroSlider from '../components/HeroSlider';
import Icon from '../components/Icon';
import ProductCard from '../components/ProductCard';
import { PRICE_RANGES, SORT_OPTIONS } from '../config/constants';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';
import { formatPrice } from '../utils/format';

const SERVICES = [
  { icon: 'shield', title: 'Hàng chính hãng', text: 'Nguyên seal, bảo hành đầy đủ' },
  { icon: 'truck', title: 'Giao hàng nhanh', text: 'Phí ship rõ ràng theo khu vực' },
  { icon: 'refresh', title: 'Đổi trả dễ dàng', text: 'Hỗ trợ đổi trả khi máy lỗi' },
  { icon: 'gift', title: 'Voucher mỗi ngày', text: 'Tự chọn mã tốt nhất khi thanh toán' },
];

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
  const [sort, setSort] = useState('');
  const { data: categories } = useFetch(productService.categories);
  const range = PRICE_RANGES[price];
  const macDinh = !q && !maLoai && !price && !sort;

  const setFilter = (key, value) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next);
  };

  const { data: products, loading, error } = useFetch(
    () => productService.list({ q, maLoai, min: range.min, max: range.max, sort }),
    [q, maLoai, price, sort]
  );
  const { data: banChay } = useFetch(() => (macDinh ? productService.list({ sort: 'ban_chay' }) : Promise.resolve(null)), [macDinh]);

  const conHang = (banChay || []).filter((p) => p.trangThai === 'CON_HANG');
  const danhGia = [...conHang].sort((a, b) => b.diemDanhGiaTb - a.diemDanhGiaTb);
  const giaTot = [...conHang].filter((p) => p.giaBan < 10000000).sort((a, b) => b.diemDanhGiaTb - a.diemDanhGiaTb);
  const hang = categories?.filter((c) => !c.maLoaiCha) || [];

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
            <div className="hero-side">
              <Link to="/?gia=1" className="side-banner sb-1"><b>Dưới 5 triệu</b><span>Điện thoại giá rẻ, pin trâu</span></Link>
              <Link to="/?gia=4" className="side-banner sb-2"><b>Flagship trên 20 triệu</b><span>Camera & hiệu năng hàng đầu</span></Link>
            </div>
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
            {hang.map((c) => (
              <Link key={c.maLoai} to={`/?loai=${c.maLoai}`}>{c.tenLoai}</Link>
            ))}
          </section>

          {conHang.length > 0 && (
            <Section title="Bán chạy nhất">
              <div className="grid grid-5">{conHang.slice(0, 5).map((sp) => <ProductCard key={sp.maSanPham} sp={sp} />)}</div>
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
              <h3>Danh mục</h3>
              <CategoryTree categories={categories} value={maLoai} onChange={(v) => setFilter('loai', v)} />
            </aside>
          )}
          <div>
            <div className="toolbar">
              <div className="chips">
                {PRICE_RANGES.map((r, i) => (
                  <button key={r.label} className={price === i ? 'chip active' : 'chip'} onClick={() => setFilter('gia', i || '')}>{r.label}</button>
                ))}
              </div>
              <select aria-label="Sắp xếp" value={sort} onChange={(e) => setSort(e.target.value)}>
                {SORT_OPTIONS.map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
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
      </section>
    </>
  );
}
