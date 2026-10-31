import { useLayoutEffect, useRef } from 'react';
import { formatMoneyInput, parseMoneyInput } from '../utils/money';

/**
 * Input tiền hiển thị dấu chấm hàng nghìn nhưng state chỉ nhận chuỗi chữ số.
 * Giữ vị trí con trỏ theo số chữ số đã nhập để không bị nhảy khi gõ/backspace.
 */
export default function MoneyInput({ value, onChange, className = '', ...props }) {
  const inputRef = useRef(null);
  const caretRef = useRef(null);

  useLayoutEffect(() => {
    if (caretRef.current === null || !inputRef.current) return;
    const pos = caretRef.current;
    caretRef.current = null;
    inputRef.current.setSelectionRange(pos, pos);
  });

  const handleChange = (event) => {
    const el = event.currentTarget;
    const caret = el.selectionStart ?? el.value.length;
    const digitsBeforeCaret = (el.value.slice(0, caret).match(/\d/g) || []).length;
    const raw = parseMoneyInput(el.value);
    const display = formatMoneyInput(raw);

    let nextCaret = 0;
    if (digitsBeforeCaret > 0) {
      let seen = 0;
      for (let i = 0; i < display.length; i += 1) {
        if (/\d/.test(display[i])) seen += 1;
        if (seen === digitsBeforeCaret) {
          nextCaret = i + 1;
          break;
        }
      }
      if (seen < digitsBeforeCaret) nextCaret = display.length;
    }

    caretRef.current = nextCaret;
    onChange(raw);
  };

  return (
    <input
      ref={inputRef}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      value={formatMoneyInput(value)}
      onChange={handleChange}
      className={className}
      {...props}
    />
  );
}
