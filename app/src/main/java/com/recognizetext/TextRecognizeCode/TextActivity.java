package com.recognizetext.TextRecognizeCode;

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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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

import com.camerakit.CameraKit;
import com.camerakit.CameraKitView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.document.FirebaseVisionCloudDocumentRecognizerOptions;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer;
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.recognizetext.FaceDetectionCode.FaceActivity;
import com.recognizetext.Helper.GraphicOverlay;
import com.recognizetext.Helper.TextGraphic;
import com.recognizetext.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import dmax.dialog.SpotsDialog;

public class TextActivity extends AppCompatActivity {
    private TextureView textureView;
    private  int rotationDgr;
    private GraphicOverlay graphicOverlay;
    private Button capture;
    private AlertDialog alertDialog;
    private FirebaseVisionTextRecognizer detector;
    private  ImageCapture imageCapture;
    private  ImageAnalysis imageAnalysis;
    private  Preview preview;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text);
        textureView = findViewById(R.id.view_finder);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        capture = findViewById(R.id.bt_Capture);

        alertDialog = new SpotsDialog.Builder()
                .setCancelable(false)
                .setMessage("Please wait...")
                .setContext(this)
                .build();
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


    private void drawTextResult(FirebaseVisionText firebaseVisionText) {

        List<FirebaseVisionText.TextBlock> blockList = firebaseVisionText.getTextBlocks();
        if (blockList.size() == 0) {
            Toast.makeText(TextActivity.this, "Text not found", Toast.LENGTH_SHORT).show();
            return;
        }
        graphicOverlay.clear();
        for (int n = 0; n < blockList.size(); n++) {
            List<FirebaseVisionText.Line> lines = blockList.get(n).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int q = 0; q < elements.size(); q++) {
                   // GraphicOverlay.Graphic textGraphic = new TextGraphic(graphicOverlay, elements.get(q));
                      TextGraphic textGraphic = new TextGraphic(graphicOverlay, elements.get(q));
                    graphicOverlay.add(textGraphic);
                }
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
                            image = FirebaseVisionImage.fromFilePath(TextActivity.this, Uri.parse(file.getAbsolutePath()));
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


//                        FirebaseVisionCloudTextRecognizerOptions options =
//                                new FirebaseVisionCloudTextRecognizerOptions.Builder()
//                                        .setLanguageHints(Arrays.asList("en", "hi"))
//                                        .build();
//                        FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance().getCloudTextRecognizer(options);//cloud

                        detector = FirebaseVision.getInstance()
                                .getOnDeviceTextRecognizer();
                        detector.processImage(image).addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                if (firebaseVisionText != null) {

                                    drawTextResult(firebaseVisionText);
                                } else {
                                    Toast.makeText(TextActivity.this, "Not available", Toast.LENGTH_SHORT).show();
                                }

                            }

                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(TextActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
        return imageAnalysis;
    }


    private void picClick(FirebaseVisionImage imageData){


//        int rotation = rotationDgr;
//        FirebaseVisionImage image =
//                FirebaseVisionImage.fromMediaImage(imageData, rotation);

        detector = FirebaseVision.getInstance()
                .getOnDeviceTextRecognizer();
        detector.processImage(imageData).addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
            @Override
            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                if (firebaseVisionText != null) {

                    drawTextResult(firebaseVisionText);
                } else {
                    Toast.makeText(TextActivity.this, "Not available", Toast.LENGTH_SHORT).show();
                }

            }

        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(TextActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        });

    }
}
