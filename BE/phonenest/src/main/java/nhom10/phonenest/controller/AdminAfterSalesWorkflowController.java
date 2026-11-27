package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.AdminAfterSalesAssigneeDto;
import nhom10.phonenest.dto.AdminAfterSalesRowDto;
import nhom10.phonenest.dto.AdminAfterSalesSummaryDto;
import nhom10.phonenest.service.AfterSalesAdminQueryService;
import nhom10.phonenest.service.AfterSalesAdminWorkflow;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/after-sales-workflow")
@RequiredArgsConstructor
public class AdminAfterSalesWorkflowController {
    private final AfterSalesAdminWorkflow service;
    private final AfterSalesAdminQueryService queries;

    @GetMapping("/requests")
    public Page<AdminAfterSalesRowDto> requests(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "priority") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean deadlineOverdue) {
        return queries.requests(q, status, source, type, from, to, sort, page, size,
                mine, deadlineOverdue, SecurityUtil.currentUserId());
    }

    @GetMapping("/summary")
    public AdminAfterSalesSummaryDto summary() { return queries.summary(); }

    @GetMapping("/assignees")
    public List<AdminAfterSalesAssigneeDto> assignees() { return service.assignees(); }

    @PostMapping("/{source}/{id}/assignment")
    public AfterSalesAdminWorkflow.Assignment assignment(@PathVariable String source, @PathVariable String id,
            @RequestBody AfterSalesAdminWorkflow.AssignmentRequest request) {
        return service.assign(source, id, request);
    }

    @GetMapping("/{source}/{id}")
    public AfterSalesAdminWorkflow.Detail detail(@PathVariable String source, @PathVariable String id) { return service.detail(source, id); }

    @GetMapping("/{source}/{id}/request")
    public Object requestDetail(@PathVariable String source, @PathVariable String id) { return service.requestDetail(source, id); }
    @PostMapping("/{source}/{id}")
    public java.util.Map<String,String> action(@PathVariable String source, @PathVariable String id, @RequestBody AfterSalesAdminWorkflow.Action action) {
        service.execute(source, id, action); return java.util.Map.of("message", "Đã cập nhật yêu cầu");
    }
}
