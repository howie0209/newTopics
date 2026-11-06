package com.example.topics;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * 點輸入框以外的地方就收起鍵盤，照 iOS 版的做法（點背景 focusedField = nil、捲動時收鍵盤）。
 * 在 Activity 的 dispatchTouchEvent 呼叫 handle() 即可。
 */
public final class KeyboardDismiss {
    private KeyboardDismiss() {}

    public static void handle(Activity activity, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return;
        View focus = activity.getCurrentFocus();
        if (!(focus instanceof EditText)) return;
        int x = (int) event.getRawX();
        int y = (int) event.getRawY();
        View root = activity.getWindow().getDecorView();
        // 點到另一個輸入框時不收，讓焦點直接移過去
        if (findEditTextAt(root, x, y) != null) return;
        hide(activity);
    }

    public static void hide(Activity activity) {
        View focus = activity.getCurrentFocus();
        View token = focus != null ? focus : activity.getWindow().getDecorView();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(token.getWindowToken(), 0);
        // 把焦點交給畫面最外層，游標就不會繼續停在輸入框裡
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.setFocusableInTouchMode(true);
            content.requestFocus();
        } else if (focus != null) {
            focus.clearFocus();
        }
    }

    private static EditText findEditTextAt(View view, int x, int y) {
        if (!view.isShown()) return null;
        if (view instanceof EditText) {
            if (!view.isEnabled()) return null;
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            Rect rect = new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
            return rect.contains(x, y) ? (EditText) view : null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                EditText found = findEditTextAt(group.getChildAt(i), x, y);
                if (found != null) return found;
            }
        }
        return null;
    }
}
