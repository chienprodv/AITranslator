package com.ngoctho.aitranslator;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

public final class OcrEngine {
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private static final TextRecognizer CHINESE =
            TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

    private OcrEngine() {}

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
