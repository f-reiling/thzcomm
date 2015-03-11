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

import java.util.logging.Level;
import java.util.logging.Logger;

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
            System.out.println("Serialport bereits geöffnet");
            return false;
        }
        System.out.println("Öffne Serialport");
        enumComm = CommPortIdentifier.getPortIdentifiers();
        while (enumComm.hasMoreElements()) {
            serialPortId = (CommPortIdentifier) enumComm.nextElement();
            if (_portName.contentEquals(serialPortId.getName())) {
                foundPort = true;
                break;
            }
        }
        if (foundPort != true) {
            System.out.println("Serialport nicht gefunden: " + _portName);
            return false;
        } else { // port found
            try {
                _serialPort = (SerialPort) serialPortId.open("Öffnen und Senden", 500);
            } catch (PortInUseException e) {
                System.out.println("Port belegt");
            }

            try {
                _outputStream = _serialPort.getOutputStream();
                //outputStream = System.out;
            } catch (IOException e) {
                System.out.println("Keinen Zugriff auf OutputStream");
            }

            try {
                _inputStream = _serialPort.getInputStream();
                //inputStream = System.in;
            } catch (IOException e) {
                System.out.println("Keinen Zugriff auf InputStream");
            }
            try {
                _serialPort.addEventListener(new serialPortEventListener());
            } catch (TooManyListenersException e) {
                System.out.println("TooManyListenersException für Serialport");
            }
            _serialPort.notifyOnDataAvailable(true);
            try {
                _serialPort.setSerialPortParams(_baudRate, _dataBits, _stopBits, _parity);
            } catch (UnsupportedCommOperationException e) {
                System.out.println("Konnte Schnittstellen-Paramter nicht setzen");
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
            System.out.println("Schließe Serialport");
            _serialPort.close();
            _serialPortOpen = false;
        } else {
            System.out.println("Serialport bereits geschlossen");
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

        System.out.println("Request: " + command);

        if (this._serialPortOpen != true) {
            System.out.println("Port closed");
            return null;
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

        // 6. read data, wait for COM_ACK+COM_ETX ("10 03")
        String readData = readFromThz(COM_ACK + COM_ETX, COM_TIMEOUT);
        if (readData == null) {
            return null;
        }

        // 7. send COM_ACK
        writeToThz(COM_ACK);

        // 8. parse data and return
        try {
            readData = decodeThzResponse(readData);
            // 9. send COM_ACK //OBSOLETE!!!
            //writeToThz(COM_ACK);

            System.out.println("Response: " + readData);
            return readData;
        } catch (Exception ex) {
            Logger.getLogger(THZComm.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Write Data to THZ">
    /**
     * write message via Com-Port to THZ
     *
     * @param msg
     */
    private void writeToThz(String msg) {
        System.out.println("LL-SEND: 0x" + msg);
        if (_serialPortOpen != true) {
            return;
        }
        try {
            //_outputStream.write(msg.getBytes());
            _outputStream.write(hexStringToByteArray(msg));
        } catch (IOException e) {
            System.out.println("Fehler beim Senden");
        }
    }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Read data from THZ">
    /**
     * read data from thz
     *
     * @param end - data which marks end of message
     * @param timeout
     * @return
     */
    private String readFromThz(String end, int timeout) {
        // TODO: wait for response from Thz until "end" is received or timout reached
        StringBuilder recvData = new StringBuilder("");
        int startIndex = 0;
        int rec;
        //TODO: Workaround: If 1003 is inside message -> checksum will fail!
        if (end.equalsIgnoreCase("1003")) startIndex = 3;
        try {
            while (timeout > 0) {
                while (_inputStream.available() > 0) {
                    rec = _inputStream.read();
                    recvData.append(String.format("%02X", rec));
                }
                if (recvData.indexOf(end,startIndex) > -1) {
                    System.out.println("LL-RECV: 0x" + recvData.toString());
                    return recvData.toString();
                }

                timeout--;
                Thread.sleep(1);
            }
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(THZComm.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("TIMEOUT!");
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
        msg = msg.replace("10", "1010");
        msg = msg.replace("2B", "2B18");
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
    static String decodeThzResponse(String data) throws Exception {
        data = data.replaceAll("1010", "10");
        data = data.replaceAll("2B18", "2B");

        int checksum = calcThzChecksum(data);
        int checksumInMsg = Integer.parseInt(data.substring(4, 6), 16);

        if (checksum != checksumInMsg) {
            throw new Exception("Checksum mismatch!");
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
        System.out.println(String.format("CHK: 0x%04X", checksum));
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
//</editor-fold>

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
}
