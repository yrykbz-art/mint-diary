package local.mintdiary;

import static local.mintdiary.ApiModels.*;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnniversaryService {
    private final JdbcTemplate jdbc;
    private final MediaService media;
    private final Clock clock;

    public AnniversaryService(JdbcTemplate jdbc, MediaService media, Clock clock) {
        this.jdbc = jdbc;
        this.media = media;
        this.clock = clock;
    }

    private record Row(long id, String title, LocalDate date, String description, UUID coverId) {}

    private List<Row> rows() {
        return jdbc.query("SELECT * FROM anniversary ORDER BY original_date,id", (rs, n) -> new Row(
                rs.getLong("id"), rs.getString("title"), rs.getDate("original_date").toLocalDate(),
                rs.getString("description"), rs.getString("cover_image_id") == null
                        ? null : UUID.fromString(rs.getString("cover_image_id"))));
    }

    private AnniversaryView view(Row row) {
        LocalDate today = LocalDate.now(clock);
        return new AnniversaryView(row.id(), row.title(), row.date(), row.description(),
                row.coverId() == null ? null : media.view(row.coverId()),
                DateRules.elapsedDays(row.date(), today), row.date().isAfter(today),
                DateRules.daysUntilStart(row.date(), today));
    }

    public List<AnniversaryView> list() { return rows().stream().map(this::view).toList(); }

    public List<AnniversaryMark> marksOn(LocalDate date) {
        return rows().stream().filter(row -> DateRules.occursOn(row.date(), date))
                .map(row -> new AnniversaryMark(row.id(), row.title(), row.date())).toList();
    }

    public java.util.Map<LocalDate, List<AnniversaryMark>> marksFor(java.time.YearMonth month) {
        List<Row> rows = rows();
        var result = new java.util.HashMap<LocalDate, List<AnniversaryMark>>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            result.put(date, rows.stream().filter(row -> DateRules.occursOn(row.date(), date))
                    .map(row -> new AnniversaryMark(row.id(), row.title(), row.date())).toList());
        }
        return result;
    }

    private Row require(long id) {
        return rows().stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> ApiException.notFound("纪念日不存在"));
    }

    private void validate(AnniversaryInput input) {
        DateRules.validateDate(input.date());
        if (input.title().trim().isEmpty()) throw ApiException.badRequest("请填写纪念日名称");
        if (input.coverImageId() != null) media.find(input.coverImageId());
    }

    @Transactional
    public AnniversaryView create(AnniversaryInput input) {
        validate(input);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO anniversary "
                    + "(title,original_date,description,cover_image_id,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, input.title().trim());
            statement.setDate(2, Date.valueOf(input.date()));
            statement.setString(3, input.description() == null ? "" : input.description().trim());
            statement.setString(4, input.coverImageId() == null ? null : input.coverImageId().toString());
            statement.setObject(5, LocalDateTime.now(clock));
            statement.setObject(6, LocalDateTime.now(clock));
            return statement;
        }, key);
        return view(require(java.util.Objects.requireNonNull(key.getKey()).longValue()));
    }

    @Transactional
    public AnniversaryView update(long id, AnniversaryInput input) {
        Row previous = require(id);
        validate(input);
        jdbc.update("UPDATE anniversary SET title=?,original_date=?,description=?,cover_image_id=?,updated_at=? WHERE id=?",
                input.title().trim(), Date.valueOf(input.date()), input.description() == null ? "" : input.description().trim(),
                input.coverImageId() == null ? null : input.coverImageId().toString(), LocalDateTime.now(clock), id);
        if (previous.coverId() != null && !previous.coverId().equals(input.coverImageId())) {
            media.cleanupAfterCommit(List.of(previous.coverId()));
        }
        return view(require(id));
    }

    @Transactional
    public void delete(long id) {
        Row previous = require(id);
        jdbc.update("DELETE FROM anniversary WHERE id=?", id);
        if (previous.coverId() != null) media.cleanupAfterCommit(List.of(previous.coverId()));
    }
}
