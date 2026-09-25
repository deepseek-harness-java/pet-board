package cn.xiaofuge.o.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 宠物寄养管家插件：把 pet-board REST API 注册为 DSH Agent 工具 */
public class BoardingPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "boarding-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public BoardingPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new RoomListTool(),
                new KeeperListTool(),
                new BookTool(),
                new OrderInfoTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("boarding-capabilities", 20, """
                ## AI 宠物寄养管家（宠物寄养连锁 · 2026-09-25）
                - 查房型 → room_list（5 种房型单价与说明：标准猫舍68/标准犬舍88/豪华大床房158/双宠拼房138/度假托管月住1880；
                  住满 7 天享 9 折）
                - 查寄养师 → keeper_list（3 位寄养师特长/评分/在单量）
                - 预订寄养 → book（customer/phone/pet/room/startDate/days 必填，keeperId 可选默认小满 K01；
                  必须先复述宠物、房型、单价、天数、总价、入住日期请顾客确认后才能调用；成功报单号）
                - 订单查询 → order_info（orderId：P4001 格式；宠物/房型/寄养师/日期/金额/状态）
                - 问运营 → stats（总单量/进行中/已完成/待住夜数/营收与预计营收/分房型分寄养师分布/营销建议）
                - 回答要求：
                  1) 预订前必须复述要素（宠物/房型/天数/总价/日期）请顾客确认
                  2) 预订结果必报单号与入住日期
                  3) 必须提醒：入住需上传疫苗证明；发情期/孕期宠物不接收；寄养师每日视频汇报
                  4) 价格与优惠只转述工具返回，禁止编造折扣
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: boarding tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("BOARDING_APP_BASE_URL", "http://127.0.0.1:18113")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class RoomListTool extends AbstractTool {
        @Override public String name() { return "room_list"; }
        @Override public String description() {
            return "寄养房型价目表：5 种房型的单价与说明（猫舍/犬舍/豪华房/拼房/月住），含优惠规则。"
                    + "报价、预订前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/rooms", args));
        }
    }

    private class KeeperListTool extends AbstractTool {
        @Override public String name() { return "keeper_list"; }
        @Override public String description() {
            return "寄养师列表：姓名/特长/评分/在单量。顾客挑寄养师、问谁照护得好时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/keepers", args));
        }
    }

    private class BookTool extends AbstractTool {
        @Override public String name() { return "book"; }
        @Override public String description() {
            return "预订寄养：customer（预订人）/phone（联系电话）/pet（宠物品种+昵称）/room（房型）/startDate（入住日期）/days（天数）必填，"
                    + "keeperId（寄养师 K01-K03）可选默认 K01。必须先复述宠物、房型、天数、总价、日期经顾客确认后才能调用。"
                    + "成功返回单号。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("预订人姓名"))
                    .prop("phone", stringSchema("联系电话"))
                    .prop("pet", stringSchema("宠物品种+昵称，如：金毛「大壮」"))
                    .prop("room", stringSchema("房型：标准猫舍 / 标准犬舍 / 豪华大床房 / 双宠拼房 / 度假托管月住"))
                    .prop("keeperId", stringSchema("寄养师编号 K01-K03，可选，默认 K01 小满"))
                    .prop("startDate", stringSchema("入住日期时间，如：周五 10:00"))
                    .prop("days", stringSchema("寄养天数，正整数"))
                    .required("customer", "phone", "pet", "room", "startDate", "days")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"pet\":\"" + json(str(args, "pet"))
                    + "\",\"room\":\"" + json(str(args, "room"))
                    + "\",\"keeperId\":\"" + json(str(args, "keeperId"))
                    + "\",\"startDate\":\"" + json(str(args, "startDate"))
                    + "\",\"days\":\"" + json(str(args, "days")) + "\"}";
            return ok(post("/api/book", body, args));
        }
    }

    private class OrderInfoTool extends AbstractTool {
        @Override public String name() { return "order_info"; }
        @Override public String description() {
            return "订单查询：orderId 必填（P4001 格式）。返回宠物/房型/寄养师/日期/金额/状态（已预订、寄养中、已完成）。"
                    + "何时必须调用：顾客问订单、问宠物住得怎么样。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("orderId", stringSchema("订单号，如 P4001"))
                    .required("orderId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/order?orderId=" + java.net.URLEncoder.encode(str(args, "orderId"), StandardCharsets.UTF_8), args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：总单量/进行中/已完成/待住夜数/营收与预计营收/分房型分寄养师分布/营销建议。"
                    + "何时必须调用：问今天运营、问单量与营收。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
