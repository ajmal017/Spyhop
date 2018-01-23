import com.ib.client.Contract;
import enums.Currency;
import enums.Exchange;
import enums.Log;
import enums.SecType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import utils.DatabaseConn;
import utils.Helper;
import utils.Logger;
import utils.SocketComm;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.LinkedList;

/**
 * Created by meng on 9/10/17.
 */
class UpdateAction {

    public static void waitForFlag() { // make it public so it can be tested
        int timeout = 0;
        while(MainGateway.callbackTracker != ((1<<11)-1) && timeout < MainGateway.updateTimeout) {
            Helper.pauseMilli(1);
            timeout++;
        }
    }

    static void updateAllSecurities() {
        String symbol_query = "SELECT symbol FROM security";
        String genericTicks = "165, 258, 456"; //165:high low  258:fundamental ratios  456: IB dividends
        ResultSet resultSet = DatabaseConn.getInstance().execQuery(symbol_query);
        LinkedList<String> allSymbols = Helper.resultToList(resultSet, "symbol");
        String prevSymbol = "";
        int reqId = MainGateway.reqId_MktData;
        for (String symbol : allSymbols) {
            MainGateway.client.getClientSocket().cancelMktData(reqId); // error on first element: OK
            Logger.getInstance().log(Log.ACTION, "[Cancel]," + prevSymbol);
            // increment reqId for new Id-symbol pair
            SocketComm.getInstance().registerRequest(++reqId, symbol);
            Contract contract = OrderBuilder.makeContract(symbol, SecType.STK, Exchange.SMART, Currency.USD);
            //MainGateway.receivedFundRatio = false;
            MainGateway.callbackTracker = 0; //reset bit map tracker
            MainGateway.client.getClientSocket().reqMktData(reqId, contract, genericTicks, false, false, null);
            prevSymbol = symbol;
            waitForFlag();
            Logger.getInstance().log(Log.ACTION, "[Request]," + symbol);
        }

        MainGateway.client.getClientSocket().cancelMktData(reqId);
        Logger.getInstance().log(Log.ACTION, "[Cancel]," + prevSymbol);
    }

    static void updateIndices() {
        try {
            Document doc = Jsoup.connect("https://finance.google.com/finance?q=INDEXSP:.INX").get();
            double indices = Double.parseDouble(doc.getElementsByAttributeValue("id", "ref_626307_l").text().replace(",", ""));
            String update = "INSERT INTO benchmark (date, snp500) VALUES ('%s',%s)";
            String query = String.format(update, Helper.today(), indices);
            DatabaseConn.getInstance().execUpdate(query);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
