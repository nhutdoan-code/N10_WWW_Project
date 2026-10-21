package nhom10.phonenest.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.DiaDanhDto;
import nhom10.phonenest.repository.PhuongXaRepository;
import nhom10.phonenest.repository.TinhThanhRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class DiaDanhController {

    private final TinhThanhRepository tinhThanhRepository;
    private final PhuongXaRepository phuongXaRepository;

    @GetMapping("/provinces")
    public List<DiaDanhDto> tinhThanh() {
        return tinhThanhRepository.findAll().stream().map(t -> new DiaDanhDto(t.getMaTinh(), t.getTenTinh())).toList();
    }

    @GetMapping("/provinces/{maTinh}/wards")
    public List<DiaDanhDto> phuongXa(@PathVariable String maTinh) {
        return phuongXaRepository.findByMaTinhOrderByTenPhuongAsc(maTinh).stream()
                .map(p -> new DiaDanhDto(p.getMaPhuong(), p.getTenPhuong())).toList();
    }
}
