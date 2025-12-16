package com.vnpay.springboot.Service;

import com.vnpay.springboot.Config.VNPayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    // ------------------- TẠO URL THANH TOÁN -------------------

    public String createOrder(long total, String orderInfor, String bankcode, String ordertype,
                              String promocode, String txnRef, String clientIp) throws UnsupportedEncodingException {

        Map<String, String> vnp_Params = new HashMap<>();

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

        cld.add(Calendar.MINUTE, 9);
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


    // ------------------- 2. XỬ LÝ TRANG TRẢ VỀ (RETURN URL - Đã có) -------------------

    public int processVnPayReturn(Map<String, String> vnpParams) {

        String vnp_SecureHash = vnpParams.get("vnp_SecureHash");
        String vnp_TxnRef = vnpParams.get("vnp_TxnRef");
        String vnp_ResponseCode = vnpParams.get("vnp_ResponseCode");
        String newSecureHash = createNewSecureHash(vnpParams);
        if (!newSecureHash.equals(vnp_SecureHash)) {
            log.warn("Invalid VNPAY Signature! TxnRef: {}", vnp_TxnRef);
            return -1;
        }

        // CHỮ KÝ HỢP LỆ -> KIỂM TRA TRẠNG THÁI GIAO DỊCH
        if ("00".equals(vnp_ResponseCode)) {
            boolean dbUpdateSuccess = updateOrderStatusToSuccess(vnp_TxnRef, vnpParams.get("vnp_Amount"));
            return dbUpdateSuccess ? 1 : 0;
        } else {
            log.warn("VNPAY Transaction Failed. TxnRef: {}, Code: {}", vnp_TxnRef, vnp_ResponseCode);
            updateOrderStatusToFailed(vnp_TxnRef, vnp_ResponseCode);
            return 0;
        }
    }


    // ------------------- 3. XỬ LÝ IPN (INSTANT PAYMENT NOTIFICATION) -------------------

    /**
     * Xử lý IPN (Instant Payment Notification) từ VNPAY.
     * Đây là phương thức nền, chịu trách nhiệm chính trong việc cập nhật trạng thái giao dịch.
     * @param vnpParams Tất cả tham số nhận được từ VNPAY.
     * @return Map JSON phản hồi theo định dạng VNPAY: RspCode và Message.
     */
    @Transactional // Rất quan trọng: đảm bảo tính toàn vẹn DB
    public Map<String, String> processVnPayIpn(Map<String, String> vnpParams) {

        String vnp_SecureHash = vnpParams.get("vnp_SecureHash");
        String vnp_TxnRef = vnpParams.get("vnp_TxnRef");
        String vnp_ResponseCode = vnpParams.get("vnp_ResponseCode");
        long vnp_Amount = Long.parseLong(vnpParams.get("vnp_Amount"));

        // 1. TẠO CHUỖI BĂM MỚI VÀ KIỂM TRA CHECKSUM
        String newSecureHash = createNewSecureHash(vnpParams); // Tái sử dụng logic hash

        if (!newSecureHash.equals(vnp_SecureHash)) {
            log.warn("IPN Failed: Invalid Checksum! TxnRef: {}", vnp_TxnRef);
            return createIpnResponse("97", "Invalid Checksum"); // Sai Checksum -> 97
        }

        // --- CHỮ KÝ HỢP LỆ -> BẮT ĐẦU KIỂM TRA DB VÀ CẬP NHẬT ---

        // TODO: (RẤT QUAN TRỌNG) THAY THẾ LOGIC DB GIẢ ĐỊNH SAU ĐÂY
////        /*
//         Optional<Order> orderOpt = orderRepository.findByVnpTxnRef(vnp_TxnRef);
//         if (orderOpt.isEmpty()) {
//             return createIpnResponse("01", "Order not Found"); // Mã giao dịch không tồn tại -> 01
//         }
//         Order order = orderOpt.get();
//
//        // // 1. Kiểm tra số tiền
//         if (order.getAmountInVnpayFormat() != vnp_Amount) {
//             return createIpnResponse("04", "Invalid Amount"); // Số tiền không trùng khớp -> 04
//         }
//
//        // // 2. Kiểm tra trạng thái (tránh xử lý trùng lặp)
//         if (order.getStatus().equals("PAID")) {
//             return createIpnResponse("02", "Order already confirmed"); // Đã cập nhật trước đó -> 02
//         }
//
//        // --- NẾU QUA TẤT CẢ CÁC KIỂM TRA, TIẾN HÀNH CẬP NHẬT DB ---
//        */

        // 3. CẬP NHẬT TRẠNG THÁI CUỐI CÙNG (Đã qua kiểm tra bảo mật)
        if ("00".equals(vnp_ResponseCode)) {
            // Cập nhật thành công
            updateOrderStatusToSuccess(vnp_TxnRef, String.valueOf(vnp_Amount));
            log.info("IPN Success: Order {} updated to PAID.", vnp_TxnRef);
        } else {
            // Cập nhật thất bại
            updateOrderStatusToFailed(vnp_TxnRef, vnp_ResponseCode);
            log.warn("IPN Failed: Order {} updated to FAILED. Code: {}", vnp_TxnRef, vnp_ResponseCode);
        }

        // 4. PHẢN HỒI THÀNH CÔNG VỚI VNPAY
        // Trả về 00 chỉ khi bạn đã thành công ghi nhận và xử lý kết quả
        return createIpnResponse("00", "Confirm Success");
    }


    // ------------------- 4. CÁC PHƯƠNG THỨC HỖ TRỢ -------------------

    // Phương thức giả định cho Database
    private boolean updateOrderStatusToSuccess(String txnRef, String amount) {
        // [Chú ý]: THAY THẾ BẰNG LOGIC CẬP NHẬT DATABASE THỰC TẾ (ví dụ: JPA repository.save)
        // Trong môi trường thực tế, bạn sẽ kiểm tra Order ID, số tiền, và trạng thái ở đây.
        return true;
    }

    private void updateOrderStatusToFailed(String txnRef, String responseCode) {
        // [Chú ý]: THAY THẾ BẰNG LOGIC CẬP NHẬT DATABASE THỰC TẾ
        // Cập nhật trạng thái đơn hàng thành FAILED/CANCELLED
    }

    /**
     * Hàm tái tạo chuỗi Hash (dùng chung cho Return URL và IPN)
     */
    private String createNewSecureHash(Map<String, String> vnpParams) {
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty() &&
                    !fieldName.equals("vnp_SecureHash") &&
                    !fieldName.equals("vnp_SecureHashType")) {
                try {
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString());
                    hashData.append(fieldName).append('=').append(encodedValue).append('&');
                } catch (UnsupportedEncodingException e) {
                    log.error("Error during URL encoding for VNPAY params: {}", e.getMessage());
                    return "";
                }
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }

        return vnPayConfig.hmacSHA512(vnPayConfig.getSecretKey(), hashData.toString());
    }

    /**
     * Tạo Map JSON Response theo định dạng VNPAY (chỉ dùng cho IPN)
     */
    private Map<String, String> createIpnResponse(String rspCode, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("RspCode", rspCode);
        response.put("Message", message);
        return response;
    }
}