package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.ChatDto;
import nhom10.phonenest.service.ChatHistoryService;
import nhom10.phonenest.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    @PostMapping
    public ChatDto.Response chat(@Valid @RequestBody ChatDto.Request request) {
        return chatService.chat(request);
    }

    @GetMapping("/conversations")
    public List<ChatDto.ConversationResponse> getConversations() {
        return chatHistoryService.layDanhSachCuocHoiThoai().stream()
                .map(c -> new ChatDto.ConversationResponse(c.getId(), c.getTieuDe(), c.getNgayTao(), c.getNgayCapNhat()))
                .toList();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatDto.MessageResponse> getMessages(@PathVariable String id) {
        return chatHistoryService.layTatCaTinNhan(id).stream()
                .map(m -> new ChatDto.MessageResponse(m.getId(), m.getRole(), chatHistoryService.layNoiDungHienThi(m), m.getNgayTao()))
                .toList();
    }
}