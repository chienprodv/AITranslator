package com.ngoctho.aitranslator;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Match the host editor's action without inserting whitespace into search fields. */
public final class EditorAction {
    private EditorAction(){}
    public static int action(EditorInfo info){
        if(info==null||(info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0)return EditorInfo.IME_ACTION_NONE;
        if(info.actionLabel!=null&&info.actionLabel.length()>0)return info.actionId;
        int action=info.imeOptions&EditorInfo.IME_MASK_ACTION;
        return action>=EditorInfo.IME_ACTION_GO&&action<=EditorInfo.IME_ACTION_PREVIOUS?action:EditorInfo.IME_ACTION_NONE;
    }
    private static boolean custom(EditorInfo info){return info!=null&&(info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0&&info.actionLabel!=null&&info.actionLabel.length()>0;}
    public static String label(EditorInfo info){
        if(custom(info))return info.actionLabel.toString();
        switch(action(info)){
            case EditorInfo.IME_ACTION_SEARCH:return "Tìm";
            case EditorInfo.IME_ACTION_SEND:return "Gửi";
            case EditorInfo.IME_ACTION_GO:return "Đi";
            case EditorInfo.IME_ACTION_NEXT:return "Tiếp";
            case EditorInfo.IME_ACTION_PREVIOUS:return "Trước";
            case EditorInfo.IME_ACTION_DONE:return "Xong";
            default:return "↵";
        }
    }
    public static void dispatch(InputConnection connection,EditorInfo info){
        if(connection==null)return;
        connection.finishComposingText();
        int command=action(info);
        // Editor actions are asynchronous. Never append a newline or send a duplicate on false.
        if(custom(info)||command!=EditorInfo.IME_ACTION_NONE){connection.performEditorAction(command);return;}
        boolean multiline=info!=null&&(info.inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT
                &&(info.inputType&InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0;
        if(multiline)connection.commitText("\n",1);
        else {connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));}
    }
}
