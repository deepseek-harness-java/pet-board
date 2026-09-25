package cn.xiaofuge.o.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 宠物寄养数据中心：房型/寄养师/订单/统计 */
@Component
public class OStore {

    /** 寄养房型：房型/单价(元/天)/时长说明/说明 */
    static final Map<String, Object[]> ROOMS = new LinkedHashMap<>();
    static {
        ROOMS.put("标准猫舍", new Object[]{68.0, "单间", "含猫粮与铲屎，适合猫咪短住"});
        ROOMS.put("标准犬舍", new Object[]{88.0, "单间", "含狗粮与遛弯 2 次/天"});
        ROOMS.put("豪华大床房", new Object[]{158.0, "单间", "独立空调+摄像头直播，猫犬皆可"});
        ROOMS.put("双宠拼房", new Object[]{138.0, "同宠两间打通", "两只同住省一间费用"});
        ROOMS.put("度假托管月住", new Object[]{1880.0, "30 天", "含洗护 2 次，适合长假出行"});
    }

    /** 寄养师：编号/姓名/特长/评分 */
    static final Map<String, Object[]> KEEPERS = new LinkedHashMap<>();
    static {
        KEEPERS.put("K01", new Object[]{"小满", "猫咪行为照护", 4.9});
        KEEPERS.put("K02", new Object[]{"阿忠", "大型犬训练照护", 5.0});
        KEEPERS.put("K03", new Object[]{"朵朵", "老年宠物护理", 4.8});
    }

    public static class Order {
        public String id; public String customer; public String phone;
        public String pet; public String room; public String keeper;
        public String startDate; public int days;
        public double total; public String status; // 已预订 / 寄养中 / 已完成
    }

    public final List<Order> orders = new ArrayList<>();
    private int orderSeq = 4001;

    public OStore() { seed(); }

    private void seed() {
        orders.add(o("纪先生", "13800077777", "布偶猫「雪球」", "标准猫舍", "K01", "周四 10:00", 5, "已预订"));
        orders.add(o("隋女士", "13800088888", "金毛「大壮」", "豪华大床房", "K02", "周四 09:00", 7, "寄养中"));
        orders.add(o("邵先生", "13800099999", "柯基「土豆」", "标准犬舍", "K03", "周三 08:00", 3, "已完成"));
    }

    private Order o(String customer, String phone, String pet, String room, String keeperId, String startDate, int days, String status) {
        Order x = new Order(); x.id = "P" + orderSeq++; x.customer = customer; x.phone = phone;
        x.pet = pet; x.room = room; x.keeper = String.valueOf(KEEPERS.get(keeperId)[0]);
        x.startDate = startDate; x.days = days;
        Object[] p = ROOMS.get(room);
        x.total = p != null ? (Double) p[0] * days : 0;
        x.status = status; return x;
    }

    /** 房型价目表 */
    public Map<String, Object> roomList() {
        List<Map<String, Object>> list = new ArrayList<>();
        ROOMS.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("room", k); m.put("pricePerDay", v[0]); m.put("type", v[1]); m.put("desc", v[2]); list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("rooms", list);
        r.put("note", "7 天以上享 9 折，需上传疫苗证明");
        return r;
    }

