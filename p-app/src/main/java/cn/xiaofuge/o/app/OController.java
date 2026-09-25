package cn.xiaofuge.o.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 宠物寄养管家 REST 接口。
 * 提供：房型价目 / 寄养师列表 / 预订寄养 / 订单查询 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class OController {

    private final OStore store;

    public OController(OStore store) {
        this.store = store;
    }

    /** 房型价目表 */
    @GetMapping("/rooms")
    public Map<String, Object> rooms() {
        return store.roomList();
    }

    /** 寄养师列表 */
    @GetMapping("/keepers")
    public Map<String, Object> keepers() {
        return store.keeperList();
    }

    /** 预订寄养 */
    @PostMapping("/book")
    public Map<String, Object> book(@RequestBody Map<String, Object> body) {
        Integer days = null;
        Object d = body.get("days");
        if (d instanceof Number n) days = n.intValue();
        else if (d != null) {
            try { days = Integer.valueOf(String.valueOf(d).trim()); } catch (NumberFormatException ignored) { }
        }
        return store.book(str(body, "customer"), str(body, "phone"), str(body, "pet"),
                str(body, "room"), str(body, "keeperId"), str(body, "startDate"), days);
    }

    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /** 订单查询 */
    @GetMapping("/order")
    public Map<String, Object> orderInfo(@RequestParam(required = false) String orderId) {
        return store.orderInfo(orderId == null ? "" : orderId);
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
