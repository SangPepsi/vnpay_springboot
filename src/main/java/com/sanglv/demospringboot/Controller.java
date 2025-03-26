package com.sanglv.demospringboot;

import com.sanglv.demospringboot.Config.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@org.springframework.stereotype.Controller
public class Controller {
    @Autowired
    private VNPayService vnPayService;


    @GetMapping("")
    public String home(){
        return "index";
    }

    @PostMapping("/submitOrder")
    public String submidOrder(@RequestParam("amount") int orderTotal,
                              @RequestParam("orderInfo") String orderInfo,
                              @RequestParam("bankcode") String bankcode,
                              @RequestParam("ordertype") String ordertype,
                              @RequestParam("promocode") String promocode,

                              HttpServletRequest request){
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String vnpayUrl = vnPayService.createOrder(orderTotal, orderInfo, bankcode, ordertype, promocode, baseUrl);
        return "redirect:" + vnpayUrl;
    }

    @GetMapping("/vnpay-payment")
    public String GetMapping(HttpServletRequest request, Model model){
        int paymentStatus =vnPayService.orderReturn(request);

        String orderInfo = request.getParameter("vnp_OrderInfo");
        String bankcode = request.getParameter("vnp_BankCode");
        String promocode = request.getParameter("vnp_PromoCode");
        String paymentTime = request.getParameter("vnp_PayDate");
        String totalPrice = request.getParameter("vnp_Amount");

        model.addAttribute("orderId", orderInfo);
        model.addAttribute("bankcode", bankcode);
        model.addAttribute("promocode", promocode);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("paymentTime", paymentTime);

        return paymentStatus == 1 ? "ordersuccess" : "orderfail";
    }
}
