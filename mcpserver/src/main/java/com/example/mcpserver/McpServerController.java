package com.example.mcpserver;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class McpServerController {

    @McpTool(name = "calculator", description = "计算器工具，支持加减乘除运算")
    public Map<String, Object> calculator(
            @McpToolParam(description = "第一个操作数", required = true) double num1,
            @McpToolParam(description = "第二个操作数", required = true) double num2,
            @McpToolParam(description = "运算类型：add(加法), subtract(减法), multiply(乘法), divide(除法)", required = true) String operation) {
        Map<String, Object> result = new HashMap<>();
        double answer;
        switch (operation.toLowerCase()) {
            case "add" -> answer = num1 + num2;
            case "subtract" -> answer = num1 - num2;
            case "multiply" -> answer = num1 * num2;
            case "divide" -> {
                if (num2 == 0) {
                    result.put("error", "除数不能为0");
                    return result;
                }
                answer = num1 / num2;
            }
            default -> {
                result.put("error", "不支持的运算类型: " + operation);
                return result;
            }
        }
        result.put("result", answer);
        result.put("expression", num1 + " " + operation + " " + num2);
        return result;
    }

    @McpTool(name = "get_weather", description = "获取指定城市的天气信息")
    public Map<String, Object> getWeather(
            @McpToolParam(description = "城市名称", required = true) String city) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Double> temperatures = new HashMap<>();
        temperatures.put("Beijing", 28.5);
        temperatures.put("Shanghai", 30.2);
        temperatures.put("Guangzhou", 32.8);
        temperatures.put("Shenzhen", 33.5);
        temperatures.put("Chengdu", 25.3);

        Double temp = temperatures.get(city);
        if (temp != null) {
            result.put("city", city);
            result.put("temperature", temp);
            result.put("unit", "°C");
            result.put("condition", temp > 30 ? "晴天" : "多云");
            result.put("humidity", "65%");
            result.put("wind", "东北风 3级");
        } else {
            result.put("error", "暂不支持该城市: " + city);
            result.put("supported_cities", temperatures.keySet());
        }
        return result;
    }

    @McpTool(name = "get_stock_price", description = "获取股票实时价格")
    public Map<String, Object> getStockPrice(
            @McpToolParam(description = "股票代码", required = true) String symbol) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Double> prices = new HashMap<>();
        prices.put("AAPL", 178.50);
        prices.put("GOOGL", 141.80);
        prices.put("MSFT", 378.25);
        prices.put("TSLA", 252.80);
        prices.put("NVDA", 875.30);
        prices.put("BABA", 85.60);
        prices.put("JD", 45.20);

        Double price = prices.get(symbol.toUpperCase());
        if (price != null) {
            result.put("symbol", symbol.toUpperCase());
            result.put("price", price);
            result.put("currency", "USD");
            result.put("change", "+2.35%");
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            result.put("error", "暂不支持该股票: " + symbol);
            result.put("supported_symbols", prices.keySet());
        }
        return result;
    }

    @McpTool(name = "get_system_info", description = "获取系统信息")
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("os_name", System.getProperty("os.name"));
        result.put("os_version", System.getProperty("os.version"));
        result.put("java_version", System.getProperty("java.version"));
        result.put("java_vendor", System.getProperty("java.vendor"));
        result.put("available_processors", Runtime.getRuntime().availableProcessors());
        result.put("max_memory_mb", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        result.put("free_memory_mb", Runtime.getRuntime().freeMemory() / 1024 / 1024);
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return result;
    }

    @McpTool(name = "echo", description = "回显消息，测试工具调用")
    public String echo(
            @McpToolParam(description = "要回显的消息", required = true) String message) {
        return "Echo: " + message;
    }
}