    /** 寄养师列表 */
    public Map<String, Object> keeperList() {
        List<Map<String, Object>> list = KEEPERS.entrySet().stream()
                .map(e -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", e.getKey()); m.put("name", e.getValue()[0]);
                    m.put("skill", e.getValue()[1]); m.put("rating", e.getValue()[2]);
                    m.put("activeOrders", orders.stream().filter(o -> o.keeper.equals(e.getValue()[0])
                            && !"已完成".equals(o.status)).count());
                    return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("keepers", list);
        return r;
    }

    /** 预订寄养 */
    public synchronized Map<String, Object> book(String customer, String phone, String pet, String room, String keeperId, String startDate, Integer days) {
        if (customer == null || customer.isBlank())
            return Map.of("ok", false, "msg", "请提供预订人姓名");
        Object[] p = ROOMS.get(room);
        if (p == null) return Map.of("ok", false, "msg", "房型 " + room + " 不在价目表，可选：" + String.join("/", ROOMS.keySet()));
        if (phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供联系电话，方便寄养师汇报宠物状态");
        if (pet == null || pet.isBlank())
            return Map.of("ok", false, "msg", "请提供宠物信息（品种+昵称）");
        if (startDate == null || startDate.isBlank())
            return Map.of("ok", false, "msg", "请提供入住日期（如：周五 10:00）");
        if (days == null || days < 1)
            return Map.of("ok", false, "msg", "请提供寄养天数（至少 1 天）");
        String keeperName;
        if (keeperId == null || keeperId.isBlank()) {
            keeperName = String.valueOf(KEEPERS.get("K01")[0]); // 默认小满
        } else {
            var kEntry = KEEPERS.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(keeperId)).findFirst().orElse(null);
            if (kEntry == null) return Map.of("ok", false, "msg", "寄养师 " + keeperId + " 不存在，可选：" + String.join("/", KEEPERS.keySet()));
            keeperName = String.valueOf(kEntry.getValue()[0]);
        }
        double unit = (Double) p[0];
        double total = days >= 7 ? unit * days * 0.9 : unit * days;
        Order x = new Order(); x.id = "P" + orderSeq++; x.customer = customer; x.phone = phone;
        x.pet = pet; x.room = room; x.keeper = keeperName; x.startDate = startDate; x.days = days;
        x.total = total; x.status = "已预订";
        orders.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", customer);
        r.put("pet", pet); r.put("room", room); r.put("keeper", keeperName);
        r.put("startDate", startDate); r.put("days", days); r.put("total", total);
        if (days >= 7) r.put("discount", "已享 9 折（住满 7 天）");
        r.put("msg", "预订成功！单号 " + x.id + "，" + pet + " 入住 " + room + "（¥" + unit + "/天 × " + days + " 天 = ¥" + total + "），寄养师 " + keeperName + "，" + startDate + " 入住");
        return r;
    }

    /** 订单查询 */
    public Map<String, Object> orderInfo(String orderId) {
        Order x = orders.stream().filter(o -> o.id.equalsIgnoreCase(orderId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "订单 " + orderId + " 不存在，当前共 " + orders.size() + " 单");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", x.customer);
        r.put("pet", x.pet); r.put("room", x.room); r.put("keeper", x.keeper);
        r.put("startDate", x.startDate); r.put("days", x.days); r.put("total", x.total); r.put("status", x.status);
        if ("寄养中".equals(x.status)) r.put("msg", "宠物在住中，寄养师每日汇报视频可随时索取");
        if ("已完成".equals(x.status)) r.put("msg", "寄养已完成，接宠时附赠宠物洗护 8 折券");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byRoom = new LinkedHashMap<String, Object>();
        for (String s : ROOMS.keySet()) {
            long n = orders.stream().filter(o -> s.equals(o.room)).count();
            if (n > 0) byRoom.put(s, n + " 单");
        }
        Map<String, Object> byKeeper = new LinkedHashMap<String, Object>();
        for (var e : KEEPERS.entrySet()) {
            long n = orders.stream().filter(o -> o.keeper.equals(e.getValue()[0])).count();
            byKeeper.put(String.valueOf(e.getValue()[0]), n + " 单");
        }
        long ongoing = orders.stream().filter(o -> "已预订".equals(o.status) || "寄养中".equals(o.status)).count();
        double revenue = orders.stream().filter(o -> "已完成".equals(o.status)).mapToDouble(o -> o.total).sum();
        double expected = orders.stream().filter(o -> !"已完成".equals(o.status)).mapToDouble(o -> o.total).sum();
        int nights = orders.stream().filter(o -> !"已完成".equals(o.status)).mapToInt(o -> o.days).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalOrders", orders.size());
        r.put("ongoing", ongoing);
        r.put("done", orders.stream().filter(o -> "已完成".equals(o.status)).count());
        r.put("upcomingNights", nights);
        r.put("revenue", revenue);
        r.put("expectedRevenue", expected);
        r.put("byRoom", byRoom);
        r.put("byKeeper", byKeeper);
        r.put("advice", "长假前 2 周是预订高峰可推早鸟 95 折；月住套餐引导长尾客户；每日视频汇报可提升续住与转介绍");
        return r;
    }
}
