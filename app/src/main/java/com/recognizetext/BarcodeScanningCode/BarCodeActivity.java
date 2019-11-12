package com.recognizetext.BarcodeScanningCode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.lifecycle.LifecycleOwner;

import android.app.AlertDialog;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.recognizetext.Helper.GraphicOverlay;
import com.recognizetext.Helper.TextGraphic;
import com.recognizetext.R;


import java.io.File;
import java.io.IOException;
import java.util.List;

import dmax.dialog.SpotsDialog;

public class BarCodeActivity extends AppCompatActivity {
    private TextureView textureView;
    private  int rotationDgr;
    private GraphicOverlay graphicOverlay;
    private Button capture;
    private AlertDialog alertDialog;
    private FirebaseVisionBarcodeDetector detector;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private Preview preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bar_code);
        textureView = findViewById(R.id.view_finder);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        capture = findViewById(R.id.bt_Capture);
        startCamera();

    }

    private void startCamera() {
        CameraX.unbindAll();
        preview = setPreview();
        imageCapture = setImageCapture();
        imageAnalysis = setImageAnalysis();
        //bind to lifecycle:
        CameraX.bindToLifecycle((LifecycleOwner) this, preview, imageCapture, imageAnalysis);
        // CameraX.bindToLifecycle((LifecycleOwner) this, preview, imageCapture);
    }

    private void updateTransform() {
        Matrix mx = new Matrix();
        float w = textureView.getMeasuredWidth();
        float h = textureView.getMeasuredHeight();
        float cX = w / 2f;
        float cY = h / 2f;


        int rotation = (int) textureView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, cX, cY);
        textureView.setTransform(mx);
    }


    private void scanBarCode(List<FirebaseVisionBarcode>  barcodesdata) {

        for (FirebaseVisionBarcode barcode: barcodesdata) {
            Rect bounds = barcode.getBoundingBox();
            Point[] corners = barcode.getCornerPoints();

            String rawValue = barcode.getRawValue();

            int valueType = barcode.getValueType();
            // See API reference for complete list of supported types
            switch (valueType) {
                case FirebaseVisionBarcode.TYPE_WIFI:
                    String ssid = barcode.getWifi().getSsid();
                    String password = barcode.getWifi().getPassword();
                    int type = barcode.getWifi().getEncryptionType();
                    Toast.makeText(this, ssid, Toast.LENGTH_SHORT).show();
                    break;
                case FirebaseVisionBarcode.TYPE_URL:
                    String title = barcode.getUrl().getTitle();
                    String url = barcode.getUrl().getUrl();

                    Toast.makeText(this, url, Toast.LENGTH_SHORT).show();
                    break;
                case FirebaseVisionBarcode.TYPE_TEXT:
                    String titleu = barcode.getDisplayValue();
                    Toast.makeText(this, titleu, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            detector.close();
        } catch (IOException e) {
            Log.e("tag", "Exception thrown while trying to close Text Detector: " + e);
        }
    }


    private Preview setPreview() {
        Rational aspectRatio = new Rational(textureView.getWidth(), textureView.getHeight());
        Size screen = new Size(textureView.getWidth(), textureView.getHeight()); //size of the screen

        PreviewConfig pConfig = new PreviewConfig.Builder().
                setTargetAspectRatio(aspectRatio).
                setTargetResolution(screen).build();
        Preview preview = new Preview(pConfig);

        Log.e("LogOne", "log==01");
        preview.setOnPreviewOutputUpdateListener(
                new Preview.OnPreviewOutputUpdateListener() {
                    //to update the surface texture we  have to destroy it first then re-add it
                    @Override
                    public void onUpdated(Preview.PreviewOutput output) {
                        ViewGroup parent = (ViewGroup) textureView.getParent();
                        parent.removeView(textureView);
                        parent.addView(textureView, 0);
                        textureView.setSurfaceTexture(output.getSurfaceTexture());
                        updateTransform();
                    }
                });
        return preview;
    }

    private ImageCapture setImageCapture() {
        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();
        final ImageCapture imgCap = new ImageCapture(imageCaptureConfig);

        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".png");
                imgCap.takePicture(file, new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(@NonNull File file) {
                        String msg = "Pic captured at " + file.getAbsolutePath();
                        FirebaseVisionImage image;
                        try {
                            image = FirebaseVisionImage.fromFilePath(BarCodeActivity.this, Uri.parse(file.getAbsolutePath()));
                            picClick(image);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCapture.UseCaseError useCaseError, @NonNull String message, @Nullable Throwable cause) {
                        String msg = "Pic capture failed : " + message;
                        Toast.makeText(getBaseContext(), msg,Toast.LENGTH_LONG).show();
                        if(cause != null){
                            cause.printStackTrace();
                        }
                    }
                });
            }
        });
        return imgCap;
    }

    private ImageAnalysis setImageAnalysis() {


        HandlerThread analyzerThread = new HandlerThread("OpenCVAnalysis");
        analyzerThread.start();


        ImageAnalysisConfig imageAnalysisConfig = new ImageAnalysisConfig.Builder()
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setCallbackHandler(new Handler(analyzerThread.getLooper()))
                .setImageQueueDepth(1).build();



        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer(
                new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(ImageProxy imageProxy, int degrees) {
                        if (imageProxy == null || imageProxy.getImage() == null) {
                            return;
                        }
                        Image mediaImage = imageProxy.getImage();

                        int rotation = rotationDgr;
                        FirebaseVisionImage image =
                                FirebaseVisionImage.fromMediaImage(mediaImage, rotation);


                        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionBarcode>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionBarcode> firebaseVisionBarcodes) {
                                if(firebaseVisionBarcodes!=null){

                                }else {
                                    Toast.makeText(BarCodeActivity.this, "Not available", Toast.LENGTH_SHORT).show();
                                }

                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(BarCodeActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });


        return imageAnalysis;
    }


    private void picClick(FirebaseVisionImage imageData){
        detector = FirebaseVision.getInstance()
                .getVisionBarcodeDetector();


        detector.detectInImage(imageData).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionBarcode>>() {
            @Override
            public void onSuccess(List<FirebaseVisionBarcode> firebaseVisionBarcodes) {
                if(firebaseVisionBarcodes!=null){

                }else {
                    Toast.makeText(BarCodeActivity.this, "Not available", Toast.LENGTH_SHORT).show();
                }

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(BarCodeActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        });

    }

}
