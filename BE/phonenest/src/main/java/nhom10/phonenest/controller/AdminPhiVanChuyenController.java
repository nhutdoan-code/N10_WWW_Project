package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.PhiVanChuyenDto;
import nhom10.phonenest.service.PhiVanChuyenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shipping-fees")
@RequiredArgsConstructor
public class AdminPhiVanChuyenController {

    private final PhiVanChuyenService phiVanChuyenService;

    @GetMapping
    public List<PhiVanChuyenDto> list() {
        return phiVanChuyenService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PhiVanChuyenDto tao(@Valid @RequestBody PhiVanChuyenDto d) {
        return phiVanChuyenService.tao(d);
    }

    @PutMapping("/{ma}")
    public PhiVanChuyenDto sua(@PathVariable String ma, @Valid @RequestBody PhiVanChuyenDto d) {
        return phiVanChuyenService.sua(ma, d);
    }

    @DeleteMapping("/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable String ma) {
        phiVanChuyenService.xoa(ma);
    }
}
