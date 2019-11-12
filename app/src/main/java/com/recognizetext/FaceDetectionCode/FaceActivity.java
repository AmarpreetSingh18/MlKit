package com.recognizetext.FaceDetectionCode;


import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.lifecycle.LifecycleOwner;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import com.recognizetext.Helper.GraphicOverlay;
import com.recognizetext.R;

import java.io.IOException;
import java.util.List;

public class FaceActivity extends AppCompatActivity {
    private TextureView textureView;
    int rotationDgr;
    private GraphicOverlay graphicOverlay;
    private FirebaseVisionFaceDetector detector;
    private HandlerThread analyzerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face);
        textureView = findViewById(R.id.view_finder);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        startCamera();
    }


    private void startCamera() {

        CameraX.unbindAll();

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
        Log.e("LogOne", "log==02");

        analyzerThread = new HandlerThread("OpenCVAnalysis");
        analyzerThread.start();


        ImageAnalysisConfig imageAnalysisConfig = new ImageAnalysisConfig.Builder()
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setCallbackHandler(new Handler(analyzerThread.getLooper()))
                .setImageQueueDepth(1).build();

        Log.e("LogOne", "log==03");

        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);


        Log.e("LogOne", "log==04");
        imageAnalysis.setAnalyzer(
                new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(ImageProxy imageProxy, int degrees) {
                        Log.e("LogOne", "log==0");

                        if (imageProxy == null || imageProxy.getImage() == null) {
                            return;
                        }
                        Image mediaImage = imageProxy.getImage();

                        int rotation = degreesToFirebaseRotation(degrees);
                        FirebaseVisionImage image =
                                FirebaseVisionImage.fromMediaImage(mediaImage, rotation);

                        FirebaseVisionFaceDetectorOptions options =
                                new FirebaseVisionFaceDetectorOptions.Builder()
                                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                                        .setMinFaceSize(0.15f)
                                        .enableTracking()
                                        .build();


//                        FirebaseVisionFaceDetectorOptions options =
//                                new FirebaseVisionFaceDetectorOptions.Builder()
//                                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
//                                        .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
//                                        .build();


                        detector = FirebaseVision.getInstance()
                                .getVisionFaceDetector(options);
                        Log.e("LogOne", "log==3");


                        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                                processFaceList(firebaseVisionFaces);
                            }

                        });
                    }
                });


        //bind to lifecycle:
        CameraX.bindToLifecycle((LifecycleOwner) this, preview, imageAnalysis);


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


    private void processFaceList(List<FirebaseVisionFace> faces) {
        Log.e("LogOne", "log==5");
        // [START mlkit_face_list]
        // graphicOverlay.clear();

        if (faces.size() == 0) {

            return;
        }


        graphicOverlay.clear();

        for (FirebaseVisionFace face : faces) {


            Rect bounds = face.getBoundingBox();
            FaceContourGraphic faceContourGraphic = new FaceContourGraphic(graphicOverlay, face);
            //  GraphicOverlay.Graphic textGraphic = new TextGraphic(graphicOverlay, bounds);


            //  TextGraphic textGraphic = new TextGraphic(graphicOverlay, bounds);
            graphicOverlay.add(faceContourGraphic);
            Log.e("LogOne", "log==6" + faceContourGraphic);

            float rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
            float rotZ = face.getHeadEulerAngleZ();  // Head is tilted sideways rotZ degrees

            // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
            // nose available):
            FirebaseVisionFaceLandmark leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR);
            if (leftEar != null) {
                FirebaseVisionPoint leftEarPos = leftEar.getPosition();
                Log.e("LogOne", "log==7" + leftEar);
            }

            // If contour detection was enabled:
            List<FirebaseVisionPoint> leftEyeContour =
                    face.getContour(FirebaseVisionFaceContour.LEFT_EYE).getPoints();
            List<FirebaseVisionPoint> upperLipBottomContour =
                    face.getContour(FirebaseVisionFaceContour.UPPER_LIP_BOTTOM).getPoints();

            // If classification was enabled:
            if (face.getSmilingProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                float smileProb = face.getSmilingProbability();
                Log.e("LogOne", "log==8" + smileProb);
            }
            if (face.getRightEyeOpenProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                float rightEyeOpenProb = face.getRightEyeOpenProbability();
                Log.e("LogOne", "log==0" + rightEyeOpenProb);
            }

            // If face tracking was enabled:
            if (face.getTrackingId() != FirebaseVisionFace.INVALID_ID) {
                int id = face.getTrackingId();
                Log.e("LogOne", "log==0" + id);
            }
        }
        // [END mlkit_face_list]
    }

    private int degreesToFirebaseRotation(int degrees) {
        switch (degrees) {
            case 0:
                return FirebaseVisionImageMetadata.ROTATION_0;
            case 90:
                return FirebaseVisionImageMetadata.ROTATION_90;
            case 180:
                return FirebaseVisionImageMetadata.ROTATION_180;
            case 270:
                return FirebaseVisionImageMetadata.ROTATION_270;
            default:
                throw new IllegalArgumentException(
                        "Rotation must be 0, 90, 180, or 270.");
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        try {
            detector.close();
            if (analyzerThread != null) {
                analyzerThread.quit();
                analyzerThread = null; // Object is no more required.
            }
        } catch (IOException e) {
            Log.e("tag", "Exception thrown while trying to close Text Detector: " + e);
        }
    }
}
