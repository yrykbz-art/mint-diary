package local.mintdiary;

import static local.mintdiary.ApiModels.ImageView;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Path root;
    private final TransactionTemplate cleanupTransaction;

    public MediaService(JdbcTemplate jdbc, Clock clock, PlatformTransactionManager manager,
            @Value("${app.upload-dir}") String directory) throws IOException {
        this.jdbc = jdbc;
        this.clock = clock;
        this.root = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(root);
        this.cleanupTransaction = new TransactionTemplate(manager);
        cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record StoredImage(UUID id, String fileName, String originalName, String type, long size) {}

    public StoredImage find(UUID id) {
        return jdbc.query("SELECT * FROM media WHERE id=?", (rs, row) -> new StoredImage(
                UUID.fromString(rs.getString("id")), rs.getString("file_name"),
                rs.getString("original_name"), rs.getString("content_type"), rs.getLong("byte_size")),
                id.toString()).stream().findFirst().orElseThrow(() -> ApiException.notFound("图片不存在"));
    }

    public ImageView view(UUID id) {
        StoredImage image = find(id);
        return new ImageView(id, "/api/images/" + id + "/content", image.originalName(), image.size());
    }

    public Path path(StoredImage image) {
        Path path = root.resolve(image.fileName()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw ApiException.notFound("图片文件不存在");
        return path;
    }

    @Transactional
    public ImageView upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw ApiException.badRequest("请选择一张图片");
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "单张图片不能超过 10 MB");
        }
        byte[] bytes = file.getBytes();
        String extension = imageExtension(bytes);
        if (!extension.equals("webp")) {
            try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw ApiException.badRequest("图片无法读取，请换一张图片");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 40_000_000L) {
                        throw ApiException.badRequest("图片尺寸过大，请先缩小图片");
                    }
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) throw ApiException.badRequest("图片无法读取");
                } finally { reader.dispose(); }
            } catch (ApiException error) { throw error; }
            catch (IOException | RuntimeException error) { throw ApiException.badRequest("图片文件不完整或已损坏"); }
        }
        UUID id = UUID.randomUUID();
        String fileName = id + "." + extension;
        String original = file.getOriginalFilename() == null ? fileName : file.getOriginalFilename();
        original = original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);
        if (original.length() > 255) original = original.substring(original.length() - 255);
        String type = switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> "image/webp";
        };
        Path target = root.resolve(fileName);
        Files.write(target, bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { Files.deleteIfExists(target); } catch (IOException e) { log.warn("Unable to remove rolled-back image", e); }
                }
            }
        });
        jdbc.update("INSERT INTO media(id,file_name,original_name,content_type,byte_size,created_at) VALUES(?,?,?,?,?,?)",
                id.toString(), fileName, original, type, bytes.length, LocalDateTime.now(clock));
        return view(id);
    }

    private String imageExtension(byte[] b) {
        if (b.length > 8 && b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10) return "png";
        if (b.length > 3 && b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff) return "jpg";
        if (b.length > 6 && new String(b, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a")) return "gif";
        if (b.length > 16 && new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "webp";
        throw ApiException.badRequest("仅支持 JPG、PNG、GIF 和 WebP 图片");
    }

    public void validateIds(Collection<UUID> ids) {
        if (ids.stream().anyMatch(java.util.Objects::isNull) || ids.stream().distinct().count() != ids.size()) {
            throw ApiException.badRequest("图片列表不正确");
        }
        ids.forEach(this::find);
    }

    public void cleanupAfterCommit(Collection<UUID> ids) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ids.forEach(this::deleteUnused);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { ids.forEach(MediaService.this::deleteUnused); }
        });
    }

    public boolean deleteUnused(UUID id) {
        String fileName = cleanupTransaction.execute(status -> {
            Integer count = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM diary_image WHERE media_id=?) + "
                    + "(SELECT COUNT(*) FROM anniversary WHERE cover_image_id=?)", Integer.class, id.toString(), id.toString());
            if (count != null && count > 0) return null;
            List<String> names = jdbc.query("SELECT file_name FROM media WHERE id=?", (rs, row) -> rs.getString(1), id.toString());
            if (names.isEmpty()) return "";
            jdbc.update("DELETE FROM media WHERE id=?", id.toString());
            return names.get(0);
        });
        if (fileName == null) return false;
        if (!fileName.isEmpty()) {
            try { Files.deleteIfExists(root.resolve(fileName)); }
            catch (IOException e) { log.warn("Unable to remove unused image file", e); }
        }
        return true;
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 21600000)
    public void cleanAbandonedUploads() {
        List<UUID> ids = jdbc.query("SELECT id FROM media WHERE created_at < ? AND "
                + "NOT EXISTS(SELECT 1 FROM diary_image WHERE media_id=media.id) AND "
                + "NOT EXISTS(SELECT 1 FROM anniversary WHERE cover_image_id=media.id)",
                (rs, row) -> UUID.fromString(rs.getString(1)), LocalDateTime.now(clock).minusDays(1));
        ids.forEach(this::deleteUnused);
    }
}
