package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.service.AfterSalesAdminWorkflow;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/after-sales-workflow")
@RequiredArgsConstructor
public class AdminAfterSalesWorkflowController {
    private final AfterSalesAdminWorkflow service;
    @GetMapping("/{source}/{id}")
    public AfterSalesAdminWorkflow.Detail detail(@PathVariable String source, @PathVariable String id) { return service.detail(source, id); }
    @PostMapping("/{source}/{id}")
    public java.util.Map<String,String> action(@PathVariable String source, @PathVariable String id, @RequestBody AfterSalesAdminWorkflow.Action action) {
        service.execute(source, id, action); return java.util.Map.of("message", "Đã cập nhật yêu cầu");
    }
}
