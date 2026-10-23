import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Icon from '../components/Icon';
import ProductCard from '../components/ProductCard';
import QuestionSection from '../components/QuestionSection';
import ReviewSection from '../components/ReviewSection';
import StatusBadge from '../components/StatusBadge';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { productService } from '../service/productService';
import { wishlistService } from '../service/wishlistService';
import { errorMessage, formatPrice } from '../utils/format';
import { productStatus } from '../utils/stock';
import { SPEC_FIELDS, specValue } from '../utils/specs';

const COMMITS = [
  { icon: 'shield', title: 'Hàng chính hãng', text: 'Nguyên seal, bảo hành theo chính sách của hãng.' },
  { icon: 'refresh', title: 'Đổi trả ngay trên web', text: 'Gửi yêu cầu đổi trả hoặc hoàn tiền và theo dõi trạng thái xử lý.' },
  { icon: 'truck', title: 'Phí giao hàng rõ ràng', text: 'Phí theo khu vực, hiển thị trước khi đặt; miễn phí khi đủ điều kiện.' },
  { icon: 'gift', title: 'Voucher tự động', text: 'Hệ thống tự chọn mã giảm giá tốt nhất ở bước thanh toán.' },
];

export default function ProductDetailPage() {
  const { ma } = useParams();
  const navigate = useNavigate();
  const { isCustomer } = useAuth();
  const { add } = useCart();
  const { ids, toggle } = useCompare();
  const toast = useToast();
  const [image, setImage] = useState(null);
  const [soLuong, setSoLuong] = useState(1);
  const { data: sp, loading, error } = useFetch(() => productService.get(ma), [ma]);
  const { data: related } = useFetch(() => productService.related(ma), [ma]);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
    } catch (e) {
      toast(e.response ? errorMessage(e) : e.message, 'error');
    }
  };

  return (
    <>
    <nav className="crumbs" aria-label="Đường dẫn">
      <Link to="/">Trang chủ</Link><span>›</span>
      <Link to={`/?loai=${sp.maLoai}`}>{sp.tenLoai}</Link><span>›</span>
      <b>{sp.tenMay}</b>
    </nav>
    <div className="detail">
      <div>
        <img className="detail-img" src={image || sp.anhChinh} alt={sp.tenMay} />
        <div className="thumbs">
          {sp.hinhAnhs.map((url) => (
            <img key={url} src={url} alt="" onClick={() => setImage(url)} />
          ))}
        </div>
        <section className="desc">
          <h3>Mô tả sản phẩm</h3>
          {(sp.moTa || 'Đang cập nhật mô tả cho sản phẩm này.').split(/\n\s*\n/).map((t) => <p key={t}>{t}</p>)}
        </section>
        <section className="commit">
          <h3>PhoneNest cam kết</h3>
          <ul>
            {COMMITS.map((c) => (
              <li key={c.title}>
                <Icon name={c.icon} size={22} />
                <span><b>{c.title}</b>{c.text}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
      <div>
        <h1>{sp.tenMay}</h1>
        <p className="muted">Hãng: {sp.tenLoai} · Mã: {sp.maSanPham} · <span className="rating">{sp.diemDanhGiaTb}</span></p>
        <div className="price big">{formatPrice(sp.giaBan)}</div>
        <p><StatusBadge status={productStatus(sp)} /> {sp.trangThai === 'CON_HANG' && <span className="muted">(còn {sp.soLuongTon})</span>}</p>
        <div className="row">
          <input type="number" min="1" max={sp.soLuongTon} value={soLuong} onChange={(e) => setSoLuong(Number(e.target.value))} className="qty" />
          <button className="btn btn-primary btn-lg" disabled={sp.trangThai !== 'CON_HANG'} onClick={() => run(() => add(sp, soLuong), 'Đã thêm vào giỏ hàng')}>
            Thêm vào giỏ
          </button>
          {isCustomer && (
            <button className="btn btn-lg" disabled={sp.trangThai !== 'CON_HANG'} onClick={() => navigate(`/checkout?mua=${sp.maSanPham}&sl=${soLuong}`)}>
              Mua ngay
            </button>
          )}
          {isCustomer && (
            <button className="btn" onClick={() => run(() => wishlistService.add(sp.maSanPham), 'Đã thêm vào yêu thích')}>
              ♡ Yêu thích
            </button>
          )}
          <button className="btn" onClick={() => run(() => toggle(sp.maSanPham), ids.includes(sp.maSanPham) ? 'Đã bỏ khỏi so sánh' : 'Đã thêm vào so sánh')}>
            ⇄ {ids.includes(sp.maSanPham) ? 'Bỏ so sánh' : 'So sánh'}
          </button>
        </div>
        <h3>Thông số kỹ thuật</h3>
        <ul className="specs">
          {SPEC_FIELDS.filter((f) => specValue(f, sp.thongSo) !== '—').map((f) => (
            <li key={f.key}><span>{f.label}</span><b>{specValue(f, sp.thongSo)}</b></li>
          ))}
        </ul>
      </div>
    </div>
    <ReviewSection ma={ma} />
    <QuestionSection ma={ma} />
    {related?.length > 0 && (
      <section>
        <h3>Sản phẩm liên quan</h3>
        <div className="grid">{related.map((r) => <ProductCard key={r.maSanPham} sp={r} />)}</div>
      </section>
    )}
    </>
  );
}
