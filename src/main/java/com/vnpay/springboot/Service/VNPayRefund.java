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

        Map<String, String> vnp_Params = new LinkedHashMap<>();
        vnp_Params.put("vnp_RequestId", vnp_RequestId);
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "refund");
        vnp_Params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnp_Params.put("vnp_TransactionType", transType);
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_Amount", vnp_Amount);
        vnp_Params.put("vnp_OrderInfo", "Hoan tien giao dich " + txnRef);
        vnp_Params.put("vnp_TransactionNo", transactionNo);
        vnp_Params.put("vnp_TransactionDate", transDate);
        vnp_Params.put("vnp_CreateBy", createBy);
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
        vnp_Params.put("vnp_IpAddr", clientIp);


        // 2. TẠO CHUỖI HASHDATA CHÍNH XÁC (Theo thứ tự cố định, nối bằng '|')
        // THỨ TỰ CHUẨN CỦA REFUND API v2.1.0:
        // vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode, vnp_TransactionType, vnp_TxnRef, vnp_Amount, vnp_TransactionNo, vnp_TransactionDate, vnp_OrderInfo, vnp_CreateBy, vnp_CreateDate, vnp_IpAddr
        String hash_Data = String.join("|",
                vnp_Params.get("vnp_RequestId"),
                vnp_Params.get("vnp_Version"),
                vnp_Params.get("vnp_Command"),
                vnp_Params.get("vnp_TmnCode"),
                vnp_Params.get("vnp_TransactionType"),
                vnp_Params.get("vnp_TxnRef"),
                vnp_Params.get("vnp_Amount"),
                vnp_Params.get("vnp_TransactionNo"),
                vnp_Params.get("vnp_TransactionDate"),
                vnp_Params.get("vnp_CreateBy"),
                vnp_Params.get("vnp_CreateDate"),
                vnp_Params.get("vnp_IpAddr"),
                vnp_Params.get("vnp_OrderInfo")
        );

        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hash_Data);
        log.info("Refund Hash Data (Raw, | separated): {}", hash_Data);



        JsonObject jsonBody = new JsonObject();
        for (Map.Entry<String, String> entry : vnp_Params.entrySet()) {
            jsonBody.addProperty(entry.getKey(), entry.getValue());
        }
        jsonBody.addProperty("vnp_SecureHash", vnp_SecureHash);

        log.info("Sending Refund Request: {}", jsonBody.toString());
        try {
            String responseJson = webClient.post()
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("VNPAY Refund Response: {}", responseJson);

            return responseJson;

        } catch (Exception e) {
            log.error("Error connecting to VNPAY API (Refund): {}", e.getMessage(), e);
            return "{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Lỗi kết nối API nội bộ\"}";
        }
    }
}
