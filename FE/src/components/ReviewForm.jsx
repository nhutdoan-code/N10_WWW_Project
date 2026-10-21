import { useState } from 'react';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { errorMessage } from '../utils/format';

const NHAN = ['', 'Rất tệ', 'Tệ', 'Bình thường', 'Tốt', 'Rất tốt'];

export default function ReviewForm({ maDonHang, sanPham, onDone, onCancel }) {
  const [form, setForm] = useState({ diemSao: 5, noiDungBinhLuan: '', hinhAnhThucTe: '' });
  const toast = useToast();
  const canLyDo = form.diemSao <= 2 && form.noiDungBinhLuan.trim().length < 10;

  const submit = async (e) => {
    e.preventDefault();
    if (canLyDo) return toast('Với đánh giá 1 - 2 sao, vui lòng cho biết lý do (ít nhất 10 ký tự)', 'error');
    try {
      await customerService.review({ maDonHang, maSanPham: sanPham.maSanPham, ...form });
      toast('Cảm ơn bạn đã đánh giá!');
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  };

  return (
    <form className="form" onSubmit={submit}>
      <div className="line-item">
        <img className="thumb" src={sanPham.anhChinh} alt="" />
        <b>{sanPham.tenMay}</b>
      </div>
      <div className="star-input" role="radiogroup" aria-label="Số sao">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={form.diemSao === n} className={n <= form.diemSao ? 'on' : ''}
            onClick={() => setForm({ ...form, diemSao: n })}>★</button>
        ))}
        <span className="muted">{NHAN[form.diemSao]}</span>
      </div>
      <label className="field">
        <span>Nhận xét {form.diemSao <= 2 ? '(bắt buộc)' : '(không bắt buộc)'}</span>
        <textarea rows="4" maxLength={1000} placeholder="Chia sẻ trải nghiệm thực tế của bạn về sản phẩm" value={form.noiDungBinhLuan}
          onChange={(e) => setForm({ ...form, noiDungBinhLuan: e.target.value })} />
      </label>
      <label className="field">
        <span>Link ảnh thực tế (không bắt buộc)</span>
        <input type="url" maxLength={500} placeholder="https://..." value={form.hinhAnhThucTe} onChange={(e) => setForm({ ...form, hinhAnhThucTe: e.target.value })} />
      </label>
      <div className="row end">
        <button type="button" className="btn" onClick={onCancel}>Hủy</button>
        <button className="btn btn-primary">Gửi đánh giá</button>
      </div>
    </form>
  );
}
