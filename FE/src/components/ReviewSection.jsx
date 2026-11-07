import { useState } from 'react';
import { Link } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';
import { formatDay } from '../utils/format';
import Modal from './Modal';
import ProductImage from './ProductImage';

const stars = (n) => '★'.repeat(n) + '☆'.repeat(5 - n);

export default function ReviewSection({ ma }) {
  const { isCustomer } = useAuth();
  const { data: reviews, loading, error, reload } = useFetch(() => productService.reviews(ma), [ma]);
  const [selectedImage, setSelectedImage] = useState(null);

  return (
    <section id="danh-gia">
      <h3>Đánh giá sản phẩm ({reviews?.length || 0})</h3>
      {loading && <p className="muted" role="status">Đang tải đánh giá...</p>}
      {error && (
        <div className="error" role="alert">
          <p>Không tải được đánh giá: {error}</p>
          <button type="button" className="btn" onClick={reload}>Thử tải lại</button>
        </div>
      )}
      {!loading && !error && reviews?.length === 0 && <p className="muted">Chưa có đánh giá nào.</p>}
      {reviews?.map((r) => (
        <div key={r.maDanhGia} className="card review">
          <div className="row between"><b>{r.tenKhach}</b><span className="stars">{stars(r.diemSao)}</span></div>
          {r.noiDungBinhLuan && <p>{r.noiDungBinhLuan}</p>}
          {r.hinhAnhThucTe && (
            <button type="button" className="review-image-button" onClick={() => setSelectedImage(r.hinhAnhThucTe)}
              aria-label={`Phóng to ảnh đánh giá của ${r.tenKhach}`}>
              <ProductImage className="review-image" src={r.hinhAnhThucTe} alt={`Ảnh đánh giá sản phẩm của ${r.tenKhach}`} />
            </button>
          )}
          <small className="muted">{formatDay(r.ngayDanhGia)} · Đã mua hàng</small>
        </div>
      ))}
      <p className="muted">
        Chỉ khách đã nhận hàng mới được đánh giá, trong vòng 30 ngày kể từ khi nhận.{' '}
        {isCustomer && <Link to="/orders?tt=DA_GIAO">Xem đơn hàng đã giao của bạn</Link>}
      </p>
      {selectedImage && (
        <Modal title="Ảnh đánh giá" onClose={() => setSelectedImage(null)}>
          <ProductImage className="review-image-full" src={selectedImage} alt="Ảnh đánh giá được phóng to" />
        </Modal>
      )}
    </section>
  );
}
