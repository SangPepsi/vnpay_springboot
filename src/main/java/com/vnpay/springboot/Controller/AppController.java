package com.vnpay.springboot.Controller;

import com.vnpay.springboot.Service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.UnsupportedEncodingException;
import java.io.IOException;

@Controller
public class AppController {

    @Autowired
    private VNPayService vnPayService;

    // ------------------- 1. ENDPOINTS CƠ BẢN -------------------

    @GetMapping("/")
    public String index(){
        // Trả về trang menu chính
        return "index";
    }

    @GetMapping("/pay")
    public String showPayForm() {
        // Hiển thị form nhập thông tin thanh toán
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

    // ------------------- 2. TẠO VÀ CHUYỂN HƯỚNG ĐƠN HÀNG -------------------

    @PostMapping("/submitOrder")
    public String submitOrder(
            @RequestParam("amount") int orderTotal,
            @RequestParam("orderInfo") String orderInfo,
            @RequestParam("bankcode") String bankcode,
            @RequestParam("ordertype") String ordertype,
            @RequestParam("promocode") String promocode,
            @RequestParam("txnRef") String txnRef,
            // Chỉ cần HttpServletRequest, loại bỏ HttpServletResponse
            HttpServletRequest request) throws UnsupportedEncodingException {

        // Xây dựng Base URL để tạo ReturnUrl động (ví dụ: http://localhost:8080)
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();

        // Gọi Service để tạo URL thanh toán VNPAY (8 tham số)
        String vnpayUrl = vnPayService.createOrder(
                orderTotal, orderInfo, bankcode, ordertype, promocode, baseUrl, txnRef, request
        );

        // Chuyển hướng người dùng đến cổng thanh toán VNPAY
        return "redirect:" + vnpayUrl;
    }

    // ------------------- 3. XỬ LÝ KẾT QUẢ TRẢ VỀ TỪ VNPAY -------------------

    @GetMapping("/vnpay-payment")
    public String handleVnPayReturn(HttpServletRequest request, Model model){

        // Lấy status trả về từ Service (1:Success, 0:Fail, -1:Invalid Sig)
        int paymentStatus = vnPayService.orderReturn(request);

        // Lấy các tham số VNPAY để hiển thị lên view
        model.addAttribute("vnp_TxnRef", request.getParameter("vnp_TxnRef"));
        model.addAttribute("vnp_Amount", request.getParameter("vnp_Amount"));
        model.addAttribute("vnp_OrderInfo", request.getParameter("vnp_OrderInfo"));
        model.addAttribute("vnp_BankCode", request.getParameter("vnp_BankCode"));
        model.addAttribute("vnp_PromotionCode", request.getParameter("vnp_PromotionCode"));
        model.addAttribute("vnp_PayDate", request.getParameter("vnp_PayDate"));

        if (paymentStatus == 1) {
            // Thanh toán thành công (Checksum hợp lệ)
            return "ordersuccess";
        } else if (paymentStatus == 0) {
            // Thanh toán thất bại (Checksum hợp lệ)
            model.addAttribute("errorMessage", "Giao dịch không thành công. Mã lỗi: " + request.getParameter("vnp_ResponseCode"));
            return "orderfail";
        } else {
            // Sai Checksum (-1)
            model.addAttribute("errorMessage", "Lỗi xác thực dữ liệu (Invalid Signature).");
            return "orderfail";
        }
    }
}