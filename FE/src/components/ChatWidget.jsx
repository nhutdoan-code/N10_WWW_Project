
import { useEffect, useRef, useState, useContext } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';
import ProductImage from './ProductImage';
import { chatService } from '../service/chatService';
import { trackingService } from '../service/trackingService';
import { formatPrice } from '../utils/format';
import { storage } from '../utils/storage';
import { STORAGE_KEYS } from '../config/constants';

// [THÊM MỚI] Import CartContext
import { CartContext } from '../context/CartContext';

const GOI_Y = [
  'Điện thoại pin trâu dưới 10 triệu',
  'Máy chụp ảnh đẹp giá tốt',
  'Phí giao hàng thế nào?',
  'Hiện có voucher nào?',
];

const CHAO = {
  role: 'assistant',
  content:
      'Xin chào! Mình là trợ lý PhoneNest. Bạn cần tư vấn chọn điện thoại, hỏi phí giao hàng hay voucher? Cứ nhắn cho mình nhé.',
};

export default function ChatWidget() {
  const token = storage.get(STORAGE_KEYS.user)?.token;

  // [THÊM MỚI] Lấy hàm cập nhật giỏ hàng
  const { refresh } = useContext(CartContext);

  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([CHAO]);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [conversationId, setConversationId] = useState(null);
  const [conversations, setConversations] = useState([]);
  const [showHistory, setShowHistory] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const bottom = useRef(null);

  useEffect(() => {
    bottom.current?.scrollIntoView({
      behavior: 'smooth',
      block: 'end',
    });
  }, [messages, sending, open]);

  // Lấy danh sách hội thoại
  const loadConversations = async () => {
    try {
      setLoadingHistory(true);

      const data = await chatService.getConversations();

      setConversations(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error('Lỗi lấy danh sách hội thoại:', e);
    } finally {
      setLoadingHistory(false);
    }
  };

  // Mở hoặc đóng ChatBox
  const toggleChat = () => {
    if (!open) {
      loadConversations();
    }

    setOpen((prev) => !prev);
  };

  // Tạo cuộc trò chuyện mới
  const newConversation = () => {
    setConversationId(null);
    setMessages([CHAO]);
    setText('');
    setShowHistory(false);
  };

  // Chọn cuộc trò chuyện cũ
  const selectConversation = async (id) => {
    try {
      setLoadingHistory(true);

      const data = await chatService.getMessages(id);

      const history = Array.isArray(data)
          ? data.map((m) => ({
            role:
                m.role?.toUpperCase() === 'USER'
                    ? 'user'
                    : 'assistant',
            content: m.noiDung,
          }))
          : [];

      setConversationId(id);
      setMessages(history.length ? history : [CHAO]);
      setShowHistory(false);
    } catch (e) {
      console.error('Lỗi tải lịch sử tin nhắn:', e);
      alert('Không thể tải lịch sử cuộc hội thoại.');
    } finally {
      setLoadingHistory(false);
    }
  };

  // Gửi tin nhắn
  const send = async (content) => {
    const message = content.trim();

    if (!message || sending) return;

    setMessages((old) => [
      ...old,
      { role: 'user', content: message },
    ]);

    setText('');
    setSending(true);

    try {
      const res = await chatService.send(
          message,
          conversationId
      );

      if (res.conversationId) {
        setConversationId(res.conversationId);
      }

      // Hiển thị câu trả lời của chatbot
      setMessages((old) => [
        ...old,
        {
          role: 'assistant',
          content: res.reply,
          products: res.products || [],
        },
      ]);

      // [THÊM MỚI] Cập nhật giỏ hàng sau khi chatbot xử lý
      try {
        await refresh();
      } catch (error) {
        console.error(
            'Lỗi đồng bộ giỏ hàng sau khi chat:',
            error
        );
      }

      await loadConversations();
    } catch (e) {
      console.error('Lỗi gửi tin nhắn:', e);

      setMessages((old) => [
        ...old,
        {
          role: 'assistant',
          content:
              'Xin lỗi, mình chưa trả lời được lúc này. Bạn thử lại sau ít phút nhé.',
        },
      ]);
    } finally {
      setSending(false);
    }
  };

  if (!token) return null;

  return (
      <div className="chat no-print">
        {open && (
            <section
                className="chat-panel"
                aria-label="Trợ lý PhoneNest"
            >
              <header>
            <span className="chat-avatar">
              <Icon name="sparkle" size={18} />
            </span>

                <div>
                  <b>Trợ lý PhoneNest</b>
                  <small>
                    Tư vấn chọn máy, phí giao hàng, voucher
                  </small>
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

              {/* Thanh công cụ quản lý hội thoại */}
              <div className="chat-toolbar">
                <button
                    type="button"
                    className={`chat-toolbar-btn ${
                        showHistory ? 'active' : ''
                    }`}
                    onClick={() => {
                      setShowHistory(!showHistory);

                      if (!showHistory) {
                        loadConversations();
                      }
                    }}
                >
                  <Icon name="history" size={16} />
                  <span>Lịch sử</span>
                </button>

                <button
                    type="button"
                    className="chat-toolbar-btn new-chat-btn"
                    onClick={newConversation}
                >
                  <Icon name="plus" size={16} />
                  <span>Cuộc trò chuyện mới</span>
                </button>
              </div>

              {/* Danh sách hội thoại */}
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
                                className={`chat-history-item${
                                    conversationId === c.id ? ' active' : ''
                                }`}
                                onClick={() => selectConversation(c.id)}
                            >
                              {c.tieuDe || 'Cuộc trò chuyện mới'}
                            </button>
                        ))
                    )}
                  </div>
              )}

              {/* Nội dung ChatBox */}
              <div className="chat-body">
                {messages.map((m, i) => (
                    <div
                        key={i}
                        className={`chat-msg ${m.role}`}
                    >
                      <p>{m.content}</p>

                      {m.products?.map((p) => (
                          <Link
                              key={p.maSanPham}
                              className="chat-product"
                              to={`/products/${p.maSanPham}`}
                              onClick={() => {
                                trackingService.trackRecommendationClick(
                                    p.maSanPham,
                                    'AI_CHATBOT'
                                );

                                setOpen(false);
                              }}
                          >
                            <ProductImage
                                src={p.anhChinh}
                                alt=""
                            />

                            <span>
                      {p.tenMay}
                              <b>{formatPrice(p.giaBan)}</b>
                    </span>
                          </Link>
                      ))}
                    </div>
                ))}

                {messages.length === 1 && (
                    <div className="chat-suggest">
                      {GOI_Y.map((g) => (
                          <button
                              key={g}
                              type="button"
                              onClick={() => send(g)}
                          >
                            {g}
                          </button>
                      ))}
                    </div>
                )}

                {sending && (
                    <div className="chat-msg assistant">
                      <p className="typing">
                        <i />
                        <i />
                        <i />
                      </p>
                    </div>
                )}

                <div ref={bottom} />
              </div>

              {/* Khung nhập tin nhắn */}
              <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    send(text);
                  }}
              >
                <input
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder="Nhập câu hỏi của bạn..."
                    maxLength={500}
                />

                <button
                    type="submit"
                    className="chat-send"
                    disabled={!text.trim() || sending}
                    aria-label="Gửi"
                >
                  <Icon name="send" size={18} />
                </button>
              </form>
            </section>
        )}

        {/* Nút mở ChatBox */}
        <button
            type="button"
            className="chat-fab"
            onClick={toggleChat}
            aria-label={open ? 'Đóng trợ lý' : 'Chat với trợ lý'}
        >
          <Icon
              name={open ? 'close' : 'chat'}
              size={26}
          />
        </button>
      </div>
  );
}
