package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.service.SearchService;
import nhom10.phonenest.service.ai.SemanticSearchService;
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
    private final SemanticSearchService semanticSearchService;

    @GetMapping
    public List<SanPhamDto> nhanh(@RequestParam String q) {
        return searchService.nhanh(gioiHan(q));
    }

    @GetMapping("/semantic")
    public List<SemanticSearchResultDto> semantic(@RequestParam String q,
                                                  @RequestParam(required = false) Integer limit) {
        return semanticSearchService.search(gioiHan(q), limit);
    }

    @GetMapping("/semantic/status")
    public java.util.Map<String, Object> semanticStatus() {
        return semanticSearchService.getStatus();
    }

    private String gioiHan(String q) {
        return q != null && q.length() > 300 ? q.substring(0, 300) : (q == null ? "" : q);
    }
}
