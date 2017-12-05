package org.loxf.jyapi.web;

import org.apache.commons.codec.digest.DigestUtils;
import org.loxf.jyadmin.base.bean.BaseResult;
import org.loxf.jyadmin.base.constant.BaseConstant;
import org.loxf.jyadmin.base.util.weixin.WeixinUtil;
import org.loxf.jyadmin.client.dto.OrderDto;
import org.loxf.jyadmin.client.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@Controller
public class WeixinController {
    private static Logger logger = LoggerFactory.getLogger(WeixinController.class);

    @Autowired
    private OrderService orderService;

    /**
     * 接入微信接口
     *
     * @param signature
     * @param timestamp
     * @param nonce
     * @param echostr
     * @return
     */
    @RequestMapping("/api/weixin/api_access")
    @ResponseBody
    public String apiAccess(String signature, String timestamp, String nonce, String echostr) {
        // 1）将token、timestamp、nonce三个参数进行字典序排序
        ArrayList<String> list = new ArrayList<String>();
        list.add(nonce);
        list.add(timestamp);
        list.add(BaseConstant.WX_TOKEN);
        Collections.sort(list);
        //2）将三个参数字符串拼接成一个字符串进行sha1加密
        String ret = DigestUtils.shaHex(list.get(0) + list.get(1) + list.get(2));
        //3）开发者获得加密后的字符串可与signature对比，标识该请求来源于微信
        if (ret.equals(signature)) {
            return echostr;
        }
        return "";
    }

    @RequestMapping("/api/weixin/payorder")
    public void payorder(HttpServletRequest request, HttpServletResponse response) {
        String resXml = "";
        try {
            //读取参数
            StringBuffer notifyData = new StringBuffer();
            InputStream inputStream = request.getInputStream();
            String s;
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            while ((s = in.readLine()) != null) {
                notifyData.append(s);
            }
            in.close();
            inputStream.close();
            logger.debug("微信支付原始报文：" + notifyData.toString());

            //------------------------------
            //处理业务
            //------------------------------
            BaseResult<Map<String, String>> notifyMapResult = WeixinUtil.payNotifySign(notifyData.toString());
            if (notifyMapResult.getCode() == BaseConstant.SUCCESS) {
                Map<String, String> notifyMap = notifyMapResult.getData();
                String out_trade_no = notifyMap.get("out_trade_no");// 交易订单ID
                String attach = notifyMap.get("attach");// 商家数据包
                String transaction_id = notifyMap.get("transaction_id");// 微信支付订单
                String total_fee = notifyMap.get("total_fee");// 订单金额，单位为分
                // 获取订单
                BaseResult<OrderDto> orderDtoBaseResult = orderService.queryOrder(out_trade_no);
                if (orderDtoBaseResult.getCode() == BaseConstant.FAILED||orderDtoBaseResult.getData()==null) {
                    resXml = createResp(FAIL, "获取商户订单失败");
                } else {
                    OrderDto orderDto = orderDtoBaseResult.getData();
                    long orderMoney = orderDto.getOrderMoney().multiply(new BigDecimal(100)).longValue();
                    if(orderMoney!=Long.parseLong(total_fee)){
                        resXml = createResp(FAIL, "订单金额不一致");
                    }
                    BaseResult completeOrderResult = orderService.completeOrder(orderDto);
                    resXml = createResp(completeOrderResult.getCode()==BaseConstant.SUCCESS?SUCCESS:FAIL,
                            completeOrderResult.getMsg());
                }
            } else {
                resXml = createResp(FAIL, notifyMapResult.getMsg());
            }
        } catch (Exception e) {
            logger.error("微信支付回调失败：", e);
            resXml = createResp(FAIL, e.getMessage());
        } finally {
            try {
                BufferedOutputStream out = new BufferedOutputStream(
                        response.getOutputStream());
                out.write(resXml.getBytes());
                out.flush();
                out.close();
            } catch (IOException e) {
                logger.error("返回微信支付回调失败", e);
            }
        }
    }

    private static String FAIL = "FAIL";
    private static String SUCCESS = "SUCCESS";

    private String createResp(String code, String msg) {
        return "<xml>" + "<return_code><![CDATA[" + code + "]]></return_code>"
                + "<return_msg><![CDATA[" + msg + "]]></return_msg>" + "</xml> ";
    }
}