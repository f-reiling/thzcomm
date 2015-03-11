/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.selfhost.reiling;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.*;
import org.json.*;

/**
 *
 * @author Florian Reiling
 */
public class THZReader {

    THZReader reader;
    THZComm thz;
    JSONObject thzConfig;
    DatagramSocket serverSocket;
    static final Logger logger = LogManager.getLogger(THZReader.class.getName());
    JSONArray thzCommandHistory = new JSONArray();
    
    private static final Long THZ_REPEAT_TIMEOUT = 5000L; // if command has been sent within this time, do not request again

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        // TODO code application logic here
        THZReader reader = new THZReader();
        reader.run();

        System.exit(0);
    }

    public THZReader() {

    }

    public void run() throws InterruptedException {
        try {
            thzConfig = new JSONObject(
                    new String(Files.readAllBytes(Paths.get("object.json"))));
        } catch (IOException ex) {
            //Logger.getLogger(THZReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        thz = new THZComm(thzConfig.getString("thzComPort"));
        thz.openThzComm();

        listAllValues();

        /*reader.readFromTHZ("elecEnergyHCDaily");
         reader.readFromTHZ("heatEnergyHCDaily");
         reader.readFromTHZ("elecEnergyDHWDaily");
         reader.readFromTHZ("heatEnergyDHWDaily");
         reader.readFromTHZ("dhw_temp");
         reader.readFromTHZ("hc1_flow_temp");
         reader.readFromTHZ("outside_temp");
         reader.readFromTHZ("status_dhw_pump");
         reader.readFromTHZ("booster_stage1");*/
        // TODO: implement UDP server and listen for requests
        try {
            serverSocket = new DatagramSocket(9876);
            //serverSocket.setSoTimeout(500);

            while (true) {
                byte[] receiveData = new byte[256];
                //byte[] sendData = new byte[256];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                //Thread.sleep(Math.round(Math.random()*1000.0));
                handleUDPRequest(receivePacket);
            }
        } catch (SocketException ex) {
            //Logger.getLogger(THZReader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            //Logger.getLogger(THZReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Check for status value in json-file and request from THZ
     *
     * @param value - data value to request from thz
     * @return JSONObject with result (contains also unit and description)
     */
    private JSONObject readFromTHZ(String value) {

        JSONObject obj = findCommand(value);

        if (obj != null) {
            boolean multiCommand = false;
            String result1 = null;
            String result2 = null;
            Object resultObj2 = null;
            Object resultObj = null;

            result1 = getResponseFromThz(obj.getString("command"));

            //TODO: TEST ONLY! 
            //result1 = "0A091E000F000000000000000000000000000000001040";
            // TODO: parse result as defined in JSON-File
            if (result1 == null) {
                return null;
            }
            resultObj = parseThzResult(obj, result1);
            if (obj.has("command2")) {
                result2 = getResponseFromThz(obj.getJSONObject("command2").getString("command"));

                //TODO: TEST ONLY!
                //result2 = "0A091F00FF";
                if (result2 == null) {
                    logger.error("No result for second command!");
                } else {
                    multiCommand = true;
                    resultObj2 = parseThzResult(obj.getJSONObject("command2"), result2);
                }
            }
            if (multiCommand == true) {
                if (resultObj instanceof Integer) {
                    Integer integer = (Integer) resultObj;
                    integer += (Integer) resultObj2;
                    resultObj = integer;
                }
                if (resultObj instanceof Double) {
                    Double dbl = (Double) resultObj;
                    dbl += (Double) resultObj2;
                    resultObj = dbl;
                }
            }

            obj.put("result", resultObj);

            logger.debug(obj.getString("description") + ": " + resultObj.toString() + obj.getString("unit"));
            return obj;
            //return resultObj;

        } else {
            logger.error("value not found");
        }
        return null;
    }

    private Object parseThzResult(JSONObject json, String resultFromTHZ) {

        try {
            int startPos = json.getInt("start");
            int length = json.getInt("length");
            String parseType = json.getString("type");
            int divisor = 1;
            int factor = 1;
            if (json.has("divisor")) {
                divisor = json.getInt("divisor");
            }
            if (json.has("factor")) {
                factor = json.getInt("factor");
            }

            String dataString = resultFromTHZ.substring(startPos, startPos + length);

            switch (parseType) {
                case "hex2int":
                    int resultValue = (short) Integer.parseInt(dataString, 16);
                    if (length == 8) {
                        resultValue = Integer.parseInt(dataString, 16);
                    }
                    resultValue = resultValue * factor;
                    resultValue = resultValue / divisor;
                    //TODO: added random value for SIMULATION
                    return resultValue;// + Math.round(Math.random()*100.0);

                case "hex2dbl":
                    double resultDbl = (short) Integer.parseInt(dataString, 16);
                    resultDbl = resultDbl * factor;
                    resultDbl = resultDbl / divisor;
                    //TODO: added random value for SIMULATION
                    return resultDbl;// + Math.round(Math.random()*100.0);

                case "string":
                    return dataString;

                case "bit0":
                case "bit1":
                case "bit2":
                case "bit3":
                    int bitPos = Integer.parseInt(parseType.substring(3));
                    byte resultByte = Byte.parseByte(dataString, 16);
                    if ((resultByte & (0x01 << bitPos)) == 1) {
                        return 1;
                    }
                    return 0;

                case "opmode":
                    int OpModeValue = Integer.parseInt(dataString, 16);
                // TODO: implement opmode parser
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * find command in json file
     *
     * @param dataField
     * @return JSONObject which contains command to send to THZ to get the
     * requested value
     */
    private JSONObject findCommand(String dataField) {
        dataField = dataField.trim();
        try {
            JSONArray arr = thzConfig.getJSONArray("statusValues");
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).getString("value").equalsIgnoreCase(dataField)) {
                    return arr.getJSONObject(i);
                }
            }
        } catch (JSONException ex) {
            //Logger.getLogger(THZReader.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    private void listAllValues() {
        try {
            JSONArray arr = thzConfig.getJSONArray("statusValues");
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).has("value")) {
                    System.out.println(String.format("value: %s -> %s",
                            arr.getJSONObject(i).getString("value"),
                            arr.getJSONObject(i).getString("description"))
                    );
                }
            }
        } catch (JSONException ex) {
            //Logger.getLogger(THZReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void handleUDPRequest(DatagramPacket receivePacket) {
        try {
            InetAddress IPAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();

            String sentence = new String(receivePacket.getData()).trim();

            logger.info("UDP-REQUEST [" + IPAddress.getHostAddress() + "]: " + sentence);

            if (sentence.contentEquals("exit")) {
                return;
            }
            JSONObject reqObj = new JSONObject(sentence);

            reqObj = (JSONObject) reqObj.get("request");

            // TODO: parse data and request from THZ
            JSONObject result = readFromTHZ(reqObj.getString("dataField"));

            JSONObject resultObj = new JSONObject();
            resultObj.put("dataField", reqObj.getString("dataField"));
            resultObj.put("unit", result.getString("unit"));
            resultObj.put("timestamp", System.currentTimeMillis());

            if (result == null) {
                resultObj.put("value", "invalid");
            } else {
                resultObj.put("value", result.get("result"));
            }

            JSONObject resObj = new JSONObject();
            resObj.put("request", reqObj);
            resObj.put("response", resultObj);

            logger.info("UDP-RESPONSE [" + IPAddress.getHostAddress() + "]: " + resObj.toString());

            byte[] sendData = resObj.toString().getBytes();

            //String capitalizedSentence = sentence.toUpperCase();
            //sendData = capitalizedSentence.getBytes();
            DatagramPacket sendPacket
                    = new DatagramPacket(sendData, sendData.length, IPAddress, port);
            serverSocket.send(sendPacket);
        } catch (IOException ex) {
            //Logger.getLogger(THZReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Request a command from THZ. Check if command has be sent 
     * @param command
     * @return 
     */
    String getResponseFromThz(String command) {

        //TODO: check if command was executed within last 5 seconds
        /*[
         {"command":"F3",
         "timestamp":123456789,
         "response":"0012FABC"
         },
         {"command":"F4",
         "timestamp":123450789,
         "response":"0014DA2C"
         }
         ]
         */
        //if yes -> parse last response
        //if no -> request new data
        JSONObject comm = null;
        Long curTime = System.currentTimeMillis();
        Long sentTime = 0L;
        String response = "";
        for (int i = 0; i < thzCommandHistory.length(); i++) {
            if (thzCommandHistory.getJSONObject(i).getString("command").equalsIgnoreCase(command)) {
                // command found
                comm = thzCommandHistory.getJSONObject(i);
                sentTime = thzCommandHistory.getJSONObject(i).optLong("timestamp", 0);
                break;
            }
        }
        // command was placed in past
        if (comm != null) {
            Long diffTime = curTime - sentTime;
            if (diffTime < THZ_REPEAT_TIMEOUT) {
                response = comm.getString("response");
            } else {
                response = thz.requestFromThz(command);
                comm.put("timestamp", curTime);
                comm.put("response", response);
            }
        } else {
            comm = new JSONObject();
            response = thz.requestFromThz(command);
            comm.put("command", command);
            comm.put("timestamp", curTime);
            comm.put("response", response);
            thzCommandHistory.put(comm);
        }

        return response;
    }
}
