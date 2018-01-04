import com.ib.client.*;
import enums.Currency;
import enums.Exchange;
import enums.SecType;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import utils.Helper;

import java.io.FileReader;
import java.time.ZonedDateTime;
import java.util.HashMap;

/**
 * Created by meng on 6/7/17.
 */
public class StrategyExecutor implements Runnable {

    private static StrategyExecutor ourInstance = new StrategyExecutor();

    public static StrategyExecutor getInstance() {
        return ourInstance;
    }

    private StrategyExecutor() {
    }

    public static HashMap<String, Integer> buy_backlog = new HashMap<>();
    public static HashMap<String, Integer> sell_backlog = new HashMap<>();
    public static HashMap<String, Integer> buy_active = new HashMap<>();
    public static HashMap<String, Integer> sell_active = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static void loadTask() {
        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObject = (JSONObject) parser.parse(
                    new FileReader("/home/meng/Projects/NeuroTrader/TradeQueue.json"));
            JSONArray buyList = (JSONArray)jsonObject.get("BUY");
            JSONArray sellList = (JSONArray)jsonObject.get("SELL");
            for (Object jsonObj : buyList) {
                String symbol =  (String)((JSONObject)jsonObj).get("symbol");
                Integer quantity = (int)(long)((JSONObject)jsonObj).get("quantity");
                System.out.println("Buy " + quantity + " shares of " + symbol);
                buy_backlog.put(symbol, quantity);
            }
            for (Object jsonObj : sellList) {
                String symbol =  (String)((JSONObject)jsonObj).get("symbol");
                Integer quantity = (int)(long)((JSONObject)jsonObj).get("quantity");
                System.out.println("Sell " + quantity + " shares of " + symbol);
                sell_backlog.put(symbol, quantity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void trigger(int orderId, String symbol, String action) {
        if (action.equals("BUY")) {
            buy_active.put(symbol, buy_backlog.remove(symbol));
        } else {
            sell_active.put(symbol, sell_backlog.remove(symbol));
        }
        Contract contract = OrderBuilder.makeContract(symbol, SecType.STK, Exchange.SMART, Currency.USD);
        Order order = OrderBuilder.createMarketOrder(action, buy_backlog.get(symbol));  //market order
        MainGateway.client.getClientSocket().placeOrder(orderId, contract, order);
    }

    @Override
    public void run() {
        loadTask();
        /*
        ZonedDateTime now = ZonedDateTime.now();
        String date = now.toString().split("T")[0];
        ZonedDateTime hit = ZonedDateTime.parse(date+"T12:30:00.000-04:00[America/New_York]");
        int triggerBase = 30000;
        while(!buy_backlog.isEmpty() || !sell_backlog.isEmpty() || !buy_active.isEmpty() || !sell_active.isEmpty()) {
            now = ZonedDateTime.now();
            if (now.isAfter(hit)) {
                for (String symbol : buy_backlog.keySet()) {
                    trigger(triggerBase, symbol, "BUY");
                    Helper.pauseSec(60);
                }
                for (String symbol : sell_backlog.keySet()) {
                    trigger(triggerBase, symbol, "SELL");
                    Helper.pauseSec(60);
                }
            }

        }
        */
    }
}
