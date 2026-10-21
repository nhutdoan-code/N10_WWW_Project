package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public List<SanPhamDto> nhanh(@RequestParam String q) {
        return searchService.nhanh(gioiHan(q));
    }

    private String gioiHan(String q) {
        return q.length() > 200 ? q.substring(0, 200) : q;
    }
}
