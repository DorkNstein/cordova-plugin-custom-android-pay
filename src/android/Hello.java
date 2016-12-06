package com.example.plugin;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.LineItem;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.MaskedWalletRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentMethodTokenizationType;
import com.google.android.gms.wallet.WalletConstants;
import com.google.android.gms.wallet.fragment.SupportWalletFragment;
import com.google.android.gms.wallet.fragment.WalletFragmentInitParams;
import com.google.android.gms.wallet.fragment.WalletFragmentMode;
import com.google.android.gms.wallet.fragment.WalletFragmentOptions;
import com.google.android.gms.wallet.fragment.WalletFragmentStyle;

import java.util.ArrayList;
import java.util.List;


public class Hello extends CordovaPlugin {

    private String mEnv;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        if (action.equals("greet")) {

            String name = data.getString(0);
            String message = "Hello, " + name;
            createMaskedWalletRequest("0.01", callbackContext);
            // callbackContext.success(message);

            return true;

        } else {
            callbackContext.error("Wrong message!");
            return false;

        }
    }

     /**
     * Create the Masked Wallet request. Note that the Tokenization Type is set to
     * {@code NETWORK_TOKEN} and the {@code publicKey} parameter is set to the public key
     * that was created by First Data.
     *
     * @param amount    The amount the user entered
     * @return  A Masked Wallet request object
     */
    private MaskedWalletRequest createMaskedWalletRequest(String amount, CallbackContext callbackContext) {
        mEnv = getIntent().getStringExtra(Constants.EXTRA_ENV);
        MaskedWalletRequest.Builder builder = MaskedWalletRequest.newBuilder()
                .setMerchantName(Constants.MERCHANT_NAME)
                .setPhoneNumberRequired(true)
                .setShippingAddressRequired(true)
                .setCurrencyCode(Constants.CURRENCY_CODE_USD)
                .setEstimatedTotalPrice(amount)
                        // Create a Cart with the current line items. Provide all the information
                        // available up to this point with estimates for shipping and tax included.
                .setCart(Cart.newBuilder()
                        .setCurrencyCode(Constants.CURRENCY_CODE_USD)
                        .setTotalPrice(amount)
                        .setLineItems(buildLineItems(amount))
                        .build());

        //  Set tokenization type and First Data issued public key
        PaymentMethodTokenizationParameters mPaymentMethodParameters = PaymentMethodTokenizationParameters.newBuilder()
                .setPaymentMethodTokenizationType(PaymentMethodTokenizationType.NETWORK_TOKEN)
                .addParameter("publicKey", EnvData.getProperties(mEnv).getPublicKey())
                .build();
        builder.setPaymentMethodTokenizationParameters(mPaymentMethodParameters);
        return builder.build();
    }

    /**
     * Invoked when the user confirms the transaction.
     *
     * @param requestCode Request Code
     * @param resultCode  Result of request execution
     * @param data        Intent
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // retrieve the error code, if available
        int errorCode = -1;
        if (data != null) {
            errorCode = data.getIntExtra(WalletConstants.EXTRA_ERROR_CODE, -1);
        }

        switch (requestCode) {
            case REQUEST_CODE_RESOLVE_ERR:
                if (resultCode == Activity.RESULT_OK) {
                    mGoogleApiClient.connect();
                } else {
                    handleUnrecoverableGoogleWalletError(errorCode);
                }
                break;
            case REQUEST_CODE_RESOLVE_LOAD_FULL_WALLET:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        //  Transaction confirmed - Full Wallet is finally here
                        if (data.hasExtra(WalletConstants.EXTRA_FULL_WALLET)) {
                            FullWallet fullWallet =
                                    data.getParcelableExtra(WalletConstants.EXTRA_FULL_WALLET);
                            // the full wallet can now be used to process the customer's payment
                            // send the wallet info up to server to process, and to get the result
                            // for sending a transaction status
                            fetchTransactionStatus(fullWallet);
                        } else if (data.hasExtra(WalletConstants.EXTRA_MASKED_WALLET)) {
                            // re-launch the activity with new masked wallet information
                            mMaskedWallet =
                                    data.getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                            mActivityLaunchIntent.putExtra(Constants.EXTRA_MASKED_WALLET,
                                    mMaskedWallet);
                            startActivity(mActivityLaunchIntent);
                        }
                        break;
                    case Activity.RESULT_CANCELED:
                        // nothing to do here
                        break;
                    default:
                        handleError(errorCode);
                        break;
                }
                break;
        }
    }

    /**
     * For unrecoverable Google Wallet errors, send the user back to the checkout page to handle the
     * problem.
     *
     * @param errorCode Error code
     */
    protected void handleUnrecoverableGoogleWalletError(int errorCode) {

        Toast.makeText(getActivity(),
                String.format("Google Wallet Error %d", errorCode), Toast.LENGTH_LONG).show();
    }

    private void handleError(int errorCode) {
        if (checkAndRetryFullWallet(errorCode)) {
            // handled by retrying
            return;
        }
        switch (errorCode) {
            case WalletConstants.ERROR_CODE_SPENDING_LIMIT_EXCEEDED:
                // may be recoverable if the user tries to lower their charge
                // take the user back to the checkout page to try to handle
            case WalletConstants.ERROR_CODE_INVALID_PARAMETERS:
            case WalletConstants.ERROR_CODE_AUTHENTICATION_FAILURE:
            case WalletConstants.ERROR_CODE_BUYER_ACCOUNT_ERROR:
            case WalletConstants.ERROR_CODE_MERCHANT_ACCOUNT_ERROR:
            case WalletConstants.ERROR_CODE_SERVICE_UNAVAILABLE:
            case WalletConstants.ERROR_CODE_UNSUPPORTED_API_VERSION:
            case WalletConstants.ERROR_CODE_UNKNOWN:
            default:
                // unrecoverable error
                // take the user back to the checkout page to handle these errors
                handleUnrecoverableGoogleWalletError(errorCode);
        }
    }


    /**
     * Create a Full Wallet request. We need to provide the line items and amount.
     *
     * @param googleTransactionId The transaction Id from the {@link MaskedWallet}
     * @return Full Wallet request object
     */
    public FullWalletRequest createFullWalletRequest(String googleTransactionId) {

        List<LineItem> lineItems = buildLineItems(mAmount);

        return FullWalletRequest.newBuilder()
                .setGoogleTransactionId(googleTransactionId)
                .setCart(Cart.newBuilder()
                        .setCurrencyCode(Constants.CURRENCY_CODE_USD)
                        .setTotalPrice(mAmount)
                        .setLineItems(lineItems)
                        .build())
                .build();
    }

    /**
     * Issue a request to load the full wallet.
     */
    private void getFullWallet() {
        FullWalletRequest fullWalletRequest = createFullWalletRequest(
                mMaskedWallet.getGoogleTransactionId());
        Wallet.Payments.loadFullWallet(mGoogleApiClient,
                fullWalletRequest,
                REQUEST_CODE_RESOLVE_LOAD_FULL_WALLET);
    }

    /**
     * Here the client should connect to First Data, process the credit card/instrument
     * and get back a status indicating whether charging the card was successful or not
     */
    private void fetchTransactionStatus(FullWallet fullWallet) {

        // Log payment method token, if it exists
        PaymentMethodToken token = fullWallet.getPaymentMethodToken();
        if (token != null) {
            // getToken returns a JSON object as a String.  Replace newlines to make LogCat output
            // nicer.  The 'id' field of the object contains the Stripe token we are interested in.
            Log.d(TAG, "PaymentMethodToken:" + token.getToken().replace('\n', ' '));
        }

        sendRequestToFirstData(fullWallet, mEnv);
    }



    /**
     * Send a request to the First Data server to process the payment. The REST request
     * includes HTTP headers that identify the developer and the merchant issuing the request:
     * <ul>
     * <li>{@code apikey} - identifies the developer</li>
     * <li>{@code token} - identifies the merchant</li>
     * </ul>
     * The values for the two headers are provided by First Data.
     * <p>
     * The token created by Android Pay is extracted from the FullWallet object. The token
     * is in JSON format and consists of the following fields:
     * <ul>
     * <li>{@code encryptedMessage} - the encrypted details of the transaction</li>
     * <li>{@code ephemeralPublicKey} - the key used, together with the key pair issued
     * by First Data, to decrypt of the transaction detail</li>
     * <li>{@code tag} - a signature field</li>
     * </ul>
     * These items, along with a {@code PublicKeyHash} that is used to identify the
     * key pair provided by First data, are used
     * to create the transaction payload. The payload is sent to the First Data servers
     * for execution.
     * </p>
     *
     * @param fullWallet Full wallet object
     * @param env        First Data environment to be used
     */
    public void sendRequestToFirstData(final FullWallet fullWallet, String env) {
        try {
            //  Parse the Json token retrieved from the Full Wallet.
            String tokenJSON = fullWallet.getPaymentMethodToken().getToken();
            final JSONObject jsonObject = new JSONObject(tokenJSON);

            String encryptedMessage = jsonObject.getString("encryptedMessage");
            String publicKey = jsonObject.getString("ephemeralPublicKey");
            String signature = jsonObject.getString("tag");

            //  Create a First Data Json request
            JSONObject requestPayload = getRequestPayload(encryptedMessage, signature, publicKey);
            final String payloadString = requestPayload.toString();
            final Map<String, String> HMACMap = computeHMAC(payloadString);


            StringRequest request = new StringRequest(
                    Request.Method.POST,
                    getUrl(env),
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            //  request completed - launch the response activity
//                            JSONObject obj = new JSONObject(response);
                            startResponseActivity("SUCCESS", response);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            startResponseActivity("ERROR", formatErrorResponse(error));
                        }
                    }) {

                @Override
                public String getBodyContentType() {
                    return "application/json";
                }

                @Override
                public byte[] getBody() {
                    try {
                        return payloadString.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        return null;
                    }
                }

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headerMap = new HashMap<>(HMACMap);
                    //  First data issued APIKey identifies the developer
                    headerMap.put("apikey", EnvData.getProperties(mEnv).getApiKey());
                    //  First data issued token identifies the merchant
                    headerMap.put("token", EnvData.getProperties(mEnv).getToken());

                    return headerMap;
                }
            };

            request.setRetryPolicy(new DefaultRetryPolicy(0, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            RequestQueue queue = Volley.newRequestQueue(getActivity());
            queue.add(request);
        } catch (JSONException e) {
            Toast.makeText(getActivity(), "Error parsing JSON payload", Toast.LENGTH_LONG).show();
        }
    }

    private void startResponseActivity(String status, String message) {
        Intent intent = ResponseActivity.newIntent(getActivity(), status, message);
        startActivity(intent);
    }

    /**
     * Convert JSON object into a String.
     * @param jo    JSON object
     * @return  String representation of the object
     */
    private String formatResponse(JSONObject jo) {
        try {
            return jo.toString(2);
        } catch (JSONException e) {
            return "Invalid JSON response";
        }
    }

    private String formatErrorResponse(VolleyError ve) {
        return String.format("Status code = %d%nError message = %s",
                ve.networkResponse.statusCode, new String(ve.networkResponse.data));
    }

    /**
     * Select the appropriate First Data server for the environment.
     *
     * @param env Environment
     * @return URL
     */
    private static String getUrl(String env) {
        return EnvData.getProperties(env).getUrl();
    }

    /**
     * Format the amount to decimal without the decimal point as required by First Data servers.
     * For example, "25.30" is converted into "2530"
     *
     * @param amount Amount with decimal point
     * @return Amount without the decimal point
     */
    private String formatAmount(String amount) {
        BigDecimal a = new BigDecimal(amount);
        BigDecimal scaled = a.setScale(2, BigDecimal.ROUND_HALF_EVEN);
        return scaled.toString().replace(".", "");
    }

    /**
     * Create First Data request payload
     *
     * @param data               Encrypted transaction detail created by Android Pay
     * @param signature          The data signature create by Android Pay
     * @param ephemeralPublicKey Ephemeral public key created by Android Pay
     * @return JSON Object containg the request payload
     */
    private JSONObject getRequestPayload(String data, String signature, String ephemeralPublicKey) {
        Map<String, Object> pm = new HashMap<>();
        pm.put("merchant_ref", "orderid");
        pm.put("transaction_type", "purchase");
        pm.put("method", "3DS");
        pm.put("amount", formatAmount(mAmount));
        pm.put("currency_code", "USD");

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("ephemeralPublicKey", ephemeralPublicKey);
        //  First data issued Public Key Hash identifies the public key used to encrypt the data
        headerMap.put("publicKeyHash", EnvData.getProperties(mEnv).getPublicKeyHash());

        Map<String, Object> ccmap = new HashMap<>();
        ccmap.put("type", "G");             //  Identify the request as Android Pay request
        ccmap.put("data", data);
        ccmap.put("signature", signature);
        ccmap.put("header", headerMap);

        pm.put("3DS", ccmap);
        return new JSONObject(pm);
    }

    /**
     * Compute HMAC signature for the payload. The signature is based on the APIKey and the
     * APISecret provided by First Data. If the APISecret is not specified, the HMAC is
     * not computed.
     *
     * @param payload The payload as a String
     * @return Map of HTTP headers to be added to the request
     */
    private Map<String, String> computeHMAC(String payload) {

        EnvProperties ep = EnvData.getProperties(mEnv);
        String apiSecret = ep.getApiSecret();
        String apiKey = ep.getApiKey();
        String token = ep.getToken();

        Map<String, String> headerMap = new HashMap<>();
        if (apiSecret != null) {
            try {
                String authorizeString;
                String nonce = Long.toString(Math.abs(SecureRandom.getInstance("SHA1PRNG").nextLong()));
                String timestamp = Long.toString(System.currentTimeMillis());

                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
                mac.init(secretKey);

                StringBuilder buffer = new StringBuilder()
                        .append(apiKey)
                        .append(nonce)
                        .append(timestamp)
                        .append(token)
                        .append(payload);

                byte[] macHash = mac.doFinal(buffer.toString().getBytes("UTF-8"));
                authorizeString = Base64.encodeToString(bytesToHex(macHash).getBytes(), Base64.NO_WRAP);

                headerMap.put("nonce", nonce);
                headerMap.put("timestamp", timestamp);
                headerMap.put("Authorization", authorizeString);
            } catch (Exception e) {
                //  Nothing to do
            }
        }
        return headerMap;
    }

    private static String bytesToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    /**
     * Retries {@link Wallet#loadFullWallet(GoogleApiClient, FullWalletRequest, int)} if
     * {@link #MAX_FULL_WALLET_RETRIES} has not been reached.
     *
     * @return {@code true} if {@link FullWalletConfirmationButtonFragment#getFullWallet()} is retried,
     * {@code false} otherwise.
     */
    private boolean checkAndRetryFullWallet(int errorCode) {
        if ((errorCode == WalletConstants.ERROR_CODE_SERVICE_UNAVAILABLE ||
                errorCode == WalletConstants.ERROR_CODE_UNKNOWN) &&
                mRetryLoadFullWalletCount < MAX_FULL_WALLET_RETRIES) {
            mRetryLoadFullWalletCount++;
            getFullWallet();
            return true;
        }
        return false;
    }

    public static Intent newIntent(Context ctx, MaskedWallet maskedWallet, String amount, String env) {
        Intent intent = new Intent(ctx, ConfirmationActivity.class);
        intent.putExtra(Constants.EXTRA_MASKED_WALLET, maskedWallet);
        intent.putExtra(Constants.EXTRA_AMOUNT, amount);
        intent.putExtra(Constants.EXTRA_ENV, env);
        return intent;
    }

}


