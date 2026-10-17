import { useState } from 'react';
import useAuth from '../hooks/useAuth';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { productService } from '../service/productService';
import { errorMessage, formatDay } from '../utils/format';

export default function QuestionSection({ ma }) {
  const { isCustomer } = useAuth();
  const { data: questions, reload } = useFetch(() => productService.questions(ma), [ma]);
  const [text, setText] = useState('');
  const toast = useToast();

  const submit = async (e) => {
    e.preventDefault();
    try {
      await customerService.ask(ma, text);
      toast('Đã gửi câu hỏi, cửa hàng sẽ trả lời sớm');
      setText('');
      reload();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  };

  return (
    <section>
      <h3>Hỏi đáp ({questions?.length || 0})</h3>
      {questions?.length === 0 && <p className="muted">Chưa có câu hỏi nào.</p>}
      {questions?.map((q) => (
        <div key={q.maCauHoi} className="card review">
          <p><b>{q.tenKhach}:</b> {q.noiDungCauHoi} <small className="muted">({formatDay(q.ngayHoi)})</small></p>
          {q.noiDungTraLoi ? <p className="answer">↳ <b>PhoneNest:</b> {q.noiDungTraLoi}</p> : <small className="muted">Đang chờ trả lời...</small>}
        </div>
      ))}
      {isCustomer ? (
        <form className="row" onSubmit={submit}>
          <input className="grow" required placeholder="Đặt câu hỏi về sản phẩm này..." value={text} onChange={(e) => setText(e.target.value)} />
          <button className="btn btn-primary">Gửi câu hỏi</button>
        </form>
      ) : (
        <p className="muted">Đăng nhập để đặt câu hỏi.</p>
      )}
    </section>
  );
}
