import { useEffect, useState } from 'react';
import ProductImage from './ProductImage';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { errorMessage } from '../utils/format';

const NHAN = ['', 'Rất tệ', 'Tệ', 'Bình thường', 'Tốt', 'Rất tốt'];

export default function ReviewForm({ maDonHang, sanPham, onDone, onCancel }) {
  const [form, setForm] = useState({ diemSao: 5, noiDungBinhLuan: '' });
  const [imageFile, setImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const toast = useToast();
  const canLyDo = form.diemSao <= 2 && form.noiDungBinhLuan.trim().length < 10;

  useEffect(() => {
    if (!imageFile) {
      setImagePreview('');
      return undefined;
    }
    const url = URL.createObjectURL(imageFile);
    setImagePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [imageFile]);

  const selectImage = (e) => {
    const file = e.target.files?.[0] || null;
    e.target.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      toast('Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast('Ảnh không được vượt quá 5MB', 'error');
      return;
    }
    setImageFile(file);
  };

  const submit = async (e) => {
    e.preventDefault();
    if (submitting) return;
    if (canLyDo) {
      toast('Với đánh giá 1 - 2 sao, vui lòng cho biết lý do (ít nhất 10 ký tự)', 'error');
      return;
    }
    setSubmitting(true);
    try {
      const hinhAnhThucTe = imageFile ? (await customerService.uploadReviewImage(imageFile)).url : null;
      await customerService.review({ maDonHang, maSanPham: sanPham.maSanPham, ...form, hinhAnhThucTe });
      toast('Cảm ơn bạn đã đánh giá!');
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="form" onSubmit={submit}>
      <div className="line-item">
        <ProductImage className="thumb" src={sanPham.anhChinh} alt="" />
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
        <span>Ảnh thực tế (không bắt buộc)</span>
        <input type="file" accept="image/jpeg,image/png,image/webp" onChange={selectImage} disabled={submitting} />
        <small className="muted">Chọn ảnh JPG, PNG hoặc WEBP từ thiết bị (tối đa 5MB).</small>
      </label>
      {imagePreview && (
        <div className="line-item">
          <ProductImage className="thumb" src={imagePreview} alt="Ảnh đánh giá đã chọn" />
          <span className="muted">{imageFile.name}</span>
          <button type="button" className="btn" onClick={() => setImageFile(null)} disabled={submitting}>Bỏ ảnh</button>
        </div>
      )}
      <div className="row end">
        <button type="button" className="btn" onClick={onCancel} disabled={submitting}>Hủy</button>
        <button className="btn btn-primary" disabled={submitting || canLyDo}>
          {submitting ? 'Đang gửi...' : 'Gửi đánh giá'}
        </button>
      </div>
    </form>
  );
}
