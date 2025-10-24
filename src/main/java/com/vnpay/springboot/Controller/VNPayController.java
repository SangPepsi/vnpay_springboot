package com.vnpay.springboot.Controller;

import com.vnpay.springboot.Config.VNPayConfig;
import com.vnpay.springboot.Service.VNPayService;
import com.vnpay.springboot.Service.VNPayQuery;
import com.vnpay.springboot.Service.VNPayRefund;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.*;

@Controller
@RequestMapping("/vnpay")
public class VNPayController {

    private static final Logger log = LoggerFactory.getLogger(VNPayController.class);
    private final VNPayService vnPayService;
    private final VNPayQuery vnPayQuery;
    private final VNPayRefund vnPayRefund;

    public VNPayController(VNPayService vnPayService, VNPayQuery vnPayQuery, VNPayRefund vnPayRefund) {
        this.vnPayService = vnPayService;
        this.vnPayQuery = vnPayQuery;
        this.vnPayRefund = vnPayRefund;
    }

    // ------------------- 1. FORM -------------------
    @GetMapping("/pay")
    public String showPayForm() {
        return "vnpay_pay";
    }

    @GetMapping("/query")
    public String querydr() {
        return "vnpay_querydr";
    }

    @GetMapping("/refund")
    public String refund() {
        return "vnpay_refund";
    }

    // ------------------- 2. TẠO ĐƠN HÀNG -------------------
    @PostMapping("/submitOrder")
    public String submitOrder(
            @RequestParam("amount") long orderTotal,
            @RequestParam("orderInfo") String orderInfo,
            @RequestParam("bankcode") String bankcode,
            @RequestParam("ordertype") String ordertype,
            @RequestParam("promocode") String promocode,
            @RequestParam("txnRef") String txnRef,
            HttpServletRequest request) {

        String clientIp = VNPayConfig.getIpAddress(request);

        try {
            String vnpayUrl = vnPayService.createOrder(
                    orderTotal, orderInfo, bankcode, ordertype, promocode, txnRef, clientIp
            );
            log.info("Redirecting user to VNPAY URL: {}", vnpayUrl);
            return "redirect:" + vnpayUrl;

        } catch (UnsupportedEncodingException e) {
            log.error("Error creating VNPAY URL", e);
            return "redirect:/vnpay/payment-error?message=URL_ENCODING_ERROR";
        }
    }

    // ------------------- 3. TRANG RETURN DUY NHẤT (ĐÃ SỬA LỖI SPEL) -------------------
    @GetMapping("/vnpay-return")
    public String handleVnPayReturn(HttpServletRequest request, Model model) {

        Map<String, String> vnpParams = new HashMap<>();
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            vnpParams.put(fieldName, fieldValue);
        }

        // CHUYỂN LOGIC XỬ LÝ CHỮ KÝ VÀ TRẠNG THÁI VÀO SERVICE
        int paymentStatus = vnPayService.processVnPayReturn(vnpParams);

        // --- KHẮC PHỤC LỖI THYMELEAF SPEL ---
        // 1. Lấy chuỗi số tiền từ VNPAY (đã nhân 100)
        String vnpAmountStr = vnpParams.get("vnp_Amount");

        // 2. Chuyển đổi chuỗi số tiền này sang Long để Thymeleaf có thể chia/format
        if (vnpAmountStr != null && !vnpAmountStr.isEmpty()) {
            try {
                long vnpAmountLong = Long.parseLong(vnpAmountStr);
                // 3. Đưa giá trị kiểu Long vào Model.
                // Thymeleaf ở view sẽ dùng giá trị Long này để thực hiện phép chia "/ 100"
                model.addAttribute("vnp_Amount", vnpAmountLong);
            } catch (NumberFormatException e) {
                log.error("Error parsing vnp_Amount: {} to Long. Using default 0.", vnpAmountStr, e);
                model.addAttribute("vnp_Amount", 0L);
            }
        } else {
            model.addAttribute("vnp_Amount", 0L);
        }
        // ------------------------------------

        // Lấy các tham số VNPAY còn lại (không cần chuyển đổi)
        model.addAttribute("vnp_TxnRef", vnpParams.get("vnp_TxnRef"));
        model.addAttribute("vnp_OrderInfo", vnpParams.get("vnp_OrderInfo"));
        model.addAttribute("vnp_BankCode", vnpParams.get("vnp_BankCode"));
        model.addAttribute("vnp_PayDate", vnpParams.get("vnp_PayDate"));
        String responseCode = vnpParams.get("vnp_ResponseCode");
        model.addAttribute("vnp_ResponseCode", responseCode);


        if (paymentStatus == -1) {
            model.addAttribute("status", "fail");
            model.addAttribute("message", "Lỗi xác thực dữ liệu (Invalid Signature).");
        } else if (paymentStatus == 1) {
            model.addAttribute("status", "success");
            model.addAttribute("message", "Thanh toán thành công!");
        } else { // paymentStatus == 0 hoặc các mã lỗi khác
            model.addAttribute("status", "fail");
            model.addAttribute("message", "Giao dịch không thành công. Mã lỗi VNPAY: " + responseCode);
        }

        return "vnpay_return";
    }

    // ------------------- 4. QUERY -------------------
    @PostMapping("/process-query")
    public String processQuery(
            @RequestParam("txnRef") String txnRef,
            @RequestParam("transDate") String transDate,
            HttpServletRequest request, Model model) {

        String clientIp = VNPayConfig.getIpAddress(request);
        String resultJson = vnPayQuery.processQuery(txnRef, transDate, clientIp);
        model.addAttribute("queryResult", resultJson);
        return "vnpay_query_result";
    }

    // ------------------- 5. REFUND -------------------
    @PostMapping("/process-refund")
    public String processRefund(
            @RequestParam("txnRef") String txnRef,
            @RequestParam("transactionNo") String transactionNo,
            @RequestParam("amount") long amount,
            @RequestParam("transType") String transType,
            @RequestParam("createBy") String createBy,
            @RequestParam("transDate") String transDate,
            HttpServletRequest request, Model model) {

        String clientIp = VNPayConfig.getIpAddress(request);
        String resultJson = vnPayRefund.sendRefundRequest(txnRef, transactionNo, amount, transType, createBy, transDate, clientIp);
        model.addAttribute("refundResult", resultJson);
        return "vnpay_refund_result";
    }
}
