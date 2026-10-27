package local.mintdiary;

import static local.mintdiary.ApiModels.*;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {
    private final JdbcTemplate jdbc;
    private final MediaService media;
    private final AnniversaryService anniversaries;
    private final Clock clock;

    public DiaryService(JdbcTemplate jdbc, MediaService media, AnniversaryService anniversaries, Clock clock) {
        this.jdbc = jdbc;
        this.media = media;
        this.anniversaries = anniversaries;
        this.clock = clock;
    }

    private List<UUID> imageIds(LocalDate date) {
        return jdbc.query("SELECT media_id FROM diary_image WHERE diary_date=? ORDER BY position",
                (rs, n) -> UUID.fromString(rs.getString(1)), Date.valueOf(date));
    }

    public DiaryView get(LocalDate date) {
        DateRules.validateDate(date);
        return jdbc.query("SELECT * FROM diary WHERE diary_date=?", (rs, row) -> new DiaryView(
                date, rs.getString("content"), imageIds(date).stream().map(media::view).toList(), true,
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime()),
                Date.valueOf(date)).stream().findFirst().orElse(new DiaryView(date, "", List.of(), false, null, null));
    }

    @Transactional
    public DiaryView save(LocalDate date, DiaryInput input) {
        DateRules.validateDate(date);
        String content = input.content() == null ? "" : input.content();
        List<UUID> ids = input.imageIds() == null ? List.of() : input.imageIds();
        if (content.isBlank() && ids.isEmpty()) throw ApiException.badRequest("写一点文字，或添加一张图片后再保存吧");
        media.validateIds(ids);
        List<UUID> previousImages = imageIds(date);
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT INTO diary(diary_date,content,created_at,updated_at) VALUES(?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE content=VALUES(content),updated_at=VALUES(updated_at)",
                Date.valueOf(date), content, now, now);
        jdbc.update("DELETE FROM diary_image WHERE diary_date=?", Date.valueOf(date));
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("INSERT INTO diary_image(diary_date,media_id,position) VALUES(?,?,?)",
                    Date.valueOf(date), ids.get(i).toString(), i);
        }
        media.cleanupAfterCommit(previousImages.stream().filter(id -> !ids.contains(id)).toList());
        return get(date);
    }

    @Transactional
    public void delete(LocalDate date) {
        DateRules.validateDate(date);
        List<UUID> ids = imageIds(date);
        jdbc.update("DELETE FROM diary WHERE diary_date=?", Date.valueOf(date));
        media.cleanupAfterCommit(ids);
    }

    public CalendarView calendar(YearMonth month) {
        DateRules.validateDate(month.atDay(1));
        var summaries = new HashMap<LocalDate, DiarySummary>();
        jdbc.query("SELECT d.diary_date,LEFT(d.content,80) AS preview,"
                + "(SELECT COUNT(*) FROM diary_image i WHERE i.diary_date=d.diary_date) AS image_count "
                + "FROM diary d WHERE d.diary_date>=? AND d.diary_date<=?", rs -> {
                    summaries.put(rs.getDate("diary_date").toLocalDate(),
                            new DiarySummary(rs.getString("preview"), rs.getInt("image_count")));
                }, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()));
        var marks = anniversaries.marksFor(month);
        List<CalendarDay> days = new ArrayList<>();
        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            LocalDate day = month.atDay(i);
            DiarySummary summary = summaries.get(day);
            days.add(new CalendarDay(day, summary != null, summary == null ? 0 : summary.images(),
                    summary == null ? "" : summary.preview(), marks.getOrDefault(day, List.of())));
        }
        return new CalendarView(month.toString(), days);
    }

    private record DiarySummary(String preview, int images) {}
}
