package com.vnpay.springboot.Service;

import com.vnpay.springboot.Config.VNPayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VNPayService {

    private static final Logger log = LoggerFactory.getLogger(VNPayService.class);
    private final VNPayConfig vnPayConfig;

    // Constructor Injection
    public VNPayService(VNPayConfig vnPayConfig) {
        this.vnPayConfig = vnPayConfig;
    }

    // ------------------- 1. TẠO URL THANH TOÁN -------------------

    public String createOrder(long total, String orderInfor, String bankcode, String ordertype,
                              String promocode, String txnRef, String clientIp) throws UnsupportedEncodingException {

        Map<String, String> vnp_Params = new HashMap<>();

        // Chuẩn bị Tham số VNPAY
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnp_Params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        vnp_Params.put("vnp_Amount", String.valueOf(total * 100));
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfor);
        vnp_Params.put("vnp_OrderType", ordertype);
        vnp_Params.put("vnp_IpAddr", clientIp);

        if (bankcode != null && !bankcode.isEmpty()) {
            vnp_Params.put("vnp_BankCode", bankcode);
        }


        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Tạo Query String và Hash Data
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                String encodedFieldName = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString());
                String encodedFieldValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString());

                hashData.append(encodedFieldName).append('=').append(encodedFieldValue).append('&');
                query.append(encodedFieldName).append('=').append(encodedFieldValue).append('&');
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
            query.setLength(query.length() - 1);
        }

        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hashData.toString());

        String queryUrl = query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;
        return vnPayConfig.getPayUrl() + "?" + queryUrl;
    }


    public int processVnPayReturn(Map<String, String> vnpParams) {

        String vnp_SecureHash = vnpParams.get("vnp_SecureHash");
        String vnp_TxnRef = vnpParams.get("vnp_TxnRef");
        String vnp_ResponseCode = vnpParams.get("vnp_ResponseCode");

        // BƯỚC 1: TẠO CHUỖI BĂM (HASH DATA) MỚI TỪ CÁC THAM SỐ GỬI VỀ
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);

            // QUAN TRỌNG: CHỈ LOẠI BỎ vnp_SecureHash VÀ vnp_SecureHashType KHỎI CHUỖI BĂM
            if (fieldValue != null && !fieldValue.isEmpty() &&
                    !fieldName.equals("vnp_SecureHash") &&
                    !fieldName.equals("vnp_SecureHashType")) {

                try {
                    // PHẢI URL ENCODE LẠI GIÁ TRỊ VÌ: Khi VNPAY gửi về, Spring đã tự động decode các tham số.
                    // Nhưng VNPAY yêu cầu chuỗi băm phải có giá trị đã được encode.
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString());

                    hashData.append(fieldName).append('=').append(encodedValue).append('&');
                } catch (UnsupportedEncodingException e) {
                    // Chỉ log lỗi, không dừng chương trình
                    log.error("Error during URL encoding for VNPAY return params: {}", e.getMessage());
                }
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1); // Bỏ ký tự & cuối cùng
        }

        // BƯỚC 2: TẠO SECURE HASH MỚI
        String newSecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hashData.toString());

        // BƯỚC 3: XÁC THỰC CHỮ KÝ
        if (!newSecureHash.equals(vnp_SecureHash)) {
            log.warn("Invalid VNPAY Signature! TxnRef: {}, Calculated Hash: {}", vnp_TxnRef, newSecureHash);
            return -1; // Sai chữ ký
        }

        // CHỮ KÝ HỢP LỆ -> BƯỚC 4: KIỂM TRA TRẠNG THÁI GIAO DỊCH

        // Kiểm tra vnp_ResponseCode (mã phản hồi VNPAY) - "00" là thành công
        if ("00".equals(vnp_ResponseCode)) {
            // Thành công (Chữ ký OK và ResponseCode OK)

            // TODO: (RẤT QUAN TRỌNG) Kiểm tra trùng lặp, số tiền và cập nhật DB.
            boolean dbUpdateSuccess = updateOrderStatusToSuccess(vnp_TxnRef, vnpParams.get("vnp_Amount"));

            return dbUpdateSuccess ? 1 : 0; // Trả về 1 nếu cập nhật DB thành công

        } else {
            // Thất bại (Chữ ký OK nhưng ResponseCode Lỗi)
            log.warn("VNPAY Transaction Failed. TxnRef: {}, Code: {}", vnp_TxnRef, vnp_ResponseCode);
            updateOrderStatusToFailed(vnp_TxnRef, vnp_ResponseCode);
            return 0;
        }
    }

    // Phương thức giả định cho Database (Bạn cần thay bằng logic JPA/JDBC)
    private boolean updateOrderStatusToSuccess(String txnRef, String amount) {
        log.info("DB: SUCCESS - Cập nhật trạng thái đơn hàng {} thành công với số tiền {}", txnRef, amount);
        return true;
    }

    private void updateOrderStatusToFailed(String txnRef, String responseCode) {
        log.info("DB: FAILED - Cập nhật trạng thái đơn hàng {} thành thất bại, Code: {}", txnRef, responseCode);
    }
}
