/**
 * Created by meng on 5/13/17.
 */
import java.sql.Timestamp;
import java.util.*;
import com.ib.client.*;
import enums.*;
import enums.OrderStage;
import utils.Logger;
import utils.SocketComm;

public class EWrapperImpl implements EWrapper {

    private EReaderSignal readSignal;
    private EClientSocket clientSocket;
    static Set<Integer> repeatTicks = new HashSet<>(Arrays.asList(0,1,2,3,4,5,32,33,45,84));

    EWrapperImpl(){
        readSignal = new EJavaSignal();
        clientSocket = new EClientSocket(this, readSignal);
    }

    void connect(String host, int port, int id, boolean async){
        clientSocket.setAsyncEConnect(async);
        clientSocket.eConnect(host, port, id);
    }

    EReaderSignal getReadSignal(){
        return readSignal;
    }

    EClientSocket getClientSocket(){
        return clientSocket;
    }

    public void tickPrice(int reqId, int field, double price, TickAttr tickAttr) {
        String symbol = SocketComm.getInstance().getSymbol(reqId);
        switch (field) {
            case 1: //bid price
            case 2: //ask price
            case 4: //last price
                CallbackAction.updateTickPrice(symbol, field, price);
                break;
            case 9: // previous day's close price
                MainGateway.callbackTracker |= (1<<0);
                CallbackAction.updateOpenClose(false, symbol, price);
                break;
            case 14: // today's opening price
                MainGateway.callbackTracker |= (1<<1);
                CallbackAction.updateOpenClose(true, symbol, price);
                break;
            case 15: //13-week low
            case 16: //13-week high
            case 17: //26-week low
            case 18: //26-week high
            case 19: //52-week low
            case 20: //52-week high
            case 21: //90-days average daily volume(mutiple of 100)
                MainGateway.callbackTracker |= (1<<(field-13));
                CallbackAction.updateHighLow(symbol, field, price);
                break;
            default:
                System.out.println("reqId:" + Integer.toString(reqId) + " field:" + Integer.toString(field) + " price:" + Double.toString(price));
                break;
        }
    }

    public void tickSize(int reqId, int field, int size) {
        switch (field) {
            case 0: //bid size
            case 3: //ask size
            case 5: //last size
                String symbol = SocketComm.getInstance().getSymbol(reqId);
                CallbackAction.updateTickSize(symbol, field, size);
                break;
            default:
        }
    }

    public void tickOptionComputation(int i, int i1, double v, double v1, double v2, double v3, double v4, double v5, double v6, double v7) {
        System.out.println("TEST1");
    }

    public void tickGeneric(int i, int i1, double v) {
        System.out.println("TEST2");
    }

    public void tickString(int reqId, int field, String value) {
        String symbol = SocketComm.getInstance().getSymbol(reqId);
        if (symbol.equals("INVALID"))
            return;
        switch (field) {
            case 47: // fundamental ratio
                CallbackAction.updateFundamentalRatios(symbol, value);
                MainGateway.callbackTracker |= (1<<9);
                break;
            case 32: //bid exchange
                CallbackAction.updateTickExchange(symbol, "bid_exchange", value);
                break;
            case 33: //ask exchange
                CallbackAction.updateTickExchange(symbol, "ask_exchange", value);
                break;
            case 45: //last time
                CallbackAction.updateTickLastTime(symbol, value);
                break;
            case 59: // IB dividends
                CallbackAction.updateDividend(symbol, value);
                MainGateway.callbackTracker |= (1<<10);
                break;
            case 84: //last exchange
                CallbackAction.updateTickExchange(symbol, "last_exchange", value);
                break;
            default:
                System.out.println("reqId:" + Integer.toString(reqId) + " field:" + Integer.toString(field) + " value:" + value);
                break;
        }
    }

