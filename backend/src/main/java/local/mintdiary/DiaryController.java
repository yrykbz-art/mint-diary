package local.mintdiary;

import static local.mintdiary.ApiModels.*;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DiaryController {
    private final DiaryService diaries;
    private final AnniversaryService anniversaries;
    private final MediaService media;
    private final Clock clock;

    public DiaryController(DiaryService diaries, AnniversaryService anniversaries, MediaService media, Clock clock) {
        this.diaries = diaries;
        this.anniversaries = anniversaries;
        this.media = media;
        this.clock = clock;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "today", LocalDate.now(clock).toString(), "timeZone", clock.getZone().toString());
    }

    @GetMapping("/calendar")
    public CalendarView calendar(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return diaries.calendar(month);
    }

    @GetMapping("/diaries/{date}")
    public DiaryView getDiary(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return diaries.get(date);
    }

    @PutMapping("/diaries/{date}")
    public DiaryView saveDiary(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DiaryInput input) {
        return diaries.save(date, input);
    }

    @DeleteMapping("/diaries/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDiary(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        diaries.delete(date);
    }

    @GetMapping("/anniversaries")
    public List<AnniversaryView> listAnniversaries() { return anniversaries.list(); }

    @PostMapping("/anniversaries")
    @ResponseStatus(HttpStatus.CREATED)
    public AnniversaryView createAnniversary(@Valid @RequestBody AnniversaryInput input) {
        return anniversaries.create(input);
    }

    @PutMapping("/anniversaries/{id}")
    public AnniversaryView updateAnniversary(@PathVariable long id, @Valid @RequestBody AnniversaryInput input) {
        return anniversaries.update(id, input);
    }

    @DeleteMapping("/anniversaries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnniversary(@PathVariable long id) { anniversaries.delete(id); }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageView uploadImage(@RequestParam("file") MultipartFile file) throws IOException { return media.upload(file); }

    @DeleteMapping("/images/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable UUID id) {
        if (!media.deleteUnused(id)) throw new ApiException(HttpStatus.CONFLICT, "这张图片正在使用中");
    }

    @GetMapping("/images/{id}/content")
    public ResponseEntity<Resource> image(@PathVariable UUID id) {
        MediaService.StoredImage image = media.find(id);
        Resource resource = new FileSystemResource(media.path(image));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.type()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .header("Content-Disposition", "inline; filename=\"" + image.fileName() + "\"")
                .body(resource);
    }
}
