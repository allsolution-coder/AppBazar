package com.appbazaar.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Settings for UPI (QR code) payments in rupees. Active only when UPI_ID and INR_PER_USD are set. */
@Service
public class UpiService {
    private final String upiId;
    private final String payee;
    private final double rate;

    public UpiService(@Value("${app.upi-id}") String upiId, @Value("${app.upi-name}") String payee,
                      @Value("${app.inr-per-usd}") double rate) {
        this.upiId = upiId == null ? "" : upiId.trim();
        this.payee = payee;
        this.rate = rate;
    }

    public boolean enabled() { return !upiId.isBlank() && rate > 0; }
    public double rate() { return rate; }
    public String id() { return upiId; }
    public String payee() { return payee; }

    /** Rupees to ask for a price in dollars, rounded to whole rupees. */
    public long toInr(int usd) { return Math.round(usd * rate); }

    /** The upi:// link that every UPI app understands. The same text goes into the QR code. */
    public String link(long inr, String reference, String note) {
        return "upi://pay?pa=" + upiId
                + "&pn=" + enc(payee)
                + "&am=" + String.format(Locale.ROOT, "%d.00", inr)
                + "&cu=INR"
                + "&tn=" + enc(note + " " + reference)
                + "&tr=" + enc(reference);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
