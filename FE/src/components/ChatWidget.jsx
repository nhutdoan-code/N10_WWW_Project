import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';
import ProductImage from './ProductImage';
import { chatService } from '../service/chatService';
import { trackingService } from '../service/trackingService';
import { formatGiaCongBo } from '../utils/format';
import { getVisitorId } from '../utils/activityTracking';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import {
  DEFAULT_GREETING,
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

// Lưu id cuộc hội thoại đang mở (theo từng tài khoản) để tải lại trang vẫn tiếp tục đúng hội thoại.
const conversationKey = (sessionKey) => `${sessionKey}_conversation`;

function loadConversationId(sessionKey) {
  try {
    return sessionStorage.getItem(conversationKey(sessionKey)) || null;
  } catch {
    return null;
  }
}

function saveConversationId(sessionKey, id) {
  try {
    if (id) sessionStorage.setItem(conversationKey(sessionKey), id);
    else sessionStorage.removeItem(conversationKey(sessionKey));
  } catch {
    // Ignore storage errors
  }
}

function ChatSession({ sessionKey, open, setOpen, onCartChanged }) {
  const [messages, setMessages] = useState(() => loadChatHistory(sessionKey));
  const [conversationId, setConversationId] = useState(() => loadConversationId(sessionKey));
  const [conversations, setConversations] = useState([]);
  const [showHistory, setShowHistory] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
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

  useEffect(() => {
    saveConversationId(sessionKey, conversationId);
  }, [sessionKey, conversationId]);

  const loadConversations = async () => {
    try {
      setLoadingHistory(true);
      const data = await chatService.getConversations();
      if (!isAbortedRef.current) setConversations(Array.isArray(data) ? data : []);
    } catch {
      if (!isAbortedRef.current) setConversations([]);
    } finally {
      if (!isAbortedRef.current) setLoadingHistory(false);
    }
  };

  const toggleHistory = () => {
    if (!showHistory) loadConversations();
    setShowHistory((v) => !v);
  };

  const newConversation = () => {
    setConversationId(null);
    setMessages([DEFAULT_GREETING]);
    setText('');
    setShowHistory(false);
  };

  const selectConversation = async (id) => {
    try {
      setLoadingHistory(true);
      const data = await chatService.getMessages(id);
      if (isAbortedRef.current) return;
      const history = Array.isArray(data)
        ? data.map((m) => ({
          role: m.role?.toUpperCase() === 'USER' ? 'user' : 'assistant',
          content: m.noiDung,
        }))
        : [];
      setConversationId(id);
      setMessages(history.length ? history : [DEFAULT_GREETING]);
      setShowHistory(false);
    } catch {
      if (!isAbortedRef.current) {
        setMessages((m) => [...m, { role: 'assistant', content: 'Không thể tải lịch sử cuộc hội thoại này.' }]);
      }
    } finally {
      if (!isAbortedRef.current) setLoadingHistory(false);
    }
  };

  const send = async (content) => {
    const message = content.trim();
    if (!message || sending) return;

    setMessages((prev) => [...prev, { role: 'user', content: message }]);
    setText('');
    setSending(true);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      // Lịch sử hội thoại được lưu và đọc ở backend theo conversationId của chính tài khoản.
      const res = await chatService.send(message, conversationId, getVisitorId(), { signal: controller.signal });

      // If component unmounted or request was aborted/superseded, ignore response
      if (isAbortedRef.current) return;

      if (res.conversationId) setConversationId(res.conversationId);
      setMessages((m) => [...m, { role: 'assistant', content: res.reply, products: res.products }]);

      // Chatbot vừa thêm sản phẩm vào giỏ: đồng bộ lại số lượng trên header.
      if (res.mode === 'CART_ADDED') {
        onCartChanged?.();
      }
    } catch (err) {
      if (isAbortedRef.current || err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') {
        // Request was aborted due to session change/logout; do not show error in UI
        return;
      }
      if (err?.response?.status === 404 && conversationId) {
        // Hội thoại không còn (đã xóa / không thuộc tài khoản): bắt đầu hội thoại mới.
        setConversationId(null);
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

          <div className="chat-toolbar">
            <button
              type="button"
              className={`chat-toolbar-btn${showHistory ? ' active' : ''}`}
              onClick={toggleHistory}
            >
              <Icon name="history" size={16} />
              <span>Lịch sử</span>
            </button>
            <button type="button" className="chat-toolbar-btn new-chat-btn" onClick={newConversation}>
              <Icon name="plus" size={16} />
              <span>Cuộc trò chuyện mới</span>
            </button>
          </div>

          {showHistory && (
            <div className="chat-history-list">
              <b>Cuộc trò chuyện của bạn</b>
              {loadingHistory ? (
                <p>Đang tải lịch sử...</p>
              ) : conversations.length === 0 ? (
                <p>Chưa có cuộc trò chuyện nào.</p>
              ) : (
                conversations.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    className={`chat-history-item${conversationId === c.id ? ' active' : ''}`}
                    onClick={() => selectConversation(c.id)}
                  >
                    {c.tieuDe || 'Cuộc trò chuyện mới'}
                  </button>
                ))
              )}
            </div>
          )}

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
                    <span>{p.tenMay}<b>{formatGiaCongBo(p.giaBan)}</b></span>
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
  const cart = useCart();
  const user = auth?.user;
  const isInitialized = auth ? (auth.isInitialized !== false && !auth.loading) : true;

  // ChatBox chỉ dành cho tài khoản đã đăng nhập (backend yêu cầu JWT cho /api/chat).
  const sessionKey = user?.maTk ? getChatStorageKey(user, isInitialized) : null;

  if (!sessionKey) return null;

  return (
    <div className="chat no-print">
      <ChatSession
        key={sessionKey}
        sessionKey={sessionKey}
        open={open}
        setOpen={setOpen}
        onCartChanged={() => cart?.refresh?.().catch(() => {})}
      />
    </div>
  );
}
