package local.mintdiary;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DiaryApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MediaService media;

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneId.of("Asia/Singapore")); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        var directory = Files.createTempDirectory("mint-diary-api-test-");
        registry.add("app.upload-dir", () -> directory.toString());
    }

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM anniversary");
        jdbc.update("DELETE FROM diary");
        List<UUID> ids = jdbc.query("SELECT id FROM media", (rs, row) -> UUID.fromString(rs.getString(1)));
        ids.forEach(media::deleteUnused);
    }

    @Test void savingTheSameDayUpdatesTheOneExistingDiary() throws Exception {
        mvc.perform(get("/api/diaries/2026-10-20")).andExpect(status().isOk()).andExpect(jsonPath("$.exists").value(false));
        save("2026-10-20", "第一版", List.of());
        save("2026-10-20", "更新后的文字\n保留换行", List.of());
        mvc.perform(get("/api/diaries/2026-10-20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("更新后的文字\n保留换行"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM diary", Integer.class));
        mvc.perform(get("/api/calendar?month=2026-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.days[19].hasDiary").value(true));
    }

    @Test void picturesKeepTheirOrderAndRemovedPicturesAreCleanedUp() throws Exception {
        JsonNode first = upload("first.png"); JsonNode second = upload("second.png");
        String a = first.get("id").asText(), b = second.get("id").asText();
        save("2026-10-20", "", List.of(a, b));
        mvc.perform(get(first.get("url").asText())).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        save("2026-10-20", "两张照片", List.of(b, a));
        mvc.perform(get("/api/diaries/2026-10-20")).andExpect(jsonPath("$.images[0].id").value(b));
        save("2026-10-20", "留下一张", List.of(b));
        mvc.perform(get(first.get("url").asText())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/diaries/2026-10-20")).andExpect(status().isNoContent());
        mvc.perform(get(second.get("url").asText())).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM media", Integer.class));
    }

    @Test void aFailedSaveDoesNotLosePreviouslySavedTextOrPhotos() throws Exception {
        String image = upload("kept.png").get("id").asText();
        save("2026-10-20", "保留这段文字", List.of(image));
        mvc.perform(put("/api/diaries/2026-10-20").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("content", "不应保存", "imageIds", List.of(UUID.randomUUID().toString())))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/diaries/2026-10-20")).andExpect(jsonPath("$.content").value("保留这段文字"))
                .andExpect(jsonPath("$.images[0].id").value(image));
    }

    @Test void anniversariesRetainOriginalYearAndRecurOnFutureCalendars() throws Exception {
        JsonNode item = createAnniversary("旅行", "2025-10-20");
        assertEquals(365, item.get("daysPassed").asInt());
        assertEquals("2025-10-20", item.get("date").asText());
        mvc.perform(get("/api/calendar?month=2027-10")).andExpect(jsonPath("$.days[19].anniversaries[0].title").value("旅行"));
        mvc.perform(get("/api/calendar?month=2024-10")).andExpect(jsonPath("$.days[19].anniversaries").isEmpty());
        save("2025-10-20", "独立的日记", List.of());
        mvc.perform(delete("/api/anniversaries/" + item.get("id").asLong())).andExpect(status().isNoContent());
        mvc.perform(get("/api/diaries/2025-10-20")).andExpect(jsonPath("$.exists").value(true));
    }

    @Test void leapDayAndFutureDayCountersStayCorrect() throws Exception {
        createAnniversary("闰日", "2024-02-29");
        mvc.perform(get("/api/calendar?month=2025-02")).andExpect(jsonPath("$.days[27].anniversaries").isEmpty());
        mvc.perform(get("/api/calendar?month=2028-02")).andExpect(jsonPath("$.days[28].anniversaries[0].title").value("闰日"));
        JsonNode future = createAnniversary("未来", "2026-10-30");
        assertTrue(future.get("future").asBoolean()); assertEquals(10, future.get("daysUntilStart").asInt());
        JsonNode same = createAnniversary("今天", "2026-10-20"); assertEquals(0, same.get("daysPassed").asInt());
    }

    @Test void invalidUploadsEmptyDiariesAndForeignOriginsAreRejected() throws Exception {
        mvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/diaries/2026-10-20").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/diaries/2025-02-29")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/anniversaries").header("Origin", "https://unrelated.example")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/diaries/2026-10-20").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("content", "a".repeat(20001), "imageIds", List.of()))))
                .andExpect(status().isBadRequest());
    }

    private void save(String date, String content, List<String> images) throws Exception {
        mvc.perform(put("/api/diaries/" + date).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("content", content, "imageIds", images))))
                .andExpect(status().isOk());
    }

    private JsonNode upload(String name) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return mapper.readTree(mvc.perform(multipart("/api/images")
                .file(new MockMultipartFile("file", name, "image/png", bytes.toByteArray())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode createAnniversary(String title, String date) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/anniversaries").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("title", title, "date", date, "description", "备注"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
