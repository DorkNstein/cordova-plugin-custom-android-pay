package com.dorknstein.plugin;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.os.Parcelable;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.FullWallet;
import com.google.android.gms.wallet.FullWalletRequest;
import com.google.android.gms.wallet.LineItem;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.MaskedWalletRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentMethodTokenizationType;
import com.google.android.gms.wallet.PaymentMethodToken;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.fragment.SupportWalletFragment;
import com.google.android.gms.wallet.fragment.WalletFragmentInitParams;
import com.google.android.gms.wallet.fragment.WalletFragmentMode;
import com.google.android.gms.wallet.fragment.WalletFragmentOptions;
import com.google.android.gms.wallet.fragment.WalletFragmentStyle;

import java.util.ArrayList;
import java.util.List;


public class androidPay extends CordovaPlugin {

    public static final int WALLET_ENVIRONMENT = WalletConstants.ENVIRONMENT_TEST;

    public static final String MERCHANT_NAME = "Mendr";

    // Intent extra keys
    public static final String EXTRA_ITEM_ID = "com.dorkstein.plugin.androidpay.EXTRA_ITEM_ID";
    public static final String EXTRA_MASKED_WALLET = "com.dorkstein.plugin.androidpay.EXTRA_MASKED_WALLET";

    public static final String EXTRA_AMOUNT = "com.dorkstein.plugin.androidpay.EXTRA_AMOUNT";
    public static final String EXTRA_ENV = "com.dorkstein.plugin.androidpay.EXTRA_ENV";

    public static final String EXTRA_RESULT_STATUS = "com.dorkstein.plugin.androidpay.EXTRA_RESULT_STATUS";
    public static final String EXTRA_RESULT_MESSAGE = "com.dorkstein.plugin.androidpay.EXTRA_RESULT_MESSAGE";

    public static final String CURRENCY_CODE_USD = "USD";

    // values to use with KEY_DESCRIPTION
    public static final String DESCRIPTION_LINE_ITEM_SHIPPING = "Shipping";
    public static final String DESCRIPTION_LINE_ITEM_TAX = "Tax";

    //  Request Codes
    public static final int REQUEST_CODE_MASKED_WALLET = 1001;
    public static final int REQUEST_CODE_CHANGE_MASKED_WALLET = 1002;
     /**
     * Request code used when attempting to resolve issues with connecting to Google Play Services.
     * Only use this request code when calling {@link ConnectionResult#startResolutionForResult(
     *android.app.Activity, int)}.
     */
    public static final int REQUEST_CODE_RESOLVE_ERR = 1003;

    /**
     * Request code used when loading a full wallet. Only use this request code when calling
     * {@link Wallet#loadFullWallet(GoogleApiClient, FullWalletRequest, int)}.
     */
    public static final int REQUEST_CODE_RESOLVE_LOAD_FULL_WALLET = 1004;

    // Maximum number of times to try to connect to GoogleApiClient if the connection is failing
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MILLISECONDS = 3000;
    private static final int MESSAGE_RETRY_CONNECTION = 1010;
    private static final String KEY_RETRY_COUNTER = "KEY_RETRY_COUNTER";
    private static final String KEY_HANDLE_FULL_WALLET_WHEN_READY =
            "KEY_HANDLE_FULL_WALLET_WHEN_READY";

    // No. of times to retry loadFullWallet on receiving a ConnectionResult.INTERNAL_ERROR
    private static final int MAX_FULL_WALLET_RETRIES = 1;
    private static final String KEY_RETRY_FULL_WALLET_COUNTER = "KEY_RETRY_FULL_WALLET_COUNTER";

    private static final String TAG = "CheckoutActivity";
    private String mEnv;
    private GoogleApiClient mGoogleApiClient;
    public static final int FULL_WALLET_REQUEST_CODE = 889;
    private FullWallet mFullWallet;
    private MaskedWalletRequest maskedWalletRequest;
    private Context context;

