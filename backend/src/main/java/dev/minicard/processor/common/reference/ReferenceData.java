package dev.minicard.processor.common.reference;

import java.util.List;
import java.util.Map;

public final class ReferenceData {

  private ReferenceData() {}

  public record MccItem(String code, String label) {}

  public record CurrencyItem(String code, int minorUnits) {}

  public record ResponseCodeItem(String code, String message) {}

  public record DeclineReasonItem(String reason, String responseCode, String message) {}

  public static final List<ResponseCodeItem> RESPONSE_CODES =
      List.of(
          new ResponseCodeItem("00", "Approved"),
          new ResponseCodeItem("12", "Invalid transaction"),
          new ResponseCodeItem("51", "Insufficient funds"),
          new ResponseCodeItem("54", "Expired card"),
          new ResponseCodeItem("57", "Transaction not permitted to cardholder"),
          new ResponseCodeItem("59", "Suspected fraud"),
          new ResponseCodeItem("61", "Exceeds amount limit"),
          new ResponseCodeItem("62", "Restricted card"));

  public static final List<DeclineReasonItem> DECLINE_REASONS =
      List.of(
          new DeclineReasonItem("CARD_NOT_ACTIVE", "62", "Restricted card"),
          new DeclineReasonItem("CARD_EXPIRED", "54", "Expired card"),
          new DeclineReasonItem("CURRENCY_MISMATCH", "12", "Invalid transaction"),
          new DeclineReasonItem("MCC_RESTRICTED", "57", "Transaction not permitted to cardholder"),
          new DeclineReasonItem("LIMIT_PER_TRANSACTION", "61", "Exceeds amount limit"),
          new DeclineReasonItem("LIMIT_DAILY", "61", "Exceeds amount limit"),
          new DeclineReasonItem("INSUFFICIENT_FUNDS", "51", "Insufficient funds"),
          new DeclineReasonItem("SUSPECTED_FRAUD", "59", "Suspected fraud"));

  public static final List<MccItem> MCCS =
      List.of(
          new MccItem("4111", "Local transport"),
          new MccItem("4121", "Taxis and ride hailing"),
          new MccItem("4511", "Airlines"),
          new MccItem("4900", "Utilities"),
          new MccItem("5200", "Home supply stores"),
          new MccItem("5311", "Department stores"),
          new MccItem("5411", "Grocery stores, supermarkets"),
          new MccItem("5541", "Service stations"),
          new MccItem("5651", "Family clothing"),
          new MccItem("5732", "Electronics stores"),
          new MccItem("5812", "Restaurants"),
          new MccItem("5814", "Fast food"),
          new MccItem("5912", "Drug stores, pharmacies"),
          new MccItem("5999", "Miscellaneous retail"),
          new MccItem("6011", "ATM cash withdrawal"),
          new MccItem("6012", "Financial institutions"),
          new MccItem("7011", "Hotels and lodging"),
          new MccItem("7832", "Cinemas"),
          new MccItem("7995", "Betting and gambling"),
          new MccItem("8062", "Hospitals"));

  public static final Map<String, String> MCC_LABELS =
      MCCS.stream()
          .collect(java.util.stream.Collectors.toUnmodifiableMap(MccItem::code, MccItem::label));

  public static final List<CurrencyItem> CURRENCIES =
      List.of(new CurrencyItem("NPR", 2), new CurrencyItem("USD", 2));

  public static final java.util.Set<String> SUPPORTED_CURRENCY_CODES =
      java.util.Set.of("NPR", "USD");

  public static final List<String> DISPUTE_REASONS =
      List.of(
          "UNAUTHORIZED_TRANSACTION",
          "DUPLICATE_CHARGE",
          "GOODS_NOT_RECEIVED",
          "INCORRECT_AMOUNT",
          "OTHER");
}
