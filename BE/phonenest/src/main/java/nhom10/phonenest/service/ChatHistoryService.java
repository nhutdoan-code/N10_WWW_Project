package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.entity.CuocHoiThoaiChat;
import nhom10.phonenest.entity.TinNhanChat;
import nhom10.phonenest.repository.CuocHoiThoaiChatRepository;
import nhom10.phonenest.repository.TinNhanChatRepository;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatHistoryService {

    private static final String META_KEY = "_phonenest_chat";
    private static final String META_VERSION = "1";

    private final CuocHoiThoaiChatRepository conversationRepository;
    private final TinNhanChatRepository messageRepository;
    private final ObjectMapper objectMapper;

    // 1. TẠO CUỘC HỘI THOẠI
    public CuocHoiThoaiChat taoCuocHoiThoai(String tieuDe) {
        LocalDateTime now = LocalDateTime.now();
        return conversationRepository.save(CuocHoiThoaiChat.builder()
                .id(UUID.randomUUID().toString())
                .maTk(SecurityUtil.currentUserId())
                .tieuDe(tieuDe)
                .ngayTao(now)
                .ngayCapNhat(now)
                .build());
    }

    // 2. LẤY DANH SÁCH CUỘC HỘI THOẠI
    @Transactional(readOnly = true)
    public List<CuocHoiThoaiChat> layDanhSachCuocHoiThoai() {
        return conversationRepository.findByMaTkOrderByNgayCapNhatDesc(SecurityUtil.currentUserId());
    }

    // 3. LẤY CUỘC HỘI THOẠI CỦA NGƯỜI DÙNG HIỆN TẠI
    @Transactional(readOnly = true)
    public CuocHoiThoaiChat layCuocHoiThoaiCuaToi(String conversationId) {
        return conversationRepository.findByIdAndMaTk(conversationId, SecurityUtil.currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
    }

    // 4. LƯU TIN NHẮN THÔNG THƯỜNG
    public TinNhanChat luuTinNhan(String conversationId, String role, String noiDung) {
        return luuTinNhan(conversationId, role, noiDung, List.of());
    }

    // 5. LƯU TIN NHẮN KÈM DANH SÁCH SẢN PHẨM
    public TinNhanChat luuTinNhan(String conversationId, String role, String noiDung, List<String> maSanPham) {
        CuocHoiThoaiChat conversation = layCuocHoiThoaiCuaToi(conversationId);

        if (!List.of("USER", "ASSISTANT").contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò tin nhắn không hợp lệ");
        }
        if (noiDung == null || noiDung.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung tin nhắn không được để trống");
        }

        String noiDungLuu = noiDung.trim();
        if ("ASSISTANT".equals(role) && maSanPham != null && !maSanPham.isEmpty()) {
            noiDungLuu = encodeMessage(noiDungLuu, maSanPham);
        }

        LocalDateTime now = LocalDateTime.now();
        conversation.setNgayCapNhat(now);
        conversationRepository.save(conversation);

        return messageRepository.save(TinNhanChat.builder()
                .cuocHoiThoaiId(conversationId)
                .role(role)
                .noiDung(noiDungLuu)
                .ngayTao(now)
                .build());
    }

    // 6. ĐÓNG GÓI NỘI DUNG VÀ DANH SÁCH MÃ SẢN PHẨM THÀNH JSON
    private String encodeMessage(String reply, List<String> maSanPham) {
        try {
            LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
            for (String id : maSanPham) {
                if (id != null && !id.isBlank()) uniqueIds.add(id.trim());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply", reply);
            data.put("productIds", new ArrayList<>(uniqueIds));
            data.put(META_KEY, META_VERSION);
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return reply;
        }
    }

    // 7. ĐỌC NỘI DUNG HIỂN THỊ
    @Transactional(readOnly = true)
    public String layNoiDungHienThi(TinNhanChat message) {
        if (message == null || message.getNoiDung() == null) return "";
        String raw = message.getNoiDung();
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!isNewFormat(root)) return raw;
            JsonNode replyNode = root.get("reply");
            return replyNode != null && replyNode.isTextual() ? replyNode.asText() : raw;
        } catch (Exception e) {
            return raw;
        }
    }

    // 8. ĐỌC DANH SÁCH MÃ SẢN PHẨM TỪ MỘT TIN NHẮN
    @Transactional(readOnly = true)
    public List<String> layMaSanPham(TinNhanChat message) {
        if (message == null || message.getNoiDung() == null) return List.of();
        try {
            JsonNode root = objectMapper.readTree(message.getNoiDung());
            if (!isNewFormat(root)) return List.of();
            JsonNode idsNode = root.get("productIds");
            if (idsNode == null || !idsNode.isArray()) return List.of();

            List<String> result = new ArrayList<>();
            for (JsonNode node : idsNode) {
                if (node.isTextual() && !node.asText().isBlank()) result.add(node.asText());
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    // 9. KIỂM TRA TIN NHẮN CÓ PHẢI ĐỊNH DẠNG MỚI KHÔNG
    private boolean isNewFormat(JsonNode root) {
        return root != null && root.isObject() && root.has(META_KEY) && META_VERSION.equals(root.get(META_KEY).asText());
    }

    // 10. LẤY TẤT CẢ TIN NHẮN
    @Transactional(readOnly = true)
    public List<TinNhanChat> layTatCaTinNhan(String conversationId) {
        layCuocHoiThoaiCuaToi(conversationId);
        return messageRepository.findByCuocHoiThoaiIdOrderByNgayTaoAscIdAsc(conversationId);
    }

    // 11. LẤY 10 TIN NHẮN GẦN NHẤT
    @Transactional(readOnly = true)
    public List<TinNhanChat> layLichSuGanNhat(String conversationId) {
        layCuocHoiThoaiCuaToi(conversationId);
        List<TinNhanChat> messages = messageRepository.findTop10ByCuocHoiThoaiIdOrderByNgayTaoDescIdDesc(conversationId);
        Collections.reverse(messages);
        return messages;
    }

    // 12. LẤY DANH SÁCH SẢN PHẨM ĐÃ ĐƯỢC ĐỀ XUẤT TRONG HỘI THOẠI
    @Transactional(readOnly = true)
    public List<String> layTatCaMaSanPhamDaGioiThieu(String conversationId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (TinNhanChat message : layTatCaTinNhan(conversationId)) {
            if ("ASSISTANT".equals(message.getRole())) result.addAll(layMaSanPham(message));
        }
        return new ArrayList<>(result);
    }
}