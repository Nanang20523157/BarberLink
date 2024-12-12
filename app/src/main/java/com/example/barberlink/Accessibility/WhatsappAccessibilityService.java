package com.example.barberlink.Accessibility;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.List;

public class WhatsappAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
//        Log.d("Testing", "Sending message1");
        if (getRootInActiveWindow() == null) {
            return;
        }

//        Log.d("Testing", "Sending message2");
        AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoCompat.wrap(getRootInActiveWindow());
        List<AccessibilityNodeInfoCompat> messageNodeList = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry");
        if (messageNodeList == null || messageNodeList.isEmpty()) {
            return;
        }

//        Log.d("Testing", "Sending message3");
        AccessibilityNodeInfoCompat messageField = messageNodeList.get(0);
        if (messageField == null || messageField.getText().length() == 0 || !messageField.getText().toString().endsWith("   ")) {
            return;
        }

//        Log.d("Testing", "Sending message4");
        List<AccessibilityNodeInfoCompat> sendMessageNodeList = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
        if (sendMessageNodeList == null || sendMessageNodeList.isEmpty()) {
            return;
        }

//        Log.d("Testing", "Sending message5");
        AccessibilityNodeInfoCompat sendMessage = sendMessageNodeList.get(0);
        if (!sendMessage.isVisibleToUser()) {
            return;
        }

//        Log.d("Testing", "Sending message6");
        sendMessage.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK);

        try {
//            Log.d("Testing", "Sending message7");
            Thread.sleep(500);
            performGlobalAction(GLOBAL_ACTION_BACK);
            Thread.sleep(300);
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (InterruptedException ignore) {}


    }

    @Override
    public void onInterrupt() {

    }

}
