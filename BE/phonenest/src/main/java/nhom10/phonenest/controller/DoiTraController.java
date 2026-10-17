package nhom10.phonenest.controller;

import nhom10.phonenest.dto.DoiTraDto;
import nhom10.phonenest.service.DoiTraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/returns")
@RequiredArgsConstructor
public class DoiTraController {

    private final DoiTraService doiTraService;

    @PostMapping
    public DoiTraDto tao(@Valid @RequestBody DoiTraDto.Request r) {
        return doiTraService.tao(SecurityUtil.currentUserId(), r);
    }

    @GetMapping
    public List<DoiTraDto> cuaToi() {
        return doiTraService.cuaToi(SecurityUtil.currentUserId());
    }
}
