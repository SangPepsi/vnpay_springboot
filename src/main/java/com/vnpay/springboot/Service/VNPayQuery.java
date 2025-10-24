package com.vnpay.springboot.Service;

import com.vnpay.springboot.Config.VNPayConfig;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.text.SimpleDateFormat;
import java.util.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class VNPayQuery {

    private static final Logger log = LoggerFactory.getLogger(VNPayQuery.class);

    private final VNPayConfig vnPayConfig;
    private final WebClient webClient;

    public VNPayQuery(VNPayConfig vnPayConfig, WebClient.Builder webClientBuilder) {
        this.vnPayConfig = vnPayConfig;
        this.webClient = webClientBuilder.baseUrl(vnPayConfig.getApiUrl()).build();
    }

    /**
     * Thực hiện truy vấn kết quả giao dịch từ VNPAY.
     * @param txnRef Mã giao dịch của đơn hàng (vnp_TxnRef)
     * @param transDate Ngày tạo giao dịch theo format yyyyMMddHHmmss (vnp_TransactionDate)
     * @param clientIp IP của người dùng thực hiện truy vấn
     * @return Chuỗi JSON phản hồi từ VNPAY
     */
    public String processQuery(String txnRef, String transDate, String clientIp) {

        // 1. Chuẩn bị các tham số cho Hash
        String vnp_RequestId = VNPayConfig.getRandomNumber(8);
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());

        // Sử dụng Map để dễ dàng sắp xếp và tạo hashData
        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_RequestId", vnp_RequestId);
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "querydr");
        vnp_Params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnp_Params.put("vnp_TxnRef", txnRef);
        // Lưu ý: Dữ liệu vnp_OrderInfo chỉ nên là ASCII để tránh lỗi ký tự
        vnp_Params.put("vnp_OrderInfo", "Kiem tra ket qua GD OrderId:" + txnRef);
        vnp_Params.put("vnp_TransactionDate", transDate); // Phải là ngày giờ tạo giao dịch gốc
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate); // Ngày giờ gửi yêu cầu Query
        vnp_Params.put("vnp_IpAddr", clientIp);

        // 2. Tạo chuỗi HashData theo quy tắc của VNPAY (Alphabetical Sorting)
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();

        try {
            for (String fieldName : fieldNames) {
                String fieldValue = vnp_Params.get(fieldName);
                if (fieldValue != null && !fieldValue.isEmpty()) {

                    // THAY ĐỔI QUY TẮC: URLEncode TẤT CẢ các giá trị bằng US_ASCII
                    // kể cả vnp_OrderInfo, để đảm bảo tính khắt khe nhất của chữ ký.
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString());

                    // Nối chuỗi băm theo format: fieldName=encodedValue&
                    hashData.append(fieldName).append("=").append(encodedValue).append("&");
                }
            }
        } catch (UnsupportedEncodingException e) {
            log.error("Encoding error during hash data creation.", e);
            // Mã 99 là mã lỗi nội bộ (local)
            return "{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Lỗi Encoding dữ liệu\"}";
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1); // Loại bỏ '&' cuối cùng
        }

        // 3. Tạo Secure Hash
        // Sửa lỗi: Sử dụng getSecretKey() thay vì secretKey()
        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hashData.toString());

        // --- LOGGING VÀ KIỂM TRA LỖI 97 ---
        // Vui lòng copy chuỗi này để kiểm tra độc lập HMAC-SHA512:
        log.info("QueryDR Hash Data (To be Hashed, Strict Encoded): {}", hashData.toString());
        log.info("QueryDR Secure Hash Generated: {}", vnp_SecureHash);
        // ------------------------------------


        // 4. Thêm Hash vào JsonObject trước khi gửi
        JsonObject jsonBody = new JsonObject();
        for (Map.Entry<String, String> entry : vnp_Params.entrySet()) {
            jsonBody.addProperty(entry.getKey(), entry.getValue());
        }
        jsonBody.addProperty("vnp_SecureHash", vnp_SecureHash);

        // 5. Gửi Yêu cầu Tra cứu
        try {
            String response = webClient.post()
                    .bodyValue(jsonBody.toString()) // Gửi JsonObject đã bao gồm hash
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response;

        } catch (Exception e) {
            log.error("Error connecting to VNPAY API (QueryDR): {}", e.getMessage(), e);
            // Mã 99 là mã lỗi nội bộ (local)
            return "{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Lỗi kết nối API nội bộ\"}";
        }
    }
}