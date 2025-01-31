package edu.ucdavis.fiehnlab.splash.splashanalysis;

import com.rabbitmq.client.Channel;
import edu.ucdavis.fiehnlab.splash.splashanalysis.algorithms.HistogramSimilarity;
import edu.ucdavis.fiehnlab.splash.splashanalysis.algorithms.SpectralSimilarity;
import edu.ucdavis.fiehnlab.splash.splashanalysis.algorithms.SumSimilarity;

import java.io.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Created by sajjan on 10/27/15.
 */
public class SplashAnalyzer {
    private static DecimalFormat FORMATTER = new DecimalFormat("0.000000");

    private static List<String> MESSAGE_QUEUE = new ArrayList<String>();


    private static void sendResults(Channel sendingChannel, String message) throws IOException {
        MESSAGE_QUEUE.add(message);

        if(MESSAGE_QUEUE.size() >= 10000)
            flushResults(sendingChannel);
    }

    private static void flushResults(Channel sendingChannel) throws IOException {
        if(MESSAGE_QUEUE.size() > 0) {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < MESSAGE_QUEUE.size(); i++) {
                if (i > 0)
                    sb.append('\n');
                sb.append(MESSAGE_QUEUE.get(i));
            }

            MESSAGE_QUEUE.clear();
            sendingChannel.basicPublish("", Application.SENDING_QUEUE_NAME, null, sb.toString().getBytes());
            System.out.println(" [x] Sent message queue");
        }
    }


    public static void analyzeSplashes(int startingIndex, Channel sendingChannel) throws IOException {
        FileInputStream fileReader = new FileInputStream(new File(Application.FILENAME));
        BufferedReader bufferedReader = null;

        if(!Application.FILENAME.toLowerCase().endsWith(".gz")){
            bufferedReader = new BufferedReader(new java.io.InputStreamReader(fileReader));
        } else{
            bufferedReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fileReader)));
        }


        // Skip initial lines
        int idx = 0;

        while(idx < startingIndex) {
            bufferedReader.readLine();
            idx++;
        }

        // Read base spectrum
        Spectrum baseSpectrum = new Spectrum(bufferedReader.readLine());
        System.out.println("Starting queue #"+ startingIndex +", working on spectrum "+ baseSpectrum.origin);


        // Iterate over all remaining spectra
        String line;

        // Start time
        long start_time = System.currentTimeMillis();
        idx = 0;

        while((line = bufferedReader.readLine()) != null) {
            Spectrum spectrum = new Spectrum((line));

            double nominalDotProduct = SpectralSimilarity.dotProductSimilarity(baseSpectrum.nominalBinnedSpectrum, spectrum.nominalBinnedSpectrum);
            double nominalSteinDotProduct = SpectralSimilarity.steinProductSimilarity(baseSpectrum.nominalBinnedSpectrum, spectrum.nominalBinnedSpectrum);
            double accurateDotProduct = SpectralSimilarity.dotProductSimilarity(baseSpectrum.accurateBinnedSpectrum, spectrum.accurateBinnedSpectrum);
            double accurateSteinDotProduct = SpectralSimilarity.steinProductSimilarity(baseSpectrum.accurateBinnedSpectrum, spectrum.accurateBinnedSpectrum);

            double shortHistogramDotProduct = HistogramSimilarity.dotProductSimilarity(baseSpectrum.shortHistogram, spectrum.shortHistogram);
            double longHistogramDotProduct = HistogramSimilarity.dotProductSimilarity(baseSpectrum.longHistogram, spectrum.longHistogram);

            double maxSimilarity = 0;
            maxSimilarity = Math.max(maxSimilarity, nominalDotProduct);
            maxSimilarity = Math.max(maxSimilarity, nominalSteinDotProduct);
            maxSimilarity = Math.max(maxSimilarity, accurateDotProduct);
            maxSimilarity = Math.max(maxSimilarity, accurateSteinDotProduct);
//            maxSimilarity = Math.max(maxSimilarity, shortHistogramDotProduct);
//            maxSimilarity = Math.max(maxSimilarity, longHistogramDotProduct);

            if(maxSimilarity < 1.0e-6) {
                idx++;
                continue;
            }


            StringBuilder sb = new StringBuilder();
            sb.append(baseSpectrum.origin);
            sb.append(',');
            sb.append(spectrum.origin);
            sb.append(',');
            sb.append(baseSpectrum.hash == spectrum.hash);
            sb.append(',');

            sb.append(FORMATTER.format(nominalDotProduct));
            sb.append(',');
            sb.append(FORMATTER.format(nominalSteinDotProduct));
            sb.append(',');
            sb.append(FORMATTER.format(accurateDotProduct));
            sb.append(',');
            sb.append(FORMATTER.format(accurateSteinDotProduct));
            sb.append(',');

            sb.append(HistogramSimilarity.manhattanDistance(baseSpectrum.shortHistogram, spectrum.shortHistogram));
            sb.append(',');
            sb.append(HistogramSimilarity.cyclicManhattanDistance(baseSpectrum.shortHistogram, spectrum.shortHistogram));
            sb.append(',');
            sb.append(HistogramSimilarity.levenshteinDistance(baseSpectrum.shortHistogram, spectrum.shortHistogram));
            sb.append(',');
            sb.append(FORMATTER.format(HistogramSimilarity.chiSquaredDistance(baseSpectrum.shortHistogram, spectrum.shortHistogram)));
            sb.append(',');
            sb.append(FORMATTER.format(HistogramSimilarity.bhattacharyyaDistance(baseSpectrum.shortHistogram, spectrum.shortHistogram)));
            sb.append(',');
            sb.append(FORMATTER.format(shortHistogramDotProduct));
            sb.append(',');

            sb.append(HistogramSimilarity.manhattanDistance(baseSpectrum.longHistogram, spectrum.longHistogram));
            sb.append(',');
            sb.append(HistogramSimilarity.cyclicManhattanDistance(baseSpectrum.longHistogram, spectrum.longHistogram));
            sb.append(',');
            sb.append(HistogramSimilarity.levenshteinDistance(baseSpectrum.longHistogram, spectrum.longHistogram));
            sb.append(',');
            sb.append(FORMATTER.format(HistogramSimilarity.chiSquaredDistance(baseSpectrum.longHistogram, spectrum.longHistogram)));
            sb.append(',');
            sb.append(FORMATTER.format(HistogramSimilarity.bhattacharyyaDistance(baseSpectrum.longHistogram, spectrum.longHistogram)));
            sb.append(',');
            sb.append(',');
            sb.append(FORMATTER.format(longHistogramDotProduct));
            sb.append(',');

            sb.append(SumSimilarity.difference(baseSpectrum.sum, spectrum.sum));
            sb.append(',');
            sb.append(SumSimilarity.difference(baseSpectrum.preciseSum, spectrum.preciseSum));

            sendResults(sendingChannel, sb.toString());

            idx++;
            if(idx % 10000 == 0) {
                System.out.println(logStatus(startingIndex, baseSpectrum.origin, idx, start_time));
            }
        }

        flushResults(sendingChannel);

        System.out.println(logStatus(startingIndex, baseSpectrum.origin, idx, start_time, true));
    }


    private static String logStatus(int startingIndex, String origin, int idx, long start_time) {
        return logStatus(startingIndex, origin, idx, start_time, false);
    }

    private static String logStatus(int startingIndex, String origin, int idx, long start_time, boolean completed) {
        StringBuilder sb = new StringBuilder("Queue #");

        if(completed)
            sb.append("Finished processing ");

        sb.append(startingIndex);
        sb.append(" (");
        sb.append(origin);
        sb.append("): processed ");
        sb.append(idx);
        sb.append(" in ");

        if(completed) {
            sb.append(String.format("%.3f", (double)(System.currentTimeMillis() - start_time) / 1000));
            sb.append(" s");
        } else {
            sb.append(String.format("%.3f", (double)(System.currentTimeMillis() - start_time) / idx));
            sb.append(" ms / spectrum");
        }

        return sb.toString();
    }
}
