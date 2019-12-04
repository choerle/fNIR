package khoerlevillanova.edu.fnir;

import android.util.Log;

import java.util.ArrayList;

public class fir1 {
        private int length;
	    private double[] delayLine;
	    private double[] impulseResponse;
		private double[] coefs;
	    private int count = 0;

	    fir1(int filterorder) {
            length = filterorder;
            coefs = new double[length];
			double singleCoefficient = 1.0/(length);
			for(int i = 0; i < length;  ++i){
				coefs[i] = singleCoefficient;
			}
			impulseResponse = coefs;
			delayLine = new double[length];
        }

	    double getOutputSample(Double inputSample) {
        	        delayLine[count] = inputSample;
        	        Double result = 0.0;
        	        int index = count;
        	        for (int i=0; i<length; i++) {
        	            result += impulseResponse[i] * delayLine[index--];
        	            if (index < 0) index = length-1;
        	        }
             if (++count >= length) count = 0;
             return result;
        }

}