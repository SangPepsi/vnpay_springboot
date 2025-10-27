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
public class VNPayRefund {

    private static final Logger log = LoggerFactory.getLogger(VNPayRefund.class);
    private final VNPayConfig vnPayConfig;
    private final WebClient webClient;

    public VNPayRefund(VNPayConfig vnPayConfig, WebClient.Builder webClientBuilder) {
        this.vnPayConfig = vnPayConfig;
        this.webClient = webClientBuilder.baseUrl(vnPayConfig.getApiUrl()).build();
    }

    /**
     * Gửi yêu cầu hoàn tiền (Refund) đến VNPAY.
     * @param txnRef Mã giao dịch của Merchant (vnp_TxnRef)
     * @param transactionNo Mã giao dịch VNPAY (vnp_TransactionNo)
     * @param amount Số tiền cần hoàn (VND, sẽ được nhân 100)
     * @param transType Loại hoàn tiền (02: Toàn phần, 03: Một phần)
     * @param createBy Người tạo yêu cầu
     * @param transDate Ngày tạo giao dịch gốc (yyyyMMddHHmmss)
     * @param clientIp IP của máy chủ gửi yêu cầu
     * @return Chuỗi JSON phản hồi từ VNPAY
     */
    public String sendRefundRequest(
            String txnRef,
            String transactionNo,
            long amount,
            String transType,
            String createBy,
            String transDate,
            String clientIp) {


        String vnp_RequestId = VNPayConfig.getRandomNumber(8);
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());


        String vnp_Amount = String.valueOf(amount * 100);

        Map<String, String> requestParamsMap = new LinkedHashMap<>();
        requestParamsMap.put("vnp_RequestId", vnp_RequestId);
        requestParamsMap.put("vnp_Version", "2.1.0");
        requestParamsMap.put("vnp_Command", "refund");
        requestParamsMap.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        requestParamsMap.put("vnp_TransactionType", transType);
        requestParamsMap.put("vnp_TxnRef", txnRef);
        requestParamsMap.put("vnp_Amount", vnp_Amount);
        requestParamsMap.put("vnp_OrderInfo", "Hoan tien giao dich " + txnRef);
        requestParamsMap.put("vnp_TransactionNo", transactionNo);
        requestParamsMap.put("vnp_TransactionDate", transDate);
        requestParamsMap.put("vnp_CreateBy", createBy);
        requestParamsMap.put("vnp_CreateDate", vnp_CreateDate);
        requestParamsMap.put("vnp_IpAddr", clientIp);


        String hash_Data = String.join("|",
                requestParamsMap.get("vnp_RequestId"),
                requestParamsMap.get("vnp_Version"),
                requestParamsMap.get("vnp_Command"),
                requestParamsMap.get("vnp_TmnCode"),
                requestParamsMap.get("vnp_TransactionType"),
                requestParamsMap.get("vnp_TxnRef"),
                requestParamsMap.get("vnp_Amount"),
                requestParamsMap.get("vnp_TransactionNo"),
                requestParamsMap.get("vnp_TransactionDate"),
                requestParamsMap.get("vnp_CreateBy"),
                requestParamsMap.get("vnp_CreateDate"),
                requestParamsMap.get("vnp_IpAddr"),
                requestParamsMap.get("vnp_OrderInfo")
        );


        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hash_Data);
        log.info("Refund Hash Data: {}", hash_Data);
        log.info("Refund Secure Hash: {}", vnp_SecureHash);



        JsonObject jsonBody = new JsonObject();
        for (Map.Entry<String, String> entry : requestParamsMap.entrySet()) {
            jsonBody.addProperty(entry.getKey(), entry.getValue());
        }
        jsonBody.addProperty("vnp_SecureHash", vnp_SecureHash);

        log.info("Sending Refund Request: {}", jsonBody.toString());


        try {
            // WebClient tự động gửi POST request với JSON body
            String responseJson = webClient.post()
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // block() để biến Monoreactive thành luồng đồng bộ

            log.info("VNPAY Refund Response: {}", responseJson);

            return responseJson;

        } catch (Exception e) {
            log.error("Error connecting to VNPAY API (Refund): {}", e.getMessage(), e);
            // Trả về mã 99 (Lỗi nội bộ) nếu không kết nối được
            return "{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Lỗi kết nối API nội bộ\"}";
        }
    }
}
