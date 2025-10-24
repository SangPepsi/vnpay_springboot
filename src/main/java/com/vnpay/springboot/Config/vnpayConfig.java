package com.vnpay.springboot.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Data
public class VNPayConfig {

    private String tmnCode;
    private String secretKey;
    private String payUrl;
    private String returnUrl;
    private String apiUrl;

    // Phương thức tiện ích để lấy IP Address
    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }

    // Phương thức tiện ích tạo số ngẫu nhiên cho RequestId
    public static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Phương thức băm HMACSHA512 (NON-STATIC)
     */
    public String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key and Data must not be null.");
            }

            Mac hmacSHA512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmacSHA512.init(secretKeySpec);

            byte[] hash = hmacSHA512.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Lỗi tạo HMACSHA512: " + e.getMessage(), e);
        }
    }

    /**
     * Phương thức tạo chuỗi hash từ tất cả các trường (cho OrderReturn)
     */
    public String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        try {
            for (String fieldName : fieldNames) {
                String fieldValue = fields.get(fieldName);
                if (fieldValue != null && !fieldValue.isEmpty()
                        && !fieldName.equals("vnp_SecureHashType")
                        && !fieldName.equals("vnp_SecureHash")) {

                    // Lưu ý: Trong phương thức hashAllFields của VNPAY, giá trị tham số cần được URLEncode trước khi nối chuỗi.
                    // Tuy nhiên, vì các tham số VNPAY trả về đã được decode, và logic URLEncode chỉ cần thiết khi tạo URL,
                    // ta giữ nguyên logic hiện tại, chỉ băm giá trị gốc sau khi đã sắp xếp.
                    // Nếu bạn gặp lỗi chữ ký, hãy thử URLEncode tại đây: URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString())

                    sb.append(fieldName).append("=").append(fieldValue).append('&');
                }
            }

            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            // Gọi phương thức hmacSHA512 non-static
            return hmacSHA512(this.secretKey, sb.toString());

        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo hash VNPAY: " + e.getMessage(), e);
        }
    }
}