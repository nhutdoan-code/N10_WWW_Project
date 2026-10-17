package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.service.WishlistService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public List<SanPhamDto> list() {
        return wishlistService.list(SecurityUtil.currentUserId());
    }

    @PostMapping("/{ma}")
    public List<SanPhamDto> add(@PathVariable String ma) {
        return wishlistService.add(SecurityUtil.currentUserId(), ma);
    }

    @DeleteMapping("/{ma}")
    public List<SanPhamDto> remove(@PathVariable String ma) {
        return wishlistService.remove(SecurityUtil.currentUserId(), ma);
    }
}
