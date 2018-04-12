package com.example.myi.a1steptracker;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public static final String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";
    public static final int PEAK_BUFFER_SIZE = 40;

    private SensorManager _sensorManager;
    Sensor _accelerometer, _stepCounter;
    GraphView graph;
    LineGraphSeries<DataPoint> _rawSeries, _smoothedSeries;

    private TextView _magnitudeVal, _smoothedVal, _smoothed2Val, _stepTrackerCount, _stepDetectorCount;
    private int _graphXindex;

    private double _smoothingTotal;
    private double[] _smoothingBuffer;
    private int _smoothingIndex;
    private boolean _smoothingBufferFull;

    private double _peakTotal, _peakMean;
    private int _peakCount, _stepCount, _aIndex;
    private double[] _peakBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        _accelerometer = _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        _stepCounter = _sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        _sensorManager.registerListener(this, _accelerometer, SensorManager.SENSOR_DELAY_UI);
        _sensorManager.registerListener(this, _stepCounter, SensorManager.SENSOR_DELAY_NORMAL);

        graph = (GraphView) findViewById(R.id.graph);
        _rawSeries = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0, 9),
        });
        graph.addSeries(_rawSeries);
        _rawSeries.setColor(Color.RED);
        _smoothedSeries = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0, 9),
        });
        graph.addSeries(_smoothedSeries);

        // legend
        _rawSeries.setTitle("Raw");
        _smoothedSeries.setTitle("Smoothed");
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

        // set manual X bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(100);

        // set manual Y bounds
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(20);
        _graphXindex = 0;

        _magnitudeVal = (TextView)findViewById(R.id.magnitudeText);
        _smoothedVal = (TextView)findViewById(R.id.smoothedText);
        _stepTrackerCount = (TextView)findViewById(R.id.stepTrackerCount);
        _stepDetectorCount = (TextView)findViewById(R.id.stepDetectorCount);

        _smoothingBuffer = new double[20];
        _smoothingIndex = 0;
        _smoothingBufferFull = false;

        _peakBuffer = new double[PEAK_BUFFER_SIZE];
        _aIndex = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            double magnitude = Math.sqrt(x*x + y*y + z*z);
            _magnitudeVal.setText(String.format("Raw: %2.2f", magnitude));
            _rawSeries.appendData(new DataPoint(++_graphXindex, magnitude), true, 100);

            double s = smoothValue(magnitude);
            _smoothedVal.setText(String.format("Smo: %2.2f", s));
            _smoothedSeries.appendData(new DataPoint(++_graphXindex, s), true, 100);

            _peakBuffer[_aIndex++] = s;

            if (_aIndex > PEAK_BUFFER_SIZE-1) {
                _aIndex = 0;
                peakDetection();
                stepDetection();
            }
            _stepTrackerCount.setText(String.valueOf(_stepCount));
        }
        else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            _stepDetectorCount.setText(String.valueOf(event.values[0]));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    // Smoothing algorithm was taken from Arduino's smoothing as referenced in Prof
    // sample code: https://www.arduino.cc/en/Tutorial/Smoothing
    public double smoothValue(double value) {
        if (_smoothingBufferFull) {
            // subtract the last reading:
            _smoothingTotal = _smoothingTotal - _smoothingBuffer[_smoothingIndex];
        }
        // add the reading to the total & store:
        _smoothingTotal += value;
        _smoothingBuffer[_smoothingIndex] = value;
        // advance to the next position in the array:
        _smoothingIndex++;

        // if we're at the end of the array...
        if (_smoothingIndex >= 20) {
            // ...wrap around to the beginning:
            _smoothingIndex = 0;
            _smoothingBufferFull = true;
        }

        if (_smoothingBufferFull) {
            return _smoothingTotal/20;
        }
        return _smoothingTotal/_smoothingIndex;
    }

    // Peak and Step detection referenced from Martin Mladenov and Michael Mock's paper titled
    // "A Step Counter Service for Java-Enabled Devices Using a Built-in Accelerometer
    public void peakDetection() {
        double fs = 0, bs = 0; // Forward Slope, Backward Slope

        for (int k=0; k<PEAK_BUFFER_SIZE; k++) {
            if (k < PEAK_BUFFER_SIZE-1)
                fs = _peakBuffer[k+1]-_peakBuffer[k];
            if (k > 0)
                bs = _peakBuffer[k]-_peakBuffer[k-1];

            if (fs < -0.02 && bs > 0.02) {
                _peakCount++;
                _peakTotal += _peakBuffer[k];
            }
        }
        _peakMean = _peakTotal/_peakCount;
    }

    public void stepDetection() {
        double fs = 0, bs = 0; // Forward Slope, Backward Slope

        for (int k=0; k<PEAK_BUFFER_SIZE; k++) {
            if (k < PEAK_BUFFER_SIZE-1)
                fs = _peakBuffer[k+1]-_peakBuffer[k];
            if (k > 0)
                bs = _peakBuffer[k]-_peakBuffer[k-1];

            if (fs < -0.02 && bs > 0.02 && _peakBuffer[k] > 0.7*_peakMean && _peakBuffer[k] > 9)
                _stepCount=_peakCount+1;
        }
    }

}
