import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';
import ProductImage from './ProductImage';
import { chatService } from '../service/chatService';
import { trackingService } from '../service/trackingService';
import { formatPrice } from '../utils/format';
import { getVisitorId } from '../utils/activityTracking';
import useAuth from '../hooks/useAuth';
import {
  getChatStorageKey,
  loadChatHistory,
  saveChatHistory,
} from '../utils/chatStorage';

const GOI_Y = [
  'Điện thoại pin trâu dưới 10 triệu',
  'Máy chụp ảnh đẹp giá tốt',
  'Phí giao hàng thế nào?',
  'Hiện có voucher nào?',
];

function ChatSession({ sessionKey, open, setOpen }) {
  const [messages, setMessages] = useState(() => loadChatHistory(sessionKey));
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const bottom = useRef(null);
  const abortControllerRef = useRef(null);
  const isAbortedRef = useRef(false);

  // When sessionKey unmounts or changes, abort any pending request
  useEffect(() => {
    isAbortedRef.current = false;
    return () => {
      isAbortedRef.current = true;
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, []);

  // Save messages to sessionKey storage whenever messages change
  useEffect(() => {
    if (!sessionKey) return;
    saveChatHistory(sessionKey, messages);
    bottom.current?.scrollIntoView({ block: 'end' });
  }, [messages, open, sending, sessionKey]);

  const send = async (content) => {
    const message = content.trim();
    if (!message || sending) return;

    const history = messages.map(({ role, content: c }) => ({ role, content: c }));
    setMessages((prev) => [...prev, { role: 'user', content: message }]);
    setText('');
    setSending(true);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      const res = await chatService.send(
        message,
        history.slice(1),
        getVisitorId(),
        { signal: controller.signal }
      );

      // If component unmounted or request was aborted/superseded, ignore response
      if (isAbortedRef.current) return;

      setMessages((m) => [...m, { role: 'assistant', content: res.reply, products: res.products }]);
    } catch (err) {
      if (isAbortedRef.current || err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') {
        // Request was aborted due to session change/logout; do not show error in UI
        return;
      }
      setMessages((m) => [
        ...m,
        {
          role: 'assistant',
          content: 'Xin lỗi, mình chưa trả lời được lúc này. Bạn thử lại sau ít phút nhé.',
        },
      ]);
    } finally {
      if (!isAbortedRef.current) {
        setSending(false);
      }
    }
  };

  return (
    <>
      {open && (
        <section className="chat-panel" aria-label="Trợ lý PhoneNest">
          <header>
            <span className="chat-avatar"><Icon name="sparkle" size={18} /></span>
            <div>
              <b>Trợ lý PhoneNest</b>
              <small>Tư vấn chọn máy, phí giao hàng, voucher</small>
            </div>
            <button
              type="button"
              className="icon-btn"
              onClick={() => setOpen(false)}
              aria-label="Đóng"
            >
              <Icon name="close" size={18} />
            </button>
          </header>
          <div className="chat-body">
            {messages.map((m, i) => (
              <div key={i} className={`chat-msg ${m.role}`}>
                <p>{m.content}</p>
                {m.products?.map((p) => (
                  <Link
                    key={p.maSanPham}
                    className="chat-product"
                    to={`/products/${p.maSanPham}`}
                    onClick={() => {
                      trackingService.trackRecommendationClick(p.maSanPham, 'AI_CHATBOT');
                      setOpen(false);
                    }}
                  >
                    <ProductImage src={p.anhChinh} alt="" />
                    <span>{p.tenMay}<b>{formatPrice(p.giaBan)}</b></span>
                  </Link>
                ))}
              </div>
            ))}
            {messages.length === 1 && (
              <div className="chat-suggest">
                {GOI_Y.map((g) => (
                  <button key={g} type="button" onClick={() => send(g)}>
                    {g}
                  </button>
                ))}
              </div>
            )}
            {sending && (
              <div className="chat-msg assistant">
                <p className="typing"><i /><i /><i /></p>
              </div>
            )}
            <div ref={bottom} />
          </div>
          <form onSubmit={(e) => { e.preventDefault(); send(text); }}>
            <input
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Nhập câu hỏi của bạn..."
              maxLength={500}
            />
            <button
              className="chat-send"
              disabled={!text.trim() || sending}
              aria-label="Gửi"
            >
              <Icon name="send" size={18} />
            </button>
          </form>
        </section>
      )}
      <button
        type="button"
        className="chat-fab"
        onClick={() => setOpen(!open)}
        aria-label={open ? 'Đóng trợ lý' : 'Chat với trợ lý'}
      >
        <Icon name={open ? 'close' : 'chat'} size={26} />
      </button>
    </>
  );
}

export default function ChatWidget() {
  const [open, setOpen] = useState(false);
  const auth = useAuth();
  const user = auth?.user;
  const isInitialized = auth ? (auth.isInitialized !== false && !auth.loading) : true;

  const sessionKey = getChatStorageKey(user, isInitialized);

  if (!sessionKey) return null;

  return (
    <div className="chat no-print">
      <ChatSession
        key={sessionKey}
        sessionKey={sessionKey}
        open={open}
        setOpen={setOpen}
      />
    </div>
  );
}
