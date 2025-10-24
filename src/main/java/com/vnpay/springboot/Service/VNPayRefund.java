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
public class VNPayRefund {

    private static final Logger log = LoggerFactory.getLogger(VNPayRefund.class);

    private final VNPayConfig vnPayConfig;
    private final WebClient webClient;

    public VNPayRefund(VNPayConfig vnPayConfig, WebClient.Builder webClientBuilder) {
        this.vnPayConfig = vnPayConfig;
        // Base URL cho API QueryDR/Refund
        this.webClient = webClientBuilder.baseUrl(vnPayConfig.getApiUrl()).build();
    }

    public String sendRefundRequest(
            String txnRef,
            String transactionNo,
            long amount,
            String transType,
            String createBy,
            String transDate,
            String clientIp) {

        // 1. Chuẩn bị các tham số cho Hash
        String vnp_RequestId = VNPayConfig.getRandomNumber(8);
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());

        // SỬ DỤNG MAP để chứa tham số và thực hiện Sắp xếp (Alphabetical Sorting)
        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_RequestId", vnp_RequestId);
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "refund"); // Lệnh hoàn tiền
        vnp_Params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnp_Params.put("vnp_TransactionType", transType);
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // Số tiền nhân 100
        vnp_Params.put("vnp_OrderInfo", "Hoan tien giao dich " + transactionNo);
        vnp_Params.put("vnp_TransactionNo", transactionNo);
        vnp_Params.put("vnp_TransactionDate", transDate);
        vnp_Params.put("vnp_CreateBy", createBy);
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
        vnp_Params.put("vnp_IpAddr", clientIp);

        // 2. Tạo chuỗi HashData theo quy tắc của VNPAY (Alphabetical Sorting)
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();

        try {
            for (String fieldName : fieldNames) {
                String fieldValue = vnp_Params.get(fieldName);
                if (fieldValue != null && !fieldValue.isEmpty()) {
                    // Cần URLEncoder.encode() cho giá trị (Rule VNPAY)
                    // Lưu ý: Mặc dù yêu cầu API thường không cần encode, VNPAY quy định mã hóa cho tất cả các tham số khi tính hash
                    hashData.append(fieldName).append("=")
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString())).append("&");
                }
            }
        } catch (UnsupportedEncodingException e) {
            log.error("Encoding error during hash data creation for Refund.", e);
            throw new RuntimeException("Encoding error.", e);
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1); // Loại bỏ '&' cuối cùng
        }

        // 3. Tạo Secure Hash (KHẮC PHỤC LỖI STATIC/NON-STATIC)
        // Gọi hmacSHA512 qua đối tượng đã inject (vnPayConfig)
        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hashData.toString());

        // 4. Thêm Hash vào JsonObject trước khi gửi
        JsonObject jsonBody = new JsonObject();
        for (Map.Entry<String, String> entry : vnp_Params.entrySet()) {
            jsonBody.addProperty(entry.getKey(), entry.getValue());
        }
        jsonBody.addProperty("vnp_SecureHash", vnp_SecureHash);

        // 5. Gửi Yêu cầu Hoàn tiền
        try {
            String response = webClient.post()
                    .bodyValue(jsonBody.toString()) // Gửi JsonObject đã bao gồm hash
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response;

        } catch (Exception e) {
            log.error("Error connecting to VNPAY API (Refund): {}", e.getMessage(), e);
            return "{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Lỗi kết nối API nội bộ\"}";
        }
    }
}
