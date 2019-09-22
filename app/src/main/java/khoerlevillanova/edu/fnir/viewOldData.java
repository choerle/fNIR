package khoerlevillanova.edu.fnir;

import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static khoerlevillanova.edu.fnir.DeviceControlActivity.EXTRAS_DEVICE_NAME;

public class viewOldData extends AppCompatActivity {


    //TODO: plot data
    //TODO: organize this code
    private final String TAG = "DeviceControlActivity";

    private String fileName;

    private LineGraphSeries<DataPoint> series_HB;
    private LineGraphSeries<DataPoint> series_HBO2;
    private GraphView dataGraph;

    private ArrayList<Double> data_time = new ArrayList<>();
    private ArrayList<Double> HB = new ArrayList<>();
    private ArrayList<Double> HBO2 = new ArrayList<>();
    private ArrayList<Double> inputData = new ArrayList<>();
    private String inputString = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_old_data);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        dataGraph = findViewById(R.id.dataGraph);
        //Getting the file name
        final Intent intent = getIntent();
        fileName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        openFile();
        graphData();
    }



    //Opens the file selected on the previous page
    public void openFile(){

        try {
            FileInputStream fileInputStream = openFileInput(fileName);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String text = null;

            while ((text = bufferedReader.readLine()) != null) {
                inputString = (inputString + text);
            }

            //Extracting all numbers from file
            Pattern p = Pattern.compile("(\\d+(?:\\.\\d+))");
            Matcher m = p.matcher(inputString);
            while(m.find()) {
                inputData.add(Double.parseDouble(m.group(1)));
            }

            //The input data needs to be split into its 3 components
            splitArrayList();

            bufferedReader.close();
        }

        catch(FileNotFoundException e){
        }

        catch(IOException e){
        }
    }//TODO: switch graph colors on original graph


    //Splitting up the input array into an array for time, HB, and HBO2
    public void splitArrayList(){
        int size = inputData.size()/3;
        for(int i = 0; i < 3; ++i){
            for(int j = 0; j < size; ++j){
                //Elements j to size, time
                if(i == 0) HB.add(inputData.get(j));
                //Elements (j + size) to 2*size, HB
                if(i == 1) HBO2.add(inputData.get(j + size));
                //Elements (j + 2*size) to end
                if(i == 2) data_time.add(inputData.get(j + 2*size));
            }
        }
    }


    //Graphing the HB, HBO2, and time
    public void graphData(){

        //Deoxygenated hemoglobin series initialization
        series_HB = new LineGraphSeries<>();
        series_HB.setColor(Color.RED);
        dataGraph.addSeries(series_HB);
        series_HB.setTitle("Oxygenated");

        //Oxygenated hemoglobin series initialization
        series_HBO2 = new LineGraphSeries<>();
        series_HBO2.setColor(Color.BLUE);
        dataGraph.addSeries(series_HBO2);
        series_HBO2.setTitle("Deoxygenated");

        //Adding the data points to the grpah
        for(int i = 0; i < data_time.size(); ++i){

            DataPoint dataPoint = new DataPoint(data_time.get(i), HB.get(i));
            series_HB.appendData(dataPoint, true,50000);

            DataPoint dataPoint2 = new DataPoint(data_time.get(i), HBO2.get(i));
            series_HBO2.appendData(dataPoint2, true,50000);
        }
    }
}
