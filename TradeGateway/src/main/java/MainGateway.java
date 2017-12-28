import com.ib.client.Contract;
import com.ib.client.EReader;
import enums.Currency;
import enums.Exchange;
import enums.SecType;
import org.omg.Messaging.SYNC_WITH_TRANSPORT;
import sun.font.CoreMetrics;
import utils.Helper;
import utils.Logger;
import utils.SocketComm;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class MainGateway{

    /* Running mode */
    static boolean Historical = true;
    static boolean Live = true; //Live trading or Paper trading
    static boolean RHT = true;  //if Paper trading, fake during RTH or off RTH

    /* Update flag */
    static boolean recvFundRatio = false;
    static int reqIdUpdateBase = 1000;
    static int updateTimeout = 3000;

    /* API objects */
    static EWrapperImpl client;
    static EReader messageQueue;
    static QueueProcessor messageProcessor;
    static Thread strategyExecutor;

    /* Pacing Metrics */
    static int pendingHistReq = 0;

    public static void main(String[] args) {

        /* Retrieve active stocks */
        LinkedList<String> symbols = CallbackAction.selectActiveStocks();

        /* One-time request status */
        boolean streaming = false;
        boolean updated = false;

        /* Debug */
        // Add debugging operation here
        //if (Live)
        //    System.exit(0);

        /* Update benchmark indices */
        UpdateAction.updateIndices();

        /* initialization */
        System.out.println("============Initialization Started============");
        client = new EWrapperImpl();
        System.out.println("TradeGateway client initialized");
        while(true) {
            while (!client.getClientSocket().isConnected()) {
                System.out.println("Connecting InteractiveBrokers API...");
                try {
                    client.connect("localhost", Live?4001:4002, 0, false);
                    if (client.getClientSocket().isConnected()) {
                        System.out.println("Connected to InteractiveBrokers API");

                        messageQueue = new EReader(client.getClientSocket(), client.getReadSignal());
                        messageQueue.isDaemon();
                        messageQueue.start();
                        System.out.println("Message queue thread started");

                        messageProcessor = new QueueProcessor(client, messageQueue);
                        messageProcessor.isDaemon();
                        messageProcessor.start();
                        System.out.println("Message processor thread started");

                        strategyExecutor = new Thread(StrategyExecutor.getInstance());
                        strategyExecutor.isDaemon();
                        strategyExecutor.start();
                        System.out.println("Strategy executor thread started");

                        System.out.println("Setting log level...");
                        client.getClientSocket().setServerLogLevel(3);

                        System.out.println("============Initialization Completed============");
                    }
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            /* Check if the market is closed */
            if (!beforeClose()) {
                /* Consolidate tick data */
                System.out.println("=============Consolidate daily data==============");
                CallbackAction.consolidateTicks("tick", "tick_history");

                /* Historical Data Requests */
                if (Historical) {
                    LinkedList<String> allSymbols = CallbackAction.selectAllStocks();
                    Calendar cal = Calendar.getInstance();
                    SimpleDateFormat form = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                    String formatted = form.format(cal.getTime());
                    int reqId = 1000; //starting reqId for historical data request
                    for (String symbol : allSymbols) {
                        while (pendingHistReq > 48) { // 50 simultaneous open historic data requests limitation
                            Helper.pauseMilli(5);
                        }
                        System.out.println("reqId:"+reqId);
                        SocketComm.getInstance().registerSymbol(reqId, symbol);
                        Contract contract = OrderBuilder.makeContract(symbol, SecType.STK, Exchange.SMART, Currency.USD);
                        pendingHistReq++;
                        client.getClientSocket().reqHistoricalData(reqId, contract, formatted, "1 D", "1 min", "TRADES", 1, 1, false, null);
                        reqId++;
                        Helper.pauseMilli(250);
                    }
                }
                // make sure pending requests are processed before exiting
                while (pendingHistReq > 0) {
                    Helper.pauseSec(1);
                }
                Helper.pauseSec(60); // give 60s seconds of lag tolerance
                break;
            }
            /* One-time requests */
            if (!streaming) {
                System.out.println("============Sending One-time Requests============");
                int reqId = 1;
                String genericTickList = "233";
                for (String symbol : symbols) {
                    Contract contract = OrderBuilder.makeContract(symbol, SecType.STK, Exchange.SMART, Currency.USD);
                    SocketComm.getInstance().registerSymbol(reqId, symbol);
                    client.getClientSocket().reqMktData(reqId,contract, genericTickList, false, false, null);
                    System.out.println("[R] reqMktData: " + symbol);
                    //client.getClientSocket().reqRealTimeBars(reqId, contract, 5, "TRADES", true, null);
                    reqId++;
                }
                streaming = true;
            }

            /* Data Refresh */
            if (!updated) {
                UpdateAction.updateAllSecurities();
                updated = true;
            }

            /* Recurring requests */
            //int reqId = 1;
            //boolean useRTH = true;
            //int period = 5;
            //for (String symbol : symbols) {
                //Contract contract = OrderBuilder.makeContract(symbol, enums.SecType.STK, enums.Exchange.SMART, enums.Currency.USD);
                //utils.SocketComm.getInstance().registerSymbol(reqId, symbol);
                //client.getClientSocket().reqHistogramData(reqId, contract,useRTH, Integer.toString(period) + " mins");
                //pauseSec(Math.max(10, period*60/numStock));
                //reqId++;
            //}
            Helper.pauseSec(1);
        }
        Logger.getInstance().close();
        System.out.println("============Main Gateway Disconnected============");
        System.exit(0);
    }

    private static boolean beforeClose() {
        while (RTHCheck() == -1) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return RTHCheck() != 1;
    }

    private static int RTHCheck() {
        try {
            ZonedDateTime now = ZonedDateTime.now();
            String date = now.toString().split("T")[0];
            ZonedDateTime open = ZonedDateTime.parse(date+"T09:30:00.000-04:00[America/New_York]");
            ZonedDateTime close = ZonedDateTime.parse(date+"T16:05:00.000-04:00[America/New_York]"); //ADD 5 min for lag
            if (now.isAfter(open) && now.isBefore(close)) {
                return 0;
            } else if (now.isBefore(open)) {
                System.out.println("Waiting for Market Opening [9:30 am - 4:00 pm EST]");
                return Live?-1:(RHT?0:-1);
            } else {
                System.out.println("Market is closed [9:30 am - 4:00 pm EST]");
                return Live?1:(RHT?0:1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}