import { useEffect, useState } from 'react';
import AdminPagination from '../../components/AdminPagination';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatDay } from '../../utils/format';

export default function AdminFeedbackPage() {
  const [reviewQ, setReviewQ] = useState('');
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewSize, setReviewSize] = useState(20);
  const [questionQ, setQuestionQ] = useState('');
  const [questionPage, setQuestionPage] = useState(0);
  const [questionSize, setQuestionSize] = useState(20);
  const reviewSearch = useDebounce(reviewQ);
  const questionSearch = useDebounce(questionQ);
  const { data: reviewResult, loading: reviewsLoading, reload: reloadReviews } = useFetch(
    () => adminService.reviewsPage({ q: reviewSearch || undefined, page: reviewPage, size: reviewSize }),
    [reviewSearch, reviewPage, reviewSize]
  );
  const { data: questionResult, loading: questionsLoading, reload: reloadQuestions } = useFetch(
    () => adminService.questionsPage({ q: questionSearch || undefined, page: questionPage, size: questionSize }),
    [questionSearch, questionPage, questionSize]
  );
  const reviews = reviewResult?.content || [];
  const questions = questionResult?.content || [];
  const [answers, setAnswers] = useState({});
  const toast = useToast();

  useEffect(() => {
    if (reviewResult && reviewPage > 0 && (reviewResult.totalElements === 0 || reviewPage >= reviewResult.totalPages)) {
      setReviewPage(Math.max(0, reviewResult.totalPages - 1));
    }
  }, [reviewResult, reviewPage]);

  useEffect(() => {
    if (questionResult && questionPage > 0 && (questionResult.totalElements === 0 || questionPage >= questionResult.totalPages)) {
      setQuestionPage(Math.max(0, questionResult.totalPages - 1));
    }
  }, [questionResult, questionPage]);

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
      <div className="row" style={{ gap: '12px', marginBottom: '16px' }}>
        <input
          type="search"
          style={{ flex: 1 }}
          placeholder="Tìm đánh giá theo sản phẩm, khách hoặc nội dung..."
          value={reviewQ}
          onChange={(event) => { setReviewQ(event.target.value); setReviewPage(0); }}
        />
      </div>
      <table className="table">
        <thead>
          <tr><th>Sản phẩm</th><th>Khách</th><th>Sao</th><th>Nội dung</th><th>Ngày</th><th></th></tr>
        </thead>
        <tbody>
          {reviews.map((r) => (
            <tr key={r.maDanhGia}>
              <td>{r.tenSanPham}</td>
              <td>{r.tenKhach}</td>
              <td>{r.diemSao} ★</td>
              <td>{r.noiDungBinhLuan}</td>
              <td>{formatDay(r.ngayDanhGia)}</td>
              <td><button className="btn btn-danger" onClick={() => window.confirm('Xóa đánh giá này?') && run(() => adminService.deleteReview(r.maDanhGia), 'Đã xóa đánh giá', reloadReviews)}>Xóa</button></td>
            </tr>
          ))}
          {!reviewsLoading && reviews.length === 0 && (
            <tr><td colSpan={6} style={{ textAlign: 'center', padding: '24px' }}>Không tìm thấy đánh giá phù hợp.</td></tr>
          )}
        </tbody>
      </table>
      <AdminPagination
        page={reviewPage}
        totalPages={reviewResult?.totalPages}
        totalElements={reviewResult?.totalElements}
        size={reviewSize}
        loading={reviewsLoading}
        onPageChange={setReviewPage}
        onSizeChange={(nextSize) => { setReviewSize(nextSize); setReviewPage(0); }}
      />

      <h2>Hỏi đáp sản phẩm</h2>
      <div className="row" style={{ gap: '12px', marginBottom: '16px' }}>
        <input
          type="search"
          style={{ flex: 1 }}
          placeholder="Tìm hỏi đáp theo sản phẩm, khách hoặc nội dung..."
          value={questionQ}
          onChange={(event) => { setQuestionQ(event.target.value); setQuestionPage(0); }}
        />
      </div>
      {questions.map((q) => (
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
      {!questionsLoading && questions.length === 0 && <p className="muted">Không tìm thấy câu hỏi phù hợp.</p>}
      <AdminPagination
        page={questionPage}
        totalPages={questionResult?.totalPages}
        totalElements={questionResult?.totalElements}
        size={questionSize}
        loading={questionsLoading}
        onPageChange={setQuestionPage}
        onSizeChange={(nextSize) => { setQuestionSize(nextSize); setQuestionPage(0); }}
      />
    </>
  );
}
