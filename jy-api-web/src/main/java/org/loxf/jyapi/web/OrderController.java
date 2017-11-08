package org.loxf.jyapi.web;

import org.loxf.jyadmin.base.bean.BaseResult;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class OrderController {

    /**
     * 创建订单
     * @param shareObj 商品/活动ID/url
     * @param type	商品/活动/page
     * @return
     */
    @RequestMapping("/api/createOrder")
    @ResponseBody
    public BaseResult createOrder(String shareObj, String type){
        return new BaseResult();
    }
}
