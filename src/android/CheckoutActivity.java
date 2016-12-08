package com.dorknstein.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the "Pay with Android" button.
 */
public class CheckoutActivity extends FragmentActivity {

    public static final int WALLET_ENVIRONMENT = WalletConstants.ENVIRONMENT_SANDBOX;

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

    private String mAmount;
    private String mEnv;

    private SupportWalletFragment mWalletFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAmount = getIntent().getStringExtra(EXTRA_AMOUNT);
        mEnv = getIntent().getStringExtra(EXTRA_ENV);

        createAndAddWalletFragment(mEnv, mAmount);
    }

    /**
     * Create a fake line item list. Set the amount to the one received from the user.
     * @param amount    Amount received from the user
     * @return  List of line items
     */
    private List<LineItem> buildLineItems(String amount) {
        List<LineItem> list = new ArrayList<LineItem>();
        list.add(LineItem.newBuilder()
                .setCurrencyCode(CURRENCY_CODE_USD)
                .setDescription("Tesla model S")
                .setQuantity("1")
                .setUnitPrice(amount)
                .setTotalPrice(amount)
                .build());
        return list;
    }

    /**
     * Create the Masked Wallet request. Note that the Tokenization Type is set to
     * {@code NETWORK_TOKEN} and the {@code publicKey} parameter is set to the public key
     * that was created by First Data.
     *
     * @param amount    The amount the user entered
     * @return  A Masked Wallet request object
     */
    private MaskedWalletRequest createMaskedWalletRequest(String amount) {

        String publicKey = "BO39Rh43UGXMQy5PAWWe7UGWd2a9YRjNLPEEVe+zWIbdIgALcDcnYCuHbmrrzl7h8FZjl6RCzoi5/cDrqXNRVSo=";

        MaskedWalletRequest.Builder builder = MaskedWalletRequest.newBuilder()
                .setMerchantName(MERCHANT_NAME)
                .setPhoneNumberRequired(true)
                .setShippingAddressRequired(true)
                .setCurrencyCode(CURRENCY_CODE_USD)
                .setEstimatedTotalPrice(amount)
                        // Create a Cart with the current line items. Provide all the information
                        // available up to this point with estimates for shipping and tax included.
                .setCart(Cart.newBuilder()
                        .setCurrencyCode(CURRENCY_CODE_USD)
                        .setTotalPrice(amount)
                        .setLineItems(buildLineItems(amount))
                        .build());

        //  Set tokenization type and First Data issued public key
        PaymentMethodTokenizationParameters mPaymentMethodParameters = PaymentMethodTokenizationParameters.newBuilder()
                .setPaymentMethodTokenizationType(PaymentMethodTokenizationType.NETWORK_TOKEN)
                .addParameter("publicKey", publicKey)
                .build();
        builder.setPaymentMethodTokenizationParameters(mPaymentMethodParameters);
        return builder.build();
    }

    /**
     * Create the wallet fragment. This will create the "Buy with Android Pay" button.
     *
     * @param env   First Data environment
     * @param amountText    Amount received from the user
     */
    private void createAndAddWalletFragment(String env, String amountText) {
        WalletFragmentStyle walletFragmentStyle = new WalletFragmentStyle()
                .setBuyButtonText(WalletFragmentStyle.BuyButtonText.BUY_WITH)
                .setBuyButtonAppearance(WalletFragmentStyle.BuyButtonAppearance.ANDROID_PAY_DARK)
                .setBuyButtonWidth(WalletFragmentStyle.Dimension.MATCH_PARENT);

        WalletFragmentOptions walletFragmentOptions = WalletFragmentOptions.newBuilder()
                .setEnvironment(WALLET_ENVIRONMENT)
                .setFragmentStyle(walletFragmentStyle)
                .setTheme(WalletConstants.THEME_LIGHT)
                .setMode(WalletFragmentMode.BUY_BUTTON)
                .build();
        mWalletFragment = SupportWalletFragment.newInstance(walletFragmentOptions);

        // Now initialize the Wallet Fragment
        MaskedWalletRequest maskedWalletRequest = createMaskedWalletRequest(amountText);

        String accountName = "Mendr";

        WalletFragmentInitParams.Builder startParamsBuilder = WalletFragmentInitParams.newBuilder()
                .setMaskedWalletRequest(maskedWalletRequest)
                .setMaskedWalletRequestCode(REQUEST_CODE_MASKED_WALLET)
                .setAccountName(accountName);
        mWalletFragment.initialize(startParamsBuilder.build());

        // add Wallet fragment to the UI
        getSupportFragmentManager().beginTransaction()
                .commit();
    }

    /**
     * Invoked after the user taps the Buy With Android Pay button and the selected
     * credit card and shipping address are confirmed. If the request succeeded,
     * a {@link MaskedWallet} object is attached to the Intent. The Confirmation Activity is
     * then launched, providing it with the {@link MaskedWallet} object.
     *
     * @param requestCode The code that was set in the Masked Wallet Request
     * @param resultCode  The result of the request execution
     * @param data  Intent carrying the results
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // retrieve the error code, if available
        int errorCode = -1;
        if (data != null) {
            errorCode = data.getIntExtra(WalletConstants.EXTRA_ERROR_CODE, -1);
        }
        switch (requestCode) {
            case REQUEST_CODE_MASKED_WALLET:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        MaskedWallet maskedWallet =
                                data.getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                        // launchConfirmationPage(maskedWallet);
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                    default:
                        handleError(errorCode);
                        break;
                }
                break;

            case WalletConstants.RESULT_ERROR:
                handleError(errorCode);
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    // private void launchConfirmationPage(MaskedWallet maskedWallet) {
    //     Intent intent = ConfirmationActivity.newIntent(this, maskedWallet, mAmount, mEnv);
    //     startActivity(intent);
    // }

    protected void handleError(int errorCode) {
        switch (errorCode) {
            case WalletConstants.ERROR_CODE_SPENDING_LIMIT_EXCEEDED:
                Toast.makeText(this, "Way too much!!", Toast.LENGTH_LONG).show();
                break;
            case WalletConstants.ERROR_CODE_INVALID_PARAMETERS:
            case WalletConstants.ERROR_CODE_AUTHENTICATION_FAILURE:
            case WalletConstants.ERROR_CODE_BUYER_ACCOUNT_ERROR:
            case WalletConstants.ERROR_CODE_MERCHANT_ACCOUNT_ERROR:
            case WalletConstants.ERROR_CODE_SERVICE_UNAVAILABLE:
            case WalletConstants.ERROR_CODE_UNSUPPORTED_API_VERSION:
            case WalletConstants.ERROR_CODE_UNKNOWN:
            default:
                // unrecoverable error
                String errorMessage = "Android Pay is unavailable" + "\n" +
                        "Error code: " + errorCode;
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                break;
        }
    }

    public static Intent newIntent(Context ctx, String amount, String env) {
        Intent intent = new Intent(ctx, CheckoutActivity.class);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_ENV, env);
        return intent;
    }

}
