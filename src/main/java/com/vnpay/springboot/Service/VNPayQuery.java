package com.vnpay.springboot.Service;

import com.vnpay.springboot.Config.VNPayConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.io.IOException;

@Service // Annotation quan trọng
public class VNPayQuery {

    // TIÊM (Inject) lớp cấu hình đã tối ưu
    @Autowired
    private VNPayConfig vnPayConfig;

    // Phương thức xử lý tra cứu
    public String processQuery(String vnpTxnRef, String vnpTransDate, HttpServletRequest req) throws IOException {

        // 1. Chuẩn bị Tham số Tra cứu
        String vnp_RequestId = VNPayConfig.getRandomNumber(8);
        String vnp_Version = "2.1.0";
        String vnp_Command = "querydr";
        String vnp_TmnCode = VNPayConfig.vnp_TmnCode; // Lấy từ instance đã inject
        String vnp_OrderInfo = "Kiem tra ket qua GD OrderId:" + vnpTxnRef;

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());

        // Dùng hàm tiện ích static (từ lớp Config)
        String vnp_IpAddr = VNPayConfig.getIpAddress(req);

        JsonObject  vnp_Params = new JsonObject ();

        // Thêm tham số vào JsonObject
        vnp_Params.addProperty("vnp_RequestId", vnp_RequestId);
        vnp_Params.addProperty("vnp_Version", vnp_Version);
        vnp_Params.addProperty("vnp_Command", vnp_Command);
        vnp_Params.addProperty("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.addProperty("vnp_TxnRef", vnpTxnRef);
        vnp_Params.addProperty("vnp_OrderInfo", vnp_OrderInfo);
        vnp_Params.addProperty("vnp_TransactionDate", vnpTransDate);
        vnp_Params.addProperty("vnp_CreateDate", vnp_CreateDate);
        vnp_Params.addProperty("vnp_IpAddr", vnp_IpAddr);

        // 2. Tạo Secure Hash
        String hash_Data= String.join("|", vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode, vnpTxnRef, vnpTransDate, vnp_CreateDate, vnp_IpAddr, vnp_OrderInfo);
        // Lấy secretKey từ instance đã inject
        String vnp_SecureHash = VNPayConfig.hmacSHA512(VNPayConfig.secretKey, hash_Data);

        vnp_Params.addProperty("vnp_SecureHash", vnp_SecureHash);

        // 3. Gửi Yêu cầu Tra cứu lên VNPAY
        URL url = new URL (VNPayConfig.vnp_apiUrl);
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        // Sử dụng try-with-resources để tự động đóng DataOutputStream
        try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
            wr.writeBytes(vnp_Params.toString());
            wr.flush();
        }

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post Data : " + vnp_Params);
        System.out.println("Response Code : " + responseCode);

        // 4. Đọc Phản hồi
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String output;
            StringBuilder response = new StringBuilder();
            while ((output = in.readLine()) != null) {
                response.append(output);
            }
            return response.toString();
        }
    }
}