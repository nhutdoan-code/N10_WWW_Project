package nhom10.phonenest.repository;

import nhom10.phonenest.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, String> {

    Optional<Wishlist> findByMaKh(String maKh);
}
