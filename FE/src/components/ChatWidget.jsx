import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';
import { chatService } from '../service/chatService';
import { formatPrice } from '../utils/format';

const KEY = 'phonenest-chat';
const GOI_Y = ['Điện thoại pin trâu dưới 10 triệu', 'Máy chụp ảnh đẹp giá tốt', 'Phí giao hàng thế nào?', 'Hiện có voucher nào?'];
const CHAO = { role: 'assistant', content: 'Xin chào! Mình là trợ lý PhoneNest. Bạn cần tư vấn chọn điện thoại, hỏi phí giao hàng hay voucher? Cứ nhắn cho mình nhé.' };

const doc = () => {
  try {
    return JSON.parse(sessionStorage.getItem(KEY)) || [CHAO];
  } catch {
    return [CHAO];
  }
};

export default function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState(doc);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const bottom = useRef(null);

  useEffect(() => {
    try {
      sessionStorage.setItem(KEY, JSON.stringify(messages));
    } catch {
      /* bỏ qua */
    }
    bottom.current?.scrollIntoView({ block: 'end' });
  }, [messages, open, sending]);

  const send = async (content) => {
    const message = content.trim();
    if (!message || sending) return;
    const history = messages.map(({ role, content: c }) => ({ role, content: c }));
    setMessages([...messages, { role: 'user', content: message }]);
    setText('');
    setSending(true);
    try {
      const res = await chatService.send(message, history.slice(1));
      setMessages((m) => [...m, { role: 'assistant', content: res.reply, products: res.products }]);
    } catch {
      setMessages((m) => [...m, { role: 'assistant', content: 'Xin lỗi, mình chưa trả lời được lúc này. Bạn thử lại sau ít phút nhé.' }]);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="chat no-print">
      {open && (
        <section className="chat-panel" aria-label="Trợ lý PhoneNest">
          <header>
            <span className="chat-avatar"><Icon name="sparkle" size={18} /></span>
            <div>
              <b>Trợ lý PhoneNest</b>
              <small>Tư vấn chọn máy, phí giao hàng, voucher</small>
            </div>
            <button type="button" className="icon-btn" onClick={() => setOpen(false)} aria-label="Đóng"><Icon name="close" size={18} /></button>
          </header>
          <div className="chat-body">
            {messages.map((m, i) => (
              <div key={i} className={`chat-msg ${m.role}`}>
                <p>{m.content}</p>
                {m.products?.map((p) => (
                  <Link key={p.maSanPham} className="chat-product" to={`/products/${p.maSanPham}`} onClick={() => setOpen(false)}>
                    <img src={p.anhChinh} alt="" />
                    <span>{p.tenMay}<b>{formatPrice(p.giaBan)}</b></span>
                  </Link>
                ))}
              </div>
            ))}
            {messages.length === 1 && (
              <div className="chat-suggest">
                {GOI_Y.map((g) => <button key={g} type="button" onClick={() => send(g)}>{g}</button>)}
              </div>
            )}
            {sending && <div className="chat-msg assistant"><p className="typing"><i /><i /><i /></p></div>}
            <div ref={bottom} />
          </div>
          <form onSubmit={(e) => { e.preventDefault(); send(text); }}>
            <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Nhập câu hỏi của bạn..." maxLength={500} />
            <button className="chat-send" disabled={!text.trim() || sending} aria-label="Gửi"><Icon name="send" size={18} /></button>
          </form>
        </section>
      )}
      <button type="button" className="chat-fab" onClick={() => setOpen(!open)} aria-label={open ? 'Đóng trợ lý' : 'Chat với trợ lý'}>
        <Icon name={open ? 'close' : 'chat'} size={26} />
      </button>
    </div>
  );
}
