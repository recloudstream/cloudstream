package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.graphics.Point;
import android.graphics.PointF;
import android.opengl.GLES20;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;


public class GlToneCurveFilter extends GlFilter {

    private final static String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    " varying highp vec2 vTextureCoord;\n" +
                    " uniform lowp sampler2D sTexture;\n" +
                    " uniform mediump sampler2D toneCurveTexture;\n" +
                    "\n" +
                    " void main()\n" +
                    " {\n" +
                    "     lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
                    "     lowp float redCurveValue = texture2D(toneCurveTexture, vec2(textureColor.r, 0.0)).r;\n" +
                    "     lowp float greenCurveValue = texture2D(toneCurveTexture, vec2(textureColor.g, 0.0)).g;\n" +
                    "     lowp float blueCurveValue = texture2D(toneCurveTexture, vec2(textureColor.b, 0.0)).b;\n" +
                    "\n" +
                    "     gl_FragColor = vec4(redCurveValue, greenCurveValue, blueCurveValue, textureColor.a);\n" +
                    " }";

    private PointF[] rgbCompositeControlPoints;
    private PointF[] redControlPoints;
    private PointF[] greenControlPoints;
    private PointF[] blueControlPoints;

    private ArrayList<Float> rgbCompositeCurve;
    private ArrayList<Float> redCurve;
    private ArrayList<Float> greenCurve;
    private ArrayList<Float> blueCurve;

    private final LinkedList<Runnable> runOnDraw;

    private int[] textures = new int[1];

    private byte[] toneCurveByteArray;


    public GlToneCurveFilter(InputStream input) {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
        PointF[] defaultCurvePoints = new PointF[]{new PointF(0.0f, 0.0f), new PointF(0.5f, 0.5f), new PointF(1.0f, 1.0f)};
        rgbCompositeControlPoints = defaultCurvePoints;
        redControlPoints = defaultCurvePoints;
        greenControlPoints = defaultCurvePoints;
        blueControlPoints = defaultCurvePoints;

        runOnDraw = new LinkedList<>();

        setFromCurveFileInputStream(input);

        setRgbCompositeControlPoints(rgbCompositeControlPoints);
        setRedControlPoints(redControlPoints);
        setGreenControlPoints(greenControlPoints);
        setBlueControlPoints(blueControlPoints);

    }

    @Override
    public void setup() {
        super.setup();// 1
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        while (!runOnDraw.isEmpty()) {
            runOnDraw.removeFirst().run();
        }
    }

    @Override
    public void onDraw() {

        int offsetDepthMapTextureUniform = getHandle("toneCurveTexture");// 3

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256 /*width*/, 1 /*height*/, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(toneCurveByteArray));

