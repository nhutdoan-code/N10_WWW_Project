package nhom11.phonenest.service;

import nhom11.phonenest.dto.ThongBaoDto;
import nhom11.phonenest.entity.ThongBao;
import nhom11.phonenest.repository.QuanTriVienRepository;
import nhom11.phonenest.repository.ThongBaoRepository;
import nhom11.phonenest.util.IdGenerator;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ThongBaoService {

    private final ThongBaoRepository thongBaoRepository;
    private final QuanTriVienRepository quanTriVienRepository;

    public void gui(String maTk, String noiDung) {
        ThongBao t = new ThongBao();
        t.setMaThongBao(IdGenerator.next("TB"));
        t.setMaTk(maTk);
        t.setNoiDung(noiDung);
        t.setDaDoc(false);
        t.setNgayTao(LocalDateTime.now());
        thongBaoRepository.save(t);
    }

    public void guiQuanTri(String noiDung) {
        quanTriVienRepository.findAll().forEach(a -> gui(a.getMaQtv(), noiDung));
    }

    @Transactional(readOnly = true)
    public List<ThongBaoDto> list(String maTk) {
        return thongBaoRepository.findByMaTkOrderByNgayTaoDesc(maTk).stream()
                .map(t -> new ThongBaoDto(t.getMaThongBao(), t.getNoiDung(), t.isDaDoc(), t.getNgayTao()))
                .toList();
    }

    public void daDoc(String maTk, String ma) {
        thongBaoRepository.findById(ma).filter(t -> t.getMaTk().equals(maTk)).ifPresent(t -> t.setDaDoc(true));
    }

    public void docHet(String maTk) {
        thongBaoRepository.findByMaTkAndDaDocFalse(maTk).forEach(t -> t.setDaDoc(true));
    }
}
