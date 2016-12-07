package com.dorknstein.plugin;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import java.util.ArrayList;
import java.util.List;


public class androidPay extends CordovaPlugin {

    private String mEnv;
    private GoogleApiClient mGoogleApiClient;
    public static final int FULL_WALLET_REQUEST_CODE = 889;
    private FullWallet mFullWallet;
    private MaskedWalletRequest maskedWalletRequest;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        if (action.equals("greet")) {

            String name = data.getString(0);
            String message = "Hello, " + name;
            // createMaskedWalletRequest("0.01", callbackContext);
            maskedWalletRequest = createMaskedWalletRequest(amountText);
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addOnConnectionFailedListener(this)
                .enableAutoManage(this, 0, this)
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                    .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                    .setTheme(WalletConstants.THEME_LIGHT)
                    .build())
                .build();
            callbackContext.success(message);

            return true;

        } else {
            callbackContext.error("Wrong message!");
            return false;

        }
    }

    private MaskedWalletRequest generateMaskedWalletRequest() {
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
                        .setMerchantName("Google I/O Codelab")
                        .setPhoneNumberRequired(true)
                        .setShippingAddressRequired(true)
                        .setCurrencyCode("USD")
                        .setCart(Cart.newBuilder()
                                .setCurrencyCode("USD")
                                .setTotalPrice("10.00")
                                .addLineItem(LineItem.newBuilder()
                                        .setCurrencyCode("USD")
                                        .setDescription("Google I/O Sticker")
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
        switch (requestCode) {
            case MASKED_WALLET_REQUEST_CODE:
                switch (resultCode) {
                    case RESULT_OK:
                        mMaskedWallet =  data
                                .getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                        Toast.makeText(this, "Got Masked Wallet", Toast.LENGTH_SHORT).show();
                        break;
                    case RESULT_CANCELED:
                        // The user canceled the operation
                        break;
                    case WalletConstants.RESULT_ERROR:
                        Toast.makeText(this, "An Error Occurred", Toast.LENGTH_SHORT).show();
                        // callbackContext.error("An Error Occurred!");
                        break;
                }
                break;
            case FULL_WALLET_REQUEST_CODE:
                switch (resultCode) {
                    case RESULT_OK:
                        mFullWallet = data
                                .getParcelableExtra(WalletConstants.EXTRA_FULL_WALLET);
                        // Show the credit card number
                        Toast.makeText(this,
                                "Got Full Wallet, Done!",
                                Toast.LENGTH_SHORT).show();
                        // callbackContext.success(mFullWallet.getPaymentMethodToken());
                        break;
                    case WalletConstants.RESULT_ERROR:
                        Toast.makeText(this, "An Error Occurred", Toast.LENGTH_SHORT).show();
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
                  .setDescription("Google I/O Sticker")
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

    public void requestFullWallet() {
      if (mMaskedWallet == null) {
        Toast.makeText(this, "No masked wallet, can't confirm", Toast.LENGTH_SHORT).show();
        return;
      } 
      Wallet.Payments.loadFullWallet(mGoogleApiClient,
          generateFullWalletRequest(mMaskedWallet.getGoogleTransactionId()),
          FULL_WALLET_REQUEST_CODE);
    }
}