        GLES20.glUniform1i(offsetDepthMapTextureUniform, 3);
    }

    private void setFromCurveFileInputStream(InputStream input) {
        try {
            int version = readShort(input);
            int totalCurves = readShort(input);

            ArrayList<PointF[]> curves = new ArrayList<PointF[]>(totalCurves);
            float pointRate = 1.0f / 255;

            for (int i = 0; i < totalCurves; i++) {
                // 2 bytes, Count of points in the curve (short integer toAndroidFormat 2...19)
                short pointCount = readShort(input);

                PointF[] points = new PointF[pointCount];

                // point count * 4
                // Curve points. Each curve point is a pair of short integers where
                // the first number is the output getNode (vertical coordinate on the
                // Curves dialog graph) and the second is the input getNode. All coordinates have range 0 to 255.
                for (int j = 0; j < pointCount; j++) {
                    short y = readShort(input);
                    short x = readShort(input);

                    points[j] = new PointF(x * pointRate, y * pointRate);
                }

                curves.add(points);
            }
            input.close();

            rgbCompositeControlPoints = curves.get(0);
            redControlPoints = curves.get(1);
            greenControlPoints = curves.get(2);
            blueControlPoints = curves.get(3);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private short readShort(InputStream input) throws IOException {
        return (short) (input.read() << 8 | input.read());
    }

    private void setRgbCompositeControlPoints(PointF[] points) {
        rgbCompositeControlPoints = points;
        rgbCompositeCurve = createSplineCurve(rgbCompositeControlPoints);
        updateToneCurveTexture();
    }

    private void setRedControlPoints(PointF[] points) {
        redControlPoints = points;
        redCurve = createSplineCurve(redControlPoints);
        updateToneCurveTexture();
    }

    private void setGreenControlPoints(PointF[] points) {
        greenControlPoints = points;
        greenCurve = createSplineCurve(greenControlPoints);
        updateToneCurveTexture();
    }

    private void setBlueControlPoints(PointF[] points) {
        blueControlPoints = points;
        blueCurve = createSplineCurve(blueControlPoints);
        updateToneCurveTexture();
    }

    private void runOnDraw(final Runnable runnable) {
        synchronized (runOnDraw) {
            runOnDraw.addLast(runnable);
        }
    }

    private void updateToneCurveTexture() {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

                if ((redCurve.size() >= 256) && (greenCurve.size() >= 256) && (blueCurve.size() >= 256) && (rgbCompositeCurve.size() >= 256)) {
                    toneCurveByteArray = new byte[256 * 4];
                    for (int currentCurveIndex = 0; currentCurveIndex < 256; currentCurveIndex++) {
                        // BGRA for upload to texture
                        toneCurveByteArray[currentCurveIndex * 4 + 2] = (byte) ((int) Math.min(Math.max(currentCurveIndex + blueCurve.get(currentCurveIndex) + rgbCompositeCurve.get(currentCurveIndex), 0), 255) & 0xff);
                        toneCurveByteArray[currentCurveIndex * 4 + 1] = (byte) ((int) Math.min(Math.max(currentCurveIndex + greenCurve.get(currentCurveIndex) + rgbCompositeCurve.get(currentCurveIndex), 0), 255) & 0xff);
                        toneCurveByteArray[currentCurveIndex * 4] = (byte) ((int) Math.min(Math.max(currentCurveIndex + redCurve.get(currentCurveIndex) + rgbCompositeCurve.get(currentCurveIndex), 0), 255) & 0xff);
                        toneCurveByteArray[currentCurveIndex * 4 + 3] = (byte) (255 & 0xff);
                    }

                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256 /*width*/, 1 /*height*/, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(toneCurveByteArray));
                }
//        Buffer pixels!
//        GLES20.glTexImage2D(int target,
//            int level,
//            int internalformat,
//            int width,
//            int height,
//            int border,
//            int format,
//            int type,
//            java.nio.Buffer pixels);
            }
        });
    }

    private ArrayList<Float> createSplineCurve(PointF[] points) {
        if (points == null || points.length <= 0) {
            return null;
        }

        // Sort the array
        PointF[] pointsSorted = points.clone();
        Arrays.sort(pointsSorted, new Comparator<PointF>() {
            @Override
            public int compare(PointF point1, PointF point2) {
                if (point1.x < point2.x) {
                    return -1;
                } else if (point1.x > point2.x) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        // Convert toAndroidFormat (0, 1) to (0, 255).
        Point[] convertedPoints = new Point[pointsSorted.length];
        for (int i = 0; i < points.length; i++) {
            PointF point = pointsSorted[i];
            convertedPoints[i] = new Point((int) (point.x * 255), (int) (point.y * 255));
        }

        ArrayList<Point> splinePoints = createSplineCurve2(convertedPoints);

        // If we have a first point like (0.3, 0) we'll be missing some points at the beginning
        // that should be 0.
        Point firstSplinePoint = splinePoints.get(0);
        if (firstSplinePoint.x > 0) {
            for (int i = firstSplinePoint.x; i >= 0; i--) {
                splinePoints.add(0, new Point(i, 0));
            }
        }

        // Insert points similarly at the end, if necessary.
        Point lastSplinePoint = splinePoints.get(splinePoints.size() - 1);
        if (lastSplinePoint.x < 255) {
            for (int i = lastSplinePoint.x + 1; i <= 255; i++) {
                splinePoints.add(new Point(i, 255));
            }
        }

        // Prepare the spline points.
        ArrayList<Float> preparedSplinePoints = new ArrayList<Float>(splinePoints.size());
        for (Point newPoint : splinePoints) {
            Point origPoint = new Point(newPoint.x, newPoint.x);

            float distance = (float) Math.sqrt(Math.pow((origPoint.x - newPoint.x), 2.0) + Math.pow((origPoint.y - newPoint.y), 2.0));

            if (origPoint.y > newPoint.y) {
                distance = -distance;
            }

            preparedSplinePoints.add(distance);
        }

        return preparedSplinePoints;
    }

    private ArrayList<Point> createSplineCurve2(Point[] points) {
        ArrayList<Double> sdA = createSecondDerivative(points);

        // Is [points count] equal to [sdA count]?
//    int n = [points count];
        int n = sdA.size();
        if (n < 1) {
            return null;
        }
        double sd[] = new double[n];

        // From NSMutableArray to sd[n];
        for (int i = 0; i < n; i++) {
            sd[i] = sdA.get(i);
        }


        ArrayList<Point> output = new ArrayList<Point>(n + 1);

        for (int i = 0; i < n - 1; i++) {
            Point cur = points[i];
            Point next = points[i + 1];

            for (int x = cur.x; x < next.x; x++) {
                double t = (double) (x - cur.x) / (next.x - cur.x);

                double a = 1 - t;
                double b = t;
                double h = next.x - cur.x;

                double y = a * cur.y + b * next.y + (h * h / 6) * ((a * a * a - a) * sd[i] + (b * b * b - b) * sd[i + 1]);

                if (y > 255.0) {
                    y = 255.0;
                } else if (y < 0.0) {
                    y = 0.0;
                }

                output.add(new Point(x, (int) Math.round(y)));
            }
        }

        // If the last point is (255, 255) it doesn't get added.
        if (output.size() == 255) {
            output.add(points[points.length - 1]);
        }
        return output;
    }

    private ArrayList<Double> createSecondDerivative(Point[] points) {
        int n = points.length;
        if (n <= 1) {
            return null;
        }

        double matrix[][] = new double[n][3];
        double result[] = new double[n];
        matrix[0][1] = 1;
        // What about matrix[0][1] and matrix[0][0]? Assuming 0 for now (Brad L.)
        matrix[0][0] = 0;
        matrix[0][2] = 0;

        for (int i = 1; i < n - 1; i++) {
            Point P1 = points[i - 1];
            Point P2 = points[i];
            Point P3 = points[i + 1];

            matrix[i][0] = (double) (P2.x - P1.x) / 6;
            matrix[i][1] = (double) (P3.x - P1.x) / 3;
            matrix[i][2] = (double) (P3.x - P2.x) / 6;
            result[i] = (double) (P3.y - P2.y) / (P3.x - P2.x) - (double) (P2.y - P1.y) / (P2.x - P1.x);
        }

        // What about result[0] and result[n-1]? Assuming 0 for now (Brad L.)
        result[0] = 0;
        result[n - 1] = 0;

        matrix[n - 1][1] = 1;
        // What about matrix[n-1][0] and matrix[n-1][2]? For now, assuming they are 0 (Brad L.)
        matrix[n - 1][0] = 0;
        matrix[n - 1][2] = 0;

        // solving pass1 (up->down)
        for (int i = 1; i < n; i++) {
            double k = matrix[i][0] / matrix[i - 1][1];
            matrix[i][1] -= k * matrix[i - 1][2];
            matrix[i][0] = 0;
            result[i] -= k * result[i - 1];
        }
        // solving pass2 (down->up)
        for (int i = n - 2; i >= 0; i--) {
            double k = matrix[i][2] / matrix[i + 1][1];
            matrix[i][1] -= k * matrix[i + 1][0];
            matrix[i][2] = 0;
            result[i] -= k * result[i + 1];
        }

        ArrayList<Double> output = new ArrayList<Double>(n);
        for (int i = 0; i < n; i++) output.add(result[i] / matrix[i][1]);

        return output;
    }


}
