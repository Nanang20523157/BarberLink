package com.example.barberlink.Services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.net.URLEncoder;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * <p>
 * helper methods.
 */
public class SenderMessageService extends IntentService {

    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_SKIP = "com.example.autosenderwhatsapp.action.FOO";
    private static final String ACTION_WHATSAPP = "com.example.autosenderwhatsapp.action.BAZ";
    private static final String MESSAGE = "com.example.autosenderwhatsapp.extra.PARAM1";
    private static final String COUNT = "com.example.autosenderwhatsapp.extra.PARAM2";
    private static final String MOBILE_NUMBER = "com.example.autosenderwhatsapp.extra.PARAM3";
    private static final String MONEY_CASHBACK_AMOUNT = "com.example.autosenderwhatsapp.extra.PARAM4";
    private static final String PAYMENT_METHOD = "com.example.autosenderwhatsapp.extra.PARAM5";
//    private static final String NEW_INDEX = "com.example.autosenderwhatsapp.extra.PARAM6";
    private static final String PREVIOUS_STATUS = "com.example.autosenderwhatsapp.extra.PARAM7";
    private static final String MESSAGE_SNACKBAR = "com.example.autosenderwhatsapp.extra.PARAM8";

    public SenderMessageService() {
        super("SenderMessageService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
//    public static void startActionSkip(Context context, String message, String count, String mobile_number,
//                                      String moneyCashBackAmount, String paymentMethod, int newIndex,
//                                      String previousStatus, String messageSnackBar) {
    public static void startActionSkip(Context context, String message, String count, String mobile_number,
                                       String moneyCashBackAmount, String paymentMethod,
                                       String previousStatus, String messageSnackBar) {
        Intent intent = new Intent(context, SenderMessageService.class);
        intent.setAction(ACTION_SKIP);
        intent.putExtra(MESSAGE, message);
        intent.putExtra(COUNT, count);
        intent.putExtra(MOBILE_NUMBER, mobile_number);
        intent.putExtra(MONEY_CASHBACK_AMOUNT, moneyCashBackAmount);
        intent.putExtra(PAYMENT_METHOD, paymentMethod);
//        intent.putExtra(NEW_INDEX, newIndex);
        intent.putExtra(PREVIOUS_STATUS, previousStatus);
        intent.putExtra(MESSAGE_SNACKBAR, messageSnackBar);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
//    public static void startActionWHATSAPP(Context context, String message, String count, String mobile_number,
//                                           String moneyCashBackAmount, String paymentMethod, int newIndex,
//                                           String previousStatus, String messageSnackBar) {
    public static void startActionWHATSAPP(Context context, String message, String count, String mobile_number,
                                           String moneyCashBackAmount, String paymentMethod,
                                           String previousStatus, String messageSnackBar) {
        Intent intent = new Intent(context, SenderMessageService.class);
        intent.setAction(ACTION_WHATSAPP);
        intent.putExtra(MESSAGE, message);
        intent.putExtra(COUNT, count);
        intent.putExtra(MOBILE_NUMBER, mobile_number);
        intent.putExtra(MONEY_CASHBACK_AMOUNT, moneyCashBackAmount);
        intent.putExtra(PAYMENT_METHOD, paymentMethod);
//        intent.putExtra(NEW_INDEX, newIndex);
        intent.putExtra(PREVIOUS_STATUS, previousStatus);
        intent.putExtra(MESSAGE_SNACKBAR, messageSnackBar);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SKIP.equals(action)) {
                handleIntentData(intent, true);
            } else if (ACTION_WHATSAPP.equals(action)) {
                handleIntentData(intent, false);
            }
        }
    }

    private void handleIntentData(Intent intent, boolean isSkip) {
        final String message = intent.getStringExtra(MESSAGE);
        final String count = intent.getStringExtra(COUNT);
        final String mobile_number = intent.getStringExtra(MOBILE_NUMBER);
        final String moneyCashBackAmount = intent.getStringExtra(MONEY_CASHBACK_AMOUNT);
        final String paymentMethod = intent.getStringExtra(PAYMENT_METHOD);
//        final int newIndex = intent.getIntExtra(NEW_INDEX, -1);
        final String previousStatus = intent.getStringExtra(PREVIOUS_STATUS);
        final String messageSnackBar = intent.getStringExtra(MESSAGE_SNACKBAR);

        if (isSkip) {
//            handleActionSkip(message, count, mobile_number, moneyCashBackAmount, paymentMethod, newIndex, previousStatus, messageSnackBar);
            handleActionSkip(message, count, mobile_number, moneyCashBackAmount, paymentMethod, previousStatus, messageSnackBar);
        } else {
//            handleActionWHATSAPP(message, count, mobile_number, moneyCashBackAmount, paymentMethod, newIndex, previousStatus, messageSnackBar);
            handleActionWHATSAPP(message, count, mobile_number, moneyCashBackAmount, paymentMethod, previousStatus, messageSnackBar);
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
//    private void handleActionSkip(String message, String count, String mobile_number, String moneyCashBackAmount,
//                                 String paymentMethod, int newIndex, String previousStatus, String messageSnackBar) {

    private void handleActionSkip(String message, String count, String mobile_number, String moneyCashBackAmount,
                                  String paymentMethod, String previousStatus, String messageSnackBar) {

        sendBroadcastData(moneyCashBackAmount, paymentMethod, previousStatus, messageSnackBar);
    }

//    private void handleActionWHATSAPP(String message, String count, String mobile_number, String moneyCashBackAmount,
//                                      String paymentMethod, int newIndex, String previousStatus, String messageSnackBar) {

    private void handleActionWHATSAPP(String message, String count, String mobile_number, String moneyCashBackAmount,
                                      String paymentMethod, String previousStatus, String messageSnackBar) {
        try {
            PackageManager packageManager = getApplicationContext().getPackageManager();
            for (int i = 0; i < Integer.parseInt(count); i++) {
                String formattedNumber = mobile_number.startsWith("+") ? mobile_number.substring(1) : mobile_number;
                String url = "https://api.whatsapp.com/send?phone=" + formattedNumber + "&text=" + URLEncoder.encode(message, "UTF-8");
                Intent whatsappIntent = new Intent(Intent.ACTION_VIEW);
                whatsappIntent.setPackage("com.whatsapp");
                whatsappIntent.setData(Uri.parse(url));
                whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (whatsappIntent.resolveActivity(packageManager) != null) {
                    getApplicationContext().startActivity(whatsappIntent);
                    Thread.sleep(500);
                    sendBroadcastData(moneyCashBackAmount, paymentMethod, previousStatus, messageSnackBar);
                } else {
                    sendBroadcastMessage("WhatsApp not installed");
                }
            }
            Log.d("WhatsAppService", "CashBack: " + moneyCashBackAmount + ", PaymentMethod: " + paymentMethod);
            Log.d("WhatsAppService", "Snackbar Message: " + messageSnackBar);

            sendBroadcastMessage("WhatsApp Message Sent Successfully");
        } catch (Exception e) {
            sendBroadcastMessage("Error: " + e.toString());
        }
    }

    private void sendBroadcastMessage(String message) {
        Intent localIntent = new Intent("my.own.broadcast.message");
        localIntent.putExtra("result", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

//    private void sendBroadcastData(String moneyCashBackAmount, String paymentMethod, int newIndex, String previousStatus, String messageSnackBar) {
    private void sendBroadcastData(String moneyCashBackAmount, String paymentMethod, String previousStatus, String messageSnackBar) {
        Intent localIntent = new Intent("my.own.broadcast.data");
        Log.d("Testing3", "sendBroadcastData");
        localIntent.putExtra("moneyCashBackAmount", moneyCashBackAmount);
        localIntent.putExtra("paymentMethod", paymentMethod);
//        localIntent.putExtra("newIndex", newIndex);
        localIntent.putExtra("previousStatus", previousStatus);
        localIntent.putExtra("messageSnackBar", messageSnackBar);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }


}