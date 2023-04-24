package com.example.bing;

import android.util.Log;

import java.util.Arrays;

public class NMEADataParser {
// Interface a ser implementada por classes que desejam receber dados NMEA analisados


    public interface NMEADataListener {
        // Método a ser chamado quando os dados NMEA são analisados

        void onNMEADataParsed(double latitude, double longitude);
    }
// Defina o ouvinte de dados NMEA para este analisador

    public void setNMEADataListener(NMEADataListener listener) {
    }
    // Classe para armazenar os dados GGA analisados
    public static class ParsedGGAData {
        public double latitude;
        public double longitude;

        public ParsedGGAData(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;

        }


    }
    // Analisa dados GGA de uma sentença NMEA


    public static ParsedGGAData parseGGAData(String data) {

        String[] sentenceParts = data.split(",");
        if (sentenceParts[0].length() < 3) {
            return null;
        }
        //System.out.println("data: " + data);

        // Obtém o tipo de sentença

        String sentenceType = sentenceParts[0].substring(3);
        // Verifica se a sentença é uma sentença GGA

        if (sentenceType.equals("GGA")) {
            double latitude = 0;
            double longitude = 0;
            // Analisa a latitude e longitude da frase


            if (sentenceParts.length >= 6 && sentenceParts[2].length() >= 4 && sentenceParts[4].length() >= 5) {
                latitude = Double.parseDouble(sentenceParts[2].substring(0, 2));
                latitude += Double.parseDouble(sentenceParts[2].substring(2)) / 60;
                if (sentenceParts[3].equals("S")) {
                    latitude = -latitude;
                }

                longitude = Double.parseDouble(sentenceParts[4].substring(0, 3));
                longitude += Double.parseDouble(sentenceParts[4].substring(3)) / 60;
                if (sentenceParts[5].equals("W")) {
                    longitude = -longitude;
                }



            } else {

                // Imprime uma mensagem de erro se o formato da frase for inválido

                //System.out.println("Invalid sentence format: " + data);
                System.out.println("Invalid sentence format: " + Arrays.toString(sentenceParts));


            }

            // Retorna os dados analisados como um objeto ParsedGGAData


            return new ParsedGGAData(latitude, longitude);
        }



        return null;
    }

}