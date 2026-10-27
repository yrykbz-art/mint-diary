package local.mintdiary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record ImageView(UUID id, String url, String originalName, long size) {}

    public record DiaryInput(
            @Size(max = 20000, message = "日记最多可以写 20000 个字") String content,
            @Size(max = 20, message = "一篇日记最多添加 20 张图片") List<@NotNull UUID> imageIds) {}

    public record DiaryView(
            LocalDate date, String content, List<ImageView> images, boolean exists,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record AnniversaryInput(
            @NotBlank(message = "请填写纪念日名称")
            @Size(max = 100, message = "名称最多 100 个字") String title,
            @NotNull(message = "请选择纪念日的原始日期") LocalDate date,
            @Size(max = 500, message = "备注最多 500 个字") String description,
            UUID coverImageId) {}

    public record AnniversaryView(
            long id, String title, LocalDate date, String description, ImageView coverImage,
            long daysPassed, boolean future, long daysUntilStart) {}

    public record AnniversaryMark(long id, String title, LocalDate originalDate) {}

    public record CalendarDay(
            LocalDate date, boolean hasDiary, int imageCount, String preview,
            List<AnniversaryMark> anniversaries) {}

    public record CalendarView(String month, List<CalendarDay> days) {}
}