    public void tickEFP(int i, int i1, double v, String s, double v1, int i2, String s1, double v2, double v3) {
        System.out.println("TEST4");
    }

    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        String symbol = SocketComm.getInstance().getOrder(orderId);
        Logger.getInstance().log(Log.CALLBACK, orderId + "->" + symbol);
        OrderTracer orderTracer = StrategyExecutor.orderBook.get(symbol);
        String logEntry = String.format("ORDER,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", status, symbol, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
        Logger.getInstance().log(Log.CALLBACK, logEntry);
        switch (status) {
            case "PendingSubmit":
            case "PreSubmitted":
            case "Submitted":
                orderTracer.setStatus(OrderStage.SUBMITTED);
                StrategyExecutor.orderBook.put(symbol, orderTracer);
                break;
            case "PendingCancel":
            case "ApiCanceled":
            case "Cancelled":
                orderTracer.setStatus(OrderStage.BACKLOG);
                StrategyExecutor.orderBook.put(symbol, orderTracer);
                break;
            case "Filled":
                // deduct the quantity of filled from active
                if (remaining == 0) {
                    StrategyExecutor.orderBook.remove(symbol);
                } else {
                    orderTracer.setQuantity((int)remaining);
                    StrategyExecutor.orderBook.put(symbol, orderTracer);
                }
                break;
            case "Inactive":
                orderTracer.setStatus(OrderStage.BACKLOG);
                StrategyExecutor.orderBook.put(symbol, orderTracer);
            default:
        }
    }

    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        String symbol = SocketComm.getInstance().getOrder(orderId);
        String logEntry = String.format("ORDER,openOrder,%s,%s", symbol, orderId);
        Logger.getInstance().log(Log.CALLBACK, logEntry);
    }

    public void openOrderEnd() {
        System.out.println("Open order end");
    }

    public void updateAccountValue(String s, String s1, String s2, String s3) {
        System.out.println("TEST8");
    }

    public void updatePortfolio(Contract contract, double v, double v1, double v2, double v3, double v4, double v5, String s) {
        System.out.println("TEST9");
    }

    public void updateAccountTime(String s) {
        System.out.println("TEST10");
    }

    public void accountDownloadEnd(String s) {
        System.out.println("TEST11");
    }

    public void nextValidId(int id) {
        StrategyExecutor.orderId = id;
    }

    public void contractDetails(int i, ContractDetails contractDetails) {
        /*
        System.out.println(contractDetails.contract().symbol());
        String symbol = contractDetails.contract().symbol();
        String template = "UPDATE security SET status=1 WHERE symbol='%s'";
        String query = String.format(template, symbol);
        CallbackAction.getInstance().execUpdate(query);
        */
        System.out.println(contractDetails.toString());
    }

    public void bondContractDetails(int i, ContractDetails contractDetails) {
        System.out.println(contractDetails.toString());
    }

    public void contractDetailsEnd(int i) {
        System.out.println("contractDetailsEnd");
    }

    public void execDetails(int i, Contract contract, Execution execution) {
        System.out.println("Execution details:");
        String message = String.format("symbol:%s, share:%s, price:%s" ,contract.symbol(), execution.shares(), execution.price());
        System.out.println(message);
    }

    public void execDetailsEnd(int i) {
        System.out.println("Execution Details End");
    }

    public void updateMktDepth(int i, int i1, int i2, int i3, double v, int i4) {
        System.out.println("TEST18");
    }

    public void updateMktDepthL2(int i, int i1, String s, int i2, int i3, double v, int i4) {
        System.out.println("TEST19");
    }

    public void updateNewsBulletin(int i, int i1, String s, String s1) {
        System.out.println("TEST20");
    }

    public void managedAccounts(String accounts) {
        System.out.println("ManagedAccounts: " + accounts);
    }

    public void receiveFA(int i, String s) {
        System.out.println("TEST22");
    }

    public void historicalData(int reqId, Bar bar) {
        CallbackAction.updateHistoricBar(SocketComm.getInstance().getSymbol(reqId), bar.time(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(), bar.count(), bar.wap());
    }

    public void scannerParameters(String s) {
        System.out.println("TEST24");
    }

    public void scannerData(int i, int i1, ContractDetails contractDetails, String s, String s1, String s2, String s3) {
        System.out.println("TEST25");
    }

    public void scannerDataEnd(int i) {
        System.out.println("TEST26");
    }

    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        consolePrint(reqId, "realtimeBar");
        CallbackAction.updateRealTimeBar(SocketComm.getInstance().getSymbol(reqId), time, open, close, high, low, volume, wap, count);
    }

    public void currentTime(long time) {
        Timestamp timestamp = new Timestamp(time*1000);
        System.out.println("Current time: " + timestamp );
    }

    public void fundamentalData(int reqId, String data) {
        //String TICKER = utils.XmlParser.getText(data, "Issues:0;Issue:0;IssueID:1");
        //String MKTCAP = utils.XmlParser.getText(data, "Ratios:0;Group:1;Ratio:0");
        //String EMPLY = utils.XmlParser.getText(data, "Ratios:0;Group:3;Ratio:5");
        //String query_template = "UPDATE security SET mkt_cap=%s, num_emply=%s WHERE symbol='%s'";
        //String query = String.format(query_template, MKTCAP, EMPLY, TICKER);
        //CallbackAction.getInstance().execUpdate(query);
    }

    public void deltaNeutralValidation(int i, DeltaNeutralContract deltaNeutralContract) {
        System.out.println("TEST30");
    }

    public void tickSnapshotEnd(int i) {
        System.out.println("TEST31");
    }

    public void marketDataType(int i, int i1) {
        System.out.println("TEST32");
    }

    public void commissionReport(CommissionReport commissionReport) {
        System.out.println("Commission Report:");
        String message = String.format("commission:%s, PnL:%s, yield:%s", commissionReport.m_commission, commissionReport.m_realizedPNL, commissionReport.m_yield);
        System.out.println(message);
    }

    public void position(String s, Contract contract, double position, double avgCost) {
        String msg = String.format("POSITION,Update,%s,%s,%s", contract.symbol(), position, avgCost);
        Logger.getInstance().log(Log.CALLBACK, msg);
    }

    public void positionEnd() {
        Logger.getInstance().log(Log.CALLBACK, "POSITION,Cancel");
    }

    public void accountSummary(int i, String s, String s1, String s2, String s3) {
        System.out.println("TEST36");
    }

    public void accountSummaryEnd(int i) {
        System.out.println("TEST37");
    }

    public void verifyMessageAPI(String s) {
        System.out.println("verifyMessageAPI");
    }

    public void verifyCompleted(boolean b, String s) {
        System.out.println("verifyCompleted");
    }

    public void verifyAndAuthMessageAPI(String s, String s1) {
        System.out.println("verifyAndAuthMessageAPI");
    }

    public void verifyAndAuthCompleted(boolean b, String s) {
        System.out.println("verifyAndAuthCompleted");
    }

    public void displayGroupList(int i, String s) {
        System.out.println("TEST42");
    }

    public void displayGroupUpdated(int i, String s) {
        System.out.println("TEST43");
    }

    public void error(Exception e) {
        System.out.println("Error Message: " + e.getMessage());
    }

    public void error(String errorMsg) {
        System.out.println("Error Message: " + errorMsg);
    }

    public void error(int reqId, int errorCode, String errorMsg) {
        switch (errorCode) {
            case 162:
                if(reqId > MainGateway.reqId_HistData) {
                    MainGateway.pendingHistReq--;
                }
                break;
            case 200:
                if(reqId > MainGateway.reqId_HistData) {
                    MainGateway.pendingHistReq--; //receive error response from historical 1-min bar data request
                }else if(reqId > MainGateway.reqId_MktData) {
                    //MainGateway.receivedFundRatio = true; //received error response from fundamental ratios request, symbol ambiguous
                    MainGateway.callbackTracker = 0; //reset bit map tracker
                }
                break;
            case 300:
                //ignored
                break;
            case 354:
                if(reqId > MainGateway.reqId_HistData) {
                    //invalid case
                }else if(reqId > MainGateway.reqId_MktData) {
                    //MainGateway.receivedFundRatio = true; //received error response from fundamental ratios request, no data subscription
                    MainGateway.callbackTracker = 0; //reset bit map tracker
                }
                break;
            default:
        }
        String error = String.format("[E]pending: %d, reqId: %d, errorCode: %d, message: %s", MainGateway.pendingHistReq, reqId, errorCode, errorCode);
        Logger.getInstance().log(Log.CALLBACK, error);
        System.out.println("reqId: " + reqId + ", Error Code: " + errorCode + ", Error Message: " + errorMsg);
    }

    public void connectionClosed() {
        System.out.println("Connection Closed");
    }

    public void connectAck() {
        if (clientSocket.isAsyncEConnect()) {
            System.out.println("Connection Acknowledged");
            clientSocket.startAPI();
        }
    }

    public void positionMulti(int i, String s, String s1, Contract contract, double v, double v1) {
        System.out.println("TEST48");
    }

    public void positionMultiEnd(int i) {
        System.out.println("TEST49");
    }

    public void accountUpdateMulti(int i, String s, String s1, String s2, String s3, String s4) {
        System.out.println("TEST50");
    }

    public void accountUpdateMultiEnd(int i) {
        System.out.println("TEST51");
    }

    public void securityDefinitionOptionalParameter(int i, String s, int i1, String s1, String s2, Set<String> set, Set<Double> set1) {
        System.out.println("TEST52");
    }

    public void securityDefinitionOptionalParameterEnd(int i) {
        System.out.println("TEST53");
    }

    public void softDollarTiers(int i, SoftDollarTier[] softDollarTiers) {
        System.out.println("TEST54");
    }

    public void familyCodes(FamilyCode[] familyCodes) {
        System.out.println("TEST55");
    }

    public void symbolSamples(int i, ContractDescription[] contractDescriptions) {
        System.out.println("TEST56");
    }

    public void historicalDataEnd(int reqId, String s, String s1) {
        MainGateway.pendingHistReq--;
        String end = String.format("[N], pending: %d, id: %d, s: %s, s1: %s", MainGateway.pendingHistReq, reqId, s, s1);
        Logger.getInstance().log(Log.CALLBACK, end);
    }

    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
        System.out.println("TEST58");
    }

    public void tickNews(int i, long l, String s, String s1, String s2, String s3) {
        System.out.println("TEST59");
    }

    public void smartComponents(int i, Map<Integer, Map.Entry<String, Character>> map) {
        System.out.println("TEST60");
    }

    public void tickReqParams(int i, double v, String s, int i1) {
        // System.out.println("[C] tickReqParams -> tickerId: " + i + ", minTick: " + v + ", bboExchange: " + s + ", snapshotPermission: " + i1);
    }

    public void newsProviders(NewsProvider[] newsProviders) {
        System.out.println("TEST62");
    }

    public void newsArticle(int i, int i1, String s) {
        System.out.println("TEST63");
    }

    public void historicalNews(int i, String s, String s1, String s2, String s3) {
        System.out.println("TEST64");
    }

    public void historicalNewsEnd(int i, boolean b) {
        System.out.println("TEST65");
    }

    public void headTimestamp(int i, String timestamp) {
        System.out.println(timestamp);
    }

    public void histogramData(int reqId, List<HistogramEntry> pricePoints) {
        Collections.sort(pricePoints);
        StringBuilder literal = new StringBuilder();
        literal.append("{");
        for (HistogramEntry pricePoint : pricePoints) {
            literal.append(pricePoint.price);
            literal.append(":");
            literal.append(pricePoint.size);
            literal.append(",");
        }
        literal.append("}");
        String literal_str = literal.toString();
        CallbackAction.updateHistogram(SocketComm.getInstance().getSymbol(reqId), pricePoints, literal_str);
        //utils.GatewayLogger.getInstance().log(enums.Log.CALLBACK, "[" + reqId + "]" + literal_str);
        consolePrint(reqId, "histogramData");
        //utils.SocketComm.getInstance().send(reqId,"histogram", utils.SocketComm.getInstance().getSymbol(reqId), literal_str);
    }

    public void historicalDataUpdate(int i, Bar bar) {
        System.out.println("TEST68");
    }

    public void rerouteMktDataReq(int i, int i1, String s) {
        System.out.println("TEST69");
    }

    public void rerouteMktDepthReq(int i, int i1, String s) {
        System.out.println("TEST70");
    }

    public void marketRule(int i, PriceIncrement[] priceIncrements) {
        System.out.println("TEST71");
    }

    public void pnl(int i, double v, double v1) {
        System.out.println("TEST72");
    }

    public void pnlSingle(int i, int i1, double v, double v1, double v2) {
        System.out.println("TEST73");
    }

    private void consolePrint(int reqId, String callback) {
        System.out.println("reqId: " + Integer.toString(reqId) + ", Symbol: " + SocketComm.getInstance().getSymbol(reqId) + ", Callback: " + callback);
    }

}