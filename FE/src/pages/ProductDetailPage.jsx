import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
    <div className="detail">
      <div>
        <img className="detail-img" src={image || sp.anhChinh} alt={sp.tenMay} />
        <div className="thumbs">
          {sp.hinhAnhs.map((url) => (
            <img key={url} src={url} alt="" onClick={() => setImage(url)} />
          ))}
        </div>
      </div>
      <div>
        <h1>{sp.tenMay}</h1>
        <p className="muted">Hãng: {sp.tenLoai} · Mã: {sp.maSanPham} · <span className="rating">{sp.diemDanhGiaTb}</span></p>
        <div className="price big">{formatPrice(sp.giaBan)}</div>
        <p><StatusBadge status={sp.trangThai} /> {sp.trangThai === 'CON_HANG' && <span className="muted">(còn {sp.soLuongTon})</span>}</p>
        <p>{sp.moTa}</p>
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
          {(sp.thongSoKyThuat || '').split('|').map((t) => (
            <li key={t}>{t.trim()}</li>
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
