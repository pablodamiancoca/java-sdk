package com.global.api.gateways;

import com.global.api.builders.AuthorizationBuilder;
import com.global.api.builders.ManagementBuilder;
import com.global.api.builders.ReportBuilder;
import com.global.api.builders.TransactionReportBuilder;
import com.global.api.entities.*;
import com.global.api.entities.enums.*;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.exceptions.GatewayException;
import com.global.api.entities.exceptions.UnsupportedTransactionException;
import com.global.api.entities.reporting.SearchCriteriaBuilder;
import com.global.api.network.NetworkMessageHeader;
import com.global.api.paymentMethods.*;
import com.global.api.serviceConfigs.GatewayConfig;
import com.global.api.utils.*;
import lombok.Getter;
import lombok.Setter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import static com.global.api.entities.enums.TransactionType.*;
import static com.global.api.utils.StringUtils.isNullOrEmpty;

public class GpApiConnector extends RestGateway implements IPaymentGateway, IReportingService {
    @Getter
    @Setter
    private GatewayConfig gatewayConfig;
    @Getter
    @Setter
    private static String accessToken;

    private static final String GP_API_VERSION = "2020-04-10";
    private static final String NONCE = "transactionsapi";

    private static final DateTimeFormatter TIMESTAMP_DTF = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final SimpleDateFormat TIMESTAMP_SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final SimpleDateFormat DATE_SDF = new SimpleDateFormat("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_DTF = DateTimeFormat.forPattern("yyyy-MM-dd");

    public GpApiConnector(GatewayConfig gatewayConfig) {
        super();    // ContentType is: "application/json"
        setGatewayConfig(gatewayConfig);
        setServiceUrl(getGatewayConfig().getServiceUrl());
        addBasicHeaders();
    }

    private void addBasicHeaders() {
        headers.put(org.apache.http.HttpHeaders.ACCEPT, "application/json");
        headers.put(org.apache.http.HttpHeaders.ACCEPT_ENCODING, "gzip");
        headers.put("X-GP-Version", GP_API_VERSION);
    }

    public void addAccessTokenHeader() throws ApiException {
        if (isNullOrEmpty(accessToken)) {
            setAccessToken(requestAccessToken());
        }
        headers.put("Authorization", String.format("Bearer %s", accessToken));
    }

    private String requestAccessToken() throws ApiException {
        String requestBodyStr =
                new JsonDoc()
                        .set("app_id", getGatewayConfig().getAppId())
                        .set("nonce", NONCE)
                        .set("secret", getSHA512SecurePassword(getGatewayConfig().getAppKey(), NONCE))
                        .set("grant_type", "client_credentials")
                        .set("seconds_to_expire", "60000")
                        .set("interval_to_expire", "WEEK")
                        .toString();

        String rawResponse = doTransaction("POST", "/accesstoken", requestBodyStr, null);
        return JsonDoc.parseSingleValue(rawResponse, "token");
    }

    public String getSHA512SecurePassword(String passwordToHash, String salt) throws GatewayException {
        String generatedPassword;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest(passwordToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
            generatedPassword = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new GatewayException("Algorithm not available in the environment", e);
        }
        return generatedPassword;
    }

    @Override
    protected String handleResponse(GatewayResponse response) throws GatewayException {
        if (response.getStatusCode() != 200) {

            JsonDoc parsed = JsonDoc.parse(response.getRawResponse());

            if (parsed.has("error_code")) {

                String errorCode = parsed.getString("error_code");
                String detailedErrorCode = parsed.getString("detailed_error_code");
                String detailedErrorDescription = parsed.getString("detailed_error_description");

                throw new GatewayException(
                        String.format("Status Code: %s - Error code: %s", response.getStatusCode(), errorCode),
                        detailedErrorCode,
                        detailedErrorDescription
                );
            }

            throw new GatewayException(String.format("Status Code: %s - %s", response.getStatusCode(), response.getRawResponse()));
        }

        return response.getRawResponse();
    }

    private String getEntryMode(AuthorizationBuilder builder) {
        IPaymentMethod builderPaymentMethod = builder.getPaymentMethod();
        if (builder.getPaymentMethod() instanceof ICardData) {
            ICardData card = (ICardData) builder.getPaymentMethod();
            if (card.isReaderPresent())
                return card.isCardPresent() ? "MANUAL" : "IN_APP";
            else
                return card.isCardPresent() ? "MANUAL" : "ECOM";
        } else if (builderPaymentMethod instanceof ITrackData) {
            ITrackData track = (ITrackData) builder.getPaymentMethod();
            if (builder.getTagData() != null) {
                return (track.getEntryMethod() == EntryMethod.Swipe) ? "CHIP" : "CONTACTLESS_CHIP";
            } else if (builder.hasEmvFallbackData()) {
                return "CONTACTLESS_SWIPE";
            }
            return "SWIPE";
        }
        return "ECOM";
    }

    private String getCaptureMode(AuthorizationBuilder builder) {
        if (builder.isMultiCapture())
            return "MULTIPLE";
        else if (builder.getTransactionType() == TransactionType.Auth)
            return "LATER";

        return "AUTO";
    }

    private String getCvvIndicator(ICardData cardData) {
        switch (cardData.getCvnPresenceIndicator()) {
            case Present:
                return "PRESENT";
            case Illegible:
                return "ILLEGIBLE";
            default:
                return "NOT_PRESENT";
        }
    }

    private String getCvvIndicator(EmvChipCondition emvChipCondition) {
        if(emvChipCondition == null) return "";
        switch (emvChipCondition) {
            case ChipFailPreviousSuccess:
                return "PREV_SUCCESS";
            case ChipFailPreviousFail:
                return "PREV_FAILED";
            default:
                return "UNKNOWN";
        }
    }


    public Transaction processAuthorization(AuthorizationBuilder builder) throws ApiException {

        addAccessTokenHeader();

        JsonDoc paymentMethod =
                new JsonDoc()
                        .set("entry_mode", getEntryMode(builder)); // [MOTO, ECOM, IN_APP, CHIP, SWIPE, MANUAL, CONTACTLESS_CHIP, CONTACTLESS_SWIPE]

        IPaymentMethod builderPaymentMethod = builder.getPaymentMethod();
        TransactionType builderTransactionType = builder.getTransactionType();
        Address builderBillingAddress = builder.getBillingAddress();

        // CardData
        if (builderPaymentMethod instanceof ICardData) {
            ICardData cardData = (ICardData) builderPaymentMethod;

            JsonDoc card = new JsonDoc();
            card.set("number", cardData.getNumber());
            card.set("expiry_month", StringUtils.padLeft(cardData.getExpMonth().toString(), 2, '0'));
            card.set("expiry_year", cardData.getExpYear().toString().substring(2, 4));
            //card.set("track", "");
            card.set("tag", builder.getTagData());
            card.set("cvv", cardData.getCvn());
            card.set("avs_address", builderBillingAddress != null ? builderBillingAddress.getStreetAddress1() : "");
            card.set("avs_postal_code", builderBillingAddress != null ? builderBillingAddress.getPostalCode() : "");
            card.set("funding", builderPaymentMethod.getPaymentMethodType() == PaymentMethodType.Debit ? "DEBIT" : "CREDIT"); // [DEBIT, CREDIT]
            card.set("authcode", builder.getOfflineAuthCode());
            //card.set("brand_reference", "")

            if (builder.getEmvChipCondition() != null) {
                card.set("chip_condition", (builder.getEmvChipCondition() == EmvChipCondition.ChipFailPreviousSuccess) ? "PREV_SUCCESS" :  "PREV_FAILED"); // [PREV_SUCCESS, PREV_FAILED]
            }
            if ( (cardData.getCvnPresenceIndicator() == CvnPresenceIndicator.Present ) || ( cardData.getCvnPresenceIndicator() == CvnPresenceIndicator.Illegible ) || (cardData.getCvnPresenceIndicator() == CvnPresenceIndicator.NotOnCard) ) {
                card.set("cvv_indicator", getCvvIndicator(cardData)); // [ILLEGIBLE, NOT_PRESENT, PRESENT]
            }

            paymentMethod.set("card", card);
        }

        // TrackData
        else if (builderPaymentMethod instanceof ITrackData) {
            ITrackData track = (ITrackData) builderPaymentMethod;

            JsonDoc card =
                    new JsonDoc()
                            .set("track", track.getValue())
                            .set("tag", builder.getTagData())
                            //.set("cvv", cardData.getCvn())
                            //.set("cvv_indicator", "") // [ILLEGIBLE, NOT_PRESENT, PRESENT]
                            .set("avs_address", builderBillingAddress != null ? builderBillingAddress.getStreetAddress1() : "")
                            .set("avs_postal_code", builderBillingAddress != null ? builderBillingAddress.getPostalCode() : "")
                            .set("authcode", builder.getOfflineAuthCode());
                            //.set("brand_reference", "")

            if (builderTransactionType == TransactionType.Sale) {
                card.set("number", track.getPan());
                card.set("expiry_month", track.getExpiry().substring(2, 4));
                card.set("expiry_year", track.getExpiry().substring(0, 2));
                card.set("chip_condition", getCvvIndicator(builder.getEmvChipCondition())); // [PREV_SUCCESS, PREV_FAILED]
                card.set("funding", builderPaymentMethod.getPaymentMethodType() == PaymentMethodType.Debit ? "DEBIT" : "CREDIT"); // [DEBIT, CREDIT]
            }

            paymentMethod.set("card", card);
        }

        // Pin Block
        if (builderPaymentMethod instanceof IPinProtected) {
            paymentMethod.get("card").set("pin_block", ((IPinProtected) builderPaymentMethod).getPinBlock());
        }

        // Authentication
        if (builderPaymentMethod instanceof CreditCardData) {
            CreditCardData creditCardData = (CreditCardData) builderPaymentMethod;
            paymentMethod.set("name", creditCardData.getCardHolderName());

            ThreeDSecure secureEcom = creditCardData.getThreeDSecure();
            if (secureEcom != null) {
                JsonDoc authentication = new JsonDoc()
                        .set("xid", secureEcom.getXid())
                        .set("cavv", secureEcom.getCavv())
                        .set("eci", secureEcom.getEci());
                        //.set("mac", ""); //A message authentication code submitted to confirm integrity of the request.

                paymentMethod.set("authentication", authentication);
            }
        }

        // Encryption
        if (builderPaymentMethod instanceof IEncryptable) {
            IEncryptable encryptable = (IEncryptable) builderPaymentMethod;
            EncryptionData encryptionData = encryptable.getEncryptionData();

            if (encryptionData != null) {
                JsonDoc encryption =
                        new JsonDoc()
                                .set("version", encryptionData.getVersion());

                if (!StringUtils.isNullOrEmpty(encryptionData.getKtb())) {
                    encryption.set("method", "KTB");
                    encryption.set("info", encryptionData.getKtb());
                } else if (!StringUtils.isNullOrEmpty(encryptionData.getKsn())) {
                    encryption.set("method", "KSN");
                    encryption.set("info", encryptionData.getKsn());
                }

                if (encryption.has("info")) {
                    paymentMethod.set("encryption", encryption);
                }
            }
        }

        JsonDoc data = new JsonDoc()
                .set("account_name", "Transaction_Processing")
                .set("type", builderTransactionType == Refund ? "REFUND" : "SALE") // [SALE, REFUND]
                .set("channel", getGatewayConfig().getChannel()) // [CP, CNP]
                .set("capture_mode", getCaptureMode(builder)) // [AUTO, LATER, MULTIPLE]
                //.set("remaining_capture_count", "") // Pending Russell
                .set("authorization_mode", builder.isAllowPartialAuth() ? "PARTIAL" : "WHOLE") // [PARTIAL, WHOLE]
                .set("amount", StringUtils.toNumeric(builder.getAmount()))
                .set("currency", builder.getCurrency())
                .set("reference", isNullOrEmpty(builder.getClientTransactionId()) ? java.util.UUID.randomUUID().toString() : builder.getClientTransactionId())
                .set("description", builder.getDescription())
                .set("order_reference", builder.getOrderId())
                //.set("initiator", "") // [PAYER, MERCHANT] //default to PAYER
                .set("gratuity_amount", builder.getGratuity())
                .set("cashback_amount", builder.getCashBackAmount())
                .set("surcharge_amount", builder.getSurchargeAmount())
                .set("convenience_amount", builder.getConvenienceAmount())
                .set("country", (builderBillingAddress != null) ? builderBillingAddress.getCountry() : "US")
                //.set("language", language)
                .set("ip_address", builder.getCustomerIpAddress())
                //.set("site_reference", "") //
                .set("payment_method", paymentMethod);

        String rawResponse = doTransaction("POST", "/transactions", data.toString());

        return mapResponse(rawResponse);
    }

    public Transaction manageTransaction(ManagementBuilder builder) throws GatewayException {
        String response = null;

        JsonDoc data = new JsonDoc()
                .set("amount", StringUtils.toNumeric(builder.getAmount()));

        TransactionType builderTransactionType = builder.getTransactionType();
        if (builderTransactionType == TransactionType.Capture) {
            data.set("gratuity_amount", builder.getGratuity());
            response = doTransaction("POST", "/transactions/" + builder.getTransactionId() + "/capture", data.toString());
        }
        else if (builderTransactionType == TransactionType.Refund) {
            response = doTransaction("POST", "/transactions/" + builder.getTransactionId() + "/refund", data.toString());
        }
        else if (builderTransactionType == TransactionType.Reversal) {
            response = doTransaction("POST", "/transactions/" + builder.getTransactionId() + "/reversal", data.toString());
        }
        return mapResponse(response);
    }

    private TransactionSummary doGetTransactionDetail(final String transactionId) throws ApiException {

        String rawResponse = doTransaction("GET", "/transactions" + "/" + transactionId, null, null);

        return mapTransactionSummary(rawResponse);
    }

    private TransactionSummary mapTransactionSummary(String rawResponse) {

        final JsonDoc doc = JsonDoc.parse(rawResponse);
        final JsonDoc paymentMethod = doc.get("payment_method");
        final JsonDoc card = paymentMethod.get("card");

        TransactionSummary summary = new TransactionSummary();

        //TODO: Map all transaction properties
        summary.setTransactionId(doc.getString("id"));
        summary.setTransactionDate(TIMESTAMP_DTF.parseDateTime(doc.getString("time_created")));
        summary.setStatus(doc.getString("status"));
        summary.setTransactionType(doc.getString("type"));
        // ?? = doc.getString("channel")
        summary.setAmount(new BigDecimal(doc.getString("amount")));
        summary.setCurrency(doc.getString("currency"));
        summary.setReferenceNumber(doc.getString("reference"));
        // ?? = DATE_FORMATTER.parseDateTime(doc.getString("time_created_reference"))
        summary.setBatchId(doc.getString("batch_id"));
        summary.setCountry(doc.getString("country"));
        // ?? = doc.getString("action_create_id")
        summary.setOriginalTransactionId(doc.getString("parent_resource_id"));
        summary.setGatewayResponseMessage(paymentMethod.getString("message"));
        summary.setEntryMode(paymentMethod.getString("entry_mode"));
        //?? = paymentMethod.getString("name")
        summary.setCardType(card.getString("brand"));
        summary.setAuthCode(card.getString("authcode"));
        //?? = card.getString("brand_reference")
        summary.setAquirerReferenceNumber(card.getString("arn"));
        summary.setMaskedCardNumber(card.getString("masked_number_first6last4"));

        return summary;
    }

    void addQueryParam(HashMap<String, String> queryParams, String name, String value) {
        if (!isNullOrEmpty(name) && !isNullOrEmpty(value)) {
            queryParams.put(name, value);
        }
    }

    String getValueIfNotNull(IStringConstant obj) {
        return (obj != null) ? obj.getValue() : "";
    }

    String getDateIfNotNull(Date obj) {
        return (obj != null) ? DATE_SDF.format(obj) : "";
    }

    private TransactionSummaryList doGetTransactions(ReportBuilder builder) throws ApiException {

        HashMap<String, String> queryParams = new HashMap<String, String>();

        if (builder instanceof TransactionReportBuilder) {

            final TransactionReportBuilder<TransactionSummary> trb = (TransactionReportBuilder<TransactionSummary>) builder;
            final SearchCriteriaBuilder<TransactionSummary> scb = trb.getSearchBuilder();

            addQueryParam(queryParams, "ID", trb.getTransactionId());
            addQueryParam(queryParams, "PAGE", String.valueOf(trb.getPage()));
            addQueryParam(queryParams, "PAGE_SIZE", String.valueOf(trb.getPageSize()));
            addQueryParam(queryParams, "ORDER_BY", getValueIfNotNull(trb.getOrderProperty()));
            addQueryParam(queryParams, "ORDER", getValueIfNotNull(trb.getOrderDirection()));
            addQueryParam(queryParams, "ACCOUNT_NAME", scb.getAccountName());
            addQueryParam(queryParams, "ID", trb.getTransactionId());
            addQueryParam(queryParams, "BRAND", scb.getCardBrand());
            addQueryParam(queryParams, "MASKED_NUMBER_FIRST6LAST4", scb.getMaskedCardNumber());
            addQueryParam(queryParams, "ARN", scb.getAquirerReferenceNumber());
            addQueryParam(queryParams, "BRAND_REFERENCE", scb.getBrandReference());
            addQueryParam(queryParams, "AUTHCODE", scb.getAuthCode());
            addQueryParam(queryParams, "REFERENCE", scb.getReferenceNumber());
            addQueryParam(queryParams, "STATUS", getValueIfNotNull(scb.getTransactionStatus()));
            addQueryParam(queryParams, "FROM_TIME_CREATED", (trb.getStartDate() != null) ? DATE_SDF.format(trb.getStartDate()) : DATE_SDF.format(new Date()));
            addQueryParam(queryParams, "TO_TIME_CREATED", getDateIfNotNull(trb.getEndDate()));
            addQueryParam(queryParams, "DEPOSIT_ID", scb.getDepositReference());
            addQueryParam(queryParams, "FROM_DEPOSIT_TIME_CREATED", getDateIfNotNull(scb.getStartDepositDate()));
            addQueryParam(queryParams, "TO_DEPOSIT_TIME_CREATED", getDateIfNotNull(scb.getEndDepositDate()));
            addQueryParam(queryParams, "FROM_BATCH_TIME_CREATED", getDateIfNotNull(scb.getStartBatchDate()));
            addQueryParam(queryParams, "TO_BATCH_TIME_CREATED", getDateIfNotNull(scb.getEndBatchDate()));
            addQueryParam(queryParams, "SYSTEM.MID", scb.getMerchantId());
            // SYSTEM.HIERARCHY ??
        }

        String rawResponse = doTransaction("GET", "/transactions", null, queryParams);
        return mapGetTransactions(rawResponse);
    }

    public Transaction doVerifyTransaction(AuthorizationBuilder builder) throws ApiException {
        JsonDoc paymentMethod = new JsonDoc()
                //.set("first_name", "")
                //.set("last_name", "")
                .set("entry_mode", "ECOM") // [MOTO, ECOM, IN_APP, CHIP, SWIPE, MANUAL, CONTACTLESS_CHIP, CONTACTLESS_SWIPE]
                .set("id", "PMT_31087d9c-e68c-4389-9f13-39378e166ea5");

        if (builder.getPaymentMethod() instanceof ICardData) {
            ICardData cardData = (ICardData) builder.getPaymentMethod();

            String expiryMonth = isNullOrEmpty(cardData.getExpMonth().toString()) ? cardData.getExpMonth().toString() : "";
            String expiryYear = isNullOrEmpty(cardData.getExpYear().toString()) ? cardData.getExpYear().toString() : "";

            JsonDoc card = new JsonDoc()
                    .set("number", cardData.getNumber())
                    .set("expiry_month", expiryMonth)
                    .set("expiry_year", expiryYear)
                    //.set("track", "")
                    //.set("tag", "")
                    //.set("chip_condition", "") // [PREV_SUCCESS, PREV_FAILED]
                    //.set("pin_block", "")
                    .set("cvv", cardData.getCvn())
                    .set("cvv_indicator", "PRESENT") // [ILLEGIBLE, NOT_PRESENT, PRESENT]
                    .set("avs_address", "Flat 123")
                    .set("avs_postal_code", "50001");
            //.set("funding", "") // [DEBIT, CREDIT]
            //.set("authcode", "")
            //.set("brand_reference", "")

            paymentMethod.set("card", card);
        }

        JsonDoc request = new JsonDoc()
                .set("account_name", "Transaction_Processing")
                .set("type", "SALE") // [SALE, REFUND]
                .set("channel", "CNP") // [CP, CNP]
                .set("capture_mode", "AUTO") // [AUTO, LATER, MULTIPLE]
                //.set("remaining_capture_count", "")
                //.set("authorization_mode", "") // [PARTIAL, WHOLE]
                .set("amount", builder.getAmount().toString())
                .set("currency", builder.getCurrency())
                .set("reference", "GENERATE ID HER")
                //.set("description", "")
                //.set("order_reference", "")
                //.set("initiator", "") // [PAYER, MERCHANT]
                .set("gratuity_amount", builder.getGratuity().toString())
                .set("cashback_amount", builder.getCashBackAmount().toString())
                //.set("surcharge_amount", "")
                .set("convenience_amount", builder.getConvenienceAmount().toString())
                .set("country", "US")
                //.set("language", "")
                //.set("ip_address", "")
                //.set("site_reference", "")
                .set("payment_method", paymentMethod);

        String rawResponse = doTransaction("Post", "/ucp/transactions", request.toString());

        return mapResponse(rawResponse);
    }

    public Transaction doSaleTransaction(AuthorizationBuilder builder) throws ApiException {
        JsonDoc paymentMethod = new JsonDoc()
                //.set("first_name", "")
                //.set("last_name", "")
                .set("entry_mode", "ECOM") // [MOTO, ECOM, IN_APP, CHIP, SWIPE, MANUAL, CONTACTLESS_CHIP, CONTACTLESS_SWIPE]
                .set("id", "PMT_31087d9c-e68c-4389-9f13-39378e166ea5");

        if (builder.getPaymentMethod() instanceof ICardData) {
            ICardData cardData = (ICardData) builder.getPaymentMethod();

            String expiryMonth = isNullOrEmpty(cardData.getExpMonth().toString()) ? cardData.getExpMonth().toString() : "";
            String expiryYear = isNullOrEmpty(cardData.getExpYear().toString()) ? cardData.getExpYear().toString() : "";

            JsonDoc card = new JsonDoc()
                    .set("number", cardData.getNumber())
                    .set("expiry_month", expiryMonth)
                    .set("expiry_year", expiryYear)
                    //.set("track", "")
                    //.set("tag", "")
                    //.set("chip_condition", "") // [PREV_SUCCESS, PREV_FAILED]
                    //.set("pin_block", "")
                    .set("cvv", cardData.getCvn())
                    .set("cvv_indicator", "PRESENT") // [ILLEGIBLE, NOT_PRESENT, PRESENT]
                    .set("avs_address", "Flat 123")
                    .set("avs_postal_code", "50001");
            //.set("funding", "") // [DEBIT, CREDIT]
            //.set("authcode", "")
            //.set("brand_reference", "")

            paymentMethod.set("card", card);
        }

        JsonDoc request = new JsonDoc()
                .set("account_name", "Transaction_Processing")
                .set("type", "SALE") // [SALE, REFUND]
                .set("channel", "CNP") // [CP, CNP]
                .set("capture_mode", "AUTO") // [AUTO, LATER, MULTIPLE]
                //.set("remaining_capture_count", "")
                //.set("authorization_mode", "") // [PARTIAL, WHOLE]
                .set("amount", StringUtils.toNumeric(builder.getAmount()))
                .set("currency", builder.getCurrency())
                .set("reference", "GENERATE ID HER")
                //.set("description", "")
                //.set("order_reference", "")
                //.set("initiator", "") // [PAYER, MERCHANT]
                .set("gratuity_amount", builder.getGratuity().toString())
                .set("cashback_amount", builder.getCashBackAmount().toString())
                //.set("surcharge_amount", "")
                .set("convenience_amount", builder.getConvenienceAmount().toString())
                .set("country", "US")
                //.set("language", "")
                //.set("ip_address", "")
                //.set("site_reference", "")
                .set("payment_method", paymentMethod);

        String rawResponse = doTransaction("Post", "/ucp/transactions", request.toString());

        return mapResponse(rawResponse);
    }

    private TransactionSummaryList mapGetTransactions(String rawResponse) {
        TransactionSummaryList transactionsList = new TransactionSummaryList();

        for (JsonDoc transaction : JsonDoc.parse(rawResponse).getEnumerator("transactions")) {
            transactionsList.add(hydrateTransactionSummary(transaction));
        }

        return transactionsList;
    }

    private TransactionSummary hydrateTransactionSummary(JsonDoc doc) {

        TransactionSummary summary = new TransactionSummary();

        //TODO: Map all transaction properties
        summary.setTransactionId(doc.getString("id"));
        summary.setTransactionDate(TIMESTAMP_DTF.parseDateTime(doc.getString("time_created")));
        summary.setTransactionStatus(doc.getString("status"));
        summary.setTransactionType(doc.getString("type"));
        // ?? = doc.getString("channel")
        summary.setAmount(doc.getDecimal("amount"));
        summary.setCurrency(doc.getString("currency"));
        summary.setReferenceNumber(doc.getString("reference"));
        // ?? = doc.GetString("time_created_reference")
        summary.setBatchSequenceNumber(doc.getString("batch_id"));
        summary.setCountry(doc.getString("country"));
        // ?? = doc.getString("action_create_id")
        summary.setOriginalTransactionId(doc.getString("parent_resource_id"));

        JsonDoc paymentMethod = doc.get("payment_method");
        JsonDoc card = paymentMethod.get("card");

        summary.setGatewayResponseMessage(paymentMethod.getString("message"));
        summary.setEntryMode(paymentMethod.getString("entry_mode"));
        // ?? = paymentMethod.getString("name")
        summary.setCardType(card.getString("brand"));
        summary.setAuthCode(card.getString("authcode"));
        // ?? = card.getString>("brand_reference"),
        summary.setAquirerReferenceNumber(card.getString("arn"));
        summary.setMaskedCardNumber(card.getString("masked_number_first6last4"));

        return summary;
    }

    private Transaction mapResponse(String rawResponse) {
        Transaction transaction = new Transaction();

        if (!StringUtils.isNullOrEmpty(rawResponse)) {
            JsonDoc jsonResponse = JsonDoc.parse(rawResponse);

            transaction.setTransactionId(jsonResponse.getString("id"));
            transaction.setBalanceAmount(jsonResponse.getDecimal("amount"));
            transaction.setTimestamp(jsonResponse.getString("time_created"));
            transaction.setResponseMessage(jsonResponse.getString("status"));
            transaction.setReferenceNumber(jsonResponse.getString("reference"));

            BatchSummary batchSummary = new BatchSummary();
            batchSummary.setSequenceNumber(jsonResponse.getString("batch_id"));
            transaction.setBatchSummary(batchSummary);

            transaction.setResponseCode(jsonResponse.get("action").getString(("result_code")));
        }

        return transaction;
    }

//    public Transaction manageTransaction(ManagementBuilder builder) throws ApiException {
//        String response = null;
//        JsonDoc data =
//                new JsonDoc()
//                        .set("amount", StringUtils.toNumeric(builder.getAmount()));
//
//        if (builder.getTransactionType() == Capture) {
//            data.set("gratuity_amount", builder.getGratuity());
//            response = doTransaction("POST", "/transactions/" + builder.getTransactionId() + "/capture", data.toString());
//        } else if (builder.getTransactionType() == Refund) {
//            response = doTransaction("POST", "/transactions/" + builder.getTransactionId() + "/refund", data.toString());
//        } else if (builder.getTransactionType() == Reversal) {
//            response = doTransaction("POST", "/transactions/" + builder.getTransactionId() + "/reversal", data.toString());
//        }
//
//        return mapResponse(response);
//    }

    @SuppressWarnings("unchecked")
    public <T> T processReport(ReportBuilder<T> builder, Class<T> clazz) throws ApiException {

        addAccessTokenHeader();

        if (builder instanceof TransactionReportBuilder) {

            TransactionReportBuilder<TransactionSummary> trb = (TransactionReportBuilder<TransactionSummary>) builder;

            switch (builder.getReportType()) {
                case FindTransactions:
                    return (T) doGetTransactions(trb);
                case TransactionDetail:
                    return (T) doGetTransactionDetail(trb.getTransactionId());
                default:
                    throw new UnsupportedTransactionException();
            }
        }

        throw new UnsupportedTransactionException();
    }

    public String serializeRequest(AuthorizationBuilder builder) {
        throw new NotImplementedException();
    }

    public NetworkMessageHeader sendKeepAlive() {
        throw new NotImplementedException();
    }

    public boolean supportsHostedPayments() {
        return true;
    }

}