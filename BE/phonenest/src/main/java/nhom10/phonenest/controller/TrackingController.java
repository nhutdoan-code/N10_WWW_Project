package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.TrackingDto;
import nhom10.phonenest.service.TrackingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracking/events")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@Valid @RequestBody TrackingDto.Event event) {
        trackingService.record(event);
    }
}
