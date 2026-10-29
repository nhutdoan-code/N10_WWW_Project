import { useEffect, useState } from 'react';
import { getProductImageUrl, PRODUCT_PLACEHOLDER } from '../utils/image';

/**
 * Anh san pham co fallback. Khong de onError lap vo han: khi placeholder cung
 * loi thi bo handler va giu placeholder.
 */
export default function ProductImage({ src, alt = '', onError, ...props }) {
  const resolved = getProductImageUrl(src);
  const [current, setCurrent] = useState(resolved);

  useEffect(() => {
    setCurrent(resolved);
  }, [resolved]);

  return (
    <img
      {...props}
      src={current}
      alt={alt}
      onError={(event) => {
        if (onError) onError(event);
        event.currentTarget.onerror = null;
        if (current !== PRODUCT_PLACEHOLDER) setCurrent(PRODUCT_PLACEHOLDER);
      }}
    />
  );
}
