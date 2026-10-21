package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final SanPhamService sanPhamService;

    public List<SanPhamDto> nhanh(String q) {
        return sanPhamService.list(q, null, null, null, null, false).stream().limit(5).toList();
    }
}
