import { useState } from 'react';
import useAuth from '../hooks/useAuth';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { productService } from '../service/productService';
import { errorMessage, formatDay } from '../utils/format';

const stars = (n) => '★'.repeat(n) + '☆'.repeat(5 - n);

export default function ReviewSection({ ma }) {
  const { isCustomer } = useAuth();
  const { data: reviews, reload } = useFetch(() => productService.reviews(ma), [ma]);
  const { data: eligible, reload: reloadEligible } = useFetch(() => (isCustomer ? customerService.canReview(ma) : Promise.resolve(null)), [ma, isCustomer]);
  const [form, setForm] = useState({ diemSao: 5, noiDungBinhLuan: '', hinhAnhThucTe: '' });
  const toast = useToast();

  const submit = async (e) => {
    e.preventDefault();
    try {
      await customerService.review({ maSanPham: ma, ...form });
      toast('Cảm ơn bạn đã đánh giá!');
      setForm({ diemSao: 5, noiDungBinhLuan: '', hinhAnhThucTe: '' });
      reload();
      reloadEligible();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  };

  return (
    <section id="danh-gia">
      <h3>Đánh giá sản phẩm ({reviews?.length || 0})</h3>
      {reviews?.length === 0 && <p className="muted">Chưa có đánh giá nào.</p>}
      {reviews?.map((r) => (
        <div key={r.maDanhGia} className="card review">
          <div className="row between"><b>{r.tenKhach}</b><span className="stars">{stars(r.diemSao)}</span></div>
          <p>{r.noiDungBinhLuan}</p>
          {r.hinhAnhThucTe && <img className="thumb" src={r.hinhAnhThucTe} alt="" />}
          <small className="muted">{formatDay(r.ngayDanhGia)}</small>
        </div>
      ))}
      {eligible?.duocDanhGia && (
        <form className="form card" onSubmit={submit}>
          <b>Viết đánh giá của bạn</b>
          <select value={form.diemSao} onChange={(e) => setForm({ ...form, diemSao: Number(e.target.value) })}>
            {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{stars(n)}</option>)}
          </select>
          <textarea rows="3" placeholder="Chia sẻ trải nghiệm của bạn" value={form.noiDungBinhLuan} onChange={(e) => setForm({ ...form, noiDungBinhLuan: e.target.value })} />
          <input placeholder="Link ảnh thực tế (tuỳ chọn)" value={form.hinhAnhThucTe} onChange={(e) => setForm({ ...form, hinhAnhThucTe: e.target.value })} />
          <button className="btn btn-primary">Gửi đánh giá</button>
        </form>
      )}
      {eligible && !eligible.duocDanhGia && <p className="muted">{eligible.lyDo}</p>}
    </section>
  );
}
