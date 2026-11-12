package com.ngoctho.aitranslator;

import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

/** Ask for a supported fast refresh rate at the current resolution. The OS has final say. */
public final class HighRefresh {
    private HighRefresh(){}
    @SuppressWarnings("deprecation") public static void request(Window window){
        if(window==null)return;
        Display display=window.getWindowManager().getDefaultDisplay();Display.Mode current=display.getMode();float best=current.getRefreshRate();
        for(Display.Mode mode:display.getSupportedModes())if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight())best=Math.max(best,mode.getRefreshRate());
        WindowManager.LayoutParams attrs=window.getAttributes();if(Math.abs(attrs.preferredRefreshRate-best)>.1f){attrs.preferredRefreshRate=best;window.setAttributes(attrs);}
    }
}
