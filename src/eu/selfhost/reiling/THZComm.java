/*
 * 
 */
package eu.selfhost.reiling;

import gnu.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import org.apache.logging.log4j.*;

/**
 * THZComm class is responsible for communication with THZ
 *
 * @author Florian Reiling
 */
public class THZComm {

    public enum Baudrate {

        B9600(9600),
        B19200(19200),
        B57600(57600),
        B115200(115200);

        private int _baudrate;

        Baudrate(int baudrate) {
            this._baudrate = baudrate;
        }

        public int getValue() {
            return this._baudrate;
        }

    }

// <editor-fold defaultstate="collapsed" desc=" CONSTANTS ">
    private static final String COM_STX = "02";
    private static final String COM_ETX = "03";
    private static final String COM_ACK = "10";
    private static final String COM_NAK = "15";
    private static final int COM_TIMEOUT = 100;
// </editor-fold>
    
// <editor-fold defaultstate="collapsed" desc=" object members ">
    private String _portName = null;
    private Integer _baudRate = null;
    int _dataBits = SerialPort.DATABITS_8;
    int _stopBits = SerialPort.STOPBITS_1;
    int _parity = SerialPort.PARITY_NONE;
    private boolean _serialPortOpen = false;
    SerialPort _serialPort;
    OutputStream _outputStream;
    InputStream _inputStream;
    static final Logger logger = LogManager.getLogger(THZComm.class.getName());
// </editor-fold>
    
    public THZComm(String portName) {
        this._portName = portName;
        setBaudrate(Baudrate.B115200);
    }

    public THZComm() {
        //throw new Exception("not possible");
    }