    private CallbackContext callbackContext;

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        context = this.cordova.getActivity().getApplicationContext(); 
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                    .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                    .setTheme(WalletConstants.THEME_LIGHT)
                    .build())
                .build();
        if (action.equals("buy")) {
            String amount = data.getString(0);
            String message = "Hello, " + amount;
            // createMaskedWalletRequest("0.01", callbackContext);
            WalletFragmentStyle walletFragmentStyle = new WalletFragmentStyle()
                    .setBuyButtonText(WalletFragmentStyle.BuyButtonText.BUY_WITH)
                    .setBuyButtonAppearance(WalletFragmentStyle.BuyButtonAppearance.ANDROID_PAY_DARK)
                    .setBuyButtonWidth(WalletFragmentStyle.Dimension.MATCH_PARENT);

            WalletFragmentOptions walletFragmentOptions = WalletFragmentOptions.newBuilder()
                    .setEnvironment(Constants.ENVIRONMENT_TEST)
                    .setFragmentStyle(walletFragmentStyle)
                    .setTheme(WalletConstants.THEME_LIGHT)
                    .setMode(WalletFragmentMode.BUY_BUTTON)
                    .build();
                    
            mWalletFragment = SupportWalletFragment.newInstance(walletFragmentOptions);
            maskedWalletRequest = generateMaskedWalletRequest(amount);

            WalletFragmentInitParams.Builder startParamsBuilder = WalletFragmentInitParams.newBuilder()
                    .setMaskedWalletRequest(maskedWalletRequest)
                    .setMaskedWalletRequestCode(REQUEST_CODE_MASKED_WALLET)
                    .setAccountName('Mendr');

            mWalletFragment.initialize(startParamsBuilder.build());

            getSupportFragmentManager().beginTransaction()
                    .commit();

            Wallet.Payments.loadMaskedWallet(mGoogleApiClient, maskedWalletRequest, REQUEST_CODE_MASKED_WALLET);
            // callbackContext.success(message);
            return true;

        } else if (action.equals("isReady")){
            IsReadyToPayRequest req = IsReadyToPayRequest.newBuilder()
                .addAllowedCardNetwork(WalletConstants.CardNetwork.MASTERCARD)
                .addAllowedCardNetwork(WalletConstants.CardNetwork.VISA)
                .addAllowedCardNetwork(WalletConstants.CardNetwork.AMEX)
                .addAllowedCardNetwork(WalletConstants.CardNetwork.DISCOVER)
                .build();
            Wallet.Payments.isReadyToPay(mGoogleApiClient, req).setResultCallback(  
                new ResultCallback<BooleanResult>() {  
                @Override
                public void onResult(@NonNull BooleanResult booleanResult) {  
                    if (booleanResult.getStatus().isSuccess()) {  
                        if (booleanResult.getValue()) { 
                            callbackContext.success("ashow androuid buttons");
                            // Show Android Pay buttons alongside regular checkout button  
                            // ...  
                        } else { 
                            callbackContext.success("cannot be used");
                            // Hide Android Pay buttons, show a message that Android Pay  
                            // cannot be used yet, and display a traditional checkout button  
                            // ...  
                        }  
                    } else {  
                        // Error making isReadyToPay call  
                        Log.e(TAG, "isReadyToPay:" + booleanResult.getStatus()); 
                        callbackContext.error("error making isReadyToPay call") ;
                    }  
                }  
            });
            // callbackContext.success("still not done");
            return true;
        } else {
            callbackContext.error("Wrong message!");
            return false;

        }
    }

    private MaskedWalletRequest generateMaskedWalletRequest(String amount) {
        // This is just an example publicKey for the purpose of this codelab. 
        // To learn how to generate your own visit:
        // https://github.com/android-pay/androidpay-quickstart
        String publicKey = "BO39Rh43UGXMQy5PAWWe7UGWd2a9YRjNLPEEVe+zWIbdIgALcDcnYCuHbmrrzl7h8FZjl6RCzoi5/cDrqXNRVSo=";
        PaymentMethodTokenizationParameters parameters =
                PaymentMethodTokenizationParameters.newBuilder()
                        .setPaymentMethodTokenizationType(
                             PaymentMethodTokenizationType.NETWORK_TOKEN)
                        .addParameter("publicKey", publicKey)
                        .build();

        MaskedWalletRequest maskedWalletRequest =
                MaskedWalletRequest.newBuilder()
                        .setMerchantName("Mendr")
                        .setPhoneNumberRequired(true)
                        .setShippingAddressRequired(true)
                        .setCurrencyCode("USD")
                        .setCart(Cart.newBuilder()
                                .setCurrencyCode("USD")
                                .setTotalPrice(amount)
                                .addLineItem(LineItem.newBuilder()
                                        .setCurrencyCode("USD")
                                        .setDescription("Mendr Sticker")
                                        .setQuantity("1")
                                        .setUnitPrice("10.00")
                                        .setTotalPrice("10.00")
                                        .build())
                                .build())
                        .setEstimatedTotalPrice("15.00")
                        .setPaymentMethodTokenizationParameters(parameters)
                        .build();
        return maskedWalletRequest;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.callbackContext.success("we are here");
        switch (requestCode) {
            case REQUEST_CODE_MASKED_WALLET:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        maskedWalletRequest =  data
                                .getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                        Toast.makeText(context, "Got Masked Wallet", Toast.LENGTH_SHORT).show();
                        break;
                    case Activity.RESULT_CANCELED:
                        // The user canceled the operation
                        break;
                    case WalletConstants.RESULT_ERROR:
                        Toast.makeText(context, "An Error Occurred", Toast.LENGTH_SHORT).show();
                        // callbackContext.error("An Error Occurred!");
                        break;
                }
                break;
            case FULL_WALLET_REQUEST_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        mFullWallet = data
                                .getParcelableExtra(WalletConstants.EXTRA_FULL_WALLET);
                        // Show the credit card number
                        Toast.makeText(context, "Got Full Wallet, Done!", Toast.LENGTH_SHORT).show();
                        // callbackContext.success(mFullWallet.getPaymentMethodToken());
                        break;
                    case WalletConstants.RESULT_ERROR:
                        Toast.makeText(context, "An Error Occurred", Toast.LENGTH_SHORT).show();
                        // callbackContext.error("An Error Occurred during Full Wallet Request!");
                        break;
                }
                break;    
        }
    }

    private FullWalletRequest generateFullWalletRequest(String googleTransactionId) {
      FullWalletRequest fullWalletRequest = FullWalletRequest.newBuilder()
          .setGoogleTransactionId(googleTransactionId)
          .setCart(Cart.newBuilder()
              .setCurrencyCode("USD")
              .setTotalPrice("10.10")
              .addLineItem(LineItem.newBuilder()
                  .setCurrencyCode("USD")
                  .setDescription("Mendr Sticker")
                  .setQuantity("1")
                  .setUnitPrice("10.00")
                  .setTotalPrice("10.00")
                  .build())
              .addLineItem(LineItem.newBuilder()
                  .setCurrencyCode("USD")
                  .setDescription("Tax")
                  .setRole(LineItem.Role.TAX)
                  .setTotalPrice(".10")
                  .build())
              .build())
          .build();
      return fullWalletRequest;
    }
}
