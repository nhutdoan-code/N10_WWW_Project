package nhom10.phonenest.controller;

import nhom10.phonenest.dto.DiaChiDto;
import nhom10.phonenest.service.DiaChiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/addresses")
@RequiredArgsConstructor
public class DiaChiController {

    private final DiaChiService diaChiService;

    @GetMapping
    public List<DiaChiDto> list() {
        return diaChiService.list(SecurityUtil.currentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaChiDto create(@Valid @RequestBody DiaChiDto d) {
        return diaChiService.save(SecurityUtil.currentUserId(), null, d);
    }

    @PutMapping("/{ma}")
    public DiaChiDto update(@PathVariable String ma, @Valid @RequestBody DiaChiDto d) {
        return diaChiService.save(SecurityUtil.currentUserId(), ma, d);
    }

    @PutMapping("/{ma}/default")
    public DiaChiDto datMacDinh(@PathVariable String ma) {
        return diaChiService.datMacDinh(SecurityUtil.currentUserId(), ma);
    }

    @DeleteMapping("/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String ma) {
        diaChiService.delete(SecurityUtil.currentUserId(), ma);
    }
}
