import { useState } from 'react';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatDay } from '../../utils/format';

export default function AdminFeedbackPage() {
  const { data: reviews, reload: reloadReviews } = useFetch(adminService.reviews);
  const { data: questions, reload: reloadQuestions } = useFetch(adminService.questions);
  const [answers, setAnswers] = useState({});
  const toast = useToast();

  const run = async (action, message, reload) => {
    try {
      await action();
      toast(message);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  return (
    <>
      <h2>Kiểm duyệt đánh giá</h2>
      <table className="table">
        <thead>
          <tr><th>Sản phẩm</th><th>Khách</th><th>Sao</th><th>Nội dung</th><th>Ngày</th><th></th></tr>
        </thead>
        <tbody>
          {reviews?.map((r) => (
            <tr key={r.maDanhGia}>
              <td>{r.tenSanPham}</td>
              <td>{r.tenKhach}</td>
              <td>{r.diemSao} ★</td>
              <td>{r.noiDungBinhLuan}</td>
              <td>{formatDay(r.ngayDanhGia)}</td>
              <td><button className="btn btn-danger" onClick={() => window.confirm('Xóa đánh giá này?') && run(() => adminService.deleteReview(r.maDanhGia), 'Đã xóa đánh giá', reloadReviews)}>Xóa</button></td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Hỏi đáp sản phẩm</h2>
      {questions?.map((q) => (
        <div key={q.maCauHoi} className="card order">
          <div className="row between">
            <b>{q.tenSanPham}</b>
            <button className="btn btn-danger" onClick={() => window.confirm('Xóa câu hỏi này?') && run(() => adminService.deleteQuestion(q.maCauHoi), 'Đã xóa câu hỏi', reloadQuestions)}>Xóa</button>
          </div>
          <p><b>{q.tenKhach}:</b> {q.noiDungCauHoi} <small className="muted">({formatDay(q.ngayHoi)})</small></p>
          {q.noiDungTraLoi && <p className="answer">↳ {q.noiDungTraLoi}</p>}
          <div className="row">
            <input
              className="grow"
              placeholder={q.noiDungTraLoi ? 'Cập nhật câu trả lời...' : 'Nhập câu trả lời...'}
              value={answers[q.maCauHoi] ?? ''}
              onChange={(e) => setAnswers({ ...answers, [q.maCauHoi]: e.target.value })}
            />
            <button
              className="btn btn-primary"
              disabled={!answers[q.maCauHoi]?.trim()}
              onClick={() => run(async () => { await adminService.answer(q.maCauHoi, answers[q.maCauHoi]); setAnswers({ ...answers, [q.maCauHoi]: '' }); }, 'Đã trả lời', reloadQuestions)}
            >
              Trả lời
            </button>
          </div>
        </div>
      ))}
    </>
  );
}
