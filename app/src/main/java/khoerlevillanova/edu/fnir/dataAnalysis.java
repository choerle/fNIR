package khoerlevillanova.edu.fnir;

import java.util.ArrayList;

// This class is used to perform analysis on the raw voltage readings
// When the class is initialized, the arrays for storing voltages and processed data will be initialized
// Then there will be a number of sample points, a constant set in DeviceControlActivity,
// that will be used to get a baseline reading.
// After getting the baseline reading, all further data points will be analyzed instantly and sent back
// to DeviceControlActivity for graphing

public class dataAnalysis {

    //Constants for blood oxygen calculation based on 730nm and 850nm light
    private final double eHB_730 = 1.1022;
    private final double eHBO2_730 = 0.390;
    private final double eHB_850 = 0.69132;
    private final double eHBO2_850 = 1.058;
    private final double L = 0.015;

    private final int filterLength = 5;

    //Baseline values
    private Double baseline_730;
    private Double baseline_850;

    //Optical density arrays
    private ArrayList<Double> OD_730;
    private ArrayList<Double> OD_850;

    //Data Storage Array
    private ArrayList<Double> HB;
    private ArrayList<Double> HBO2;
    private ArrayList<Double> data_730;
    private ArrayList<Double> data_850;

    private Double[] h = new Double[]{.2,.2,.2,.2,.2};


    double sampleCount;


    dataAnalysis(ArrayList<Double> HB1, ArrayList<Double> HBO21, ArrayList<Double> data_7301,
                 ArrayList<Double> data_8501, double sampleCount1) {



        OD_730 = new ArrayList<>();
        OD_850 = new ArrayList<>();

        HB = HB1;
        HBO2 = HBO21;
        data_730 = data_7301;
        data_850 = data_8501;

        sampleCount = sampleCount1;

        getBaseLine();
    }


    //Creating the baseline arrays from first sampleCount samples
    private void getBaseLine() {

        Double sum730 = 0.0;
        Double sum850 = 0.0;

        for (int i = 0; i < sampleCount; ++i) {
            sum730 += data_730.get(i);
            sum850 += data_850.get(i);
        }

        baseline_730 = sum730 / sampleCount;
        baseline_850 = sum850 / sampleCount;
    }


    //Getting the oxygenated hemoglobin levels from voltages and adding to the given arraylist
    public void addHemoglobin(Integer count) {

        OD_730.add(-Math.log10(data_730.get(count) / baseline_730));
        OD_850.add(-Math.log10(data_850.get(count) / baseline_850));

        HB.add(((OD_850.get(count) * eHBO2_730) - (OD_730.get(count) * eHBO2_850)) / ((eHBO2_730 * eHB_850) - (eHBO2_850 * eHB_730)) / L);
        HBO2.add(((OD_730.get(count) * eHB_850) - (OD_850.get(count) * eHB_730)) / ((eHBO2_730 * eHB_850) - (eHBO2_850 * eHB_730)) / L);
    }


    //This function adds the most recent voltage readings onto the arrays and performing a simple filter on the samples
    //This sums up the previous filterLength samples, and then divides by filterLength to find the avergae of the last couple samples
    public void addSamplesToVoltageArray(Double data_730d, Double data_850d, int count){

        /*int numberSamples = 0;

        for(int i = 0; i < filterLength; ++i){
            if(data_730.get(count-i-1) != null){
                data_730d += data_730.get(count-i-1);
                data_850d += data_850.get(count-i-1);
                ++numberSamples;
            }
        }

        data_730d = data_730d/numberSamples;*/
        data_730.add(data_730d);

        //data_730d = data_730d/numberSamples;
        data_850.add(data_850d);
    }
}