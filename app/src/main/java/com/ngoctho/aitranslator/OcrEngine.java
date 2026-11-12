package com.ngoctho.aitranslator;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

public final class OcrEngine {
    public interface RegionCallback {void onSuccess(List<ScreenRegion> regions);void onError(String message);}
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private static final TextRecognizer CHINESE =
            TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

    private OcrEngine() {}

    public static void recognizeRegions(Bitmap bitmap,RegionCallback callback){
        if(bitmap==null||bitmap.isRecycled()){callback.onError("Không có ảnh màn hình.");return;}
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        CHINESE.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(result->{
            List<ScreenRegion> regions=new ArrayList<>();
            for(com.google.mlkit.vision.text.Text.TextBlock block:result.getTextBlocks()){
                Rect rect=block.getBoundingBox();String text=block.getText();
                if(rect==null||text==null||text.trim().isEmpty())continue;
                ScreenRegion r=new ScreenRegion(regions.size(),text,rect.left/(double)width,rect.top/(double)height,rect.right/(double)width,rect.bottom/(double)height,block.getLines().size(),"");
                if(r.valid())regions.add(r);
            }
            if(regions.isEmpty())callback.onError("Không nhận ra chữ trên ảnh. Thử chọn vùng rõ hơn.");else callback.onSuccess(regions);
        }).addOnFailureListener(e->callback.onError(e.getMessage()==null?"OCR thất bại.":e.getMessage()));
    }

    public static void recognizeChineseScreen(Bitmap bitmap, Callback callback) {
        if (bitmap == null) {
            callback.onError("Không có ảnh màn hình.");
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        CHINESE.process(image)
                .addOnSuccessListener(result -> {
                    String text = result.getText() == null ? "" : result.getText().trim();
                    if (text.isEmpty()) {
                        callback.onError("Không nhận ra chữ trên màn hình.");
                    } else {
                        callback.onSuccess(text);
                    }
                })
                .addOnFailureListener(e -> {
                    String m = e.getMessage();
                    callback.onError(m == null || m.trim().isEmpty() ? "OCR thất bại." : m);
                });
    }
}