    private void setBaudrate(Baudrate baudrate) {
        this._baudRate = baudrate.getValue();
    }

//<editor-fold defaultstate="collapsed" desc="Open communication port for THZ">
    /**
     * Opens com-port for communication with THZ
     *
     * @return
     */
    public boolean openThzComm() {
        // open thz comm port

        CommPortIdentifier serialPortId = null;
        Enumeration enumComm;
        Boolean foundPort = false;
        if (_serialPortOpen != false) {
            logger.error("Serialport already opened");
            return false;
        }
        logger.info("Open Serialport");
        enumComm = CommPortIdentifier.getPortIdentifiers();
        while (enumComm.hasMoreElements()) {
            serialPortId = (CommPortIdentifier) enumComm.nextElement();
            if (_portName.contentEquals(serialPortId.getName())) {
                foundPort = true;
                break;
            }
        }
        if (foundPort != true) {
            logger.error("Serialport not found: " + _portName);
            return false;
        } else { // port found
            try {
                _serialPort = (SerialPort) serialPortId.open("Open and Send", 500);
            } catch (PortInUseException e) {
                logger.error("Port in use");
            }

            try {
                _outputStream = _serialPort.getOutputStream();
            } catch (IOException e) {
                logger.error("No Acces to OutputStream");
            }

            try {
                _inputStream = _serialPort.getInputStream();
            } catch (IOException e) {
                logger.error("No Acces to InputStream");
            }
            try {
                _serialPort.addEventListener(new serialPortEventListener());
            } catch (TooManyListenersException e) {
                logger.error("TooManyListenersException for Serialport");
            }
            _serialPort.notifyOnDataAvailable(true);
            try {
                _serialPort.setSerialPortParams(_baudRate, _dataBits, _stopBits, _parity);
            } catch (UnsupportedCommOperationException e) {
                logger.error("Not poosible to set communication parameters");
            }

            _serialPortOpen = true;
            return true;
        }
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Close communication port">
    /**
     * Close com port
     */
    public void closeThzComm() {
        // close thz comm port
        if (_serialPortOpen == true) {
            logger.debug("Close Serialport");
            _serialPort.close();
            _serialPortOpen = false;
        } else {
            logger.error("Serialport already closed");
        }
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Request Data from THZ">
    /**
     * Request data from THZ
     *
     * @param command
     * @return returns the data from THZ (without header/checksum/footer)
     */
    public String requestFromThz(String command) {

        logger.debug("THZ-command: " + command);

        if (this._serialPortOpen != true) {
            logger.error("Port closed");
            // TODO: SIM only!
            return getTHZSimulatorValue(command);
            //return "0A091E000F000000000000000000000000000000001040";
            //return null;
        }

        // 1. write COM_STX
        writeToThz(COM_STX);

        // 2. wait for COM_ACK
        if (readFromThz(COM_ACK, COM_TIMEOUT) == null) {
            return null;
        }

        // 3. send command
        writeToThz(encodeThzDataMsg(command));

        // 4. wait for ack + "02" -> means data available
        if (readFromThz(COM_ACK + COM_STX, COM_TIMEOUT) == null) {
            return null;
        }

        // 5. send COM_ACK
        writeToThz(COM_ACK);

        // 6. read data, wait for COM_ACK+COM_ETX ("10 03"), minimum String length = 6 (header+chksum+footer)
        String readData = readFromThz(COM_ACK + COM_ETX, COM_TIMEOUT, 6);
        if (readData == null) {
            writeToThz(COM_NAK);
            logger.error("invalid Response received.");
            return null;
        }

        // 7. send COM_ACK
        writeToThz(COM_ACK);

        // 8. parse data and return
        readData = decodeThzResponse(readData);

        logger.debug("THZ-Response: " + readData);
        return readData;
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Write Data to THZ">
    /**
     * write message via Com-Port to THZ
     *
     * @param msg
     */
    private void writeToThz(String msg) {
        logger.debug("LL-SEND: 0x" + msg);
        if (_serialPortOpen != true) {
            return;
        }
        try {
            //_outputStream.write(msg.getBytes());
            _outputStream.write(hexStringToByteArray(msg));
        } catch (IOException e) {
            logger.error("send error");
        }
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Read data from THZ">
    /**
     * read data from thz
     *
     * @param end - data which marks end of message
     * @param timeout
     * @param minimumLength - minimum length of read Data
     * @return
     */
    private String readFromThz(String end, int timeout) {
        return readFromThz(end, timeout, 0);
    }

    private String readFromThz(String end, int timeout, int minimumLength) {
        // TODO: wait for response from Thz until "end" is received or timout reached
        StringBuilder recvData = new StringBuilder("");
        int startIndex = Math.max(minimumLength - end.length(), 0);
        int rec;
        try {
            while (timeout > 0) {
                while (_inputStream.available() > 0) {
                    rec = _inputStream.read();
                    recvData.append(String.format("%02X", rec));
                }
                if (recvData.indexOf(end, startIndex) > -1) {
                    logger.debug("LL-RECV: 0x" + recvData.toString());
                    return recvData.toString();
                }

                timeout--;
                Thread.sleep(5);
            }
        } catch (InterruptedException | IOException ex) {
            //Logger.getLogger(THZComm.class.getName()).log(Level.SEVERE, null, ex);
        }
        logger.error("TIMEOUT!");
        return null;
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Encode THZ message">
    /**
     * encode message, calc checksum, replace "10" by "1010" and "2B" by "2B18"
     *
     * @param msg
     * @return
     */
    static String encodeThzDataMsg(String msg) {
        StringBuilder dataMsg = new StringBuilder("0100");
        dataMsg.append("FF");
        //msg = msg.replace("10", "1010");
        //msg = msg.replace("2B", "2B18");
        dataMsg.append(msg);
        dataMsg.append("1003");

        int checksum = calcThzChecksum(dataMsg.toString());

        String checkSumStr = bytesToHex(new byte[]{(byte) checksum});
        dataMsg.replace(4, 6, checkSumStr);

        dataMsg = new StringBuilder("0100");
        dataMsg.append(checkSumStr);
        
        msg = replaceDataString(msg, "10", "1010");
        msg = replaceDataString(msg, "2B", "2B18");
        dataMsg.append(msg);
        dataMsg.append("1003");

        //dataMsg = new StringBuilder(dataMsg.substring(4, dataMsg.length()-4).toString().replace("10", "1010"));
        //dataMsg = new StringBuilder(dataMsg.substring(4, dataMsg.length()-4).toString().replace("2B", "2B18"));
        return dataMsg.toString();
    }
//</editor-fold>

// <editor-fold defaultstate="collapsed" desc="Decode THZ response">
    /**
     * replaces escape strings, return response without header/footer
     *
     * @param data
     */
    static String decodeThzResponse(String data) {
        data = replaceDataString(data, "1010", "10");
        data = replaceDataString(data, "2B18", "2B");

        int checksum = calcThzChecksum(data);
        int checksumInMsg = Integer.parseInt(data.substring(4, 6), 16);

        if (checksum != checksumInMsg) {
            //throw new Exception("Checksum mismatch!");
            logger.error("Checksum mismatch!");
            //return null;
        }

        return data.substring(6, data.length() - 4);
    }

// </editor-fold>
    
// <editor-fold defaultstate="collapsed" desc="Calculate THZ Checksum">
    /**
     * calc checksum: add all bytes, except dummy byte ([2]) and footer for
     * checksum
     *
     * @param msg (0100FFAABBCCDD1003)
     * @return
     */
    static int calcThzChecksum(String msg) {
        int checksum = 0;
        byte[] data = hexStringToByteArray(msg);
        for (int i = 0; i < data.length - 2; i++) {
            if (i != 2) {
                checksum += ((int) data[i]) & 0xFF;
            }
        }
        logger.debug(String.format("checksum: 0x%04X", checksum));
        return (checksum & 0xFF);
    }

// </editor-fold>
    
//<editor-fold defaultstate="collapsed" desc="Support functions">
    static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    /**
     * replaces hex-data in data string
     * @param msg - original message
     * @param search - search data which shall be replaced
     * @param replace - replace data
     * @return String with replaced data
     */
    static String replaceDataString(String msg, String search, String replace){
        StringBuilder newMsg = new StringBuilder(msg);
        StringBuilder searchString = new StringBuilder(search);
        StringBuilder replaceString = new StringBuilder(replace);
        for (int i = 2; i < newMsg.length(); i=i+3) {
            newMsg.insert(i, ".");
        }
        for (int i = 2; i < searchString.length(); i=i+3) {
            searchString.insert(i, ".");
        }
        for (int i = 2; i < replaceString.length(); i=i+3) {
            replaceString.insert(i, ".");
        }
        
        String returnStr = newMsg.toString().replace(searchString.toString(), replaceString.toString());
        //returnStr = returnStr.replace(searchString, replaceString);
        
        returnStr = returnStr.toString().replace(".", "");
        
        return returnStr;
    }
//</editor-fold>
    
//<editor-fold defaultstate="collapsed" desc="response simulator">
    private String getTHZSimulatorValue(String command) {
        String response = null;
        switch (command) {
            case "F3":
                response = "F30194FFF9017C00002008000000020002DC5A";
                break;
            case "F4":
                response = "F4FFF901E9012C000F0149012D013401000201200800640200000000D20000160000D20200001600";
                break;
            case "FB":
                response = "FBFDA8FFF90149012C032C0194FDA8FDA8FFBF0131200811010E010E02BC000C0014001400060000000001320599409F2D253FD1374C";
                break;
            case "0A091A":
                response = "0A091A037D";
                break;
            case "0A091B":
                response = "0A091B0004";
                break;
            case "0A0920":
                response = "0A092002BB";
                break;
            case "0A0921":
                response = "0A09210008";
                break;
            case "0A092A":
                response = "0A092A0290";
                break;
            case "0A092B":
                response = "0A092B0009";
                break;
            case "0A092C":
                response = "0A092D0290";
                break;
            case "0A092D":
                response = "0A092D0003";
                break;
            case "0A092E":
                response = "0A092E02BB";
                break;
            case "0A092F":
                response = "0A092F0028";
                break;
            case "0A0930":
                response = "0A093002BB";
                break;
            case "0A0931":
                response = "0A09310008";
                break;
            case "0A091C":
                response = "0A091C03A3";
                break;
            case "0A091D":
                response = "0A091D000D";
                break;
            case "0A091E":
                response = "0A091E03D3";
                break;
            case "0A091F":
                response = "0A091F000D";
                break;
            case "FC":
                response = "FC050B28120E030B";
                break;
        }
        return response;
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Event Listener for serial port">
    class serialPortEventListener implements SerialPortEventListener {

        @Override
        public void serialEvent(SerialPortEvent event) {
            //System.out.println("serialPortEventlistener");
            switch (event.getEventType()) {
                case SerialPortEvent.DATA_AVAILABLE:
                    //serialPortDatenVerfuegbar();
                    break;
                case SerialPortEvent.BI:
                case SerialPortEvent.CD:
                case SerialPortEvent.CTS:
                case SerialPortEvent.DSR:
                case SerialPortEvent.FE:
                case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                case SerialPortEvent.PE:
                case SerialPortEvent.RI:
                default:
            }
        }
    }
//</editor-fold>
}
