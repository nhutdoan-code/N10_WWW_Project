import { Link } from 'react-router-dom';
import ProductCard from '../components/ProductCard';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { wishlistService } from '../service/wishlistService';
import { errorMessage } from '../utils/format';

export default function WishlistPage() {
  const { data: products, loading, error, reload } = useFetch(wishlistService.list);
  const toast = useToast();

  const remove = async (ma) => {
    try {
      await wishlistService.remove(ma);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;
  if (products.length === 0) return <p className="muted">Chưa có sản phẩm yêu thích. <Link to="/">Xem sản phẩm</Link></p>;

  return (
    <>
      <h2>Sản phẩm yêu thích</h2>
      <div className="grid">
        {products.map((sp) => (
          <div key={sp.maSanPham}>
            <ProductCard sp={sp} />
            <button className="btn btn-danger full" onClick={() => remove(sp.maSanPham)}>Bỏ yêu thích</button>
          </div>
        ))}
      </div>
    </>
  );
}
