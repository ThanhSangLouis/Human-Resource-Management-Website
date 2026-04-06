package org.example.hrmsystem.service;

import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class AvatarService {

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private final EmployeeRepository employeeRepository;
    private final String uploadDir;

    public AvatarService(EmployeeRepository employeeRepository,
                         @Value("${app.upload.dir:uploads/avatars}") String uploadDir) {
        this.employeeRepository = employeeRepository;
        this.uploadDir = uploadDir;
    }

    /**
     * Validate, lưu file, cập nhật avatarUrl vào DB, trả về URL public.
     */
    public String uploadAvatar(Long employeeId, MultipartFile file) throws IOException {
        // 1. Tìm nhân viên
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhân viên không tồn tại: id=" + employeeId));

        // 2. Validate Content-Type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Định dạng file không hợp lệ. Chỉ chấp nhận: JPEG, PNG, WebP, GIF");
        }

        // 3. Validate size (phòng ngừa double-check ngoài Spring config)
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("File quá lớn. Tối đa 5 MB");
        }

        // 4. Tạo tên file duy nhất
        String extension = getExtension(file.getOriginalFilename(), contentType);
        String filename  = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 5. Tạo thư mục nếu chưa có và lưu file
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, file.getBytes());

        // 6. Xóa avatar cũ (nếu có và là file local)
        deleteOldAvatar(employee.getAvatarUrl());

        // 7. Cập nhật DB
        String publicUrl = "/uploads/avatars/" + filename;
        employee.setAvatarUrl(publicUrl);
        employeeRepository.save(employee);

        return publicUrl;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getExtension(String originalName, String contentType) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (contentType.toLowerCase()) {
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            case "image/gif"  -> "gif";
            default           -> "jpg";
        };
    }

    private void deleteOldAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("/uploads/avatars/")) return;
        try {
            String filename = avatarUrl.substring("/uploads/avatars/".length());
            Path old = Paths.get(uploadDir).resolve(filename);
            Files.deleteIfExists(old);
        } catch (IOException ignored) {
            // Không crash nếu xóa file cũ thất bại
        }
    }
}
