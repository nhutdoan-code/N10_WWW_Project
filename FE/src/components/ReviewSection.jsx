import { Link } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import useFetch from '../hooks/useFetch';
import { productService } from '../service/productService';
import { formatDay } from '../utils/format';

const stars = (n) => '★'.repeat(n) + '☆'.repeat(5 - n);

export default function ReviewSection({ ma }) {
  const { isCustomer } = useAuth();
  const { data: reviews } = useFetch(() => productService.reviews(ma), [ma]);

  return (
    <section id="danh-gia">
      <h3>Đánh giá sản phẩm ({reviews?.length || 0})</h3>
      {reviews?.length === 0 && <p className="muted">Chưa có đánh giá nào.</p>}
      {reviews?.map((r) => (
        <div key={r.maDanhGia} className="card review">
          <div className="row between"><b>{r.tenKhach}</b><span className="stars">{stars(r.diemSao)}</span></div>
          {r.noiDungBinhLuan && <p>{r.noiDungBinhLuan}</p>}
          {r.hinhAnhThucTe && <img className="thumb" src={r.hinhAnhThucTe} alt="" />}
          <small className="muted">{formatDay(r.ngayDanhGia)} · Đã mua hàng</small>
        </div>
      ))}
      <p className="muted">
        Chỉ khách đã nhận hàng mới được đánh giá, trong vòng 30 ngày kể từ khi nhận.{' '}
        {isCustomer && <Link to="/orders?tt=DA_GIAO">Xem đơn hàng đã giao của bạn</Link>}
      </p>
    </section>
  );
}
