package khoerlevillanova.edu.fnir;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.os.Environment.DIRECTORY_DOCUMENTS;
import static khoerlevillanova.edu.fnir.DeviceControlActivity.LOADINGDATA_LOG_TAG;
import static khoerlevillanova.edu.fnir.DeviceControlActivity.DIRECTORY_NAME;

public class viewOldData extends AppCompatActivity{

    private final String TAG = "viewOldData";
    private String fileName;
    //Three arrays to hold data that is loaded from app storage
    private ArrayList<Double> saved_data_one = new ArrayList<>();
    private ArrayList<Double> saved_data_two = new ArrayList<>();
    private ArrayList<Double> savedTime = new ArrayList<>();

    private GraphView dataGraph;
    public Integer count = 0;
    private double time = 0;
    private int SAMPLING_RATE = 100; //In milliseconds
    private LineGraphSeries<DataPoint> series_HB;
    private LineGraphSeries<DataPoint> series_HBO2;
    private LineGraphSeries<DataPoint> series_730;
    private LineGraphSeries<DataPoint> series_850;
    private Button loadNewDataSet;
    boolean data_analysis;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_old_data);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Graph initialization
        dataGraph = findViewById(R.id.dataGraph);
        setUpGraph();
        //TODO: loading raw vs analyzed data
        //Load all saved device files on the screen in a listview
        displayDataFiles();

        loadNewDataSet = findViewById(R.id.loadNewDataSet);
        loadNewDataSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: loading raw vs analyzed data
                setUpGraph();
                //Load all saved device files on the screen in a listview
                displayDataFiles();
            }
        });
    }

    public void setUpGraph(){
        dataGraph.getViewport().setYAxisBoundsManual(true);
        dataGraph.getViewport().setMinY(-20);
        dataGraph.getViewport().setMaxY(30);
        dataGraph.getViewport().setXAxisBoundsManual(true);
        dataGraph.setTitleTextSize(110);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(70);
        dataGraph.getGridLabelRenderer().setVerticalAxisTitleTextSize(70);
        dataGraph.getGridLabelRenderer().setGridColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
        dataGraph.setTitleColor(Color.WHITE);
        dataGraph.setTitle("fNIRS");
        dataGraph.getGridLabelRenderer().setPadding(50);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        dataGraph.getGridLabelRenderer().setHighlightZeroLines(true);
    }




    //Load all saved device files on the screen in an Alert Dialog
    public void displayDataFiles() {

        //This is accomplished with a pop up dialog
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(viewOldData.this);
        builderSingle.setTitle("Saved Files: ");

        //These lines of code find the directory where this app saves its files and then puts all of the file names into a string array
        String path = (Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).toString()+ "/" + DIRECTORY_NAME);
        File f = new File(path);
        String[] files = f.list();

        //If the directory is empty, do not try to list all the files in it or the app will crash
        //Normally the directory will never be empty, but this is a precaution
        if(files != null) {

            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(viewOldData.this, R.layout.file_listview, R.id.fileName, files);

            builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            //Creates buttons to determine what happens with selected file
            builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    fileName = arrayAdapter.getItem(which);
                    AlertDialog.Builder builderInner = new AlertDialog.Builder(viewOldData.this);
                    builderInner.setMessage(fileName);
                    builderInner.setTitle("You selected: ");
                    //Opens the selected file
                    builderInner.setPositiveButton("Open", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openFile();
                            dialog.dismiss();
                        }
                    });
                    //Deletes the selected file
                    builderInner.setNegativeButton("Delete File", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AlertDialog.Builder builderDelete = new AlertDialog.Builder(viewOldData.this);
                            builderDelete.setTitle("Are you sure you want to delete " + fileName + "?");

                            builderDelete.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    deleteFile();
                                    dialog.dismiss();
                                }
                            });
                            builderDelete.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            });

                            builderDelete.show();


                        }
                    });
                    builderInner.show();
                }
            });
            builderSingle.show();
        }

        else Toast.makeText(viewOldData.this, "You have no saved files", Toast.LENGTH_LONG).show();
    }




    //Opens the file selected from the alert dialog
    public void openFile () {

        try {
            File extStore = getPublicAlbumStorageDir(DIRECTORY_NAME);   //Creating the directory, within external storage, to hold this apps files
            String path = extStore.getAbsolutePath() + "/" + fileName;  //The address of the file we are creating
            File myFile = new File(path);
            Log.d("LOADING DATA", "Attempting to load file: " + fileName);
            FileInputStream fileInputStream = new FileInputStream(myFile);
            DataInputStream in = new DataInputStream(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));

            String inputString = null;
            String text;

            while ((text = bufferedReader.readLine()) != null) {
                inputString = (inputString + text);
            }

            if(inputString != null) {
                //Extracting all numbers from file
                ArrayList<Double> inputData = new ArrayList<>();
                Pattern p = Pattern.compile("(\\d+(?:\\.\\d+))");
                Matcher m = p.matcher(inputString);
                while (m.find()) {
                    inputData.add(Double.parseDouble(m.group(1)));
                }

                Log.d(TAG, LOADINGDATA_LOG_TAG + "Successfully loaded data. Graphing...");
                //The input data needs to be split into its 3 components
                splitArrayList(inputData);
                //Graph the data
                graphSavedData();
            }

            else{
                Log.d(TAG, LOADINGDATA_LOG_TAG + "is empty");
            }

            bufferedReader.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, LOADINGDATA_LOG_TAG + "File Not Found Exception");
        } catch (IOException e) {
            Log.d(TAG, LOADINGDATA_LOG_TAG + "IO Exception");
        }
    }//TODO: switch graph colors on original graph




    //Deletes the selected file
    public boolean deleteFile(){
        File extStore = getPublicAlbumStorageDir(DIRECTORY_NAME);   //Creating the directory, within external storage, to hold this apps files
        String path = extStore.getAbsolutePath() + "/" + fileName;  //The address of the file we are creating
        File myFile = new File(path);
        return myFile.delete();
    }




    //Returns a directory within the main external storage folder, named DIRECTORY_NAME
    //If this directory does not exist, this function will attempt to create it
    private File getPublicAlbumStorageDir(String DIRECTORY_NAME) {
        //Creating the file for the desired directory
        File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), DIRECTORY_NAME);
        //If the directory does not exist, try to create it
        if (!file.exists()) {
            boolean created = file.mkdirs();
            //If the directory can not be created
            if (!created) {
                Log.d(TAG, LOADINGDATA_LOG_TAG + "Directory not created");
            }
            else Log.d(TAG, LOADINGDATA_LOG_TAG + "Directory created!! YES!");
        }
        return file;
    }




    //Splitting up the input array into an array for time, HB, and HBO2
    //The last character of the file is 1 if the data is HB and HBO2 and 0 if raw voltages
    public void splitArrayList (ArrayList<Double> inputData) {
        int size = (inputData.size()-1) / 3;

        if(inputData.get(inputData.size()-1) == -1.0){
            data_analysis = true;
        }
        else{
            data_analysis = false;
        }

        saved_data_one = new ArrayList<>();
        saved_data_two = new ArrayList<>();
        savedTime = new ArrayList<>();

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < size; ++j) {
                //Elements j to size
                if (i == 0) saved_data_one.add(inputData.get(j));
                //Elements (j + size) to 2*size, HB
                if (i == 1) saved_data_two.add(inputData.get(j + size));
                //Elements (j + 2*size) to end
                if (i == 2) savedTime.add(inputData.get(j + 2 * size));
            }
        }
    }





    //Graphing the data file that was just opened
    public void graphSavedData(){

        Log.d(TAG, LOADINGDATA_LOG_TAG + "Graphing...");

        //Old series must be removed from the graph
        dataGraph.removeAllSeries();

        //Graphing HB and HBO2
        if(data_analysis) {

            //Deoxygenated hemoglobin series initialization
            series_HB = new LineGraphSeries<>();
            series_HB.setColor(Color.RED);
            dataGraph.addSeries(series_HB);
            series_HB.setTitle("Deoxygenated");

            //Oxygenated hemoglobin series initialization
            series_HBO2 = new LineGraphSeries<>();
            series_HBO2.setColor(Color.BLUE);
            dataGraph.addSeries(series_HBO2);
            series_HBO2.setTitle("Oxygenated");

            Double smallest = 10000.0;
            Double largest = 0.0;

            //Adding the data points to the grpah
            for (int i = 0; i < savedTime.size(); ++i) {

                if(saved_data_one.get(i) > largest){
                    largest = saved_data_one.get(i);
                }
                if(saved_data_two.get(i) > largest){
                    largest = saved_data_two.get(i);
                }

                if(saved_data_one.get(i) < smallest){
                    smallest = saved_data_one.get(i);
                }
                if(saved_data_two.get(i) < smallest){
                    smallest = saved_data_two.get(i);
                }

                DataPoint dataPoint = new DataPoint(savedTime.get(i), saved_data_one.get(i));
                series_HB.appendData(dataPoint, true, 50000);

                DataPoint dataPoint2 = new DataPoint(savedTime.get(i), saved_data_two.get(i));
                series_HBO2.appendData(dataPoint2, true, 50000);
            }

            //Setting up the graph for analyzed data
            dataGraph.getViewport().setMinX(0);
            dataGraph.getViewport().setMaxX(savedTime.get(savedTime.size() - 1));
            dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Hemodynamic Changes");
            dataGraph.setTitle("Oxygenation Levels");
            dataGraph.getLegendRenderer().setVisible(true);
            dataGraph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);
            dataGraph.getLegendRenderer().setTextColor(Color.WHITE);
            dataGraph.getLegendRenderer().setTextSize(60);
            dataGraph.getLegendRenderer().setMargin(20);
            dataGraph.getLegendRenderer().setSpacing(40);
            dataGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
            dataGraph.getViewport().setMinY(smallest - 5);
            dataGraph.getViewport().setMaxY(largest + 5);
        }

        //Graphing voltages
        else{
            //Deoxygenated hemoglobin series initialization
            series_730 = new LineGraphSeries<>();
            series_730.setColor(Color.RED);
            dataGraph.addSeries(series_730);
            series_730.setTitle("730 nm");

            //Oxygenated hemoglobin series initialization
            series_850 = new LineGraphSeries<>();
            series_850.setColor(Color.BLUE);
            dataGraph.addSeries(series_850);
            series_850.setTitle("850 nm");

            Double smallest = 10000.0;
            Double largest = 0.0;

            //Adding the data points to the grpah
            for (int i = 0; i < savedTime.size(); ++i) {

                if(saved_data_one.get(i) > largest){
                    largest = saved_data_one.get(i);
                }
                if(saved_data_two.get(i) > largest){
                    largest = saved_data_two.get(i);
                }

                if(saved_data_one.get(i) < smallest){
                    smallest = saved_data_one.get(i);
                }
                if(saved_data_two.get(i) < smallest){
                    smallest = saved_data_two.get(i);
                }

                DataPoint dataPoint = new DataPoint(savedTime.get(i), saved_data_one.get(i));
                series_730.appendData(dataPoint, true, 50000);

                DataPoint dataPoint2 = new DataPoint(savedTime.get(i), saved_data_two.get(i));
                series_850.appendData(dataPoint2, true, 50000);
            }

            //Setting up the graph for raw data
            dataGraph.getViewport().setMinX(0);
            dataGraph.getViewport().setMaxX(15);
            dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Voltage");//TODO: what is the y axis?
            dataGraph.setTitle("Light Sensor Data");
            dataGraph.getLegendRenderer().setVisible(true);
            dataGraph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);
            dataGraph.getLegendRenderer().setTextColor(Color.WHITE);
            dataGraph.getLegendRenderer().setTextSize(60);
            dataGraph.getLegendRenderer().setMargin(20);
            dataGraph.getLegendRenderer().setSpacing(40);
            dataGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
            dataGraph.getViewport().setMinY(smallest - 100);
            dataGraph.getViewport().setMaxY(largest + 100);
        }
    }
}