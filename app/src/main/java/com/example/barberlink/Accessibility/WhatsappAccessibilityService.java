package com.example.barberlink.Accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log; // Import Log
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo; // Import AccessibilityNodeInfo

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.List;

public class WhatsappAccessibilityService extends AccessibilityService {
    private static final String TAG = "WhatsappAccessibility"; // Tambahkan TAG untuk logging

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            Log.w(TAG, "AccessibilityEvent is null. Ignoring.");
            return;
        }

        // Dapatkan root node sekali dan periksa apakah null
        AccessibilityNodeInfo rootInfo = getRootInActiveWindow();
        if (rootInfo == null) {
            Log.w(TAG, "Root in active window is null. EventType: " + AccessibilityEvent.eventTypeToString(event.getEventType()));
            return;
        }

        AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoCompat.wrap(rootInfo);
        // Meskipun rootInfo tidak null, rootNode setelah di-wrap mungkin masih
        // menyebabkan masalah jika operasi selanjutnya gagal. Namun, sumber NPE utama
        // adalah jika rootInfo itu sendiri null.

        // Log untuk debugging lebih lanjut
        Log.d(TAG, "Processing event: " + AccessibilityEvent.eventTypeToString(event.getEventType()) + " from package: " + event.getPackageName());

        List<AccessibilityNodeInfoCompat> messageNodeList = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry");
        if (messageNodeList == null || messageNodeList.isEmpty()) {
            Log.d(TAG, "Message entry field not found or list is empty.");
            // rootInfo.recycle(); // Recycle jika Anda selesai dengan rootInfo di sini
            return;
        }

        AccessibilityNodeInfoCompat messageField = messageNodeList.get(0);
        // Periksa messageField dan teksnya dengan hati-hati
        if (messageField == null || messageField.getText() == null || messageField.getText().length() == 0 || !messageField.getText().toString().endsWith("   ")) {
            Log.d(TAG, "Message field is null, has no text, is empty, or doesn't end with '   '.");
            // rootInfo.recycle(); // Recycle jika Anda selesai
            return;
        }

        Log.d(TAG, "Message field found and text is: " + messageField.getText());

        List<AccessibilityNodeInfoCompat> sendMessageNodeList = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
        if (sendMessageNodeList == null || sendMessageNodeList.isEmpty()) {
            Log.d(TAG, "Send button not found or list is empty.");
            // rootInfo.recycle(); // Recycle jika Anda selesai
            return;
        }

        AccessibilityNodeInfoCompat sendMessage = sendMessageNodeList.get(0);
        if (sendMessage == null || !sendMessage.isVisibleToUser()) { // Tambahkan null check untuk sendMessage
            Log.d(TAG, "Send button is null or not visible to user.");
            // rootInfo.recycle(); // Recycle jika Anda selesai
            return;
        }

        Log.d(TAG, "Send button found and is visible. Clicking send.");
        sendMessage.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK);

        try {
            Log.d(TAG, "Performing global actions after sending.");
            Thread.sleep(500);
            performGlobalAction(GLOBAL_ACTION_BACK);
            Thread.sleep(300);
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (InterruptedException ignore) {
            Log.w(TAG, "Thread interrupted during sleep.");
            Thread.currentThread().interrupt(); // Set interrupt flag again
        }

        // Penting: Recycle AccessibilityNodeInfo yang Anda peroleh secara eksplisit
        // jika Anda tidak lagi menggunakannya untuk mencegah kebocoran memori.
        // Namun, node yang ada di dalam list yang dikembalikan oleh findAccessibilityNodeInfosByViewId
        // biasanya dikelola oleh sistem atau wrapper Compat.
        // rootInfo yang asli (dari getRootInActiveWindow()) harus di-recycle.
        // rootInfo.recycle(); // Pindahkan ini ke akhir jika tidak ada return lebih awal,
        // atau di setiap blok return.
        // Lebih aman untuk tidak me-recycle jika Anda tidak yakin
        // tentang siklus hidupnya dalam konteks AccessibilityService.
        // Sistem umumnya menangani node yang diteruskan ke onAccessibilityEvent.
        // Untuk node yang Anda ambil dengan getRootInActiveWindow(),
        // praktik yang baik adalah me-recycle-nya ketika Anda selesai.
        // Namun, berhati-hatilah agar tidak me-recycle terlalu dini.
        // Untuk saat ini, kita bisa mengabaikan recycle eksplisit untuk rootInfo
        // karena service akan segera berakhir atau memproses event berikutnya.
        // Pengelolaan memori node bisa rumit.
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted.");
    }
}
