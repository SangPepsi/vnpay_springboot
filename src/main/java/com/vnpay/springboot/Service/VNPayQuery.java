package com.vnpay.springboot.Service;

import com.vnpay.springboot.Config.VNPayConfig;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.text.SimpleDateFormat;
import java.util.*;

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
     * Thực hiện truy vấn kết quả giao dịch (QueryDR) VNPAY.
     * @param txnRef Mã giao dịch của Merchant (vnp_TxnRef)
     * @param transDate Ngày tạo giao dịch gốc (vnp_TransactionDate)
     * @param clientIp IP của máy chủ gửi yêu cầu
     * @return Chuỗi JSON phản hồi từ VNPAY
     */
    public String processQuery(String txnRef, String transDate, String clientIp, String transactionNo) {
        String vnp_RequestId = VNPayConfig.getRandomNumber(8);
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());


        Map<String, String> vnp_Params = new LinkedHashMap<>();
        vnp_Params.put("vnp_RequestId", vnp_RequestId);
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "querydr");
        vnp_Params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_OrderInfo", "Kiem tra ket qua GD");
        vnp_Params.put("vnp_TransactionDate", transDate);
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
        vnp_Params.put("vnp_IpAddr", clientIp);

        if (transactionNo != null && !transactionNo.isEmpty()) {
            vnp_Params.put("vnp_TransactionNo", transactionNo);
        }


        // 2. TẠO CHUỖI HASHDATA CHÍNH XÁC (Theo thứ tự cố định, nối bằng '|', KHÔNG URL ENCODE)
        // Thứ tự chuẩn của API QueryDR: vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode, vnp_TxnRef, vnp_TransactionDate, vnp_CreateDate, vnp_IpAddr, vnp_OrderInfo
        String hash_Data = String.join("|",
                vnp_Params.get("vnp_RequestId"),
                vnp_Params.get("vnp_Version"),
                vnp_Params.get("vnp_Command"),
                vnp_Params.get("vnp_TmnCode"),
                vnp_Params.get("vnp_TxnRef"),
                vnp_Params.get("vnp_TransactionDate"),
                vnp_Params.get("vnp_CreateDate"),
                vnp_Params.get("vnp_IpAddr"),
                vnp_Params.get("vnp_OrderInfo")
        );


        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hash_Data);

        log.info("QueryDR Hash Data (Raw, | separated): {}", hash_Data);


        JsonObject jsonBody = new JsonObject();
        for (Map.Entry<String, String> entry : vnp_Params.entrySet()) {
            jsonBody.addProperty(entry.getKey(), entry.getValue());
        }
        jsonBody.addProperty("vnp_SecureHash", vnp_SecureHash);
        log.info("Sending QueryDR Request: {}", jsonBody.toString());


        try {
            String responseJson = webClient.post()
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // Chặn luồng để nhận kết quả (phù hợp với Service đồng bộ)

            log.info("VNPAY QueryDR Response: {}", responseJson);

            return responseJson;

        } catch (Exception e) {
            log.error("Error connecting to VNPAY API (QueryDR): {}", e.getMessage(), e);
            // Mã 99 là lỗi kết nối nội bộ
            return "{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Lỗi kết nối API nội bộ\"}";
        }
    }
}